package com.luanvv.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application configuration class.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
    
    private final AppProperties appProperties;
    
    public AppConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }
    
    /**
     * Configure multipart resolver for file uploads.
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
    
    /**
     * Ensure upload directory exists.
     */
    @Bean
    public String uploadDirectory() {
        String uploadDir = appProperties.getFile().getUploadDir();
        Path uploadPath = Paths.get(uploadDir);
        
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + uploadDir, e);
        }
        
        return uploadDir;
    }
}
