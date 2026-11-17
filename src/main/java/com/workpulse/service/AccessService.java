package com.workpulse.service;

import com.workpulse.entity.Employee;
import com.workpulse.entity.EmployeeStatus;
import com.workpulse.entity.Role;
import com.workpulse.entity.User;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central place for "who is calling" and "may they touch this resource" decisions.
 * Always loads the user from the database, so role changes apply immediately.
 * Call it from inside a @Transactional service method (employee relations are lazy).
 */
@Component
public class AccessService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public AccessService(UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Not authenticated");
        }
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
        if (!user.isEnabled()) {
            throw new AccessDeniedException("Account is deactivated");
        }
        return user;
    }

    public Employee currentEmployee() {
        User user = currentUser();
        Employee emp = employeeRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new AccessDeniedException("No employee profile for this account"));
        if (emp.getStatus() != EmployeeStatus.ACTIVE) {
            throw new AccessDeniedException("Employee profile is inactive");
        }
        return emp;
    }

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private boolean isManagerOf(Employee manager, Employee target) {
        return target.getManager() != null && target.getManager().getId().equals(manager.getId());
    }

    /** Admin, the employee themself, or their direct manager may see private details. */
    public boolean canViewPrivate(User viewer, Employee viewerEmp, Employee target) {
        return isAdmin(viewer) || viewerEmp.getId().equals(target.getId()) || isManagerOf(viewerEmp, target);
    }

    public void requireCanViewPrivate(Employee target) {
        User viewer = currentUser();
        Employee me = employeeRepository.findByUser_UserId(viewer.getUserId())
                .orElseThrow(() -> new AccessDeniedException("No employee profile for this account"));
        if (!canViewPrivate(viewer, me, target)) {
            throw new AccessDeniedException("You may not access this employee's records");
        }
    }

    /**
     * Review rule for leave / correction requests: nobody reviews their own request;
     * ADMIN reviews anyone; MANAGER reviews only direct reports; EMPLOYEE never reviews.
     */
    public boolean canReview(User reviewer, Employee reviewerEmp, Employee subject) {
        if (reviewerEmp.getId().equals(subject.getId())) return false;
        if (reviewer.getRole() == Role.ADMIN) return true;
        return reviewer.getRole() == Role.MANAGER && isManagerOf(reviewerEmp, subject);
    }

    /** Who should be told about / can act on a request: the active manager, otherwise all active admins. */
    public List<User> approversFor(Employee subject) {
        Employee m = subject.getManager();
        if (m != null && m.getStatus() == EmployeeStatus.ACTIVE && m.getUser().isEnabled()) {
            return List.of(m.getUser());
        }
        return userRepository.findByRoleAndEnabledTrue(Role.ADMIN).stream()
                .filter(u -> !u.getUserId().equals(subject.getUser().getUserId()))
                .toList();
    }
}
