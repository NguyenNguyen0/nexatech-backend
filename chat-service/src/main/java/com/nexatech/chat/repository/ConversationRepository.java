package com.nexatech.chat.repository;

import com.nexatech.chat.model.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ChatConversation, UUID> {

    @Query("select c from ChatConversation c where c.customerId = :userId or c.agentId = :userId")
    Page<ChatConversation> findByParticipant(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select c from ChatConversation c
            where c.customerId = :customerId
              and c.status <> com.nexatech.chat.model.ConversationStatus.CLOSED
            order by c.updatedAt desc
            """)
    List<ChatConversation> findCurrentByCustomerId(
            @Param("customerId") UUID customerId,
            Pageable pageable);

    @Query("""
            select c from ChatConversation c
            where c.guestSessionId = :guestSessionId
              and c.status <> com.nexatech.chat.model.ConversationStatus.CLOSED
              and (c.expiresAt is null or c.expiresAt > :now)
            order by c.updatedAt desc
            """)
    List<ChatConversation> findCurrentByGuestSessionId(
            @Param("guestSessionId") String guestSessionId,
            @Param("now") Instant now,
            Pageable pageable);

    long countByAgentIdAndStatus(UUID agentId, com.nexatech.chat.model.ConversationStatus status);

    List<ChatConversation> findByStatusOrderByCreatedAtAsc(com.nexatech.chat.model.ConversationStatus status);
}
