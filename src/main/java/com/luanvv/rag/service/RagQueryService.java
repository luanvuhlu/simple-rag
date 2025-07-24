package com.luanvv.rag.service;

import com.luanvv.rag.config.AppProperties;
import com.luanvv.rag.entity.Document;
import com.luanvv.rag.entity.DocumentChunk;
import com.luanvv.rag.entity.QueryHistory;
import com.luanvv.rag.repository.DocumentChunkRepository;
import com.luanvv.rag.repository.DocumentRepository;
import com.luanvv.rag.repository.QueryHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Service for handling RAG queries and generating responses.
 */
@Service
@Transactional
public class RagQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;
    private final QueryHistoryRepository queryHistoryRepository;
    private final EmbeddingProvider embeddingProvider;
    private final AppProperties appProperties;
    private final ChatService chatService;
    
    public RagQueryService(DocumentChunkRepository documentChunkRepository,
                          DocumentRepository documentRepository,
                          QueryHistoryRepository queryHistoryRepository,
                          EmbeddingProvider embeddingProvider,
                          AppProperties appProperties,
                          ChatService chatService) {
        this.documentChunkRepository = documentChunkRepository;
        this.documentRepository = documentRepository;
        this.queryHistoryRepository = queryHistoryRepository;
        this.embeddingProvider = embeddingProvider;
        this.appProperties = appProperties;
        this.chatService = chatService;
    }
    
    /**
     * Process a RAG query and return the response.
     */
    public QueryHistory processQuery(String question) {
        logger.info("Processing RAG query: {}", question);
        
        long startTime = System.currentTimeMillis();
        
        try {
            var queryResult = findResult(question);
            
            long processingTime = System.currentTimeMillis() - startTime;

            QueryHistory queryHistory = new QueryHistory(question, queryResult.getAnswer());
            queryHistory.setRelevantDocuments(queryResult.getRelevantDocuments());
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

    private QueryResult findResult(String question) {
        // Analyze user question to determine search strategy
            SearchAnalysis searchAnalysis = analyzeSearchIntent(question);
            if (!searchAnalysis.getDocumentIds().isEmpty() || !searchAnalysis.getDocumentNames().isEmpty()) {
                logger.info("Found specific documents in search analysis");
                var documents = findReferencedDocument(searchAnalysis.getDocumentIds(), searchAnalysis.getDocumentNames());
                if (!documents.isEmpty()) {
                    var documentContent = documents.stream()
                        .map(Document::getExtractedText)
                        .collect(Collectors.toList());
                    var answer = generateAnswerFromCompleteDocument(question, documentContent);
                    String relevantDocuments = documents.stream()
                        .map(Document::getFilename)
                        .collect(Collectors.joining(", "));
                    return new QueryResult(answer, relevantDocuments);
                }

            }
            List<DocumentChunk> relevantChunks = List.of();
            if (searchAnalysis.isNeedsDocumentSearch()) {
                relevantChunks = findRelevantChunks(searchAnalysis.getSearchQuery());
            }
            
            String answer = generateAnswer(question, relevantChunks, searchAnalysis);
            String relevantDocuments = getRelevantDocumentNames(relevantChunks);
            return new QueryResult(answer, relevantDocuments);
    }

    private static class QueryResult {
        private final String answer;
        private final String relevantDocuments;

        public QueryResult(String answer, String relevantDocuments) {
            this.answer = answer;
            this.relevantDocuments = relevantDocuments;
        }

        public String getAnswer() {
            return answer;
        }

        public String getRelevantDocuments() {
            return relevantDocuments;
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

                Carefully examine the question for:

                **Document References:**
                - Explicit document IDs (e.g., "document ID 12", "doc 5", "file #3")
                - Document names/filenames (e.g., "resume.pdf", "contract.docx", "financial_report_2024.xlsx")
                - Implicit document references (e.g., "the resume", "my contract", "the report I uploaded")
                - Multiple document references (e.g., "compare documents 1 and 3", "all PDFs")

                **Search Requirements:**
                - Does the question require reading/analyzing uploaded documents?
                - What specific information needs to be extracted from documents?
                - Are there keywords that would help locate relevant content?

                **Question Classification:**
                - Document-specific: Requires specific uploaded documents
                - General-knowledge: Can be answered without documents
                - Mixed: Combines document analysis with general knowledge

                **Instructions:**
                1. Extract ALL document IDs mentioned (numbers only, as integers)
                2. Extract ALL document names/filenames mentioned (exact strings)
                3. For implicit references, leave arrays empty but set needs_document_search to true
                4. Create comprehensive search terms including synonyms and related concepts
                5. Be precise about question type classification

                Respond in this exact JSON format:
                {
                    "needs_document_search": true/false,
                    "document_ids": [list of integers only, e.g., [1, 12, 5]],
                    "document_names": ["exact filenames mentioned", "case-sensitive"],
                    "search_query": "comprehensive keywords including synonyms and related terms",
                    "question_type": "document-specific|general-knowledge|mixed",
                    "reasoning": "detailed explanation of document references found and why search is/isn't needed"
                }

                **Examples:**
                - "Analyze document ID 12 and Profile.pdf" → document_ids: [12], document_names: ["Profile.pdf"]
                - "What does the resume say about experience?" → document_ids: [], document_names: [], but needs_document_search: true
                - "Compare files 1, 3, and resume.docx" → document_ids: [1, 3], document_names: ["resume.docx"]
                - "What is machine learning?" → needs_document_search: false
                - "Based on the uploaded contract, what are the terms?" → document_ids: [], document_names: [], needs_document_search: true
                """, question);
            
            String response = generateChatResponse(analysisPrompt);
            return parseSearchAnalysis(response, question);
            
        } catch (Exception e) {
            logger.warn("Failed to analyze search intent, using fallback: {}", e.getMessage());
            // Fallback: create a SearchAnalysis with fallback values
            SearchAnalysis fallback = new SearchAnalysis();
            fallback.setNeedsDocumentSearch(true);
            fallback.setSearchQuery(extractSimpleKeywords(question));
            fallback.setQuestionType("mixed");
            fallback.setReasoning("Fallback analysis");
            fallback.setDocumentIds(Collections.emptyList());
            fallback.setDocumentNames(Collections.emptyList());
            return fallback;
        }
    }
    
    /**
     * Parse LLM response into SearchAnalysis object.
     */
    private SearchAnalysis parseSearchAnalysis(String response, String originalQuestion) {
        logger.debug("Parsing search analysis from response: {}", response);
        try {
            // Extract JSON from response
            String jsonResponse = extractJsonFromResponse(response);
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            // Deserialize JSON directly to DTO
            SearchAnalysis dto = mapper.readValue(jsonResponse, SearchAnalysis.class);
            
            // Apply fallback values if needed
            if (dto.getSearchQuery() == null || dto.getSearchQuery().trim().isEmpty()) {
                dto.setSearchQuery(extractSimpleKeywords(originalQuestion));
            }
            if (dto.getQuestionType() == null || dto.getQuestionType().trim().isEmpty()) {
                dto.setQuestionType("mixed");
            }
            if (dto.getReasoning() == null || dto.getReasoning().trim().isEmpty()) {
                dto.setReasoning("AI analysis");
            }

            logger.debug("Search analysis: {}", dto);
            return dto;

        } catch (Exception e) {
            logger.warn("Failed to parse search analysis JSON, using fallback: {}", e.getMessage());
            SearchAnalysis fallback = new SearchAnalysis();
            fallback.setNeedsDocumentSearch(true);
            fallback.setSearchQuery(extractSimpleKeywords(originalQuestion));
            fallback.setQuestionType("mixed");
            fallback.setReasoning("Parse error fallback");
            fallback.setDocumentIds(Collections.emptyList());
            fallback.setDocumentNames(Collections.emptyList());
            return fallback;
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
                    .toList();
                
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
        if (!searchAnalysis.isNeedsDocumentSearch()) {
            return generateGeneralAnswer(question);
        }
        // Handle document-specific questions
        if (relevantChunks.isEmpty()) {
            logger.info("No relevant chunks found for search query: {}", searchAnalysis.getSearchQuery());
            return generateEmptyAnswer(question, searchAnalysis);
        }
        return generateAnswerFromChunks(question, relevantChunks, searchAnalysis);

    }

    private String generateEmptyAnswer(String originalQuestion, SearchAnalysis searchAnalysis) {
        // If no specific document referenced, return the default "not found" message
        return String.format("""
                I searched for information about "%s" in the uploaded documents but couldn't find any relevant content.
                
                This could mean:
                - The documents don't contain information about this topic
                - Try rephrasing your question with different keywords
                - Make sure you've uploaded documents that relate to your question
                
                You can also:
                - Ask me general knowledge questions that don't require document search""",
            searchAnalysis.getSearchQuery());
    }

    /**
     * Find a document referenced in the user's question by ID or filename.
     */
    private List<Document> findReferencedDocument(List<Integer> documentIds, List<String> documentNames) {
        if (documentIds.isEmpty() && documentNames.isEmpty()) {
            return List.of();
        }

        logger.info("Finding referenced documents by IDs: {} and names: {}", documentIds, documentNames);

        // Find documents by IDs
        List<Document> documents = documentRepository.getDocumentExtractedTextByIdsOrNames(new HashSet<>(documentIds), new HashSet<>(documentNames));
        logger.info("Found {} documents", documents.size());
        return documents;
    }

    /**
     * Generate answer by analyzing the complete text content of a specific document.
     */
    private String generateAnswerFromCompleteDocument(String question, List<String> documents) {
        if (documents.isEmpty()) {
            return "I couldn't find any documents to analyze for your question. Please ensure you have uploaded documents.";
        }
        documents.forEach(document -> logger.info("Analyzing document: {}", document));

        var documentContent = documents.stream()
            .collect(Collectors.joining("\n\n---\n\n"));
        // Create a prompt that includes the entire document content
        String prompt = String.format("""
            Based on the complete content of the multiple documents, please answer the following question:

            QUESTION: %s
            
            COMPLETE DOCUMENT CONTENT:
            %s
            
            INSTRUCTIONS:
            - Provide a comprehensive answer based on the entire document content
            - If the document doesn't contain information to answer the question, state this clearly
            - Reference specific sections or parts of the document when relevant
            - Be thorough but concise in your response
            """, question, documentContent);

        try {
            // Call the LLM with the complete document context
            String response = generateChatResponse(prompt);
            
            // Add document metadata to the response
            return response.trim();

        } catch (Exception e) {
            logger.error("Error analyzing complete documents", e);
            return "I encountered an error while analyzing the complete documents. Please try again or rephrase your question.";
        }
    }

    private String generateAnswerFromChunks(String question, List<DocumentChunk> relevantChunks,
        SearchAnalysis searchAnalysis) {
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

    private String generateGeneralAnswer(String question) {
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
     * Generate chat response using Spring AI.
     */
    private String generateChatResponse(String prompt) {
        logger.debug("Generating chat response for prompt: {}", prompt);
        try {
            return chatService.generateResponse(prompt);
        } catch (Exception e) {
            logger.error("Error calling Spring AI chat service: {}", e.getMessage());
            throw e;
        }
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
     * DTO class for parsing JSON response from LLM.
     */
    private static class SearchAnalysis {
        @JsonProperty("needs_document_search")
        private boolean needsDocumentSearch;
        
        @JsonProperty("document_ids")
        private List<Integer> documentIds = Collections.emptyList();
        
        @JsonProperty("document_names") 
        private List<String> documentNames = Collections.emptyList();
        
        @JsonProperty("search_query")
        private String searchQuery;
        
        @JsonProperty("question_type")
        private String questionType;
        
        @JsonProperty("reasoning")
        private String reasoning;
        
        // Default constructor for Jackson
        public SearchAnalysis() {}
        
        // Getters
        public boolean isNeedsDocumentSearch() { return needsDocumentSearch; }
        public List<Integer> getDocumentIds() { return documentIds != null ? documentIds : Collections.emptyList(); }
        public List<String> getDocumentNames() { return documentNames != null ? documentNames : Collections.emptyList(); }
        public String getSearchQuery() { return searchQuery; }
        public String getQuestionType() { return questionType; }
        public String getReasoning() { return reasoning; }
        
        // Setters
        public void setNeedsDocumentSearch(boolean needsDocumentSearch) { this.needsDocumentSearch = needsDocumentSearch; }
        public void setDocumentIds(List<Integer> documentIds) { this.documentIds = documentIds; }
        public void setDocumentNames(List<String> documentNames) { this.documentNames = documentNames; }
        public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    }
}
