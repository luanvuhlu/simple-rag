package com.luanvv.rag.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a text chunk from a document with its vector embedding.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;
    
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;
    
    // PostgreSQL vector type for embeddings (768 dimensions for nomic-embed-text)
    @Column(name = "embedding_vector", columnDefinition = "vector(768)")
    private String embeddingVector;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public DocumentChunk() {
        this.createdAt = LocalDateTime.now();
    }
    
    public DocumentChunk(Document document, String chunkText, Integer chunkIndex) {
        this();
        this.document = document;
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Document getDocument() {
        return document;
    }
    
    public void setDocument(Document document) {
        this.document = document;
    }
    
    public String getChunkText() {
        return chunkText;
    }
    
    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }
    
    public Integer getChunkIndex() {
        return chunkIndex;
    }
    
    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public String getEmbeddingVector() {
        return embeddingVector;
    }
    
    public void setEmbeddingVector(String embeddingVector) {
        this.embeddingVector = embeddingVector;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
