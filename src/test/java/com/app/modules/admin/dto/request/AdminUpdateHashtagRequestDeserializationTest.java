package com.app.modules.admin.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.modules.hashtag.enums.HashtagStatus;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class AdminUpdateHashtagRequestDeserializationTest {

    // Mirror the production ObjectMapper configuration (FAIL_ON_UNKNOWN_PROPERTIES=true
    // is enabled globally via spring.jackson.deserialization.fail-on-unknown-properties)
    private final ObjectMapper mapper =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void rejectsABodyCarryingAName() {
        String json =
                """
				{
				"status": "banned",
				"note": "spam wave",
				"name": "renamed"
				}
				""";

        assertThatThrownBy(() -> mapper.readValue(json, AdminUpdateHashtagRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void acceptsStatusAndNote() {
        String json =
                """
				{
				"status": "banned",
				"note": "spam wave"
				}
				""";

        AdminUpdateHashtagRequest request = mapper.readValue(json, AdminUpdateHashtagRequest.class);

        assertThat(request.status()).isEqualTo(HashtagStatus.BANNED);
        assertThat(request.note()).isEqualTo("spam wave");
    }

    @Test
    void rejectsAnUnknownStatusValue() {
        String json =
                """
				{
				"status": "shadowbanned",
				"note": "spam wave"
				}
				""";

        assertThatThrownBy(() -> mapper.readValue(json, AdminUpdateHashtagRequest.class))
                .isInstanceOf(RuntimeException.class);
    }
}
