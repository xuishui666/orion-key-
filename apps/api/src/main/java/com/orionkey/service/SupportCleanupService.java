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

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int imageCount = images.deleteOlderThan(cutoff);
        int messageCount = messages.deleteOlderThan(cutoff);
        int conversationCount = conversations.deleteEmptyOlderThan(cutoff);
        if (imageCount + messageCount + conversationCount > 0) {
            log.info("Purged expired support data: images={}, messages={}, conversations={}",
                    imageCount, messageCount, conversationCount);
        }
    }
}
