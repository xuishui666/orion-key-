package com.orionkey;

import com.orionkey.entity.OperationLog;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.OperationLogRepository;
import com.orionkey.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OperationLogTests {
    @Autowired OperationLogService service;
    @Autowired OperationLogRepository repository;

    @Test
    void deletesOnlySelectedLogsAndValidatesInput() {
        OperationLog selected = log();
        OperationLog retained = log();
        try {
            assertThrows(BusinessException.class, () -> service.deleteLogs(null));
            assertThrows(BusinessException.class, () -> service.deleteLogs(List.of()));
            assertThrows(BusinessException.class, () -> service.deleteLogs(Arrays.asList((UUID) null)));
            assertThrows(BusinessException.class, () -> service.deleteLogs(Collections.nCopies(501, selected.getId())));
            assertTrue(repository.existsById(selected.getId()));
            assertEquals(1, service.deleteLogs(List.of(selected.getId(), selected.getId(), UUID.randomUUID())));
            assertFalse(repository.existsById(selected.getId()));
            assertTrue(repository.existsById(retained.getId()));
            assertEquals(0, service.deleteLogs(List.of(selected.getId())));
        } finally {
            repository.deleteAllById(List.of(selected.getId(), retained.getId()));
        }
    }

    private OperationLog log() {
        OperationLog log = new OperationLog();
        log.setUserId(UUID.randomUUID());
        log.setAction("test.action");
        return repository.save(log);
    }
}

