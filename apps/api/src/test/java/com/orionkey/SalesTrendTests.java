package com.orionkey;

import com.orionkey.entity.Order;
import com.orionkey.repository.OrderRepository;
import com.orionkey.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesTrendTests {
    @Test
    void includesZeroDaysAndUsesBoundedPaidOrderQuery() {
        var repo = mock(OrderRepository.class);
        var start = LocalDateTime.of(2026, 9, 1, 0, 0);
        var end = start.plusDays(3);
        Order order = new Order();
        order.setPaidAt(start.plusDays(1));
        order.setActualAmount(new BigDecimal("12.50"));
        when(repo.findPaidInRange(start, end)).thenReturn(List.of(order));
        var service = new DashboardServiceImpl(repo, null, null, null, null);
        var rows = service.getSalesTrend("daily", "2026-09-01", "2026-09-03");
        assertEquals(3, rows.size());
        assertEquals(BigDecimal.ZERO, ((Map<?, ?>) rows.get(0)).get("sales_amount"));
        assertEquals(new BigDecimal("12.50"), ((Map<?, ?>) rows.get(1)).get("sales_amount"));
        assertEquals(1, ((Map<?, ?>) rows.get(1)).get("order_count"));
        assertEquals("2026-09-03", ((Map<?, ?>) rows.get(2)).get("date"));
        verify(repo, never()).findAll();
    }
}
