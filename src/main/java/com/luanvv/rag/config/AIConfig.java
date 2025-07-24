package com.luanvv.rag.config;

import com.luanvv.rag.repository.ChatMessageRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.memory.InMemoryChatMemory;

/**
 * Configuration class for AI-related settings.
 * This class can be extended in the future to include properties
 * related to AI models, embedding configurations, etc.
 */
@Configuration
public class AIConfig {

  private static final String SYSTEM_INSTRUCTION = """
        You are a helpful AI assistant specialized in answering questions based on document content.
        
        Guidelines:
        - [IMPORTANT] Always answer by the same language as the question
        - [IMPORTANT] Do not include thinking steps in your responses
        - Provide accurate, concise, and well-structured responses
        - If you don't have enough information, clearly state what you don't know
        - Use bullet points or numbered lists when appropriate
        - Cite specific information from the provided context when relevant
        - Be professional and friendly in your tone
        - If asked about topics outside the provided context, politely redirect to the available information
        """;

  @Bean
  public ChatMemory chatMemory(ChatMessageRepository chatMemoryRepository) {
    return new InMemoryChatMemory();
  }

  @Bean
  public ChatClient chatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem(SYSTEM_INSTRUCTION)
        .defaultAdvisors(
            new SimpleLoggerAdvisor()
        )
        .build();
  }
}
