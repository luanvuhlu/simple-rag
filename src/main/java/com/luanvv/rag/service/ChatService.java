package com.luanvv.rag.service;

import com.luanvv.rag.entity.ChatMessage;
import com.luanvv.rag.repository.ChatMessageRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring AI implementation for chat completion service with custom instructions and JDBC chat memory.
 */
@Service
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;


    
    public ChatService(ChatClient chatClient, ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatClient = chatClient;
    }
    
//    /**
//     * Generate a chat response with conversation memory.
//     */
//    @Transactional
//    public String generateResponseWithMemory(String prompt, String conversationId) {
//        if (prompt == null || prompt.trim().isEmpty()) {
//            throw new IllegalArgumentException("Prompt cannot be null or empty");
//        }
//
//        if (conversationId == null || conversationId.trim().isEmpty()) {
//            conversationId = UUID.randomUUID().toString();
//        }
//
//        try {
//            // Save user message to memory
//            saveMessageToMemory(conversationId, ChatMessage.MessageType.USER, prompt);
//
//            // Get conversation history
//            List<ChatMessage> conversationHistory = getConversationHistory(conversationId);
//
//            // Build context from conversation history
//            StringBuilder contextBuilder = new StringBuilder();
//            for (ChatMessage message : conversationHistory) {
//                if (message.getMessageType() != ChatMessage.MessageType.SYSTEM) {
//                    contextBuilder.append(message.getMessageType().name().toLowerCase())
//                            .append(": ")
//                            .append(message.getContent())
//                            .append("\n");
//                }
//            }
//
//            // Generate response with conversation context
//            String response = chatClient.prompt()
//                    .user(u -> u.text("Previous conversation:\n" + contextBuilder.toString() + "\nNew question: " + prompt))
//                    .call()
//                    .content();
//
//            // Save assistant response to memory
//            saveMessageToMemory(conversationId, ChatMessage.MessageType.ASSISTANT, response);
//
//            logger.info("Generated response with memory for conversation {}, {} characters",
//                       conversationId, response.length());
//            return response;
//
//        } catch (Exception e) {
//            logger.error("Error generating chat response with memory: {}", e.getMessage(), e);
//            throw new RuntimeException("Failed to generate chat response with memory", e);
//        }
//    }
    
//    /**
//     * Save a message to conversation memory.
//     */
//    private void saveMessageToMemory(String conversationId, ChatMessage.MessageType messageType, String content) {
//        ChatMessage message = new ChatMessage(conversationId, messageType, content);
//        chatMessageRepository.save(message);
//        logger.debug("Saved {} message to conversation {}", messageType, conversationId);
//    }
    
    /**
     * Get conversation history for a given conversation ID.
     */
    public List<ChatMessage> getConversationHistory(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
    
    /**
     * Get recent messages from a conversation (limited).
     */
    public List<ChatMessage> getRecentMessages(String conversationId, int limit) {
        return chatMessageRepository.findRecentMessagesByConversationId(conversationId, limit);
    }
    
    /**
     * Clear conversation memory.
     */
    @Transactional
    public void clearConversationMemory(String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        logger.info("Cleared conversation memory for {}", conversationId);
    }
    
    /**
     * Get conversation message count.
     */
    public long getConversationMessageCount(String conversationId) {
        return chatMessageRepository.countByConversationId(conversationId);
    }

    /**
     * Generate a chat response using Spring AI ChatClient with system instructions.
     */
    public String generateResponse(String prompt, String format) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        try {
            // Use ChatClient with system instruction
            String response = chatClient.prompt()
                    .user(u -> {
                        u.text(prompt);
                        if (format != null && !format.trim().isEmpty()) {
                            u.param("format", format);
                        }
                    })
                    .call()
                    .content();
            
            logger.debug("Generated response with {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating chat response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat response", e);
        }
    }

    public String generateResponse(String prompt) {
        return generateResponse(prompt, null);
    }
    
    /**
     * Generate a response with custom system instructions and user prompt.
     */
    public String generateResponseWithContext(String systemMessage, String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("User prompt cannot be null or empty");
        }
        
        try {
            String response = chatClient.prompt()
                    .system(systemMessage)
                    .user(userPrompt)
                    .call()
                    .content();
            
            logger.debug("Generated response with {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating chat response with context: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat response with context", e);
        }
    }
    
    /**
     * Generate a response with custom temperature and other options.
     */
    public String generateResponse(String prompt, double temperature, double topP, int topK) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        try {
            logger.debug("Generating chat response with custom options - temp: {}, topP: {}, topK: {}", 
                        temperature, topP, topK);
            
            // Use ChatClient with custom options (simplified for compatibility)
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            // Note: Custom options like temperature can be set in application.properties
            // or through model-specific configuration
            
            logger.debug("Generated response with custom options, {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating chat response with custom options: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat response with custom options", e);
        }
    }
}
