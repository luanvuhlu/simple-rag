package com.luanvv.rag.service;

import com.luanvv.rag.config.AppProperties;
import com.luanvv.rag.entity.Document;
import com.luanvv.rag.entity.DocumentChunk;
import com.luanvv.rag.repository.DocumentRepository;
import com.luanvv.rag.repository.DocumentChunkRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling document upload, processing, and management.
 */
@Service
@Transactional
public class DocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TextExtractionService textExtractionService;
    private final DocumentChunkingService documentChunkingService;
    private final EmbeddingProvider embeddingProvider;
    private final AppProperties appProperties;
    private final String uploadDirectory;
    
    public DocumentService(DocumentRepository documentRepository,
                          DocumentChunkRepository documentChunkRepository,
                          TextExtractionService textExtractionService,
                          DocumentChunkingService documentChunkingService,
                          EmbeddingProvider embeddingProvider,
                          AppProperties appProperties,
                          String uploadDirectory) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.textExtractionService = textExtractionService;
        this.documentChunkingService = documentChunkingService;
        this.embeddingProvider = embeddingProvider;
        this.appProperties = appProperties;
        this.uploadDirectory = uploadDirectory;
    }
    
    /**
     * Upload and process a document.
     */
    public Document uploadDocument(MultipartFile file) throws IOException {
        logger.info("Starting document upload: {}", file.getOriginalFilename());
        
        validateFile(file);
        
        // Save file to disk
        String filename = file.getOriginalFilename();
        String uniqueFilename = generateUniqueFilename(filename);
        Path filePath = Paths.get(uploadDirectory, uniqueFilename);
        
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Create document entity
        Document document = new Document(
            filename,
            filePath.toString(),
            file.getSize(),
            file.getContentType()
        );
        
        document = documentRepository.save(document);
        logger.info("Document saved with ID: {}", document.getId());
        
        // Process document asynchronously
        processDocumentAsync(document);
        
        return document;
    }
    
    /**
     * Process document: extract text, chunk, and generate embeddings.
     */
    @Transactional
    public void processDocument(Document document) {
        logger.info("Processing document: {}", document.getFilename());
        
        try {
            document.setStatus(Document.DocumentStatus.PROCESSING);
            documentRepository.save(document);

            // Extract text
            Path filePath = Paths.get(document.getFilePath());
            String extractedText = textExtractionService.extractText(filePath, document.getContentType());
            
            if (extractedText.isEmpty()) {
                logger.warn("No text extracted from document: {}", document.getFilename());
                document.setStatus(Document.DocumentStatus.ERROR);
                documentRepository.save(document);
                return;
            }
            document.setExtractedText(extractedText);
            
            // Chunk text
            List<String> chunks = documentChunkingService.chunkText(extractedText);
            
            // Create and save document chunks with embeddings
            int chunkIndex = 0;
            for (String chunkText : chunks) {
                if (documentChunkingService.isValidChunk(chunkText)) {
                    // Generate embedding for the chunk
                    try {
                        float[] embedding = embeddingProvider.generateEmbedding(chunkText);
                        String vectorString = convertEmbeddingToVector(embedding);
                        
                        // Use custom insert method to properly handle vector type
                        documentChunkRepository.insertChunkWithVector(
                            document.getId(),
                            chunkText,
                            chunkIndex,
                            vectorString,
                            java.time.LocalDateTime.now()
                        );
                        
                        logger.debug("Generated embedding for chunk {}: {} dimensions", 
                                   chunkIndex, embedding.length);
                        chunkIndex++;
                        
                    } catch (Exception e) {
                        logger.warn("Failed to generate embedding for chunk {}: {}", 
                                  chunkIndex, e.getMessage());
                        
                        // Save chunk without embedding using regular JPA method
                        DocumentChunk chunk = new DocumentChunk(document, chunkText, chunkIndex++);
                        documentChunkRepository.save(chunk);
                    }
                }
            }
            
            document.setTotalChunks(chunkIndex);
            document.setStatus(Document.DocumentStatus.PROCESSED);
            documentRepository.save(document);
            
            logger.info("Document processed successfully: {} chunks created", chunkIndex);
            
        } catch (Exception e) {
            logger.error("Error processing document: {}", document.getFilename(), e);
            document.setStatus(Document.DocumentStatus.ERROR);
            documentRepository.save(document);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Process document asynchronously (placeholder for async processing).
     */
    private void processDocumentAsync(Document document) {
        // In a real application, this would be handled by @Async or a message queue
        // For now, we'll process synchronously
        try {
            processDocument(document);
        } catch (Exception e) {
            logger.error("Error in async document processing", e);
        }
    }
    
    /**
     * Get all documents.
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAllByOrderByUploadDateDesc();
    }
    
    /**
     * Get document by ID.
     */
    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with ID: " + id));
    }
    
    /**
     * Delete document and its chunks.
     */
    public void deleteDocument(Long id) {
        logger.info("Deleting document with ID: {}", id);
        
        Document document = getDocumentById(id);
        
        // Delete file from disk
        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.warn("Failed to delete file: {}", document.getFilePath(), e);
        }
        
        // Delete from database (chunks will be deleted by cascade)
        documentRepository.delete(document);
        
        logger.info("Document deleted successfully: {}", document.getFilename());
    }
    
    /**
     * Get document chunks.
     */
    public List<DocumentChunk> getDocumentChunks(Long documentId) {
        return documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
    }
    
    /**
     * Validate uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }
        
        // Check file size
        if (file.getSize() > appProperties.getFile().getMaxSize()) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }
        
        // Check file extension
        String extension = getFileExtension(filename);
        String[] allowedExtensions = appProperties.getFile().getAllowedExtensions();
        
        if (!Arrays.asList(allowedExtensions).contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("File type not supported: " + extension);
        }
    }
    
    /**
     * Generate unique filename to prevent conflicts.
     */
    private String generateUniqueFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        return baseName + "_" + uuid + "." + extension;
    }
    
    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * Convert float array embedding to PostgreSQL vector format.
     * PostgreSQL vector format is: [1.0,2.0,3.0]
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
}
