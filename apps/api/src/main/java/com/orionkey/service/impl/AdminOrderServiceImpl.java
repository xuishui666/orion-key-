package com.orionkey.service.impl;

import com.orionkey.common.PageResult;
import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.constant.OrderType;
import com.orionkey.entity.Order;
import com.orionkey.entity.OrderItem;
import com.orionkey.entity.User;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.OrderItemRepository;
import com.orionkey.repository.OrderRepository;
import com.orionkey.repository.UserRepository;
import com.orionkey.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    @Override
    public PageResult<?> listOrders(String status, String orderType, String paymentMethod,
                                     Boolean isRiskFlagged, String keyword, int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize);
        OrderStatus os = null;
        OrderType ot = null;
        try {
            if (status != null) os = OrderStatus.valueOf(status);
            if (orderType != null) ot = OrderType.valueOf(orderType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的筛选参数: " + e.getMessage());
        }
        Page<Order> orderPage;
        if (keyword != null && !keyword.isBlank()) {
            orderPage = orderRepository.findAdminOrdersByKeyword(os, ot, paymentMethod, isRiskFlagged, "%" + keyword + "%", pageable);
        } else {
            orderPage = orderRepository.findAdminOrders(os, ot, paymentMethod, isRiskFlagged, pageable);
        }

        var list = orderPage.getContent().stream().map(this::toAdminOrder).toList();
        return PageResult.of(orderPage, list);
    }

    @Override
    public Object getOrderDetail(UUID id) {
        Order order = orderRepository.findById(id)
                .filter(o -> o.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));
        return toAdminOrder(order);
    }

    @Override
    @Transactional
    public void markPaid(UUID id) {
        Order order = orderRepository.findById(id)
                .filter(o -> o.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅 PENDING 或 EXPIRED 状态订单可标记为已支付");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void deleteOrder(UUID id) {
        if (orderRepository.softDeleteByIds(List.of(id)) == 0) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
    }

    @Override
    @Transactional
    public int batchDeleteOrders(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        if (ids.size() > 500) throw new BusinessException(ErrorCode.BAD_REQUEST, "每次最多删除 500 个订单");
        return orderRepository.softDeleteByIds(ids);
    }

    @Override
    public Object getRevenueStats(String startDate, String endDate) {
        LocalDateTime start = parseDate(startDate);
        LocalDateTime end = parseDate(endDate);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (end != null) end = end.plusDays(1);
        if (start == null) start = LocalDate.of(1970, 1, 1).atStartOfDay();
        if (end == null) end = LocalDate.of(9999, 1, 1).atStartOfDay();

        Object[] totalRow = orderRepository.summarizeRevenue(start, end).getFirst();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_amount", totalRow[0] != null ? totalRow[0] : BigDecimal.ZERO);
        result.put("order_count", totalRow[1] != null ? totalRow[1] : 0L);
        result.put("by_payment_method", rows(orderRepository.summarizeRevenueByPaymentMethod(start, end), "payment_method"));
        result.put("by_product", rows(orderRepository.summarizeRevenueByProduct(start, end), "product_title"));
        return result;
    }

    private LocalDateTime parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date).atStartOfDay();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日期格式应为 YYYY-MM-DD");
        }
    }

    private List<Map<String, Object>> rows(List<Object[]> rows, String nameKey) {
        return rows.stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(nameKey, row[0]);
            map.put("amount", row[1] != null ? row[1] : BigDecimal.ZERO);
            map.put("count", row[2] != null ? row[2] : 0L);
            return map;
        }).toList();
    }

    private Map<String, Object> toAdminOrder(Order o) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", o.getId());
        map.put("total_amount", o.getTotalAmount());
        map.put("actual_amount", o.getActualAmount());
        map.put("status", o.getStatus().name());
        map.put("order_type", o.getOrderType().name());
        map.put("payment_method", o.getPaymentMethod());
        map.put("created_at", o.getCreatedAt());
        map.put("email", o.getEmail());
        map.put("points_deducted", o.getPointsDeducted());
        map.put("points_discount", o.getPointsDiscount());
        map.put("expires_at", o.getExpiresAt());
        map.put("paid_at", o.getPaidAt());
        map.put("delivered_at", o.getDeliveredAt());
        map.put("user_id", o.getUserId());
        map.put("is_risk_flagged", o.isRiskFlagged());

        if (o.getUserId() != null) {
            userRepository.findById(o.getUserId()).ifPresent(u -> map.put("username", u.getUsername()));
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
        map.put("items", items.stream().map(i -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", i.getId());
            im.put("product_id", i.getProductId());
            im.put("product_title", i.getProductTitle());
            im.put("spec_name", i.getSpecName());
            im.put("quantity", i.getQuantity());
            im.put("unit_price", i.getUnitPrice());
            im.put("subtotal", i.getSubtotal());
            return im;
        }).toList());
        return map;
    }
}
