package com.workpulse.service;

import com.workpulse.dto.AccountDtos;
import com.workpulse.dto.EmployeeDtos;
import com.workpulse.entity.Employee;
import com.workpulse.entity.User;
import com.workpulse.exception.BadRequestException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.UserRepository;
import com.workpulse.security.SessionRevocationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service profile and password operations, plus the admin password reset. */
@Service
public class AccountService {

    private final AccessService access;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService revocation;
    private final AuditService audit;

    public AccountService(AccessService access, UserRepository userRepository, EmployeeRepository employeeRepository,
                          EmployeeMapper mapper, PasswordEncoder passwordEncoder,
                          SessionRevocationService revocation, AuditService audit) {
        this.access = access;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.revocation = revocation;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.Response profile() {
        return mapper.toResponse(access.currentEmployee(), true);
    }

    /** Users may change only their own name and phone. E-mail, role and status are not editable here. */
    @Transactional
    public EmployeeDtos.Response updateProfile(AccountDtos.UpdateProfileRequest req) {
        Employee me = access.currentEmployee();
        me.getUser().setUsername(req.username().trim());
        me.setPhone(UserProvisioningService.blankToNull(req.phone()));
        userRepository.save(me.getUser());
        employeeRepository.save(me);
        audit.logCurrent("PROFILE_UPDATED", "EMPLOYEE", me.getId(), "fields=name,phone");
        return mapper.toResponse(me, true);
    }

    @Transactional
    public void changePassword(AccountDtos.ChangePasswordRequest req) {
        User user = access.currentUser();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        revocation.revokeAllSessions(user.getEmail());
        audit.logCurrent("PASSWORD_CHANGED", "USER", user.getUserId(), null);
    }

    @Transactional
    public void adminResetPassword(Long userId, AccountDtos.ResetPasswordRequest req) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        target.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(target);
        revocation.revokeAllSessions(target.getEmail());
        audit.logCurrent("PASSWORD_RESET", "USER", target.getUserId(), "target=" + target.getEmail());
    }
}
