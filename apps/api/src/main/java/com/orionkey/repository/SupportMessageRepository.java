package com.orionkey.repository;

import com.orionkey.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    @Modifying
    @Query("delete from SupportMessage m where m.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    List<SupportMessage> findTop200ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<SupportMessage> findTop10BySenderAndTelegramMessageIdIsNullAndNextNotifyAtBeforeOrderByCreatedAtAsc(
            SupportMessage.Sender sender, LocalDateTime now);

    Optional<SupportMessage> findByTelegramMessageId(Long messageId);

    boolean existsByTelegramUpdateId(Long updateId);
}
