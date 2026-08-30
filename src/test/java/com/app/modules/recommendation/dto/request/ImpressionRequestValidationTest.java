package com.app.modules.recommendation.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.modules.recommendation.enums.ImpressionSurface;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class ImpressionRequestValidationTest {

    private ValidatorFactory factory;
    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        // Mirrors spring.jackson.deserialization.fail-on-unknown-properties: true from
        // application.yaml; a default mapper ignores unknown properties instead.
        objectMapper =
                JsonMapper.builder()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void validate_batchAtMaximumSize_hasNoViolations() {
        ImpressionBatchRequest request =
                new ImpressionBatchRequest(impressions(ImpressionBatchRequest.MAX_BATCH_SIZE));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void validate_batchOverMaximumSize_isRejected() {
        ImpressionBatchRequest request =
                new ImpressionBatchRequest(impressions(ImpressionBatchRequest.MAX_BATCH_SIZE + 1));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void validate_emptyBatch_isRejected() {
        assertThat(validator.validate(new ImpressionBatchRequest(List.of()))).isNotEmpty();
    }

    @Test
    void validate_negativeDwell_isRejected() {
        ImpressionBatchRequest request =
                new ImpressionBatchRequest(
                        List.of(
                                new ImpressionRequest(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        -1.0,
                                        ImpressionSurface.FEED)));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void validate_missingImpressionId_isRejected() {
        ImpressionBatchRequest request =
                new ImpressionBatchRequest(
                        List.of(
                                new ImpressionRequest(
                                        null, UUID.randomUUID(), 1.0, ImpressionSurface.FEED)));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void deserialize_unknownProperty_throws() {
        String json =
                """
				{"impressions":[{"impressionId":"%s","postId":"%s","dwellSeconds":1.5,\
				"surface":"feed","bogus":1}]}"""
                        .formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> objectMapper.readValue(json, ImpressionBatchRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void deserialize_surfaceWireForm_isLowercase() {
        String json =
                """
				{"impressions":[{"impressionId":"%s","postId":"%s","dwellSeconds":1.5,\
				"surface":"explore"}]}"""
                        .formatted(UUID.randomUUID(), UUID.randomUUID());

        ImpressionBatchRequest request = objectMapper.readValue(json, ImpressionBatchRequest.class);

        assertThat(request.impressions().get(0).surface()).isEqualTo(ImpressionSurface.EXPLORE);
    }

    private List<ImpressionRequest> impressions(int count) {
        List<ImpressionRequest> impressions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            impressions.add(
                    new ImpressionRequest(
                            UUID.randomUUID(), UUID.randomUUID(), 1.0, ImpressionSurface.FEED));
        }
        return impressions;
    }
}
