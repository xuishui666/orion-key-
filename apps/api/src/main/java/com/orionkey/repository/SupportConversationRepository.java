package com.orionkey.repository;

import com.orionkey.entity.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {
    List<SupportConversation> findTop100ByOrderByLastActivityAtDesc();

    @Modifying
    @Query("delete from SupportConversation c where c.lastActivityAt < :cutoff and not exists "
            + "(select 1 from SupportMessage m where m.conversationId = c.id)")
    int deleteEmptyOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
