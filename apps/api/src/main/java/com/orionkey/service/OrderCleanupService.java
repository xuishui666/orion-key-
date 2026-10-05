package com.orionkey.service;

import com.orionkey.entity.Order;
import com.orionkey.entity.UnmatchedTransaction;
import com.orionkey.repository.CardKeyRepository;
import com.orionkey.repository.OrderItemRepository;
import com.orionkey.repository.OrderRepository;
import com.orionkey.repository.PointsLogRepository;
import com.orionkey.repository.UnmatchedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCleanupService {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final CardKeyRepository cards;
    private final UnmatchedTransactionRepository reviews;
    private final PointsLogRepository pointsLogs;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void purgeOldOrders() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime paidCutoff = now.minusDays(30);
        LocalDateTime unpaidCutoff = LocalDate.now(BUSINESS_ZONE).atStartOfDay();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int count = transaction.execute(status -> {
                List<Order> candidates = orders.findPurgeCandidates(
                        paidCutoff, unpaidCutoff, now, PageRequest.of(0, BATCH_SIZE));
                if (candidates.isEmpty()) return 0;
                List<UUID> ids = candidates.stream().map(Order::getId).toList();
                for (Order order : candidates) {
                    if (order.getUsdtTxId() == null || order.getUsdtTxId().isBlank()) continue;
                    UnmatchedTransaction marker = reviews.findByTxid(order.getUsdtTxId()).orElseGet(() -> {
                        UnmatchedTransaction record = new UnmatchedTransaction();
                        record.setTxid(order.getUsdtTxId());
                        record.setSubmittedAt(now);
                        return record;
                    });
                    marker.setOrderId(null);
                    marker.setChain(order.getUsdtChain());
                    marker.setStatus("AUTO_APPROVED");
                    marker.setSource("PURGED_ORDER");
                    reviews.saveAndFlush(marker);
                }
                cards.detachOrders(ids);
                reviews.detachOrders(ids);
                pointsLogs.detachOrders(ids);
                items.deleteByOrderIds(ids);
                orders.deleteAllInBatch(candidates);
                return ids.size();
            });
            if (count == 0) break;
            total += count;
            if (count < BATCH_SIZE) break;
        }
        if (total > 0) log.info("Purged {} old orders", total);
        if (total == BATCH_SIZE * MAX_BATCHES) log.warn("Order cleanup batch limit reached; remaining orders await next run");
    }
}
