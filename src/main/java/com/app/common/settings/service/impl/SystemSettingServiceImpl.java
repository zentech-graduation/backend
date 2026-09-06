package com.app.common.settings.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.settings.repository.SystemSettingRepository;
import com.app.common.settings.service.SystemSettingService;

@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    public SystemSettingServiceImpl(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long getRequiredLong(String key) {
        String rawValue =
                systemSettingRepository
                        .findValueByKey(key)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ApiErrorCode.SERVICE_UNAVAILABLE,
                                                "Required system setting is missing"));
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            throw new AppException(
                    ApiErrorCode.SERVICE_UNAVAILABLE, "Required system setting is invalid");
        }
    }
}
