package com.orionkey.repository;

import com.orionkey.entity.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {
    List<SupportConversation> findTop100ByOrderByLastActivityAtDesc();
}

