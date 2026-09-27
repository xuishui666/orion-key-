package com.orionkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportMessage;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SupportTelegramServiceTest {
    private final SupportService support = mock(SupportService.class);
    private final SupportMessageRepository messages = mock(SupportMessageRepository.class);
    private final SupportConversationRepository conversations = mock(SupportConversationRepository.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SupportTelegramService telegram = new SupportTelegramService(
            restTemplate, messages, conversations, support);
    private final ObjectMapper json = new ObjectMapper();

    private void configure() {
        ReflectionTestUtils.setField(telegram, "botToken", "test-token");
        ReflectionTestUtils.setField(telegram, "chatId", "123");
        ReflectionTestUtils.setField(telegram, "webhookSecret", "private-secret");
        ReflectionTestUtils.setField(telegram, "webhookUrl", "https://example.com/api/support/telegram/webhook");
    }

    @Test
    void routesOnlyPrivateAuthorizedReplies() throws Exception {
        configure();
        assertTrue(telegram.validSecret("private-secret"));
        assertFalse(telegram.validSecret("wrong-secret"));
        when(support.telegramReply(42L, 7L, "reply")).thenReturn(true);
        telegram.acceptUpdate(json.readTree("""
                {"update_id":7,"message":{"chat":{"id":123},"from":{"id":123},
                 "reply_to_message":{"message_id":42},"text":"reply"}}
                """));
        telegram.acceptUpdate(json.readTree("""
                {"update_id":8,"message":{"chat":{"id":123},"from":{"id":999},
                 "reply_to_message":{"message_id":42},"text":"spoof"}}
                """));
        verify(support).telegramReply(42L, 7L, "reply");
        verifyNoMoreInteractions(support);
    }

    @Test
    void sendsCustomerImageToTelegramAsPhoto() throws Exception {
        configure();
        ReflectionTestUtils.setField(telegram, "webhookReady", true);
        SupportMessage message = new SupportMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(UUID.randomUUID());
        message.setSender(SupportMessage.Sender.CUSTOMER);
        message.setText("[图片]");
        message.setHasImage(true);
        when(messages.findTop10BySenderAndTelegramMessageIdIsNullAndNextNotifyAtBeforeOrderByCreatedAtAsc(
                eq(SupportMessage.Sender.CUSTOMER), any(LocalDateTime.class))).thenReturn(List.of(message));
        when(conversations.findById(message.getConversationId())).thenReturn(Optional.of(new SupportConversation()));
        when(support.notificationImageData(message)).thenReturn(new SupportService.ImageData("image/png", new byte[]{1, 2, 3}));
        when(restTemplate.postForObject(contains("/sendPhoto"), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(json.readTree("{\"ok\":true,\"result\":{\"message_id\":99}}"));

        telegram.sendPendingNotifications();

        assertTrue(message.getTelegramMessageId() == 99L);
        verify(messages).save(message);
    }
}
