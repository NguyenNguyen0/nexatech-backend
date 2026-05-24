package com.nexatech.chat.service;

import com.nexatech.chat.model.ChatConversation;
import com.nexatech.chat.model.ConversationStatus;
import com.nexatech.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationAssignmentService {

    private static final String ROUND_ROBIN_SEQUENCE_KEY = "chat:assignment:round-robin-sequence";

    private final ConversationRepository conversationRepository;
    private final OnlineStatusService onlineStatusService;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public ChatConversation assignIfPossible(ChatConversation conversation) {
        if (conversation.getAgentId() != null || conversation.getStatus() != ConversationStatus.WAITING) {
            return conversation;
        }

        Optional<UUID> selectedAgent = selectAgent();
        if (selectedAgent.isEmpty()) {
            return conversation;
        }

        conversation.setAgentId(selectedAgent.get());
        conversation.setStatus(ConversationStatus.OPEN);
        return conversation;
    }

    @Transactional
    public void assignWaitingConversations() {
        List<ChatConversation> waitingConversations =
                conversationRepository.findByStatusOrderByCreatedAtAsc(ConversationStatus.WAITING);

        for (ChatConversation conversation : waitingConversations) {
            ChatConversation assigned = assignIfPossible(conversation);
            if (assigned.getAgentId() == null) {
                return;
            }
            conversationRepository.save(assigned);
        }
    }

    private Optional<UUID> selectAgent() {
        Set<UUID> onlineAgents = onlineStatusService.getOnlineAgentIds();
        if (onlineAgents.isEmpty()) {
            return Optional.empty();
        }

        List<AgentLoad> loads = onlineAgents.stream()
                .map(agentId -> new AgentLoad(
                        agentId,
                        conversationRepository.countByAgentIdAndStatus(agentId, ConversationStatus.OPEN)))
                .sorted(Comparator.comparingLong(AgentLoad::openConversationCount)
                        .thenComparing(load -> load.agentId().toString()))
                .toList();

        long lowestLoad = loads.get(0).openConversationCount();
        List<AgentLoad> leastBusyAgents = loads.stream()
                .filter(load -> load.openConversationCount() == lowestLoad)
                .toList();

        if (leastBusyAgents.size() == 1) {
            return Optional.of(leastBusyAgents.get(0).agentId());
        }

        Long sequence = stringRedisTemplate.opsForValue().increment(ROUND_ROBIN_SEQUENCE_KEY);
        long safeSequence = sequence != null ? sequence : 0L;
        int selectedIndex = Math.floorMod(safeSequence - 1L, leastBusyAgents.size());
        return Optional.of(leastBusyAgents.get(selectedIndex).agentId());
    }

    private record AgentLoad(UUID agentId, long openConversationCount) {
    }
}
