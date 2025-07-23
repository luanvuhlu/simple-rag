package com.luanvv.rag.repository;

import com.luanvv.rag.entity.QueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for QueryHistory entity operations.
 */
@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {
    
    /**
     * Find query history ordered by query date descending.
     */
    List<QueryHistory> findAllByOrderByQueryDateDesc();
    
    /**
     * Find recent query history within the last N days.
     */
    List<QueryHistory> findByQueryDateAfterOrderByQueryDateDesc(LocalDateTime date);
    
    /**
     * Find queries containing specific text in the question.
     */
    List<QueryHistory> findByQuestionContainingIgnoreCaseOrderByQueryDateDesc(String searchText);
    
    /**
     * Get average processing time.
     */
    @Query("SELECT AVG(qh.processingTimeMs) FROM QueryHistory qh WHERE qh.processingTimeMs IS NOT NULL")
    Double getAverageProcessingTime();
    
    /**
     * Count total queries.
     */
    @Query("SELECT COUNT(qh) FROM QueryHistory qh")
    long getTotalQueryCount();
}
