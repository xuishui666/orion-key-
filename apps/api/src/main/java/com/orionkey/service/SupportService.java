package com.orionkey.service;

import com.orionkey.constant.ErrorCode;
import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportImage;
import com.orionkey.entity.SupportMessage;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportImageRepository;
import com.orionkey.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class SupportService {
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    private static final List<UnsafeContentRule> UNSAFE_CUSTOMER_CONTENT = List.of(
            new UnsafeContentRule("JNDI_LOOKUP", Pattern.compile(
                    "(?i)\\$\\{\\s*(?:jndi|lower|upper|env|sys):|\\b(?:ldap|ldaps|rmi|iiop)\\s*://")),
            new UnsafeContentRule("SCRIPT_INJECTION", Pattern.compile(
                    "(?i)<\\s*script\\b|<[^>]{0,512}\\s+on[a-z]{2,32}\\s*=|<\\s*svg/onload\\s*=|javascript\\s*:")),
            new UnsafeContentRule("TEMPLATE_INJECTION", Pattern.compile(
                    "(?is)(?:[$%]\\{|\\{\\{|<%|\\{%)[^\\r\\n]{0,128}(?:\\d\\s*[-+*/]\\s*\\d|jndi|class|request|config|self|__)[^\\r\\n]{0,128}(?:\\}|%>)")),
            new UnsafeContentRule("SQL_INJECTION", Pattern.compile(
                    "(?i)\\b(?:union\\s+(?:all\\s+)?select|drop\\s+table|information_schema|sleep\\s*\\(|benchmark\\s*\\()")),
            new UnsafeContentRule("PATH_TRAVERSAL", Pattern.compile(
                    "(?i)(?:\\.\\./|%2e%2e(?:%2f|/)|/etc/passwd|\\\\(?:windows\\\\)?system32)")),
            new UnsafeContentRule("COMMAND_INJECTION", Pattern.compile(
                    "(?i)(?:\\b(?:curl|wget)\\s+https?://|\\b(?:powershell(?:\\.exe)?\\s+-|cmd\\.exe\\s+/)|\\bbase64\\s+-d\\b)"))
    );
    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportImageRepository images;

    public record MessageView(UUID id, String sender, String text, boolean hasImage, LocalDateTime createdAt) {}
    public record ImageData(String contentType, byte[] data) {}
    public record ConversationView(UUID id, String email, String orderReference,
                                   LocalDateTime lastActivityAt, List<MessageView> messages) {}
    public record CreatedConversation(UUID id, String token) {}

    @Transactional
    public CreatedConversation create(String email, String orderReference, String text) {
        rejectUnsafeCustomerText(text, null);
        rejectUnsafeCustomerText(email, null);
        rejectUnsafeCustomerText(orderReference, null);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        SupportConversation conversation = new SupportConversation();
        conversation.setTokenHash(hash(token));
        conversation.setEmail(optional(email, 254));
        conversation.setOrderReference(optional(orderReference, 80));
        conversation.setLastActivityAt(LocalDateTime.now());
        conversations.save(conversation);
        if (text != null && !text.isBlank()) {
            saveMessage(conversation, SupportMessage.Sender.CUSTOMER, text, null);
        }
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
        SupportConversation conversation = findForWrite(id);
        authorize(conversation, token);
        return saveMessage(conversation, SupportMessage.Sender.CUSTOMER, text, null);
    }

    @Transactional
    public MessageView customerImage(UUID id, String token, MultipartFile file) {
        SupportConversation conversation = findForWrite(id);
        authorize(conversation, token);
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小须在 3 MB 以内");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "图片读取失败");
        }
        String contentType = imageType(data);
        if (contentType == null || !contentType.equals(file.getContentType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 JPG、PNG、WebP 图片");
        }
        return saveImage(conversation, SupportMessage.Sender.CUSTOMER, "[图片]", null, contentType, data);
    }

    @Transactional(readOnly = true)
    public ImageData customerImageData(UUID id, UUID messageId, String token) {
        authorize(find(id), token);
        return imageData(id, messageId);
    }

    @Transactional(readOnly = true)
    public ImageData adminImageData(UUID id, UUID messageId) {
        find(id);
        return imageData(id, messageId);
    }

    @Transactional(readOnly = true)
    public ImageData notificationImageData(SupportMessage message) {
        return imageData(message.getConversationId(), message.getId());
    }

    private ImageData imageData(UUID id, UUID messageId) {
        SupportMessage message = messages.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片不存在", HttpStatus.NOT_FOUND));
        if (!message.getConversationId().equals(id) || !message.isHasImage()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在", HttpStatus.NOT_FOUND);
        }
        SupportImage image = images.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图片不存在", HttpStatus.NOT_FOUND));
        return new ImageData(image.getContentType(), image.getData());
    }

    private static String imageType(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) return "image/jpeg";
        if (data.length >= 8 && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47 && data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a) return "image/png";
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return "image/webp";
        return null;
    }

    @Transactional(readOnly = true)
    public List<ConversationView> adminConversations() {
        return conversations.findTop100ByLastActivityAtAfterOrderByLastActivityAtDesc(SupportCleanupService.cutoff()).stream()
                .map(c -> new ConversationView(c.getId(), c.getEmail(), c.getOrderReference(),
                        c.getLastActivityAt(), List.of())).toList();
    }

    @Transactional(readOnly = true)
    public ConversationView adminConversation(UUID id) {
        return view(find(id));
    }

    @Transactional
    public void adminDelete(UUID id) {
        SupportConversation conversation = findForWrite(id);
        images.deleteByConversation(id);
        messages.deleteByConversation(id);
        conversations.delete(conversation);
        conversations.flush();
    }

    @Transactional
    public MessageView adminMessage(UUID id, String text) {
        return saveMessage(findForWrite(id), SupportMessage.Sender.ADMIN, text, null);
    }

    @Transactional
    public boolean telegramReply(long repliedMessageId, long updateId, String text) {
        if (messages.existsByTelegramUpdateId(updateId)) return true;
        SupportMessage original = messages.findByTelegramMessageId(repliedMessageId)
                .orElse(null);
        if (original == null || original.getSender() != SupportMessage.Sender.CUSTOMER) return false;
        var conversation = conversations.lockById(original.getConversationId()).filter(this::isActive).orElse(null);
        if (conversation == null) return false;
        saveMessage(conversation, SupportMessage.Sender.ADMIN, text, updateId);
        return true;
    }

    @Transactional
    public boolean telegramImageReply(long repliedMessageId, long updateId, String caption, byte[] data) {
        if (messages.existsByTelegramUpdateId(updateId)) return true;
        SupportMessage original = messages.findByTelegramMessageId(repliedMessageId).orElse(null);
        if (original == null || original.getSender() != SupportMessage.Sender.CUSTOMER) return false;
        if (data == null || data.length == 0 || data.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小须在 3 MB 以内");
        }
        String contentType = imageType(data);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 JPG、PNG、WebP 图片");
        }
        String text = caption == null || caption.isBlank() ? "[图片]" : caption.trim();
        var conversation = conversations.lockById(original.getConversationId()).filter(this::isActive).orElse(null);
        if (conversation == null) return false;
        saveImage(conversation, SupportMessage.Sender.ADMIN, text, updateId, contentType, data);
        return true;
    }

    private MessageView saveImage(SupportConversation conversation, SupportMessage.Sender sender,
                                  String text, Long updateId, String contentType, byte[] data) {
        MessageView view = saveMessage(conversation, sender, text, updateId);
        messages.getReferenceById(view.id()).setHasImage(true);
        SupportImage image = new SupportImage();
        image.setMessageId(view.id());
        image.setContentType(contentType);
        image.setData(data);
        images.save(image);
        return new MessageView(view.id(), view.sender(), view.text(), true, view.createdAt());
    }

    private MessageView saveMessage(SupportConversation conversation, SupportMessage.Sender sender,
                                    String text, Long updateId) {
        if (text == null || text.isBlank() || text.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息长度需为 1 到 2000 字");
        }
        if (sender == SupportMessage.Sender.CUSTOMER) {
            rejectUnsafeCustomerText(text, conversation.getId());
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
        return new MessageView(message.getId(), sender.name(), message.getText(), false, message.getCreatedAt());
    }

    static String unsafeCustomerContentCategory(String text) {
        if (text == null || text.isBlank()) return null;
        return UNSAFE_CUSTOMER_CONTENT.stream()
                .filter(rule -> rule.pattern().matcher(text).find())
                .map(UnsafeContentRule::category)
                .findFirst()
                .orElse(null);
    }

    private static void rejectUnsafeCustomerText(String text, UUID conversationId) {
        if (text != null && text.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息长度不能超过 2000 字");
        }
        String category = unsafeCustomerContentCategory(text);
        if (category == null) return;
        log.warn("Blocked unsafe customer support content: category={}, conversationId={}, length={}",
                category, conversationId == null ? "new" : conversationId, text.length());
        throw new BusinessException(ErrorCode.BAD_REQUEST, "消息包含不允许的内容");
    }

    private record UnsafeContentRule(String category, Pattern pattern) {}

    private SupportConversation find(UUID id) {
        return conversations.findById(id).filter(this::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在", HttpStatus.NOT_FOUND));
    }

    private SupportConversation findForWrite(UUID id) {
        return conversations.lockById(id).filter(this::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在", HttpStatus.NOT_FOUND));
    }

    private boolean isActive(SupportConversation conversation) {
        return conversation.getLastActivityAt().isAfter(SupportCleanupService.cutoff());
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
                .stream().map(m -> new MessageView(m.getId(), m.getSender().name(), m.getText(), m.isHasImage(), m.getCreatedAt()))
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
