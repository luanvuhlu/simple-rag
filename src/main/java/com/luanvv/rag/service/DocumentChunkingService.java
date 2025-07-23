package com.luanvv.rag.service;

import com.luanvv.rag.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for splitting documents into chunks for vector embedding.
 */
@Service
public class DocumentChunkingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkingService.class);
    
    private final AppProperties appProperties;
    
    public DocumentChunkingService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }
    
    /**
     * Split text into chunks with configurable size and overlap.
     */
    public List<String> chunkText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        int chunkSize = appProperties.getDocument().getChunkSize();
        int overlap = appProperties.getDocument().getChunkOverlap();
        int maxChunks = appProperties.getDocument().getMaxChunksPerDocument();
        
        logger.debug("Chunking text of {} characters with chunk size: {}, overlap: {}", 
                    text.length(), chunkSize, overlap);
        
        List<String> chunks = new ArrayList<>();
        String cleanText = text.trim();
        
        // If the text is smaller than chunk size, return as single chunk
        if (cleanText.length() <= chunkSize) {
            chunks.add(cleanText);
            return chunks;
        }
        
        int start = 0;
        int chunkCount = 0;
        
        while (start < cleanText.length() && chunkCount < maxChunks) {
            int end = Math.min(start + chunkSize, cleanText.length());
            
            // Try to find a good breaking point (end of sentence or paragraph)
            if (end < cleanText.length()) {
                int sentenceEnd = findSentenceBreak(cleanText, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }
            
            String chunk = cleanText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                chunkCount++;
            }
            
            // Calculate next start position with overlap
            start = Math.max(start + 1, end - overlap);
        }
        
        logger.debug("Created {} chunks from text", chunks.size());
        return chunks;
    }
    
    /**
     * Find a good breaking point for chunks (end of sentence or paragraph).
     */
    private int findSentenceBreak(String text, int start, int end) {
        // Look for paragraph breaks first
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start) {
            return paragraphBreak + 2;
        }
        
        // Look for sentence endings
        String sentenceEnders = ".!?";
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (sentenceEnders.indexOf(c) != -1) {
                // Make sure it's not a decimal number or abbreviation
                if (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                    return i + 1;
                }
            }
        }
        
        // Look for line breaks
        int lineBreak = text.lastIndexOf("\n", end);
        if (lineBreak > start) {
            return lineBreak + 1;
        }
        
        // Look for word boundaries
        for (int i = end - 1; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        
        // If no good break point found, return the original end
        return end;
    }
    
    /**
     * Validate chunk quality and filter out low-quality chunks.
     */
    public boolean isValidChunk(String chunk) {
        if (chunk == null || chunk.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = chunk.trim();
        
        // Filter out very short chunks
        if (trimmed.length() < 20) {
            return false;
        }
        
        // Filter out chunks that are mostly numbers or symbols
        long letterCount = trimmed.chars().filter(Character::isLetter).count();
        double letterRatio = (double) letterCount / trimmed.length();
        
        return letterRatio >= 0.3; // At least 30% letters
    }
}
