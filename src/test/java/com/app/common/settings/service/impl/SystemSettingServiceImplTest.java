package com.app.common.settings.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.settings.repository.SystemSettingRepository;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceImplTest {

    @Mock private SystemSettingRepository systemSettingRepository;

    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(systemSettingRepository);
    }

    @Test
    void getRequiredLong_validValue_returnsParsedLong() {
        when(systemSettingRepository.findValueByKey("limit")).thenReturn(Optional.of("42"));

        assertThat(service.getRequiredLong("limit")).isEqualTo(42L);
    }

    @Test
    void getRequiredLong_missingKey_throwsServiceUnavailable() {
        when(systemSettingRepository.findValueByKey("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequiredLong("missing"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void getRequiredLong_nonNumericValue_throwsServiceUnavailable() {
        when(systemSettingRepository.findValueByKey("bad")).thenReturn(Optional.of("not-a-number"));

        assertThatThrownBy(() -> service.getRequiredLong("bad"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.SERVICE_UNAVAILABLE);
    }
}
