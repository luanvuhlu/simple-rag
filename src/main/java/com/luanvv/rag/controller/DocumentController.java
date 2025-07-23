package com.luanvv.rag.controller;

import com.luanvv.rag.entity.Document;
import com.luanvv.rag.entity.DocumentChunk;
import com.luanvv.rag.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for document management operations.
 */
@Controller
@RequestMapping("/documents")
public class DocumentController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    
    private final DocumentService documentService;
    
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }
    
    /**
     * Display all documents.
     */
    @GetMapping
    public String listDocuments(Model model) {
        logger.debug("Displaying documents list");
        
        try {
            List<Document> documents = documentService.getAllDocuments();
            model.addAttribute("documents", documents);
            
        } catch (Exception e) {
            logger.error("Error loading documents", e);
            model.addAttribute("error", "Error loading documents: " + e.getMessage());
        }
        
        return "documents";
    }
    
    /**
     * Upload a new document.
     */
    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file, 
                                RedirectAttributes redirectAttributes) {
        
        logger.info("Uploading document: {}", file.getOriginalFilename());
        
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
            return "redirect:/";
        }
        
        try {
            Document document = documentService.uploadDocument(file);
            redirectAttributes.addFlashAttribute("success", 
                "Document uploaded successfully: " + document.getFilename());
            
        } catch (Exception e) {
            logger.error("Error uploading document: {}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", "Error uploading document: " + e.getMessage());
        }
        
        return "redirect:/";
    }
    
    /**
     * Upload document via AJAX.
     */
    @PostMapping("/upload-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadDocumentAjax(@RequestParam("file") MultipartFile file) {
        
        logger.info("AJAX upload: {}", file.getOriginalFilename());
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }
            
            Document document = documentService.uploadDocument(file);
            
            response.put("success", true);
            response.put("message", "Document uploaded successfully");
            response.put("document", Map.of(
                "id", document.getId(),
                "filename", document.getFilename(),
                "status", document.getStatus().toString(),
                "uploadDate", document.getUploadDate().toString()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error in AJAX upload: {}", file.getOriginalFilename(), e);
            
            response.put("success", false);
            response.put("message", "Error uploading document: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * View document details.
     */
    @GetMapping("/{id}")
    public String viewDocument(@PathVariable Long id, Model model) {
        logger.debug("Viewing document: {}", id);
        
        try {
            Document document = documentService.getDocumentById(id);
            List<DocumentChunk> chunks = documentService.getDocumentChunks(id);
            
            model.addAttribute("document", document);
            model.addAttribute("chunks", chunks);
            
        } catch (Exception e) {
            logger.error("Error loading document: {}", id, e);
            model.addAttribute("error", "Error loading document: " + e.getMessage());
        }
        
        return "document-detail";
    }
    
    /**
     * Delete a document.
     */
    @PostMapping("/{id}/delete")
    public String deleteDocument(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        
        logger.info("Deleting document: {}", id);
        
        try {
            documentService.deleteDocument(id);
            redirectAttributes.addFlashAttribute("success", "Document deleted successfully");
            
        } catch (Exception e) {
            logger.error("Error deleting document: {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Error deleting document: " + e.getMessage());
        }
        
        return "redirect:/documents";
    }
    
    /**
     * Get document status via AJAX.
     */
    @GetMapping("/{id}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable Long id) {
        
        try {
            Document document = documentService.getDocumentById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", document.getId());
            response.put("status", document.getStatus().toString());
            response.put("totalChunks", document.getTotalChunks());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting document status: {}", id, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Error getting document status: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
