package com.luanvv.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Direct HTTP client for Ollama embedding API.
 * This bypasses Spring AI compatibility issues.
 */
@Service
@Primary
public class DirectOllamaEmbeddingService implements EmbeddingProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectOllamaEmbeddingService.class);
    
    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;
    
    @Value("${ollama.embedding.model:nomic-embed-text}")
    private String model;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public DirectOllamaEmbeddingService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        try {
            logger.debug("Generating embedding for text of length: {}", text.length());
            
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", text);
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // Call Ollama API
            String response = restTemplate.postForObject(
                baseUrl + "/api/embeddings", 
                request, 
                String.class
            );
            
            // Parse response
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode embeddingNode = jsonNode.get("embedding");
            
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Invalid response from Ollama API");
            }
            
            // Convert to float array
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }
            
            logger.debug("Generated embedding with {} dimensions", embedding.length);
            return embedding;
            
        } catch (Exception e) {
            logger.error("Error generating embedding for text", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Texts list cannot be null or empty");
        }
        
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
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
