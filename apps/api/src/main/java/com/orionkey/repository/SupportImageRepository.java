package com.orionkey.repository;

import com.orionkey.entity.SupportImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface SupportImageRepository extends JpaRepository<SupportImage, UUID> {
    @Modifying
    @Query("delete from SupportImage i where i.messageId in (select m.id from SupportMessage m where m.createdAt < :cutoff)")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
