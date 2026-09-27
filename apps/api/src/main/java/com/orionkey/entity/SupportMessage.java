package com.orionkey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "support_messages", indexes =
        @Index(name = "idx_support_conversation", columnList = "conversation_id,created_at"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_support_tg_message", columnNames = "telegram_message_id"),
                @UniqueConstraint(name = "uq_support_tg_update", columnNames = "telegram_update_id")
        })
public class SupportMessage extends BaseEntity {
    public enum Sender { CUSTOMER, ADMIN }

    @Column(nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Sender sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    private Long telegramMessageId;

    private Long telegramUpdateId;

    private LocalDateTime nextNotifyAt;
}

