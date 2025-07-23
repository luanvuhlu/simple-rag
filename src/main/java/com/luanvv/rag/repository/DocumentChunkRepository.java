package com.luanvv.rag.repository;

import com.luanvv.rag.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for DocumentChunk entity operations.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    
    /**
     * Find chunks by document ID.
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);
    
    /**
     * Delete all chunks for a specific document.
     */
    void deleteByDocumentId(Long documentId);
    
    /**
     * Count chunks for a specific document.
     */
    long countByDocumentId(Long documentId);
    
    /**
     * Find similar chunks using vector similarity search.
     * Uses cosine similarity with pgvector extension.
     */
    @Query(value = """
        SELECT dc.* FROM document_chunks dc 
        WHERE dc.embedding_vector IS NOT NULL 
        ORDER BY dc.embedding_vector <=> CAST(:queryVector AS vector) 
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(@Param("queryVector") String queryVector, @Param("limit") int limit);
    
    /**
     * Find similar chunks with similarity threshold.
     * Only returns chunks with similarity score above the threshold.
     */
    @Query(value = """
        SELECT dc.*, (1 - (dc.embedding_vector <=> CAST(:queryVector AS vector))) as similarity 
        FROM document_chunks dc 
        WHERE dc.embedding_vector IS NOT NULL 
        AND (1 - (dc.embedding_vector <=> CAST(:queryVector AS vector))) >= :threshold
        ORDER BY dc.embedding_vector <=> CAST(:queryVector AS vector) 
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarChunksWithScore(
        @Param("queryVector") String queryVector, 
        @Param("threshold") double threshold, 
        @Param("limit") int limit
    );
    
    /**
     * Find chunks that don't have embeddings yet.
     */
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.embeddingVector IS NULL")
    List<DocumentChunk> findChunksWithoutEmbeddings();
    
    /**
     * Insert a document chunk with vector embedding using native SQL.
     * This method properly handles the vector type casting.
     */
    @Modifying
    @Query(value = """
        INSERT INTO document_chunks (document_id, chunk_text, chunk_index, embedding_vector, created_at) 
        VALUES (:documentId, :chunkText, :chunkIndex, CAST(:embeddingVector AS vector), :createdAt)
        """, nativeQuery = true)
    void insertChunkWithVector(
        @Param("documentId") Long documentId,
        @Param("chunkText") String chunkText, 
        @Param("chunkIndex") Integer chunkIndex,
        @Param("embeddingVector") String embeddingVector,
        @Param("createdAt") java.time.LocalDateTime createdAt
    );
}
