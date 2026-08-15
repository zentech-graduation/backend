package com.app.modules.media.storage;

import java.util.Optional;

/** Service API for reading stored-object metadata from object storage. */
public interface ObjectStorageMetadataService {

    /**
     * Reads the metadata of a single stored object without transferring its body.
     *
     * <p>An empty result means the object is absent, which the caller must treat as a client error.
     * A storage outage never returns empty; it raises so the caller can fail closed with a
     * retryable status instead of registering an asset that does not exist.
     *
     * @param storageKey object key to look up
     * @return the stored object's metadata, or empty when no object exists under that key
     * @throws com.app.common.exception.AppException when object storage is unreachable or errors
     */
    Optional<StoredObjectMetadata> findObjectMetadata(String storageKey);

    /** Value object describing an object that is present in storage. */
    record StoredObjectMetadata(long contentLength, String contentType) {}
}
