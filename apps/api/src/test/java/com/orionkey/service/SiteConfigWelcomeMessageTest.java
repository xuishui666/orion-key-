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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteConfigWelcomeMessageTest {
    @Test
    void savesAndPublishesWelcomeMessageAndAllowsDisabling() {
        SiteConfigRepository repository = mock(SiteConfigRepository.class);
        SiteConfigServiceImpl service = new SiteConfigServiceImpl(repository);
        SiteConfig config = new SiteConfig();
        config.setConfigKey("support_welcome_message");
        when(repository.findByConfigKey("support_welcome_message")).thenReturn(Optional.of(config));

        service.updateConfigs(List.of(Map.of("config_key", "support_welcome_message", "config_value", "  欢迎咨询  ")));
        assertEquals("欢迎咨询", service.getPublicConfig().get("support_welcome_message"));

        service.updateConfigs(List.of(Map.of("config_key", "support_welcome_message", "config_value", "")));
        assertEquals("", service.getPublicConfig().get("support_welcome_message"));
        verify(repository, times(2)).save(config);
    }

    @Test
    void rejectsOverlongWelcomeMessage() {
        SiteConfigRepository repository = mock(SiteConfigRepository.class);
        SiteConfigServiceImpl service = new SiteConfigServiceImpl(repository);

        assertThrows(BusinessException.class, () -> service.updateConfigs(List.of(
                Map.of("config_key", "support_welcome_message", "config_value", "x".repeat(501)))));
        verify(repository, never()).save(any());
    }
}
