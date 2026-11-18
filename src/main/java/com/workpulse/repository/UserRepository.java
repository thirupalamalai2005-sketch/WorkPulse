package com.workpulse.repository;

import com.workpulse.entity.Role;
import com.workpulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRoleAndEnabledTrue(Role role);
    List<User> findByRoleAndEnabledTrue(Role role);
}
