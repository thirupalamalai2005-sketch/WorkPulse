package com.workpulse.service;

import com.workpulse.dto.AuthResponse;
import com.workpulse.dto.LoginRequest;
import com.workpulse.dto.RegisterRequest;
import com.workpulse.dto.UserResponse;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.entity.User;
import com.workpulse.exception.RateLimitExceededException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.UserRepository;
import com.workpulse.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login, logout and "who am I". */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final UserProvisioningService provisioning;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditService audit;
    private final boolean registrationEnabled;

    public AuthService(UserRepository userRepository, EmployeeRepository employeeRepository,
                       UserProvisioningService provisioning, AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository, LoginRateLimiter loginRateLimiter,
                       AuditService audit,
                       @Value("${workpulse.registration.enabled:false}") boolean registrationEnabled) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.provisioning = provisioning;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.loginRateLimiter = loginRateLimiter;
        this.audit = audit;
        this.registrationEnabled = registrationEnabled;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    /** Public registration always creates an EMPLOYEE; the role is never taken from the request. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!registrationEnabled) {
            throw new AccessDeniedException("Self-registration is disabled");
        }
        Employee employee = provisioning.provision(request.getUsername(), request.getEmail(), request.getPassword(),
                Role.EMPLOYEE, null, null, null, null, null);
        audit.log(employee.getUser().getEmail(), "USER_REGISTERED", "USER", String.valueOf(employee.getUser().getUserId()),
                "role=EMPLOYEE");
        UserResponse r = toUserResponse(employee, "User registered successfully with EMPLOYEE role");
        return r;
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (loginRateLimiter.isBlocked(normalizedEmail)) {
            long remaining = loginRateLimiter.getRemainingLockoutSeconds(normalizedEmail);
            audit.log(normalizedEmail, "LOGIN_BLOCKED", "USER", null, "throttled");
            throw new RateLimitExceededException(
                    "Too many failed login attempts. Please try again in " + remaining + " seconds.", remaining);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword()));
        } catch (AuthenticationException ex) {
            loginRateLimiter.recordFailedAttempt(normalizedEmail);
            audit.log(normalizedEmail, "LOGIN_FAILURE", "USER", null, null);
            // Same message for unknown user, wrong password and disabled account.
            throw new BadCredentialsException("Invalid email or password");
        }
        loginRateLimiter.resetAttempts(normalizedEmail);

        // Session fixation protection: never reuse a pre-login session.
        HttpSession old = httpRequest.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        httpRequest.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        audit.log(user.getEmail(), "LOGIN_SUCCESS", "USER", String.valueOf(user.getUserId()), null);
        return new AuthResponse(user.getUserId(), user.getUsername(), user.getEmail(), user.getRole(), "Login successful");
    }

    public void logout(HttpServletRequest httpRequest) {
        audit.logCurrent("LOGOUT", "USER", null, null);
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("No authenticated user found in session");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Employee employee = employeeRepository.findByUser_UserId(user.getUserId()).orElse(null);
        return toUserResponse(user, employee, "Authenticated user details retrieved successfully");
    }

    UserResponse toUserResponse(Employee employee, String message) {
        return toUserResponse(employee.getUser(), employee, message);
    }

    static UserResponse toUserResponse(User u, Employee e, String message) {
        UserResponse r = new UserResponse(u.getUserId(), u.getUsername(), u.getEmail(), u.getRole(),
                u.isEnabled(), u.getCreatedAt(), message);
        if (e != null) {
            r.setEmployeeId(e.getId());
            r.setEmployeeCode(e.getEmployeeCode());
        }
        return r;
    }
}
