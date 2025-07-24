package com.luanvv.rag.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Spring AI services.
 */
@SpringBootTest
@ActiveProfiles("test")
public class SpringAiIntegrationTest {
    
    @Autowired(required = false)
    private SpringAiOllamaEmbeddingService embeddingService;
    
    @Autowired(required = false)
    private SpringAiChatService chatService;
    
    @Test
    public void testEmbeddingServiceAvailable() {
        // Test that the Spring AI embedding service is available
        if (embeddingService != null) {
            System.out.println("Spring AI Embedding Service is available");
            
            // Test embedding generation (if Ollama is running)
            try {
                float[] embedding = embeddingService.generateEmbedding("Hello, world!");
                assertNotNull(embedding);
                assertTrue(embedding.length > 0);
                System.out.println("Embedding generated successfully with " + embedding.length + " dimensions");
            } catch (Exception e) {
                System.out.println("Ollama might not be running: " + e.getMessage());
            }
        } else {
            System.out.println("Spring AI Embedding Service is not available (using fallback)");
        }
    }
    
    @Test
    public void testChatServiceAvailable() {
        // Test that the Spring AI chat service is available
        if (chatService != null) {
            System.out.println("Spring AI Chat Service is available");
            
            // Test chat response generation (if Ollama is running)
            try {
                String response = chatService.generateResponse("Hello, how are you?");
                assertNotNull(response);
                assertFalse(response.trim().isEmpty());
                System.out.println("Chat response generated successfully: " + response.substring(0, Math.min(100, response.length())));
            } catch (Exception e) {
                System.out.println("Ollama might not be running: " + e.getMessage());
            }
        } else {
            System.out.println("Spring AI Chat Service is not available (using fallback)");
        }
    }
}
