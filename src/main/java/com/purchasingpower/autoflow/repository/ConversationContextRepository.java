package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.conversation.ConversationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing conversation contexts.
 */
@Repository
public interface ConversationContextRepository extends JpaRepository<ConversationContext, String> {

}