package com.nexatech.chat.repository;

import com.nexatech.chat.model.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ChatConversation, UUID> {

    @Query("select c from ChatConversation c where c.customerId = :userId or c.agentId = :userId")
    Page<ChatConversation> findByParticipant(@Param("userId") UUID userId, Pageable pageable);
}
