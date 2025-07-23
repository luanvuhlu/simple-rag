package com.luanvv.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the RAG application.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    
    private File file = new File();
    private Document document = new Document();
    private Vector vector = new Vector();
    
    public File getFile() {
        return file;
    }
    
    public void setFile(File file) {
        this.file = file;
    }
    
    public Document getDocument() {
        return document;
    }
    
    public void setDocument(Document document) {
        this.document = document;
    }
    
    public Vector getVector() {
        return vector;
    }
    
    public void setVector(Vector vector) {
        this.vector = vector;
    }
    
    public static class File {
        private String uploadDir = "./uploads";
        private String[] allowedExtensions = {"pdf", "docx", "txt"};
        private long maxSize = 52428800; // 50MB
        
        public String getUploadDir() {
            return uploadDir;
        }
        
        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }
        
        public String[] getAllowedExtensions() {
            return allowedExtensions;
        }
        
        public void setAllowedExtensions(String[] allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
        
        public long getMaxSize() {
            return maxSize;
        }
        
        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
    }
    
    public static class Document {
        private int chunkSize = 1000;
        private int chunkOverlap = 200;
        private int maxChunksPerDocument = 500;
        
        public int getChunkSize() {
            return chunkSize;
        }
        
        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }
        
        public int getChunkOverlap() {
            return chunkOverlap;
        }
        
        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
        
        public int getMaxChunksPerDocument() {
            return maxChunksPerDocument;
        }
        
        public void setMaxChunksPerDocument(int maxChunksPerDocument) {
            this.maxChunksPerDocument = maxChunksPerDocument;
        }
    }
    
    public static class Vector {
        private double similarityThreshold = 0.7;
        private int maxResults = 10;
        
        public double getSimilarityThreshold() {
            return similarityThreshold;
        }
        
        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }
        
        public int getMaxResults() {
            return maxResults;
        }
        
        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }
}
