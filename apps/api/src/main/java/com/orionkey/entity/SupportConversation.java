package com.orionkey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "support_conversations", indexes =
        @Index(name = "idx_support_activity", columnList = "last_activity_at"))
public class SupportConversation extends BaseEntity {
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(length = 254)
    private String email;

    @Column(length = 80)
    private String orderReference;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt;
}
