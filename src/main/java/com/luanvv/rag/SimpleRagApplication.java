package com.luanvv.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for the Simple RAG application.
 * 
 * This application provides a complete RAG (Retrieval-Augmented Generation) system
 * that allows users to upload documents, process them into vector embeddings,
 * and query the content using natural language with AI-powered responses.
 */
@SpringBootApplication
@EnableTransactionManagement
public class SimpleRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleRagApplication.class, args);
    }
}
