package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.conversation.Conversation;
import com.purchasingpower.autoflow.model.conversation.ConversationMessage;
import com.purchasingpower.autoflow.model.conversation.Workflow;
import com.purchasingpower.autoflow.repository.ConversationRepository;
import com.purchasingpower.autoflow.repository.WorkflowRepository;
import com.purchasingpower.autoflow.service.ConversationService;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.util.GitUrlParser;
import com.purchasingpower.autoflow.workflow.state.ChatMessage;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ConversationService.
 *
 * Responsible for persisting conversations and workflows to Oracle database.
 * This ensures conversation history is queryable and survives workflow completion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepo;
    private final WorkflowRepository workflowRepo;
    private final GitOperationsService gitService;

    @Override
    @Transactional
    public Conversation createConversation(String conversationId, String userId, String repoUrl) {
        // Default to "anonymous" if userId is null
        String effectiveUserId = (userId != null && !userId.trim().isEmpty()) ? userId : "anonymous";
        log.info("Creating conversation: {} for user: {}", conversationId, effectiveUserId);

        String repoName = null;
        if (repoUrl != null && !repoUrl.trim().isEmpty()) {
            try {
                // ✅ FIX: Parse URL correctly to extract repo name (handles /tree/branch URLs)
                repoName = GitUrlParser.parse(repoUrl).getRepoName();
            } catch (Exception e) {
                log.warn("Failed to extract repo name from URL: {}", repoUrl, e);
            }
        }

        Conversation conversation = Conversation.builder()
                .conversationId(conversationId)
                .userId(effectiveUserId)
                .repoUrl(repoUrl)
                .repoName(repoName)
                .mode(Conversation.ConversationMode.IMPLEMENT)  // Default mode
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .build();

        return conversationRepo.save(conversation);
    }

    @Override
    public Optional<Conversation> getConversation(String conversationId) {
        return conversationRepo.findById(conversationId);
    }

    @Override
    public Optional<Conversation> getConversationWithWorkflows(String conversationId) {
        return conversationRepo.findByIdWithWorkflows(conversationId);
    }

    @Override
    public Optional<Conversation> getConversationWithMessages(String conversationId) {
        return conversationRepo.findByIdWithMessages(conversationId);
    }

    @Override
    public List<Conversation> getActiveConversations(String userId) {
        return conversationRepo.findByUserIdAndIsActiveTrue(userId);
    }

    @Override
    @Transactional
    public Conversation saveConversation(Conversation conversation) {
        return conversationRepo.save(conversation);
    }

    @Override
    @Transactional
    public void saveConversationFromWorkflowState(WorkflowState state) {
        try {
            String conversationId = state.getConversationId();
            log.debug("Saving conversation from WorkflowState: {}", conversationId);

            // Get or create conversation
            Conversation conversation = conversationRepo.findById(conversationId)
                    .orElseGet(() -> createConversation(
                            conversationId,
                            state.getUserId(),
                            state.getRepoUrl()
                    ));

            // Update conversation activity
            conversation.setLastActivity(LocalDateTime.now());

            // Sync messages from WorkflowState to Conversation
            if (state.getConversationHistory() != null && !state.getConversationHistory().isEmpty()) {
                syncMessages(conversation, state.getConversationHistory());
            }

            conversationRepo.save(conversation);
            log.debug("Conversation saved: {}", conversationId);

        } catch (Exception e) {
            log.error("Failed to save conversation from WorkflowState", e);
        }
    }

    /**
     * Sync messages from WorkflowState to Conversation entity.
     * Only adds new messages that don't already exist.
     */
    private void syncMessages(Conversation conversation, List<ChatMessage> chatMessages) {
        // Get existing message count
        int existingCount = conversation.getMessages() != null ? conversation.getMessages().size() : 0;

        // Only add new messages (avoid duplicates)
        for (int i = existingCount; i < chatMessages.size(); i++) {
            ChatMessage chatMsg = chatMessages.get(i);
            ConversationMessage dbMsg = new ConversationMessage(
                    chatMsg.getRole(),
                    chatMsg.getContent()
            );
            dbMsg.setTimestamp(chatMsg.getTimestamp());
            conversation.addMessage(dbMsg);
        }
    }

    @Override
    @Transactional
    public void addMessage(String conversationId, String role, String content) {
        conversationRepo.findById(conversationId).ifPresent(conversation -> {
            ConversationMessage message = new ConversationMessage(role, content);
            conversation.addMessage(message);
            conversationRepo.save(conversation);
            log.debug("Message added to conversation {}: {} - {}",
                    conversationId, role, content.substring(0, Math.min(50, content.length())));
        });
    }

    @Override
    @Transactional
    public Workflow addWorkflow(String conversationId, String workflowId, String goal) {
        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        Workflow workflow = Workflow.builder()
                .workflowId(workflowId)
                .conversation(conversation)
                .goal(goal)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();

        conversation.addWorkflow(workflow);
        conversationRepo.save(conversation);

        log.info("Workflow added to conversation {}: {}", conversationId, workflowId);
        return workflow;
    }

    @Override
    @Transactional
    public void updateWorkflowStatus(String workflowId, String status) {
        workflowRepo.findById(workflowId).ifPresent(workflow -> {
            workflow.setStatus(status);
            workflowRepo.save(workflow);
            log.debug("Workflow {} status updated to: {}", workflowId, status);
        });
    }

    @Override
    @Transactional
    public void completeWorkflow(String workflowId) {
        workflowRepo.findById(workflowId).ifPresent(workflow -> {
            workflow.complete();
            workflowRepo.save(workflow);
            log.info("Workflow completed: {}", workflowId);
        });
    }

    @Override
    @Transactional
    public void failWorkflow(String workflowId, String errorMessage) {
        workflowRepo.findById(workflowId).ifPresent(workflow -> {
            workflow.fail(errorMessage);
            workflowRepo.save(workflow);
            log.warn("Workflow failed: {} - {}", workflowId, errorMessage);
        });
    }

    @Override
    @Transactional
    public void closeConversation(String conversationId) {
        conversationRepo.findById(conversationId).ifPresent(conversation -> {
            conversation.close();
            conversationRepo.save(conversation);
            log.info("Conversation closed: {}", conversationId);
        });
    }

    @Override
    @Transactional
    public void reopenConversation(String conversationId) {
        conversationRepo.findById(conversationId).ifPresent(conversation -> {
            conversation.reopen();
            conversationRepo.save(conversation);
            log.info("Conversation reopened: {}", conversationId);
        });
    }

    @Override
    public boolean hasRunningWorkflow(String conversationId) {
        Conversation conversation = conversationRepo.findById(conversationId).orElse(null);
        return conversation != null && conversation.hasRunningWorkflow();
    }
}
