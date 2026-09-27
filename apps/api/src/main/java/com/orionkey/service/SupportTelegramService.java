package com.orionkey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.orionkey.entity.SupportMessage;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTelegramService {
    private final RestTemplate restTemplate;
    private final SupportMessageRepository messages;
    private final SupportConversationRepository conversations;
    private final SupportService supportService;

    @Value("${support.telegram.bot-token:}")
    private String botToken;
    @Value("${support.telegram.chat-id:}")
    private String chatId;
    @Value("${support.telegram.webhook-secret:}")
    private String webhookSecret;
    @Value("${support.telegram.webhook-url:}")
    private String webhookUrl;

    private volatile boolean webhookReady;

    public boolean enabled() {
        return !botToken.isBlank() && !chatId.isBlank() && !webhookSecret.isBlank()
                && webhookUrl.startsWith("https://");
    }

    public boolean validSecret(String supplied) {
        return enabled() && supplied != null && MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 300_000)
    public void ensureWebhook() {
        if (!enabled() || webhookReady) return;
        try {
            JsonNode response = restTemplate.postForObject(api("setWebhook"), Map.of(
                    "url", webhookUrl,
                    "secret_token", webhookSecret,
                    "allowed_updates", List.of("message")
            ), JsonNode.class);
            webhookReady = response != null && response.path("ok").asBoolean(false);
            if (!webhookReady) log.warn("Support Telegram webhook registration was rejected");
        } catch (Exception e) {
            log.warn("Support Telegram webhook registration failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void sendPendingNotifications() {
        if (!enabled() || !webhookReady) return;
        List<SupportMessage> pending = messages
                .findTop10BySenderAndTelegramMessageIdIsNullAndNextNotifyAtBeforeOrderByCreatedAtAsc(
                        SupportMessage.Sender.CUSTOMER, LocalDateTime.now());
        for (SupportMessage message : pending) {
            try {
                String prefix = conversations.findById(message.getConversationId())
                        .map(c -> c.getOrderReference() == null ? "新客服消息" : "订单 " + c.getOrderReference() + " 的客服消息")
                        .orElse("新客服消息");
                String notice = prefix + "\n\n" + message.getText().substring(0, Math.min(300, message.getText().length()))
                        + "\n\n请直接回复这条消息";
                JsonNode response;
                if (message.isHasImage()) {
                    var image = supportService.notificationImageData(message);
                    boolean webp = "image/webp".equals(image.contentType());
                    var form = new LinkedMultiValueMap<String, Object>();
                    form.add("chat_id", chatId);
                    form.add("caption", notice);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(image.contentType()));
                    String filename = webp ? "image.webp" : "image." + ("image/png".equals(image.contentType()) ? "png" : "jpg");
                    form.add(webp ? "document" : "photo", new HttpEntity<>(new ByteArrayResource(image.data()) {
                        @Override public String getFilename() { return filename; }
                    }, headers));
                    response = restTemplate.postForObject(api(webp ? "sendDocument" : "sendPhoto"), form, JsonNode.class);
                } else {
                    response = restTemplate.postForObject(api("sendMessage"), Map.of(
                            "chat_id", chatId, "text", notice), JsonNode.class);
                }
                if (response == null || !response.path("ok").asBoolean(false)
                        || !response.path("result").path("message_id").canConvertToLong()) {
                    throw new IllegalStateException("Telegram rejected support notification");
                }
                message.setTelegramMessageId(response.path("result").path("message_id").asLong());
                message.setNextNotifyAt(null);
            } catch (Exception e) {
                message.setNextNotifyAt(LocalDateTime.now().plusMinutes(2));
                log.warn("Support Telegram notification failed for message {}: {}",
                        message.getId(), e.getClass().getSimpleName());
            }
            messages.save(message);
        }
    }

    public void acceptUpdate(JsonNode update) {
        if (!enabled()) return;
        JsonNode telegramMessage = update.path("message");
        if (!chatId.equals(telegramMessage.path("chat").path("id").asText())) return;
        if (!chatId.equals(telegramMessage.path("from").path("id").asText())) return;
        JsonNode replyId = telegramMessage.path("reply_to_message").path("message_id");
        JsonNode updateId = update.path("update_id");
        JsonNode text = telegramMessage.path("text");
        if (!text.isTextual() || !updateId.canConvertToLong()) return;
        if (!replyId.canConvertToLong()) {
            explainReply(telegramMessage, "请直接回复一条客服通知，以便找到对应的访客会话。");
            return;
        }
        String reply = text.asText().trim();
        if (reply.isEmpty() || reply.length() > 2000) {
            explainReply(telegramMessage, "回复内容需为 1 到 2000 字。");
            return;
        }
        if (!supportService.telegramReply(replyId.asLong(), updateId.asLong(), reply)) {
            explainReply(telegramMessage, "找不到对应的客服通知，请回复最近一条通知。");
        }
    }

    private void explainReply(JsonNode message, String reason) {
        try {
            restTemplate.postForObject(api("sendMessage"), Map.of(
                    "chat_id", chatId,
                    "reply_to_message_id", message.path("message_id").asLong(),
                    "text", reason
            ), JsonNode.class);
        } catch (Exception e) {
            log.warn("Support Telegram reply hint failed: {}", e.getClass().getSimpleName());
        }
    }

    private String api(String method) {
        return "https://api.telegram.org/bot" + botToken + "/" + method;
    }
}
