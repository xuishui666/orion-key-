package com.orionkey.service;

import com.orionkey.constant.ErrorCode;
import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportMessage;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;

    public record MessageView(UUID id, String sender, String text, LocalDateTime createdAt) {}
    public record ConversationView(UUID id, String email, String orderReference,
                                   LocalDateTime lastActivityAt, List<MessageView> messages) {}
    public record CreatedConversation(UUID id, String token) {}

    @Transactional
    public CreatedConversation create(String email, String orderReference, String text) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        SupportConversation conversation = new SupportConversation();
        conversation.setTokenHash(hash(token));
        conversation.setEmail(optional(email, 254));
        conversation.setOrderReference(optional(orderReference, 80));
        conversation.setLastActivityAt(LocalDateTime.now());
        conversations.save(conversation);
        saveMessage(conversation, SupportMessage.Sender.CUSTOMER, text, null);
        return new CreatedConversation(conversation.getId(), token);
    }

    @Transactional(readOnly = true)
    public ConversationView customerConversation(UUID id, String token) {
        SupportConversation conversation = find(id);
        authorize(conversation, token);
        return view(conversation);
    }

    @Transactional
    public MessageView customerMessage(UUID id, String token, String text) {
        SupportConversation conversation = find(id);
        authorize(conversation, token);
        return saveMessage(conversation, SupportMessage.Sender.CUSTOMER, text, null);
    }

    @Transactional(readOnly = true)
    public List<ConversationView> adminConversations() {
        return conversations.findTop100ByOrderByLastActivityAtDesc().stream()
                .map(c -> new ConversationView(c.getId(), c.getEmail(), c.getOrderReference(),
                        c.getLastActivityAt(), List.of())).toList();
    }

    @Transactional(readOnly = true)
    public ConversationView adminConversation(UUID id) {
        return view(find(id));
    }

    @Transactional
    public MessageView adminMessage(UUID id, String text) {
        return saveMessage(find(id), SupportMessage.Sender.ADMIN, text, null);
    }

    @Transactional
    public boolean telegramReply(long repliedMessageId, long updateId, String text) {
        if (messages.existsByTelegramUpdateId(updateId)) return false;
        SupportMessage original = messages.findByTelegramMessageId(repliedMessageId)
                .orElse(null);
        if (original == null || original.getSender() != SupportMessage.Sender.CUSTOMER) return false;
        saveMessage(find(original.getConversationId()), SupportMessage.Sender.ADMIN, text, updateId);
        return true;
    }

    private MessageView saveMessage(SupportConversation conversation, SupportMessage.Sender sender,
                                    String text, Long updateId) {
        if (text == null || text.isBlank() || text.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息长度需为 1 到 2000 字");
        }
        SupportMessage message = new SupportMessage();
        message.setConversationId(conversation.getId());
        message.setSender(sender);
        message.setText(text.trim());
        message.setTelegramUpdateId(updateId);
        if (sender == SupportMessage.Sender.CUSTOMER) message.setNextNotifyAt(LocalDateTime.now());
        messages.saveAndFlush(message);
        conversation.setLastActivityAt(LocalDateTime.now());
        conversations.save(conversation);
        return new MessageView(message.getId(), sender.name(), message.getText(), message.getCreatedAt());
    }

    private SupportConversation find(UUID id) {
        return conversations.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在", HttpStatus.NOT_FOUND));
    }

    private void authorize(SupportConversation conversation, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}") || !MessageDigest.isEqual(
                conversation.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此会话", HttpStatus.FORBIDDEN);
        }
    }

    private ConversationView view(SupportConversation conversation) {
        List<MessageView> items = messages.findTop200ByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .stream().map(m -> new MessageView(m.getId(), m.getSender().name(), m.getText(), m.getCreatedAt()))
                .collect(java.util.stream.Collectors.toList());
        Collections.reverse(items);
        return new ConversationView(conversation.getId(), conversation.getEmail(),
                conversation.getOrderReference(), conversation.getLastActivityAt(), items);
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new BusinessException(ErrorCode.BAD_REQUEST, "联系信息过长");
        return trimmed;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

