package com.app.modules.auth.validation;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces the account password policy behind {@link ValidPassword}.
 *
 * <p>Reports at most one violation per value, naming the rule that failed, because {@code
 * GlobalExceptionHandler} collects field errors into a map keyed by field name and a second
 * violation on the same field would silently replace the first.
 */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 64;

    // BCrypt truncates its input at 72 bytes and Spring Security's encoder throws rather than
    // truncate, so a value above this limit reaches the encoder as an unhandled failure
    static final int MAX_UTF8_BYTES = 72;

    // Zero-width and joining characters that render as nothing; Character.isWhitespace and
    // Character.isSpaceChar both report false for every one of them
    private static final Set<Integer> INVISIBLE_CODE_POINTS =
            Set.of(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String failure = firstFailure(value);
        if (failure == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(failure).addConstraintViolation();
        return false;
    }

    private static String firstFailure(String value) {
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters long";
        }
        if (length > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters long";
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return "Password must be at most "
                    + MAX_UTF8_BYTES
                    + " bytes long when encoded as UTF-8";
        }
        if (value.codePoints().anyMatch(PasswordPolicyValidator::isBlankOrInvisible)) {
            return "Password must not contain whitespace or invisible characters";
        }
        if (value.codePoints().noneMatch(Character::isUpperCase)) {
            return "Password must contain at least one uppercase letter";
        }
        if (value.codePoints().noneMatch(PasswordPolicyValidator::isDigitOrSpecial)) {
            return "Password must contain at least one digit or one special character";
        }
        return null;
    }

    private static boolean isBlankOrInvisible(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || INVISIBLE_CODE_POINTS.contains(codePoint);
    }

    private static boolean isDigitOrSpecial(int codePoint) {
        // Whitespace and invisible characters are already rejected by the time this runs, so
        // anything that is not a letter is either a digit or a special character
        return !Character.isLetter(codePoint);
    }
}
