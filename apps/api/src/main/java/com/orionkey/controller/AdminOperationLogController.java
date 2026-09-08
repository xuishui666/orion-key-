package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import com.orionkey.annotation.LogOperation;

@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    @LogOperation(action = "log.delete", targetType = "OPERATION_LOG", detail = "'删除操作日志: ' + #request.get('ids')")
    @PostMapping("/batch-delete")
    public ApiResponse<?> deleteLogs(@RequestBody Map<String, List<UUID>> request) {
        return ApiResponse.success(Map.of("deleted_count", operationLogService.deleteLogs(request.get("ids"))));
    }

    @GetMapping
    public ApiResponse<?> listLogs(
            @RequestParam(value = "user_id", required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(value = "target_type", required = false) String targetType,
            @RequestParam(value = "start_date", required = false) String startDate,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.success(operationLogService.listLogs(userId, action, targetType, startDate, endDate, page, pageSize));
    }
}

