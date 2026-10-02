package com.orionkey.service;

import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportImage;
import com.orionkey.entity.SupportMessage;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportImageRepository;
import com.orionkey.repository.SupportMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupportImageTest {
    private final SupportConversationRepository conversations = mock(SupportConversationRepository.class);
    private final SupportMessageRepository messages = mock(SupportMessageRepository.class);
    private final SupportImageRepository images = mock(SupportImageRepository.class);
    private final SupportService service = new SupportService(conversations, messages, images);

    @Test
    void savesImageOnlyForAuthorizedConversationAndCorrectFileType() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String token = "A".repeat(43);
        SupportConversation conversation = new SupportConversation();
        conversation.setId(conversationId);
        conversation.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))));
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> {
            SupportMessage message = invocation.getArgument(0);
            message.setId(messageId);
            message.setCreatedAt(LocalDateTime.now());
            return message;
        });
        SupportMessage stored = new SupportMessage();
        stored.setId(messageId);
        stored.setConversationId(conversationId);
        stored.setHasImage(true);
        when(messages.getReferenceById(messageId)).thenReturn(stored);
        when(messages.findById(messageId)).thenReturn(Optional.of(stored));

        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        var file = new MockMultipartFile("file", "image.png", "image/png", png);
        assertThrows(BusinessException.class, () -> service.customerImage(conversationId, "wrong", file));
        assertThrows(BusinessException.class, () -> service.customerImage(conversationId, token,
                new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3})));
        assertTrue(service.customerImage(conversationId, token, file).hasImage());
        verify(images).save(argThat(image -> image.getMessageId().equals(messageId)
                && image.getContentType().equals("image/png")));

        SupportImage image = new SupportImage();
        image.setMessageId(messageId);
        image.setContentType("image/png");
        image.setData(png);
        when(images.findById(messageId)).thenReturn(Optional.of(image));
        assertArrayEquals(png, service.customerImageData(conversationId, messageId, token).data());
        assertThrows(BusinessException.class, () -> service.customerImageData(conversationId, messageId, "wrong"));
    }

    @Test
    void telegramImageReplyUsesOriginalConversationAndIsIdempotent() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        SupportConversation conversation = new SupportConversation();
        conversation.setId(conversationId);
        SupportMessage original = new SupportMessage();
        original.setConversationId(conversationId);
        original.setSender(SupportMessage.Sender.CUSTOMER);
        when(messages.findByTelegramMessageId(42L)).thenReturn(Optional.of(original));
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> {
            SupportMessage message = invocation.getArgument(0);
            message.setId(messageId);
            message.setCreatedAt(LocalDateTime.now());
            return message;
        });
        SupportMessage stored = new SupportMessage();
        when(messages.getReferenceById(messageId)).thenReturn(stored);
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        assertTrue(service.telegramImageReply(42L, 7L, "answer", png));
        verify(messages).saveAndFlush(argThat(message -> message.getConversationId().equals(conversationId)
                && message.getSender() == SupportMessage.Sender.ADMIN && message.getTelegramUpdateId() == 7L
                && message.getText().equals("answer")));
        verify(images).save(argThat(image -> image.getMessageId().equals(messageId)
                && image.getContentType().equals("image/png")));
        assertTrue(stored.isHasImage());

        when(messages.existsByTelegramUpdateId(7L)).thenReturn(true);
        assertTrue(service.telegramImageReply(42L, 7L, "answer", png));
        verify(images, times(1)).save(any());
        assertThrows(BusinessException.class, () -> service.telegramImageReply(42L, 8L, "", new byte[]{1, 2, 3}));
    }
}
