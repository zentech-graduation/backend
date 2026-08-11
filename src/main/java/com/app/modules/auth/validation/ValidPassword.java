package com.app.modules.auth.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Marks a password field as subject to the account password policy.
 *
 * <p>Carries the complete length, byte-length, character-class, and whitespace policy so that no
 * field applying it needs its own {@code @Size} or {@code @Pattern}. Apply it only to passwords a
 * caller is choosing; never to a password a caller is presenting for authentication, since bounding
 * those would reject accounts created under an earlier policy and would disclose the policy to an
 * attacker probing the login endpoint.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface ValidPassword {

    String message() default "Password does not meet the password policy";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
