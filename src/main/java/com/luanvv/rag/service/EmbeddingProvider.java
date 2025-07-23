package com.luanvv.rag.service;

import java.util.List;

/**
 * Interface for embedding services to allow switching between real and mock implementations.
 */
public interface EmbeddingProvider {
    
    /**
     * Generate embedding for a single text.
     */
    float[] generateEmbedding(String text);
    
    /**
     * Generate embeddings for multiple texts in batch.
     */
    List<float[]> generateEmbeddings(List<String> texts);
    
    /**
     * Convert embedding array to PostgreSQL vector format.
     */
    String embeddingToVector(float[] embedding);
    
    /**
     * Parse PostgreSQL vector format back to embedding array.
     */
    float[] vectorToEmbedding(String vectorString);
}
