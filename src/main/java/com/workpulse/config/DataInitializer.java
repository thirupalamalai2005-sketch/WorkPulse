package com.workpulse.config;

import com.workpulse.entity.Role;
import com.workpulse.repository.UserRepository;
import com.workpulse.security.PasswordConstraintValidator;
import com.workpulse.service.UserProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Safe, explicit administrator provisioning.
 *
 * There is NO built-in default administrator. If the system has no active ADMIN, one is created
 * from the environment variables WORKPULSE_ADMIN_EMAIL and WORKPULSE_ADMIN_PASSWORD (the password must
 * satisfy the normal password policy). The password is never logged. If a variable is missing, a warning
 * explains how to provision the first administrator.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final UserProvisioningService provisioning;
    private final String email;
    private final String username;
    private final String password;

    public DataInitializer(UserRepository userRepository, UserProvisioningService provisioning,
                           @Value("${workpulse.bootstrap-admin.email:}") String email,
                           @Value("${workpulse.bootstrap-admin.username:System Administrator}") String username,
                           @Value("${workpulse.bootstrap-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.provisioning = provisioning;
        this.email = email;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) > 0) {
            logger.info("An active administrator already exists - bootstrap skipped.");
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            logger.warn("No active ADMIN account exists. Set WORKPULSE_ADMIN_EMAIL and WORKPULSE_ADMIN_PASSWORD "
                    + "and restart to create the first administrator.");
            return;
        }
        String problem = PasswordConstraintValidator.violation(password);
        if (problem != null) {
            throw new IllegalStateException("WORKPULSE_ADMIN_PASSWORD rejected: " + problem);
        }
        if (userRepository.existsByEmail(email.toLowerCase().trim())) {
            logger.error("Bootstrap admin e-mail already belongs to a non-admin or disabled account; not modifying it.");
            return;
        }
        provisioning.provision(username, email, password, Role.ADMIN, null, null, "Administrator", null, null);
        logger.info("First administrator created for the configured e-mail address.");
    }
}
