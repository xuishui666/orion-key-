package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/support")
@RequiredArgsConstructor
public class AdminSupportController {
    private final SupportService supportService;

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.success(supportService.adminConversations());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable UUID id) {
        return ApiResponse.success(supportService.adminConversation(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<?> send(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(supportService.adminMessage(id, body.get("text")));
    }

    @GetMapping("/{id}/images/{messageId}")
    public ResponseEntity<byte[]> imageData(@PathVariable UUID id, @PathVariable UUID messageId) {
        var image = supportService.adminImageData(id, messageId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
    }
}
