package com.workpulse.service;

import com.workpulse.dto.*;
import com.workpulse.entity.*;
import com.workpulse.exception.BadRequestException;
import com.workpulse.exception.ConflictException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.DepartmentRepository;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.UserRepository;
import com.workpulse.security.SessionRevocationService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmployeeService {

    /** Whitelist of client sort keys -> entity property paths. */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "user.username", "email", "user.email", "code", "employeeCode",
            "joinDate", "joinDate", "status", "status", "department", "department.name");

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final UserProvisioningService provisioning;
    private final AccessService access;
    private final EmployeeMapper mapper;
    private final SessionRevocationService revocation;
    private final AuditService audit;

    public EmployeeService(EmployeeRepository employeeRepository, UserRepository userRepository,
                           DepartmentRepository departmentRepository, UserProvisioningService provisioning,
                           AccessService access, EmployeeMapper mapper, SessionRevocationService revocation,
                           AuditService audit) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.provisioning = provisioning;
        this.access = access;
        this.mapper = mapper;
        this.revocation = revocation;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDtos.Response> list(String q, Long departmentId, EmployeeStatus status,
                                                    String sort, String dir, int page, int size) {
        User viewer = access.currentUser();
        Employee viewerEmp = access.currentEmployee();
        boolean admin = access.isAdmin(viewer);
        // Non-admins only see active colleagues in the directory.
        EmployeeStatus effectiveStatus = admin ? status : EmployeeStatus.ACTIVE;

        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<Employee, User> user = root.join("user", JoinType.INNER);
            if (q != null && !q.isBlank()) {
                String like = "%" + AuditService.escapeLike(q.trim().toLowerCase()) + "%";
                p.add(cb.or(cb.like(cb.lower(user.get("username")), like, '\\'),
                            cb.like(cb.lower(user.get("email")), like, '\\'),
                            cb.like(cb.lower(root.get("employeeCode")), like, '\\')));
            }
            if (departmentId != null) p.add(cb.equal(root.get("department").get("id"), departmentId));
            if (effectiveStatus != null) p.add(cb.equal(root.get("status"), effectiveStatus));
            return cb.and(p.toArray(new Predicate[0]));
        };

        String property = SORTABLE.getOrDefault(sort == null ? "name" : sort, "user.username");
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Page<Employee> result = employeeRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), AuditService.clamp(size), Sort.by(direction, property)));
        return PageResponse.of(result, e -> mapper.toResponse(e, access.canViewPrivate(viewer, viewerEmp, e)));
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.Response get(Long id) {
        User viewer = access.currentUser();
        Employee viewerEmp = access.currentEmployee();
        Employee e = find(id);
        if (!access.isAdmin(viewer) && e.getStatus() != EmployeeStatus.ACTIVE && !e.getId().equals(viewerEmp.getId())) {
            throw new ResourceNotFoundException("Employee not found");
        }
        return mapper.toResponse(e, access.canViewPrivate(viewer, viewerEmp, e));
    }

    /** Admin creates an account together with its employee profile. Role is chosen by the admin only. */
    @Transactional
    public UserResponse adminCreate(AdminCreateUserRequest req) {
        Department department = req.getDepartmentId() == null ? null : findDepartment(req.getDepartmentId());
        Employee manager = req.getManagerId() == null ? null : resolveManager(req.getManagerId(), null);
        Employee created = provisioning.provision(req.getUsername(), req.getEmail(), req.getPassword(), req.getRole(),
                department, manager, req.getDesignation(), req.getPhone(), req.getJoinDate());
        audit.logCurrent("USER_CREATED", "USER", created.getUser().getUserId(),
                "email=" + created.getUser().getEmail() + ", role=" + req.getRole());
        return AuthService.toUserResponse(created.getUser(), created,
                "Account created successfully with " + req.getRole() + " role");
    }

    @Transactional
    public EmployeeDtos.Response update(Long id, EmployeeDtos.UpdateRequest req) {
        Employee e = find(id);
        List<String> changed = new ArrayList<>();
        if (!e.getUser().getUsername().equals(req.username().trim())) changed.add("name");
        Long oldDept = e.getDepartment() == null ? null : e.getDepartment().getId();
        if (!java.util.Objects.equals(oldDept, req.departmentId())) changed.add("department");
        Long oldMgr = e.getManager() == null ? null : e.getManager().getId();
        if (!java.util.Objects.equals(oldMgr, req.managerId())) changed.add("manager");
        if (!java.util.Objects.equals(e.getDesignation(), UserProvisioningService.blankToNull(req.designation()))) changed.add("designation");
        if (!java.util.Objects.equals(e.getPhone(), UserProvisioningService.blankToNull(req.phone()))) changed.add("phone");
        if (!e.getJoinDate().equals(req.joinDate())) changed.add("joinDate");

        e.getUser().setUsername(req.username().trim());
        e.setDepartment(req.departmentId() == null ? null : findDepartment(req.departmentId()));
        e.setManager(req.managerId() == null ? null : resolveManager(req.managerId(), e));
        e.setDesignation(UserProvisioningService.blankToNull(req.designation()));
        e.setPhone(UserProvisioningService.blankToNull(req.phone()));
        e.setJoinDate(req.joinDate());
        userRepository.save(e.getUser());
        employeeRepository.save(e);
        audit.logCurrent("EMPLOYEE_UPDATED", "EMPLOYEE", e.getId(), "changed=" + changed);
        return mapper.toResponse(e, true);
    }

    /** Activates or deactivates the account and the employee profile together. */
    @Transactional
    public EmployeeDtos.Response setActive(Long id, boolean active) {
        Employee e = find(id);
        User admin = access.currentUser();
        User target = e.getUser();
        if (!active) {
            if (target.getUserId().equals(admin.getUserId())) {
                throw new ConflictException("You cannot deactivate your own account");
            }
            if (target.getRole() == Role.ADMIN && target.isEnabled()
                    && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
                throw new ConflictException("Cannot deactivate the last active administrator");
            }
            if (employeeRepository.existsByManager_Id(e.getId())) {
                throw new ConflictException("Reassign this employee's direct reports before deactivating");
            }
        }
        target.setEnabled(active);
        e.setStatus(active ? EmployeeStatus.ACTIVE : EmployeeStatus.INACTIVE);
        userRepository.save(target);
        employeeRepository.save(e);
        if (!active) revocation.revokeAllSessions(target.getEmail());
        audit.logCurrent(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", target.getUserId(),
                "target=" + target.getEmail());
        return mapper.toResponse(e, true);
    }

    @Transactional
    public EmployeeDtos.Response changeRole(Long id, Role newRole) {
        Employee e = find(id);
        User admin = access.currentUser();
        User target = e.getUser();
        if (target.getUserId().equals(admin.getUserId())) {
            throw new ConflictException("You cannot change your own role");
        }
        Role oldRole = target.getRole();
        if (oldRole == newRole) return mapper.toResponse(e, true);
        if (oldRole == Role.ADMIN && target.isEnabled() && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("Cannot demote the last active administrator");
        }
        if (newRole == Role.EMPLOYEE && employeeRepository.existsByManager_Id(e.getId())) {
            throw new ConflictException("Reassign this person's direct reports before making them a plain employee");
        }
        target.setRole(newRole);
        userRepository.save(target);
        revocation.revokeAllSessions(target.getEmail());
        audit.logCurrent("ROLE_CHANGED", "USER", target.getUserId(),
                "target=" + target.getEmail() + ", " + oldRole + " -> " + newRole);
        return mapper.toResponse(e, true);
    }

    private Employee find(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> new BadRequestException("Department does not exist"));
    }

    /** A manager must be an active MANAGER/ADMIN, not the employee themself, and must not create a reporting cycle. */
    private Employee resolveManager(Long managerId, Employee subject) {
        Employee m = employeeRepository.findById(managerId)
                .orElseThrow(() -> new BadRequestException("Manager does not exist"));
        if (m.getStatus() != EmployeeStatus.ACTIVE) throw new BadRequestException("Manager must be an active employee");
        Role r = m.getUser().getRole();
        if (r != Role.MANAGER && r != Role.ADMIN) throw new BadRequestException("Manager must have the MANAGER or ADMIN role");
        if (subject != null) {
            Employee cursor = m;
            for (int depth = 0; cursor != null && depth < 50; depth++) {
                if (cursor.getId().equals(subject.getId())) {
                    throw new BadRequestException("Invalid manager: this would create a reporting loop");
                }
                cursor = cursor.getManager();
            }
        }
        return m;
    }
}
