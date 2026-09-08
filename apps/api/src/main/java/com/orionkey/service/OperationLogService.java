package com.orionkey.service;

import com.orionkey.common.PageResult;

import java.util.UUID;
import java.util.List;

public interface OperationLogService {
    int deleteLogs(List<UUID> ids);

    PageResult<?> listLogs(UUID userId, String action, String targetType,
                            String startDate, String endDate, int page, int pageSize);

    void log(UUID userId, String username, String action, String targetType, String targetId, String detail, String ip);
}

