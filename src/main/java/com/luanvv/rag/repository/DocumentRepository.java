package com.luanvv.rag.repository;

import com.luanvv.rag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for Document entity operations.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    /**
     * Find documents by filename containing the given text (case-insensitive).
     */
    List<Document> findByFilenameContainingIgnoreCase(String filename);
    
    /**
     * Find documents by status.
     */
    List<Document> findByStatus(Document.DocumentStatus status);
    
    /**
     * Find documents uploaded after a specific date, ordered by upload date descending.
     */
    List<Document> findByUploadDateAfterOrderByUploadDateDesc(LocalDateTime date);
    
    /**
     * Find all documents ordered by upload date descending.
     */
    List<Document> findAllByOrderByUploadDateDesc();
    
    /**
     * Count documents by status.
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.status = :status")
    long countByStatus(Document.DocumentStatus status);
    
    /**
     * Get total file size of all documents.
     */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d")
    long getTotalFileSize();
}
