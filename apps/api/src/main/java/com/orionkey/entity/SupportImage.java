package com.orionkey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "support_images")
public class SupportImage {
    @Id
    private UUID messageId;

    @Column(nullable = false, length = 16)
    private String contentType;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
