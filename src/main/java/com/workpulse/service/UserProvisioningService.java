package com.workpulse.service;

import com.workpulse.entity.*;
import com.workpulse.exception.EmailAlreadyExistsException;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** The one place that creates a User together with its Employee profile. */
@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserProvisioningService(UserRepository userRepository, EmployeeRepository employeeRepository,
                                   PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Employee provision(String username, String email, String rawPassword, Role role,
                              Department department, Employee manager, String designation,
                              String phone, LocalDate joinDate) {
        String normalizedEmail = email.toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email is already registered: " + normalizedEmail);
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        user = userRepository.saveAndFlush(user); // flush so the generated id is available

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(Employee.codeFor(user.getUserId()));
        employee.setDepartment(department);
        employee.setManager(manager);
        employee.setDesignation(blankToNull(designation));
        employee.setPhone(blankToNull(phone));
        employee.setJoinDate(joinDate != null ? joinDate : LocalDate.now(clock));
        employee.setStatus(EmployeeStatus.ACTIVE);
        return employeeRepository.save(employee);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
