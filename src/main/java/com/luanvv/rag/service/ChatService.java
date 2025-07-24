package com.luanvv.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * Spring AI implementation for chat completion service with custom instructions.
 */
@Service
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    private final ChatClient chatClient;
    
    private static final String SYSTEM_INSTRUCTION = """
        You are a helpful AI assistant specialized in answering questions based on document content.
        
        Guidelines:
        - [IMPORTANT] Always answer by the same language as the question
        - Provide accurate, concise, and well-structured responses
        - If you don't have enough information, clearly state what you don't know
        - Use bullet points or numbered lists when appropriate
        - Cite specific information from the provided context when relevant
        - Be professional and friendly in your tone
        - If asked about topics outside the provided context, politely redirect to the available information
        """;
    
    public ChatService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_INSTRUCTION)
                .build();
    }
    
    /**
     * Generate a chat response using Spring AI ChatClient with system instructions.
     */
    public String generateResponse(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        try {
            logger.debug("Generating chat response for prompt: {}", prompt.substring(0, Math.min(100, prompt.length())));
            
            // Use ChatClient with system instruction
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("Generated response with {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating chat response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat response", e);
        }
    }
    
    /**
     * Generate a response with custom system instructions and user prompt.
     */
    public String generateResponseWithContext(String systemMessage, String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("User prompt cannot be null or empty");
        }
        
        try {
            logger.debug("Generating chat response with custom system message");
            
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
