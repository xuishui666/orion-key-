package com.orionkey.controller;

import com.orionkey.annotation.LogOperation;
import com.orionkey.common.ApiResponse;
import com.orionkey.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public ApiResponse<?> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(value = "order_type", required = false) String orderType,
            @RequestParam(value = "payment_method", required = false) String paymentMethod,
            @RequestParam(value = "is_risk_flagged", required = false) Boolean isRiskFlagged,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.success(adminOrderService.listOrders(status, orderType, paymentMethod,
                isRiskFlagged, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getOrderDetail(@PathVariable UUID id) {
        return ApiResponse.success(adminOrderService.getOrderDetail(id));
    }

    @LogOperation(action = "order.mark_paid", targetType = "ORDER", targetId = "#id", detail = "'手动标记已付'")
    @PostMapping("/{id}/mark-paid")
    public ApiResponse<Void> markPaid(@PathVariable UUID id) {
        adminOrderService.markPaid(id);
        return ApiResponse.success();
    }

    @LogOperation(action = "order.delete", targetType = "ORDER", targetId = "#id", detail = "'删除订单'")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrder(@PathVariable UUID id) {
        adminOrderService.deleteOrder(id);
        return ApiResponse.success();
    }

    @LogOperation(action = "order.batch_delete", targetType = "ORDER", detail = "'批量删除订单'")
    @PostMapping("/batch-delete")
    public ApiResponse<?> batchDeleteOrders(@RequestBody Map<String, List<UUID>> request) {
        int count = adminOrderService.batchDeleteOrders(request.getOrDefault("ids", List.of()));
        return ApiResponse.success(Map.of("deleted_count", count));
    }

    @GetMapping("/revenue-stats")
    public ApiResponse<?> getRevenueStats(@RequestParam(value = "start_date", required = false) String startDate,
                                          @RequestParam(value = "end_date", required = false) String endDate) {
        return ApiResponse.success(adminOrderService.getRevenueStats(startDate, endDate));
    }
}
