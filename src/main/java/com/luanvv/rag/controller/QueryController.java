package com.luanvv.rag.controller;

import com.luanvv.rag.entity.QueryHistory;
import com.luanvv.rag.service.RagQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for query history and RAG query operations.
 */
@Controller
@RequestMapping("/queries")
public class QueryController {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryController.class);
    
    private final RagQueryService ragQueryService;
    
    public QueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }
    
    /**
     * Display query history page.
     */
    @GetMapping
    public String queryHistory(Model model) {
        logger.debug("Displaying query history");
        
        try {
            List<QueryHistory> queries = ragQueryService.getQueryHistory();
            model.addAttribute("queries", queries);
            
        } catch (Exception e) {
            logger.error("Error loading query history", e);
            model.addAttribute("error", "Error loading query history: " + e.getMessage());
        }
        
        return "query-history";
    }
    
    /**
     * Process RAG query via AJAX.
     */
    @PostMapping("/ask")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> askQuestion(@RequestParam("question") String question) {
        
        logger.info("Processing AJAX query: {}", question);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (question == null || question.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Question cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }
            
            QueryHistory queryResult = ragQueryService.processQuery(question.trim());
            
            response.put("success", true);
            response.put("query", Map.of(
                "id", queryResult.getId(),
                "question", queryResult.getQuestion(),
                "answer", queryResult.getAnswer(),
                "relevantDocuments", queryResult.getRelevantDocuments() != null ? 
                    queryResult.getRelevantDocuments() : "",
                "processingTime", queryResult.getProcessingTimeMs(),
                "queryDate", queryResult.getQueryDate().toString()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing AJAX query: {}", question, e);
            
            response.put("success", false);
            response.put("message", "Error processing query: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get query by ID via AJAX.
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getQuery(@PathVariable Long id) {
        
        try {
            // For simplicity, we'll just return a success response
            // In a real implementation, you'd fetch the query by ID
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Query details would be loaded here");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting query: {}", id, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error loading query: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
