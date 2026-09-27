package com.orionkey.service;

import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportImage;
import com.orionkey.entity.SupportMessage;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportImageRepository;
import com.orionkey.repository.SupportMessageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SupportCleanupService.class)
class SupportCleanupServiceTest {
    @Autowired private SupportCleanupService cleanup;
    @Autowired private SupportConversationRepository conversations;
    @Autowired private SupportMessageRepository messages;
    @Autowired private SupportImageRepository images;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void removesOldImagesAndMessagesButKeepsRecentConversation() {
        LocalDateTime old = LocalDateTime.now().minusDays(31);
        SupportConversation inactive = conversation(old);
        SupportMessage oldMessage = message(inactive);
        SupportImage image = new SupportImage();
        image.setMessageId(oldMessage.getId());
        image.setContentType("image/png");
        image.setData(new byte[]{1, 2, 3});
        images.saveAndFlush(image);
        SupportConversation active = conversation(LocalDateTime.now());
        SupportMessage newMessage = message(active);

        jdbc.update("update support_messages set created_at = ? where id = ?",
                Timestamp.valueOf(old), oldMessage.getId());
        entityManager.clear();

        cleanup.purgeExpired();
        entityManager.clear();

        assertFalse(images.existsById(oldMessage.getId()));
        assertFalse(messages.existsById(oldMessage.getId()));
        assertFalse(conversations.existsById(inactive.getId()));
        assertTrue(messages.existsById(newMessage.getId()));
        assertTrue(conversations.existsById(active.getId()));
    }

    private SupportConversation conversation(LocalDateTime lastActivity) {
        SupportConversation item = new SupportConversation();
        item.setTokenHash("a".repeat(64));
        item.setLastActivityAt(lastActivity);
        return conversations.saveAndFlush(item);
    }

    private SupportMessage message(SupportConversation conversation) {
        SupportMessage item = new SupportMessage();
        item.setConversationId(conversation.getId());
        item.setSender(SupportMessage.Sender.CUSTOMER);
        item.setText("test");
        return messages.saveAndFlush(item);
    }
}
