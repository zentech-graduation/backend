package com.app.common.enums;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** Machine-readable success codes for all API success responses. */
@Getter
public enum ApiSuccessCode {
    // spotless:off

	// Common
	OK("OK", "Operation completed successfully", HttpStatus.OK),
	CREATED("CREATED", "Resource created successfully", HttpStatus.CREATED),
	ACCEPTED("ACCEPTED", "Request accepted for processing", HttpStatus.ACCEPTED),
	NO_CONTENT("NO_CONTENT", "No content", HttpStatus.NO_CONTENT);

	// spotless:on

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ApiSuccessCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
