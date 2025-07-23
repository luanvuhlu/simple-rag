package com.luanvv.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock embedding service when Ollama is not available.
 * This allows the application to run and demonstrate other features
 * while the embedding functionality remains disabled.
 * CURRENTLY DISABLED - using OllamaEmbeddingService instead
 */
//@Service
//@Primary
public class MockEmbeddingService implements EmbeddingProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(MockEmbeddingService.class);
    
    /**
     * Generate a mock embedding (all zeros) for demonstration purposes.
     */
    public float[] generateEmbedding(String text) {
        logger.warn("Using mock embedding service. Install and configure Ollama for real embeddings.");
        // Return a mock 768-dimension embedding (all zeros)
        return new float[768];
    }
    
    /**
     * Generate mock embeddings for multiple texts.
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        logger.warn("Using mock embedding service for {} texts", texts.size());
        return texts.stream()
                .map(text -> generateEmbedding(text))
                .toList();
    }
    
    /**
     * Convert embedding array to PostgreSQL vector format.
     */
    public String embeddingToVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }
        
        // Convert to PostgreSQL vector format: [1.0,2.0,3.0]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    /**
     * Parse PostgreSQL vector format back to embedding array.
     */
    public float[] vectorToEmbedding(String vectorString) {
        if (vectorString == null || vectorString.trim().isEmpty()) {
            throw new IllegalArgumentException("Vector string cannot be null or empty");
        }
        
        try {
            // Remove brackets and split by comma
            String cleanVector = vectorString.trim().substring(1, vectorString.length() - 1);
            String[] values = cleanVector.split(",");
            
            float[] embedding = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                embedding[i] = Float.parseFloat(values[i].trim());
            }
            
            return embedding;
            
        } catch (Exception e) {
            logger.error("Error parsing vector string: {}", vectorString, e);
            throw new RuntimeException("Failed to parse vector string: " + e.getMessage(), e);
        }
    }
}
