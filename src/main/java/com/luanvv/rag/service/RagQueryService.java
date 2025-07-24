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
            // Analyze user question to determine search strategy
            SearchAnalysis searchAnalysis = analyzeSearchIntent(question);
            
            List<DocumentChunk> relevantChunks = List.of();
            if (searchAnalysis.needsDocumentSearch()) {
                relevantChunks = findRelevantChunks(searchAnalysis.getSearchQuery());
            }
            
            String answer = generateAnswer(question, relevantChunks, searchAnalysis);
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
     * Analyze user question to determine search intent and strategy.
     */
    private SearchAnalysis analyzeSearchIntent(String question) {
        try {
            String analysisPrompt = String.format("""
                Analyze this user question and determine the search strategy:
                
                Question: %s
                
                Determine:
                1. Does this question need to search through uploaded documents? (yes/no)
                2. If yes, what are the key search terms/concepts to find in documents?
                3. What type of question is this? (document-specific, general-knowledge, mixed)
                
                Respond in this exact JSON format:
                {
                    "needs_document_search": true/false,
                    "search_query": "key terms for document search (if needed)",
                    "question_type": "document-specific|general-knowledge|mixed",
                    "reasoning": "brief explanation of the analysis"
                }
                
                Examples:
                - "What is Python?" -> needs_document_search: false (general knowledge)
                - "What does my contract say about termination?" -> needs_document_search: true, search_query: "contract termination"
                - "Based on the financial report, what was the revenue?" -> needs_document_search: true, search_query: "financial report revenue"
                """, question);
            
            String response = generateChatResponse(analysisPrompt);
            return parseSearchAnalysis(response, question);
            
        } catch (Exception e) {
            logger.warn("Failed to analyze search intent, using fallback: {}", e.getMessage());
            // Fallback: assume document search is needed and extract simple keywords
            return new SearchAnalysis(true, extractSimpleKeywords(question), "mixed", "Fallback analysis");
        }
    }
    
    /**
     * Parse LLM response into SearchAnalysis object.
     */
    private SearchAnalysis parseSearchAnalysis(String response, String originalQuestion) {
        try {
            // Extract JSON from response
            String jsonResponse = extractJsonFromResponse(response);
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);
            
            boolean needsSearch = root.path("needs_document_search").asBoolean(true);
            String searchQuery = root.path("search_query").asText(extractSimpleKeywords(originalQuestion));
            String questionType = root.path("question_type").asText("mixed");
            String reasoning = root.path("reasoning").asText("AI analysis");
            
            logger.debug("Search analysis - Needs search: {}, Query: '{}', Type: {}", 
                needsSearch, searchQuery, questionType);
            
            return new SearchAnalysis(needsSearch, searchQuery, questionType, reasoning);
            
        } catch (Exception e) {
            logger.warn("Failed to parse search analysis, using fallback");
            return new SearchAnalysis(true, extractSimpleKeywords(originalQuestion), "mixed", "Parse error fallback");
        }
    }
    
    /**
     * Extract JSON content from LLM response.
     */
    private String extractJsonFromResponse(String response) {
        int jsonStart = Math.max(response.indexOf('{'), response.indexOf('['));
        int jsonEnd = Math.max(response.lastIndexOf('}'), response.lastIndexOf(']'));
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        
        throw new RuntimeException("No valid JSON found in response: " + response);
    }
    
    /**
     * Simple keyword extraction fallback.
     */
    private String extractSimpleKeywords(String question) {
        return question.toLowerCase()
            .replaceAll("\\b(what|how|when|where|why|who|can|could|would|should|please|tell|show|explain|describe|based on|from the|in the|documents?|files?)\\b", "")
            .replaceAll("\\b(the|a|and|or|but|in|on|at|to|for|of|with|by|is|are|was|were)\\b", "")
            .replaceAll("[^a-zA-Z0-9\\s-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Find relevant chunks for the search query using vector similarity search.
     */
    private List<DocumentChunk> findRelevantChunks(String searchQuery) {
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
            
            // Generate embedding for the search query
            float[] queryEmbedding = embeddingProvider.generateEmbedding(searchQuery);
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
            return performSimpleKeywordSearch(searchQuery);
        }
    }
    
    /**
     * Fallback simple keyword search when vector search fails.
     */
    private List<DocumentChunk> performSimpleKeywordSearch(String searchQuery) {
        List<DocumentChunk> allChunks = documentChunkRepository.findAll();
        
        if (allChunks.isEmpty()) {
            return List.of();
        }
        
        // Simple keyword-based filtering
        String[] keywords = searchQuery.toLowerCase().split("\\s+");
        
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
     * Generate answer based on question, relevant chunks, and search analysis using LLM.
     */
    private String generateAnswer(String question, List<DocumentChunk> relevantChunks, SearchAnalysis searchAnalysis) {
        // Handle questions that don't need document search
        if (!searchAnalysis.needsDocumentSearch()) {
            String generalPrompt = String.format("""
                Answer this general knowledge question:
                
                Question: %s
                
                Instructions:
                - Provide a helpful and accurate answer
                - This question doesn't require searching through specific documents
                - Use your general knowledge to provide a comprehensive response
                
                Answer:""", question);
            
            try {
                String answer = generateChatResponse(generalPrompt);
                return answer != null && !answer.trim().isEmpty() ? answer.trim() : 
                    "I can help with general questions, but I don't have enough information to answer this specific question.";
            } catch (Exception e) {
                logger.warn("Failed to generate general knowledge response: {}", e.getMessage());
                return "I can help with general questions, but I encountered an error processing your question.";
            }
        }
        
        // Handle document-specific questions
        if (relevantChunks.isEmpty()) {
            return String.format("""
                I searched for information about "%s" in the uploaded documents but couldn't find any relevant content.
                
                This could mean:
                - The documents don't contain information about this topic
                - Try rephrasing your question with different keywords
                - Make sure you've uploaded documents that relate to your question
                
                You can also ask me general knowledge questions that don't require document search.""", 
                searchAnalysis.getSearchQuery());
        }
        
        // Build context from relevant chunks
        StringBuilder contextBuilder = new StringBuilder();
        for (DocumentChunk chunk : relevantChunks) {
            contextBuilder.append(chunk.getChunkText()).append("\n\n");
        }
        String context = contextBuilder.toString().trim();
        
        // Create enhanced RAG prompt with search context
        String prompt = buildEnhancedRagPrompt(question, context, searchAnalysis);
        
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
     * Build enhanced RAG prompt that considers search analysis.
     */
    private String buildEnhancedRagPrompt(String question, String context, SearchAnalysis searchAnalysis) {
        return String.format("""
            You are a helpful assistant that answers questions based on the provided context from uploaded documents.
            
            Search Analysis:
            - Search terms used: %s
            - Question type: %s
            
            Context from documents:
            %s
            
            User Question: %s
            
            Instructions:
            - Answer the question based primarily on the information provided in the context above
            - The context was found by searching for: "%s"
            - If the context doesn't fully answer the question, clearly state what information is available and what is missing
            - Be specific and reference relevant parts of the context when possible
            - If you need to make reasonable inferences, clearly indicate this
            - Do not make up information that is not supported by the context
            
            Answer:""", 
            searchAnalysis.getSearchQuery(),
            searchAnalysis.getQuestionType(),
            context, 
            question,
            searchAnalysis.getSearchQuery());
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
    
    /**
     * Search analysis result containing search intent and strategy.
     */
    private static class SearchAnalysis {
        private final boolean needsDocumentSearch;
        private final String searchQuery;
        private final String questionType;
        private final String reasoning;
        
        public SearchAnalysis(boolean needsDocumentSearch, String searchQuery, String questionType, String reasoning) {
            this.needsDocumentSearch = needsDocumentSearch;
            this.searchQuery = searchQuery;
            this.questionType = questionType;
            this.reasoning = reasoning;
        }
        
        public boolean needsDocumentSearch() {
            return needsDocumentSearch;
        }
        
        public String getSearchQuery() {
            return searchQuery;
        }
        
        public String getQuestionType() {
            return questionType;
        }
        
        public String getReasoning() {
            return reasoning;
        }
    }
}
