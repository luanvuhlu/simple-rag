package com.luanvv.rag.service;

import com.luanvv.rag.config.AppProperties;
import com.luanvv.rag.entity.DocumentChunk;
import com.luanvv.rag.entity.QueryHistory;
import com.luanvv.rag.repository.DocumentChunkRepository;
import com.luanvv.rag.repository.QueryHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for handling RAG queries and generating responses.
 */
@Service
@Transactional
public class RagQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    
    private final DocumentChunkRepository documentChunkRepository;
    private final QueryHistoryRepository queryHistoryRepository;
    private final EmbeddingProvider embeddingProvider;
    private final AppProperties appProperties;
    
    public RagQueryService(DocumentChunkRepository documentChunkRepository,
                          QueryHistoryRepository queryHistoryRepository,
                          EmbeddingProvider embeddingProvider,
                          AppProperties appProperties) {
        this.documentChunkRepository = documentChunkRepository;
        this.queryHistoryRepository = queryHistoryRepository;
        this.embeddingProvider = embeddingProvider;
        this.appProperties = appProperties;
    }
    
    /**
     * Process a RAG query and return the response.
     */
    public QueryHistory processQuery(String question) {
        logger.info("Processing RAG query: {}", question);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // For now, return a simple response without vector search
            // This will be enhanced when Spring AI is properly integrated
            List<DocumentChunk> relevantChunks = findRelevantChunks(question);
            
            String answer = generateAnswer(question, relevantChunks);
            String relevantDocuments = getRelevantDocumentNames(relevantChunks);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            QueryHistory queryHistory = new QueryHistory(question, answer);
            queryHistory.setRelevantDocuments(relevantDocuments);
            queryHistory.setProcessingTimeMs(processingTime);
            
            queryHistory = queryHistoryRepository.save(queryHistory);
            
            logger.info("Query processed in {}ms", processingTime);
            return queryHistory;
            
        } catch (Exception e) {
            logger.error("Error processing query: {}", question, e);
            
            QueryHistory errorQuery = new QueryHistory(question, "Sorry, I encountered an error while processing your question. Please try again.");
            errorQuery.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            return queryHistoryRepository.save(errorQuery);
        }
    }
    
    /**
     * Find relevant chunks for the query using vector similarity search.
     */
    private List<DocumentChunk> findRelevantChunks(String question) {
        try {
            // First check if we have any documents at all
            long totalChunks = documentChunkRepository.count();
            logger.info("Total chunks in database: {}", totalChunks);
            
            if (totalChunks == 0) {
                logger.warn("No document chunks found in database. Please upload documents first.");
                return List.of();
            }
            
            // Check chunks with embeddings
            List<DocumentChunk> chunksWithoutEmbeddings = documentChunkRepository.findChunksWithoutEmbeddings();
            logger.info("Chunks without embeddings: {}", chunksWithoutEmbeddings.size());
            
            // Generate embedding for the query
            float[] queryEmbedding = embeddingProvider.generateEmbedding(question);
            String queryVector = convertEmbeddingToVector(queryEmbedding);
            
            // Use vector similarity search
            double threshold = appProperties.getVector().getSimilarityThreshold();
            int maxResults = appProperties.getVector().getMaxResults();
            
            logger.info("Searching for similar chunks with threshold: {} and max results: {}", threshold, maxResults);
            
            List<DocumentChunk> similarChunks = documentChunkRepository.findSimilarChunks(queryVector, maxResults);
            logger.info("Found {} similar chunks", similarChunks.size());
            
            // If no similar chunks found with current threshold, try without threshold
            if (similarChunks.isEmpty()) {
                logger.warn("No chunks found with threshold {}, trying without threshold", threshold);
                // Get all chunks with embeddings and return top matches
                List<DocumentChunk> allChunksWithEmbeddings = documentChunkRepository.findAll()
                    .stream()
                    .filter(chunk -> chunk.getEmbeddingVector() != null)
                    .collect(Collectors.toList());
                
                logger.info("Total chunks with embeddings: {}", allChunksWithEmbeddings.size());
                
                if (!allChunksWithEmbeddings.isEmpty()) {
                    // Return first few chunks as fallback
                    return allChunksWithEmbeddings.stream()
                        .limit(maxResults)
                        .collect(Collectors.toList());
                }
            }
            
            return similarChunks;
            
        } catch (Exception e) {
            logger.warn("Failed to perform vector search, falling back to simple search: {}", e.getMessage());
            
            // Fallback to simple keyword search
            return performSimpleKeywordSearch(question);
        }
    }
    
    /**
     * Fallback simple keyword search when vector search fails.
     */
    private List<DocumentChunk> performSimpleKeywordSearch(String question) {
        List<DocumentChunk> allChunks = documentChunkRepository.findAll();
        
        if (allChunks.isEmpty()) {
            return List.of();
        }
        
        // Simple keyword-based filtering
        String[] keywords = question.toLowerCase().split("\\s+");
        
        return allChunks.stream()
                .filter(chunk -> containsKeywords(chunk.getChunkText().toLowerCase(), keywords))
                .limit(appProperties.getVector().getMaxResults())
                .collect(Collectors.toList());
    }
    
    /**
     * Convert float array embedding to PostgreSQL vector format.
     */
    private String convertEmbeddingToVector(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Check if text contains any of the keywords.
     */
    private boolean containsKeywords(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Generate answer based on question and relevant chunks using LLM.
     */
    private String generateAnswer(String question, List<DocumentChunk> relevantChunks) {
        if (relevantChunks.isEmpty()) {
            return "I couldn't find any relevant information in the uploaded documents to answer your question. Please make sure you have uploaded documents that contain information related to your query.";
        }
        
        // Build context from relevant chunks
        StringBuilder contextBuilder = new StringBuilder();
        for (DocumentChunk chunk : relevantChunks) {
            contextBuilder.append(chunk.getChunkText()).append("\n\n");
        }
        String context = contextBuilder.toString().trim();
        
        // Create RAG prompt
        String prompt = buildRagPrompt(question, context);
        
        try {
            // Use Ollama to generate the answer
            String answer = generateChatResponse(prompt);
            
            if (answer != null && !answer.trim().isEmpty()) {
                return answer.trim();
            } else {
                return fallbackToSimpleAnswer(question, relevantChunks);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to generate LLM response, using fallback: {}", e.getMessage());
            return fallbackToSimpleAnswer(question, relevantChunks);
        }
    }
    
    /**
     * Build RAG prompt for the LLM.
     */
    private String buildRagPrompt(String question, String context) {
        return String.format("""
            You are a helpful assistant that answers questions based on the provided context.
            
            Context:
            %s
            
            Question: %s
            
            Instructions:
            - Answer the question based only on the information provided in the context above
            - If the context doesn't contain enough information to answer the question, say so clearly
            - Be concise but comprehensive in your response
            - Do not make up information that is not in the context
            - If relevant, you can reference specific parts of the context
            
            Answer:""", context, question);
    }
    
    /**
     * Generate chat response using Ollama.
     */
    private String generateChatResponse(String prompt) {
        try {
            // Use the DirectOllamaEmbeddingService's HTTP client approach for chat
            return callOllamaChatAPI(prompt);
            
        } catch (Exception e) {
            logger.error("Error calling Ollama chat API: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Call Ollama chat API directly using HTTP.
     */
    private String callOllamaChatAPI(String prompt) {
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            // Get Ollama configuration
            String baseUrl = getOllamaBaseUrl();
            String model = getOllamaChatModel();
            
            // Build request
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("model", model);
            request.put("prompt", prompt);
            request.put("stream", false);
            request.put("options", java.util.Map.of(
                "temperature", 0.7,
                "top_p", 0.9,
                "top_k", 40
            ));
            
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = 
                new org.springframework.http.HttpEntity<>(request, headers);
            
            // Make request
            String url = baseUrl + "/api/generate";
            logger.debug("Calling Ollama chat API: {}", url);
            
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractResponseFromOllama(response.getBody());
            } else {
                throw new RuntimeException("Failed to get response from Ollama: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error calling Ollama chat API: {}", e.getMessage());
            throw new RuntimeException("Failed to generate response", e);
        }
    }
    
    /**
     * Extract response text from Ollama JSON response.
     */
    private String extractResponseFromOllama(String jsonResponse) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);
            
            if (root.has("response")) {
                return root.get("response").asText();
            } else {
                throw new RuntimeException("No 'response' field in Ollama response");
            }
            
        } catch (Exception e) {
            logger.error("Error parsing Ollama response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Ollama response", e);
        }
    }
    
    /**
     * Get Ollama base URL from configuration.
     */
    private String getOllamaBaseUrl() {
        // Default to localhost if not configured
        return "http://localhost:11434";
    }
    
    /**
     * Get Ollama chat model from configuration.
     */
    private String getOllamaChatModel() {
        // Default to qwen2.5:7b if not configured
        return "qwen2.5:7b";
    }
    
    /**
     * Fallback to simple answer when LLM fails.
     */
    private String fallbackToSimpleAnswer(String question, List<DocumentChunk> relevantChunks) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Based on the uploaded documents, here's what I found:\n\n");
        
        int maxChunks = Math.min(3, relevantChunks.size());
        for (int i = 0; i < maxChunks; i++) {
            DocumentChunk chunk = relevantChunks.get(i);
            contextBuilder.append("• ").append(truncateText(chunk.getChunkText(), 200)).append("\n\n");
        }
        
        if (relevantChunks.size() > maxChunks) {
            contextBuilder.append("... and ").append(relevantChunks.size() - maxChunks).append(" more relevant sections found.");
        }
        
        return contextBuilder.toString();
    }
    
    /**
     * Get names of documents that contain relevant chunks.
     */
    private String getRelevantDocumentNames(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> chunk.getDocument().getFilename())
                .distinct()
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Truncate text to specified length.
     */
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Get query history.
     */
    public List<QueryHistory> getQueryHistory() {
        return queryHistoryRepository.findAllByOrderByQueryDateDesc();
    }
    
    /**
     * Get recent query history.
     */
    public List<QueryHistory> getRecentQueries(int limit) {
        List<QueryHistory> allQueries = getQueryHistory();
        return allQueries.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
