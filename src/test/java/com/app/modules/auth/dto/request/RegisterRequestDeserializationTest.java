package com.app.modules.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class RegisterRequestDeserializationTest {

    // Mirror the production ObjectMapper configuration (FAIL_ON_UNKNOWN_PROPERTIES=true
    // is enabled globally via spring.jackson.deserialization.fail-on-unknown-properties)
    private final ObjectMapper mapper =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void rejectsUnrecognisedField() {
        String json =
                """
				{
				"username": "john_doe",
				"email": "john@example.com",
				"password": "S3cur3P@ssword",
				"displayName": "John Doe",
				"role": "admin"
				}
				""";

        assertThatThrownBy(() -> mapper.readValue(json, RegisterRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void acceptsValidFields() throws Exception {
        String json =
                """
				{
				"username": "john_doe",
				"email": "john@example.com",
				"password": "S3cur3P@ssword",
				"displayName": "John Doe"
				}
				""";

        RegisterRequest request = mapper.readValue(json, RegisterRequest.class);

        assertThat(request.username()).isEqualTo("john_doe");
        assertThat(request.email()).isEqualTo("john@example.com");
        assertThat(request.password()).isEqualTo("S3cur3P@ssword");
        assertThat(request.displayName()).isEqualTo("John Doe");
    }
}
