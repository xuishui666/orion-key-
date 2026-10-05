package com.orionkey.repository;

import com.orionkey.entity.PointsLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.UUID;

public interface PointsLogRepository extends JpaRepository<PointsLog, UUID> {

    Page<PointsLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Query("UPDATE PointsLog pl SET pl.orderId = null WHERE pl.orderId IN :orderIds")
    int detachOrders(@Param("orderIds") List<UUID> orderIds);
}
