package com.luanvv.rag.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for extracting text content from various document formats.
 */
@Service
public class TextExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(TextExtractionService.class);
    
    /**
     * Extract text from a file based on its content type.
     */
    public String extractText(Path filePath, String contentType) throws IOException {
        logger.debug("Extracting text from file: {} with content type: {}", filePath, contentType);
        
        switch (contentType.toLowerCase()) {
            case "application/pdf":
                return extractFromPdf(filePath);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return extractFromDocx(filePath);
            case "text/plain":
                return extractFromTxt(filePath);
            default:
                throw new UnsupportedOperationException("Unsupported content type: " + contentType);
        }
    }
    
    /**
     * Extract text from PDF file.
     */
    private String extractFromPdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            logger.debug("Extracted {} characters from PDF", text.length());
            return cleanText(text);
            
        } catch (IOException e) {
            logger.error("Error extracting text from PDF: {}", filePath, e);
            throw new IOException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract text from DOCX file.
     */
    private String extractFromDocx(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            
            String text = extractor.getText();
            
            logger.debug("Extracted {} characters from DOCX", text.length());
            return cleanText(text);
            
        } catch (IOException e) {
            logger.error("Error extracting text from DOCX: {}", filePath, e);
            throw new IOException("Failed to extract text from DOCX: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract text from plain text file.
     */
    private String extractFromTxt(Path filePath) throws IOException {
        try {
            String text = Files.readString(filePath, StandardCharsets.UTF_8);
            
            logger.debug("Extracted {} characters from TXT", text.length());
            return cleanText(text);
            
        } catch (IOException e) {
            logger.error("Error extracting text from TXT: {}", filePath, e);
            throw new IOException("Failed to extract text from TXT: " + e.getMessage(), e);
        }
    }
    
    /**
     * Clean and normalize extracted text.
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        
        // Remove excessive whitespace and normalize line breaks
        return text.replaceAll("\\s+", " ")
                  .replaceAll("\\n\\s*\\n", "\n")
                  .trim();
    }
}
