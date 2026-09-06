package com.app.modules.media.service;

import com.app.modules.media.entity.MediaAsset;

/** Service API for recording media domain events through the transactional outbox. */
public interface MediaEventService {

    /**
     * Records a media-uploaded event for an already persisted media asset.
     *
     * @param mediaAsset persisted media asset that becomes the event aggregate
     */
    void publishMediaUploaded(MediaAsset mediaAsset);
}
