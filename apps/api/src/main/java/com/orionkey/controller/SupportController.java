package com.orionkey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.orionkey.common.ApiResponse;
import com.orionkey.constant.ErrorCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.service.SupportService;
import com.orionkey.service.SupportTelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportService supportService;
    private final SupportTelegramService telegram;

    @GetMapping("/config")
    public ApiResponse<?> config() {
        return ApiResponse.success(Map.of("enabled", telegram.enabled()));
    }

    @PostMapping("/conversations")
    public ApiResponse<?> create(@RequestBody Map<String, String> body) {
        requireEnabled();
        return ApiResponse.success(supportService.create(body.get("email"),
                body.get("order_reference"), body.get("text")));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<?> get(@PathVariable UUID id,
                              @RequestHeader(value = "X-Support-Token", required = false) String token) {
        requireEnabled();
        return ApiResponse.success(supportService.customerConversation(id, token));
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<?> send(@PathVariable UUID id,
                               @RequestHeader(value = "X-Support-Token", required = false) String token,
                               @RequestBody Map<String, String> body) {
        requireEnabled();
        return ApiResponse.success(supportService.customerMessage(id, token, body.get("text")));
    }

    @PostMapping(value = "/conversations/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> image(@PathVariable UUID id,
                                @RequestHeader(value = "X-Support-Token", required = false) String token,
                                @RequestParam("file") MultipartFile file) {
        requireEnabled();
        return ApiResponse.success(supportService.customerImage(id, token, file));
    }

    @GetMapping("/conversations/{id}/images/{messageId}")
    public ResponseEntity<byte[]> imageData(@PathVariable UUID id, @PathVariable UUID messageId,
                                            @RequestHeader(value = "X-Support-Token", required = false) String token) {
        requireEnabled();
        var image = supportService.customerImageData(id, messageId, token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
    }

    @PostMapping("/telegram/webhook")
    public ApiResponse<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody JsonNode update) {
        if (!telegram.validSecret(secret)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无效的回调", HttpStatus.FORBIDDEN);
        }
        telegram.acceptUpdate(update);
        return ApiResponse.success();
    }

    private void requireEnabled() {
        if (!telegram.enabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "客服暂未开放", HttpStatus.NOT_FOUND);
        }
    }
}
