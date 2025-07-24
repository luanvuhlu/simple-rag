package com.luanvv.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

/**
 * Spring AI implementation for Ollama embedding service.
 */
@Service
public class EmbeddingService implements EmbeddingProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    
    private final EmbeddingModel embeddingModel;
    
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        try {
            logger.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));
            
            // Use Spring AI to generate embedding
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            
            if (response.getResults().isEmpty()) {
                throw new RuntimeException("No embedding results returned from Ollama");
            }
            
            // Extract embedding as float array
            float[] embedding = response.getResults().get(0).getOutput();
            
            logger.debug("Generated embedding with {} dimensions", embedding.length);
            return embedding;
            
        } catch (Exception e) {
            logger.error("Error generating embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
    
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Texts cannot be null or empty");
        }
        
        try {
            logger.debug("Generating embeddings for {} texts", texts.size());
            
            // Use Spring AI to generate embeddings for multiple texts
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            
            if (response.getResults().size() != texts.size()) {
                throw new RuntimeException("Mismatch between input texts and embedding results");
            }
            
            // Convert to list of float arrays
            List<float[]> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(response.getResults().get(i).getOutput());
            }
            
            logger.debug("Generated {} embeddings", embeddings.size());
            return embeddings;
            
        } catch (Exception e) {
            logger.error("Error generating embeddings: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }
    
    @Override
    public String embeddingToVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }
        
        // Convert to PostgreSQL vector format: [1.0,2.0,3.0]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    @Override
    public float[] vectorToEmbedding(String vectorString) {
        if (vectorString == null || vectorString.isEmpty()) {
            throw new IllegalArgumentException("Vector string cannot be null or empty");
        }
        
        try {
            // Remove brackets and split by comma
            String cleanVector = vectorString.substring(1, vectorString.length() - 1);
            String[] parts = cleanVector.split(",");
            
            float[] embedding = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                embedding[i] = Float.parseFloat(parts[i].trim());
            }
            
            return embedding;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse vector string: " + e.getMessage(), e);
        }
    }
}
