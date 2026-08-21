package com.app.common.vocabulary.service;

import com.app.common.vocabulary.dto.response.VocabularyResponse;

/** Read access to the display vocabularies behind the API's closed enum sets. */
public interface VocabularyService {

    /**
     * Returns every display vocabulary in one response.
     *
     * <p>Includes disabled rows. A client that never sees a disabled reason keeps offering it and
     * the user meets a rejection for something the interface said was valid, which is the failure
     * this endpoint exists to prevent.
     *
     * @return report reasons, notification types and moderation action types, each with its display
     *     metadata and its enabled flag
     */
    VocabularyResponse getVocabularies();
}
