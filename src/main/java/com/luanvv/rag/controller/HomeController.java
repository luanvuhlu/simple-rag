package com.luanvv.rag.controller;

import com.luanvv.rag.entity.Document;
import com.luanvv.rag.entity.QueryHistory;
import com.luanvv.rag.service.DocumentService;
import com.luanvv.rag.service.RagQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Main controller for the RAG application home page and query interface.
 */
@Controller
public class HomeController {
    
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    
    private final DocumentService documentService;
    private final RagQueryService ragQueryService;
    
    public HomeController(DocumentService documentService, RagQueryService ragQueryService) {
        this.documentService = documentService;
        this.ragQueryService = ragQueryService;
    }
    
    /**
     * Display the home page with upload form and query interface.
     */
    @GetMapping("/")
    public String home(Model model) {
        logger.debug("Displaying home page");
        
        try {
            List<Document> documents = documentService.getAllDocuments();
            List<QueryHistory> recentQueries = ragQueryService.getRecentQueries(5);
            
            model.addAttribute("documents", documents);
            model.addAttribute("recentQueries", recentQueries);
            model.addAttribute("documentCount", documents.size());
            
            long processedCount = documents.stream()
                    .mapToLong(doc -> doc.getStatus() == Document.DocumentStatus.PROCESSED ? 1 : 0)
                    .sum();
            
            model.addAttribute("processedCount", processedCount);
            
        } catch (Exception e) {
            logger.error("Error loading home page data", e);
            model.addAttribute("error", "Error loading application data: " + e.getMessage());
        }
        
        return "index";
    }
    
    /**
     * Process a RAG query.
     */
    @PostMapping("/query")
    public String processQuery(@RequestParam("question") String question, 
                              Model model, 
                              RedirectAttributes redirectAttributes) {
        
        logger.info("Processing query: {}", question);
        
        if (question == null || question.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Question cannot be empty");
            return "redirect:/";
        }
        
        try {
            QueryHistory queryResult = ragQueryService.processQuery(question.trim());
            redirectAttributes.addFlashAttribute("queryResult", queryResult);
            redirectAttributes.addFlashAttribute("success", "Query processed successfully");
            
        } catch (Exception e) {
            logger.error("Error processing query: {}", question, e);
            redirectAttributes.addFlashAttribute("error", "Error processing query: " + e.getMessage());
        }
        
        return "redirect:/";
    }
}
