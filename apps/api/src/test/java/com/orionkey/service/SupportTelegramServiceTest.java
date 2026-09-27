package com.orionkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SupportTelegramServiceTest {
    private final SupportService support = mock(SupportService.class);
    private final SupportTelegramService telegram = new SupportTelegramService(
            mock(RestTemplate.class), mock(SupportMessageRepository.class),
            mock(SupportConversationRepository.class), support);
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
}

