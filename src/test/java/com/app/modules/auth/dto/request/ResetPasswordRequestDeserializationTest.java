package com.app.modules.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class ResetPasswordRequestDeserializationTest {

    // Mirror the production ObjectMapper configuration (FAIL_ON_UNKNOWN_PROPERTIES=true
    // is enabled globally via spring.jackson.deserialization.fail-on-unknown-properties)
    private final ObjectMapper mapper =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void rejectsUnrecognisedField() {
        String json =
                """
				{
				"token": "a1b2c3d4e5f6",
				"newPassword": "N3wS3cur3P@ss",
				"role": "admin"
				}
				""";

        assertThatThrownBy(() -> mapper.readValue(json, ResetPasswordRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void acceptsValidFields() throws Exception {
        String json =
                """
				{
				"token": "a1b2c3d4e5f6",
				"newPassword": "N3wS3cur3P@ss"
				}
				""";

        ResetPasswordRequest request = mapper.readValue(json, ResetPasswordRequest.class);

        assertThat(request.token()).isEqualTo("a1b2c3d4e5f6");
        assertThat(request.newPassword()).isEqualTo("N3wS3cur3P@ss");
    }
}
