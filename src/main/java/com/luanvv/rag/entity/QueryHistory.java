package com.luanvv.rag.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a user query and its response from the RAG system.
 */
@Entity
@Table(name = "query_history")
public class QueryHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;
    
    @Column(name = "query_date", nullable = false)
    private LocalDateTime queryDate;
    
    @Column(name = "relevant_documents", columnDefinition = "TEXT")
    private String relevantDocuments;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public QueryHistory() {
        this.queryDate = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
    
    public QueryHistory(String question, String answer) {
        this();
        this.question = question;
        this.answer = answer;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public LocalDateTime getQueryDate() {
        return queryDate;
    }
    
    public void setQueryDate(LocalDateTime queryDate) {
        this.queryDate = queryDate;
    }
    
    public String getRelevantDocuments() {
        return relevantDocuments;
    }
    
    public void setRelevantDocuments(String relevantDocuments) {
        this.relevantDocuments = relevantDocuments;
    }
    
    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
