package com.nexatech.chat.repository;

import com.nexatech.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends MongoRepository<ChatMessage, UUID> {

    @Query(value = "{ 'conversationId': ?0 }", sort = "{ 'createdAt': -1, '_id': -1 }")
    List<ChatMessage> findLatest(UUID conversationId, Pageable pageable);

    @Query(value = "{ 'conversationId': ?0, '$or': [ { 'createdAt': { '$lt': ?1 } }, { 'createdAt': ?1, '_id': { '$lt': ?2 } } ] }",
            sort = "{ 'createdAt': -1, '_id': -1 }")
    List<ChatMessage> findBefore(
            UUID conversationId,
            Instant createdAt,
            UUID messageId,
            Pageable pageable);

    java.util.Optional<ChatMessage> findByIdAndConversationId(UUID messageId, UUID conversationId);
}
