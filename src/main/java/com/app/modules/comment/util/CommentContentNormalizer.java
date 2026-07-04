package com.app.modules.comment.util;

import java.text.Normalizer;

/** Normalizes raw comment input before moderation and persistence. */
public final class CommentContentNormalizer {

    private CommentContentNormalizer() {}

    /**
     * Trims, Unicode-NFC-normalizes, and strips control characters (except newline, carriage
     * return, and tab) from raw comment content.
     *
     * @param raw raw user input; null is treated as empty
     * @return normalized content, never null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        String nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        return nfc.replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");
    }
}
