package com.luanvv.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * Spring AI implementation for chat completion service.
 */
@Service
public class SpringAiChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringAiChatService.class);
    
    private final ChatModel chatModel;
    
    public SpringAiChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    /**
     * Generate a chat response using Spring AI.
     */
    public String generateResponse(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        try {
            logger.debug("Generating chat response for prompt: {}", prompt.substring(0, Math.min(100, prompt.length())));
            
            // Create prompt and get response using Spring AI
            Prompt chatPrompt = new Prompt(prompt);
            ChatResponse response = chatModel.call(chatPrompt);
            
            if (response.getResult() == null || response.getResult().getOutput() == null) {
                throw new RuntimeException("No response generated from chat model");
            }
            
            String generatedText = response.getResult().getOutput().getText();
            
            logger.debug("Generated response with {} characters", generatedText.length());
            return generatedText;
            
        } catch (Exception e) {
            logger.error("Error generating chat response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat response", e);
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
            
            // Note: Spring AI options configuration depends on the specific model
            // For now, use the basic approach and log the parameters
            logger.debug("Using basic prompt generation (custom options may require model-specific configuration)");
            
            return generateResponse(prompt);
            
        } catch (Exception e) {
            logger.error("Error generating chat response with custom options: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate chat response with custom options", e);
        }
    }
}
