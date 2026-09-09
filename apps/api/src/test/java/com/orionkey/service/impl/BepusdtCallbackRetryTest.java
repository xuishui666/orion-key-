package com.orionkey.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.entity.Order;
import com.orionkey.entity.PaymentChannel;
import com.orionkey.repository.*;
import com.orionkey.service.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BepusdtCallbackRetryTest {
    @Test
    void incompletePaymentContextAndPendingChainVerificationRemainRetryable() {
        var events = mock(WebhookEventRepository.class);
        var orders = mock(OrderRepository.class);
        var channels = mock(PaymentChannelRepository.class);
        var gateway = mock(BepusdtService.class);
        var chain = mock(TxidVerifyService.class);
        var service = new WebhookServiceImpl(events, orders, channels, mock(EpayService.class),
                gateway, new ObjectMapper(), mock(PaymentServiceImpl.class), chain);
        var order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentMethod("usdt_trc20");
        var channel = new PaymentChannel();
        channel.setConfigData("{\"api_token\":\"test-only\"}");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(channels.findByChannelCodeAndIsDeleted("usdt_trc20", 0)).thenReturn(Optional.of(channel));
        when(gateway.verifySign(anyString(), anyMap(), anyString())).thenReturn(true);
        Map<String, Object> params = Map.of("order_id", order.getId().toString(), "trade_id", "test-trade",
                "status", 2, "actual_amount", "1", "signature", "test-signature", "block_transaction_id", "test-tx");
        assertEquals("fail", service.processBepusdtCallback(params));
        order.setUsdtCryptoAmount("1");
        when(chain.verifyForWebhook(any(), any(), any(), any(), any()))
                .thenReturn(new TxidVerifyService.ChainVerifyResult(false, "Awaiting confirmation"));
        assertEquals("fail", service.processBepusdtCallback(params));
        channel.setConfigData("{}");
        assertEquals("fail", service.processBepusdtCallback(params));
        verify(events, never()).save(any());
        verify(orders, never()).save(any());
    }
}
