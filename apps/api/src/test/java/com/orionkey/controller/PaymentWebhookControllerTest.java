package com.orionkey.controller;

import com.orionkey.service.WebhookService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaymentWebhookControllerTest {
    @Test
    void failedUsdtCallbackUsesRetryableHttpStatus() {
        WebhookService service = mock(WebhookService.class);
        PaymentWebhookController controller = new PaymentWebhookController(service);
        Map<String, Object> params = Map.of("status", 2);
        when(service.processBepusdtCallback(params)).thenReturn("fail", "ok");
        var failed = controller.handleBepusdtCallback(params);
        assertEquals(503, failed.getStatusCode().value());
        assertEquals("fail", failed.getBody());
        assertEquals(200, controller.handleBepusdtCallback(params).getStatusCode().value());
    }
}
