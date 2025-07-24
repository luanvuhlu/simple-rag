package com.luanvv.rag.service;

import com.luanvv.rag.entity.ChatMessage;
import com.luanvv.rag.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for ChatService with memory functionality.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ChatServiceMemoryTest {


    @MockBean
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private ChatClient chatClient;

    @Test
    public void testSaveMessageToMemory() {
        // Given
        ChatService chatService = new ChatService(chatClient, chatMessageRepository);
        String conversationId = "test-conversation-123";
        String userMessage = "Hello, how are you?";
        
        // Mock repository behavior
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(new ChatMessage());
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(Arrays.asList(
                        new ChatMessage(conversationId, MessageType.USER, userMessage)
                ));

        // When
        List<ChatMessage> history = chatService.getConversationHistory(conversationId);

        // Then
        verify(chatMessageRepository).findByConversationIdOrderByCreatedAtAsc(conversationId);
        assertNotNull(history);
    }

    @Test
    public void testClearConversationMemory() {
        // Given
        ChatService chatService = new ChatService(chatClient, chatMessageRepository);
        String conversationId = "test-conversation-456";

        // When
        chatService.clearConversationMemory(conversationId);

        // Then
        verify(chatMessageRepository).deleteByConversationId(conversationId);
    }

    @Test
    public void testGetConversationMessageCount() {
        // Given
        ChatService chatService = new ChatService(chatClient, chatMessageRepository);
        String conversationId = "test-conversation-789";
        
        when(chatMessageRepository.countByConversationId(conversationId)).thenReturn(5L);

        // When
        long count = chatService.getConversationMessageCount(conversationId);

        // Then
        assertEquals(5L, count);
        verify(chatMessageRepository).countByConversationId(conversationId);
    }
}
