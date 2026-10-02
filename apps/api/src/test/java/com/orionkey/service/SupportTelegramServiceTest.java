package com.orionkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportMessage;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SupportTelegramServiceTest {
    private final SupportService support = mock(SupportService.class);
    private final SupportMessageRepository messages = mock(SupportMessageRepository.class);
    private final SupportConversationRepository conversations = mock(SupportConversationRepository.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SupportTelegramService telegram = new SupportTelegramService(
            restTemplate, new ObjectMapper(), messages, conversations, support);
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
        when(support.telegramReply(43L, 9L, "other")).thenReturn(true);
        telegram.acceptUpdate(json.readTree("""
                {"update_id":7,"message":{"chat":{"id":123},"from":{"id":123},
                 "reply_to_message":{"message_id":42},"text":"reply"}}
                """));
        telegram.acceptUpdate(json.readTree("""
                {"update_id":8,"message":{"chat":{"id":123},"from":{"id":999},
                 "reply_to_message":{"message_id":42},"text":"spoof"}}
                """));
        telegram.acceptUpdate(json.readTree("""
                {"update_id":9,"message":{"chat":{"id":123},"from":{"id":123},
                 "reply_to_message":{"message_id":43},"text":"other"}}
                """));
        verify(support).telegramReply(42L, 7L, "reply");
        verify(support).telegramReply(43L, 9L, "other");
        verifyNoMoreInteractions(support);
    }

    @Test
    void routesPhotoRepliesAndRejectsOversizedImages() throws Exception {
        configure();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        when(restTemplate.postForObject(contains("/getFile"), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(json.readTree("{\"ok\":true,\"result\":{\"file_path\":\"photos/test.png\",\"file_size\":8}}"));
        when(restTemplate.execute(any(URI.class), eq(HttpMethod.GET), isNull(), any(ResponseExtractor.class)))
                .thenReturn(png);
        when(support.telegramImageReply(eq(42L), eq(7L), eq("caption"), any(byte[].class))).thenReturn(true);

        telegram.acceptUpdate(json.readTree("""
                {"update_id":7,"message":{"chat":{"id":123},"from":{"id":123},
                 "reply_to_message":{"message_id":42},"photo":[{"file_id":"small","file_size":4},
                 {"file_id":"large","file_size":8}],"caption":"caption"}}
                """));
        verify(support).telegramImageReply(42L, 7L, "caption", png);

        telegram.acceptUpdate(json.readTree("""
                {"update_id":8,"message":{"chat":{"id":123},"from":{"id":999},
                 "reply_to_message":{"message_id":42},"photo":[{"file_id":"large","file_size":8}]}}
                """));
        telegram.acceptUpdate(json.readTree("""
                {"update_id":9,"message":{"chat":{"id":123},"from":{"id":123},
                 "reply_to_message":{"message_id":42},"photo":[{"file_id":"huge","file_size":4000000}]}}
                """));
        verify(support, times(1)).telegramImageReply(anyLong(), anyLong(), anyString(), any());
        verify(restTemplate, times(1)).execute(any(URI.class), eq(HttpMethod.GET), isNull(), any(ResponseExtractor.class));
    }

    @Test
    void routesImageDocumentsToTheRepliedConversation() throws Exception {
        configure();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        when(restTemplate.postForObject(contains("/getFile"), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(json.readTree("{\"ok\":true,\"result\":{\"file_path\":\"documents/test.png\"}}"));
        when(restTemplate.execute(any(URI.class), eq(HttpMethod.GET), isNull(), any(ResponseExtractor.class)))
                .thenReturn(png);
        when(support.telegramImageReply(eq(44L), eq(10L), eq(""), any(byte[].class))).thenReturn(true);

        telegram.acceptUpdate(json.readTree("""
                {"update_id":10,"message":{"chat":{"id":123},"from":{"id":123},
                 "reply_to_message":{"message_id":44},
                 "document":{"file_id":"document-id","mime_type":"image/png"}}}
                """));
        verify(support).telegramImageReply(44L, 10L, "", png);
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
        SupportConversation conversation = new SupportConversation();
        conversation.setEmail("buyer@example.com");
        when(conversations.findById(message.getConversationId())).thenReturn(Optional.of(conversation));
        SupportMessage previous = new SupportMessage();
        previous.setTelegramMessageId(98L);
        when(messages.findTopByConversationIdAndSenderAndTelegramMessageIdIsNotNullOrderByCreatedAtDesc(
                message.getConversationId(), SupportMessage.Sender.CUSTOMER)).thenReturn(Optional.of(previous));
        when(support.notificationImageData(message)).thenReturn(new SupportService.ImageData("image/png", new byte[]{1, 2, 3}));
        when(restTemplate.postForObject(contains("/sendPhoto"), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(json.readTree("{\"ok\":true,\"result\":{\"message_id\":99}}"));

        telegram.sendPendingNotifications();

        assertTrue(message.getTelegramMessageId() == 99L);
        verify(messages).save(message);
        var form = org.mockito.ArgumentCaptor.forClass(MultiValueMap.class);
        verify(restTemplate).postForObject(contains("/sendPhoto"), form.capture(), eq(com.fasterxml.jackson.databind.JsonNode.class));
        assertTrue(form.getValue().getFirst("caption").toString().contains("客服会话 #"));
        assertTrue(form.getValue().getFirst("caption").toString().contains("buyer@example.com"));
        assertEquals(98, json.readTree(form.getValue().getFirst("reply_parameters").toString())
                .path("message_id").asInt());
    }

    @Test
    void keepsInterleavedConversationsDistinct() throws Exception {
        configure();
        ReflectionTestUtils.setField(telegram, "webhookReady", true);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SupportMessage first = customerMessage(firstId, "first");
        SupportMessage second = customerMessage(secondId, "second");
        SupportMessage next = customerMessage(firstId, "next");
        when(messages.findTop10BySenderAndTelegramMessageIdIsNullAndNextNotifyAtBeforeOrderByCreatedAtAsc(
                eq(SupportMessage.Sender.CUSTOMER), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second, next));
        when(conversations.findById(any())).thenReturn(Optional.of(new SupportConversation()));
        when(messages.findTopByConversationIdAndSenderAndTelegramMessageIdIsNotNullOrderByCreatedAtDesc(
                eq(firstId), eq(SupportMessage.Sender.CUSTOMER)))
                .thenReturn(Optional.empty(), Optional.of(first));
        when(restTemplate.postForObject(contains("/sendMessage"), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(json.readTree("{\"ok\":true,\"result\":{\"message_id\":101}}"),
                        json.readTree("{\"ok\":true,\"result\":{\"message_id\":102}}"),
                        json.readTree("{\"ok\":true,\"result\":{\"message_id\":103}}"));

        telegram.sendPendingNotifications();

        var body = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(restTemplate, times(3)).postForObject(contains("/sendMessage"), body.capture(), eq(com.fasterxml.jackson.databind.JsonNode.class));
        assertTrue(body.getAllValues().get(0).get("text").toString().contains(firstId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT)));
        assertTrue(body.getAllValues().get(1).get("text").toString().contains(secondId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT)));
        assertEquals(null, body.getAllValues().get(0).get("reply_parameters"));
        assertEquals(body.getAllValues().get(2).get("reply_parameters"),
                Map.of("message_id", 101L, "allow_sending_without_reply", true));
    }

    private SupportMessage customerMessage(UUID conversationId, String text) {
        SupportMessage message = new SupportMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setSender(SupportMessage.Sender.CUSTOMER);
        message.setText(text);
        return message;
    }
}
