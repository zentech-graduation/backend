package com.app.modules.media.service.impl;

import java.sql.SQLException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.mapper.MediaAssetMapper;
import com.app.modules.media.repository.MediaAssetRepository;
import com.app.modules.media.service.MediaEventService;

/**
 * Transactional write half of media upload confirmation.
 *
 * <p>Package-private, and absent from the {@code MediaService} contract, so nothing outside this
 * package can reach the insert. The only route to it is {@code MediaServiceImpl.completeUpload},
 * which verifies the uploaded object against storage first. Keeping this separate from that caller
 * is what allows the storage probe to run with no transaction open and therefore no pooled database
 * connection held.
 */
@Service
class MediaAssetRegistrar {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaEventService mediaEventService;
    private final MediaAssetMapper mediaAssetMapper;

    MediaAssetRegistrar(
            MediaAssetRepository mediaAssetRepository,
            MediaEventService mediaEventService,
            MediaAssetMapper mediaAssetMapper) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaEventService = mediaEventService;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    /**
     * Persists a verified media asset and records its uploaded event in the same transaction.
     *
     * <p>Public only because Spring's proxy-based transaction support ignores
     * {@code @Transactional} on non-public methods; the enclosing class is package-private, so this
     * is not reachable from outside the package.
     *
     * @param mediaAsset asset whose stored object has already been verified against storage
     * @return the persisted asset as an API response
     */
    @Transactional
    public MediaAssetResponse register(MediaAsset mediaAsset) {
        if (mediaAssetRepository.existsByStorageKey(mediaAsset.getStorageKey())) {
            throw new AppException(ApiErrorCode.MEDIA_STORAGE_KEY_ALREADY_EXISTS);
        }
        try {
            MediaAsset saved = mediaAssetRepository.insert(mediaAsset);
            mediaEventService.publishMediaUploaded(saved);
            return mediaAssetMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw new AppException(ApiErrorCode.MEDIA_STORAGE_KEY_ALREADY_EXISTS);
            }
            throw ex;
        }
    }

    private static boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
