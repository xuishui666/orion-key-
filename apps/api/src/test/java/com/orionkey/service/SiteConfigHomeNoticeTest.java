package com.orionkey.service;

import com.orionkey.entity.SiteConfig;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.SiteConfigRepository;
import com.orionkey.service.impl.SiteConfigServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SiteConfigHomeNoticeTest {
    @Test
    void publishesEditableNoticeAndRejectsUnsafeStyles() {
        SiteConfigRepository repository = mock(SiteConfigRepository.class);
        SiteConfigServiceImpl service = new SiteConfigServiceImpl(repository);
        SiteConfig title = new SiteConfig();
        title.setConfigKey("home_notice_title");
        when(repository.findByConfigKey("home_notice_title")).thenReturn(Optional.of(title));

        service.updateConfigs(List.of(Map.of("config_key", "home_notice_title", "config_value", "Latest news")));
        assertEquals("Latest news", service.getPublicConfig().get("home_notice_title"));
        verify(repository).save(title);

        assertThrows(BusinessException.class, () -> service.updateConfigs(List.of(
                Map.of("config_key", "home_notice_body", "config_value", "x".repeat(3001)))));
        assertThrows(BusinessException.class, () -> service.updateConfigs(List.of(
                Map.of("config_key", "home_notice_font", "config_value", "url(evil)"))));
        assertThrows(BusinessException.class, () -> service.updateConfigs(List.of(
                Map.of("config_key", "home_notice_size", "config_value", "99"))));
        assertThrows(BusinessException.class, () -> service.updateConfigs(List.of(
                Map.of("config_key", "home_notice_color", "config_value", "red;position:absolute"))));
        verify(repository, times(1)).save(any());
    }
}
