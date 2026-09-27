package com.orionkey.repository;

import com.orionkey.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    List<SupportMessage> findTop200ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<SupportMessage> findTop10BySenderAndTelegramMessageIdIsNullAndNextNotifyAtBeforeOrderByCreatedAtAsc(
            SupportMessage.Sender sender, LocalDateTime now);

    Optional<SupportMessage> findByTelegramMessageId(Long messageId);

    boolean existsByTelegramUpdateId(Long updateId);
}

