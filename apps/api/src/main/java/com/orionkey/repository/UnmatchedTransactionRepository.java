package com.orionkey.repository;

import com.orionkey.entity.UnmatchedTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnmatchedTransactionRepository extends JpaRepository<UnmatchedTransaction, UUID> {

    Optional<UnmatchedTransaction> findByTxid(String txid);

    Page<UnmatchedTransaction> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<UnmatchedTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<UnmatchedTransaction> findByOrderId(UUID orderId);

    @Modifying
    @Query("UPDATE UnmatchedTransaction ut SET ut.orderId = null WHERE ut.orderId IN :orderIds")
    int detachOrders(@Param("orderIds") List<UUID> orderIds);
}
