package com.app.common.vocabulary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.vocabulary.dto.response.VocabularyResponse;
import com.app.common.vocabulary.repository.VocabularyRepository;
import com.app.common.vocabulary.service.VocabularyService;

@Service
public class VocabularyServiceImpl implements VocabularyService {

    private final VocabularyRepository vocabularyRepository;

    public VocabularyServiceImpl(VocabularyRepository vocabularyRepository) {
        this.vocabularyRepository = vocabularyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public VocabularyResponse getVocabularies() {
        // One transaction over four reads rather than four, so a client cannot observe the tables
        // mid-change: an administrator disabling a reason and its notification type in one
        // migration would otherwise be visible half-applied.
        return new VocabularyResponse(
                vocabularyRepository.findReportReasons(),
                vocabularyRepository.findNotificationTypes(),
                vocabularyRepository.findModerationActions(),
                vocabularyRepository.findSupportCategories());
    }
}
