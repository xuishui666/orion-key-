package com.orionkey.repository;

import com.orionkey.entity.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {
    List<SupportConversation> findTop100ByLastActivityAtAfterOrderByLastActivityAtDesc(LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SupportConversation c where c.id = :id")
    Optional<SupportConversation> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SupportConversation> findByLastActivityAtLessThanEqualOrderById(LocalDateTime cutoff);
}
