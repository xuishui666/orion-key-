package com.orionkey.service;

import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportImageRepository;
import com.orionkey.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportCleanupService {
    private final SupportImageRepository images;
    private final SupportMessageRepository messages;
    private final SupportConversationRepository conversations;

    static LocalDateTime cutoff() {
        return LocalDateTime.now().minusDays(7);
    }

    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Shanghai")
    @Transactional
    public void purgeExpired() {
        var expired = conversations.findByLastActivityAtLessThanEqualOrderById(cutoff());
        int imageCount = 0;
        int messageCount = 0;
        for (var conversation : expired) {
            imageCount += images.deleteByConversation(conversation.getId());
            messageCount += messages.deleteByConversation(conversation.getId());
            conversations.delete(conversation);
        }
        if (!expired.isEmpty()) {
            conversations.flush();
            log.info("Purged expired support data: images={}, messages={}, conversations={}",
                    imageCount, messageCount, expired.size());
        }
    }
}
