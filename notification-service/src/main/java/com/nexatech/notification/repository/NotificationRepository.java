package com.nexatech.notification.repository;

import com.nexatech.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :status WHERE n.userId = :userId")
    int updateStatusByUserId(@Param("userId") UUID userId, @Param("status") Notification.NotificationStatus status);

    List<Notification> findByStatusAndRetryCountLessThan(
            Notification.NotificationStatus status, int maxRetryCount);

    Page<Notification> findByStatus(Notification.NotificationStatus status, Pageable pageable);

    long countByUserIdAndStatusNot(UUID userId, Notification.NotificationStatus status);
}
