package com.workpulse.config;

import com.workpulse.entity.Department;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.repository.DepartmentRepository;
import com.workpulse.repository.UserRepository;
import com.workpulse.service.UserProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sample data for LOCAL DEVELOPMENT ONLY (profile "dev" and workpulse.dev-seed.enabled=true).
 * Never active in production or tests. The shared dev password comes from workpulse.dev-seed.password.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "workpulse.dev-seed.enabled", havingValue = "true")
@Order(100)
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final UserProvisioningService provisioning;
    private final String devPassword;

    public DevDataSeeder(UserRepository userRepository, DepartmentRepository departmentRepository,
                         UserProvisioningService provisioning,
                         @Value("${workpulse.dev-seed.password:Dev@Password#2026}") String devPassword) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.provisioning = provisioning;
        this.devPassword = devPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail("manager@workpulse.local")) {
            logger.info("Dev seed data already present.");
            return;
        }
        Department eng = department("Engineering");
        Department hr = department("Human Resources");
        department("Finance");

        if (!userRepository.existsByEmail("admin@workpulse.local")) {
            provisioning.provision("Dev Admin", "admin@workpulse.local", devPassword, Role.ADMIN, hr, null, "Administrator", null, null);
        }
        Employee manager = provisioning.provision("Meera Manager", "manager@workpulse.local", devPassword, Role.MANAGER, eng, null, "Engineering Manager", null, null);
        provisioning.provision("Arun Kumar", "arun@workpulse.local", devPassword, Role.EMPLOYEE, eng, manager, "Software Engineer", null, null);
        provisioning.provision("Priya Sharma", "priya@workpulse.local", devPassword, Role.EMPLOYEE, eng, manager, "QA Engineer", null, null);
        logger.info("Dev seed data created: admin@/manager@/arun@/priya@workpulse.local (password from workpulse.dev-seed.password).");
    }

    private Department department(String name) {
        return departmentRepository.findAll().stream().filter(d -> d.getName().equalsIgnoreCase(name)).findFirst()
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setName(name);
                    return departmentRepository.save(d);
                });
    }
}
