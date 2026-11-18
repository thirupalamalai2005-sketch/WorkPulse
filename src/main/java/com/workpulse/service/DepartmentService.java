package com.workpulse.service;

import com.workpulse.dto.OrgDtos;
import com.workpulse.entity.Department;
import com.workpulse.entity.Employee;
import com.workpulse.exception.BadRequestException;
import com.workpulse.exception.ConflictException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.DepartmentRepository;
import com.workpulse.repository.EmployeeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService audit;

    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository,
                             AuditService audit) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrgDtos.DepartmentResponse> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : employeeRepository.countGroupedByDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return departmentRepository.findAll(Sort.by("name")).stream()
                .map(d -> new OrgDtos.DepartmentResponse(d.getId(), d.getName(), d.getDescription(),
                        counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional
    public OrgDtos.DepartmentResponse create(OrgDtos.DepartmentRequest req) {
        String name = req.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A department named '" + name + "' already exists");
        }
        Department d = new Department();
        d.setName(name);
        d.setDescription(UserProvisioningService.blankToNull(req.description()));
        d = departmentRepository.save(d);
        audit.logCurrent("DEPARTMENT_CREATED", "DEPARTMENT", d.getId(), "name=" + name);
        return new OrgDtos.DepartmentResponse(d.getId(), d.getName(), d.getDescription(), 0);
    }

    @Transactional
    public OrgDtos.DepartmentResponse update(Long id, OrgDtos.DepartmentRequest req) {
        Department d = find(id);
        String name = req.name().trim();
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A department named '" + name + "' already exists");
        }
        d.setName(name);
        d.setDescription(UserProvisioningService.blankToNull(req.description()));
        departmentRepository.save(d);
        audit.logCurrent("DEPARTMENT_UPDATED", "DEPARTMENT", d.getId(), "name=" + name);
        return new OrgDtos.DepartmentResponse(d.getId(), d.getName(), d.getDescription(),
                employeeRepository.countByDepartment_Id(id));
    }

    /**
     * Deleting a department that still has employees is refused (409) unless a target department is given,
     * in which case the employees are moved first, in the same transaction.
     */
    @Transactional
    public void delete(Long id, Long reassignToId) {
        Department d = find(id);
        long members = employeeRepository.countByDepartment_Id(id);
        if (members > 0) {
            if (reassignToId == null) {
                throw new ConflictException("Department has " + members + " employee(s). Choose a department to move them to.");
            }
            if (reassignToId.equals(id)) throw new BadRequestException("Target department must be different");
            Department target = departmentRepository.findById(reassignToId)
                    .orElseThrow(() -> new BadRequestException("Target department does not exist"));
            List<Employee> employees = employeeRepository.findByDepartment_Id(id);
            employees.forEach(e -> e.setDepartment(target));
            employeeRepository.saveAll(employees);
            employeeRepository.flush();
        }
        departmentRepository.delete(d);
        audit.logCurrent("DEPARTMENT_DELETED", "DEPARTMENT", id,
                "name=" + d.getName() + ", reassigned=" + members + (reassignToId != null ? " to " + reassignToId : ""));
    }

    private Department find(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }
}
