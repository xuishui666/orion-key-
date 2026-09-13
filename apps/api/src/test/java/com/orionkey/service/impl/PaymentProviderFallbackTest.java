package com.orionkey.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.repository.OrderRepository;
import com.orionkey.repository.UnmatchedTransactionRepository;
import com.orionkey.service.EpayService.ChannelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentProviderFallbackTest {

    @Test
    void bscVerificationFallsBackToSecondRpc() {
        RestTemplate rest = mock(RestTemplate.class);
        String wallet = "0x37ea4232e967d1bce2e068c107332de4b863a7a9";
        String topicAddress = "0x000000000000000000000000" + wallet.substring(2);
        String receipt = "{\"result\":{\"status\":\"0x1\",\"blockNumber\":\"0x1\",\"logs\":[{"
                + "\"address\":\"0x55d398326f99059ff775485246999027b3197955\","
                + "\"topics\":[\"0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef\","
                + "\"0x0000000000000000000000000000000000000000000000000000000000000001\",\"" + topicAddress + "\"],"
                + "\"data\":\"0x" + new java.math.BigInteger("11620000000000000000").toString(16) + "\"}]}}";
        String block = "{\"result\":{\"timestamp\":\"0x65920080\"}}";
        when(rest.postForObject(eq("https://bsc-rpc.publicnode.com"), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("offline"));
        when(rest.postForObject(eq("https://bsc-dataseed.bnbchain.org/"), any(), eq(String.class)))
                .thenReturn(receipt, block);

        var service = new TxidVerifyServiceImpl(mock(OrderRepository.class),
                mock(UnmatchedTransactionRepository.class), rest, new ObjectMapper());
        var result = service.verifyForWebhook("usdt_bep20", "0xtx", wallet, "11.62",
                LocalDateTime.of(2024, 1, 1, 0, 0));

        assertNotNull(result);
        assertTrue(result.verified(), result.reason());
    }

    @Test
    void mobileEpayUsesHttpQrPageWhenPayUrlIsMissing() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok(
                "{\"code\":1,\"trade_no\":\"trade\",\"qrcode\":\"https://pay.example/checkout\"}"));
        var service = new EpayServiceImpl(rest, new ObjectMapper());

        var result = service.createPayment(new ChannelConfig("pid", "key", "https://api.example/pay",
                        "https://shop.example/notify", "https://shop.example/order/query"),
                "order", "alipay", "product", BigDecimal.ONE, "127.0.0.1", "ios_safari");

        assertEquals("https://pay.example/checkout", result.payUrl());
        assertEquals("https://pay.example/checkout", result.qrcodeUrl());
    }
}

