package com.luanvv.rag.repository;

import com.luanvv.rag.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

/**
 * Repository for managing chat memory messages.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    /**
     * Find all messages for a conversation ordered by creation time.
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    
    /**
     * Find recent messages for a conversation with limit.
     */
    @Query("SELECT c FROM ChatMessage c WHERE c.conversationId = :conversationId ORDER BY c.createdAt DESC LIMIT :limit")
    List<ChatMessage> findRecentMessagesByConversationId(@Param("conversationId") String conversationId, @Param("limit") int limit);
    
    /**
     * Delete all messages for a conversation.
     */
    void deleteByConversationId(String conversationId);
    
    /**
     * Count messages in a conversation.
     */
    long countByConversationId(String conversationId);

    @Query("SELECT DISTINCT c.conversationId FROM ChatMessage c")
    List<String> findConversationIds();

    default List<Message> findByConversationId(String conversationId) {
        List<ChatMessage> messages = findByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.stream()
                .map(message -> {
                    String content = message.getContent();
                    return switch (message.getMessageType()) {
                        case USER -> new UserMessage(content);
                        case ASSISTANT -> new AssistantMessage(content);
                        case SYSTEM -> new SystemMessage(content);
                        case TOOL -> new ToolResponseMessage(List.of());
                        default -> throw new IncompatibleClassChangeError();
                    };
                })
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Replaces all the existing messages for the given conversation ID with the provided
     * messages.
     */
    default void saveAll(String conversationId, List<Message> messages) {
        deleteByConversationId(conversationId);
        var chatMessages = messages.stream()
            .map(message -> {
                Assert.notNull(message, "Message must not be null");
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setConversationId(conversationId);
                chatMessage.setContent(message.getText());
                chatMessage.setCreatedAt(LocalDateTime.now());
                chatMessage.setMessageType(message.getMessageType());
                return chatMessage;
            })
            .toList();
        if (!chatMessages.isEmpty()) {
            saveAll(chatMessages);
        }
    }
}
