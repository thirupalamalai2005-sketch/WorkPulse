package com.workpulse.repository;

import com.workpulse.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);
    Page<Notification> findByUserIdAndReadFlagFalse(Long userId, Pageable pageable);
    long countByUserIdAndReadFlagFalse(Long userId);

    /** Ownership is part of the query: a user can never load someone else's notification. */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.readFlag = true where n.userId = :uid and n.readFlag = false")
    int markAllRead(@Param("uid") Long userId);
}
