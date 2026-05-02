package com.app.modules.mail.dto;

import java.util.Map;

import com.app.modules.mail.enums.MailTemplate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Carries all data needed to render and dispatch a single transactional email. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailRequest {

    private String toEmail;
    private String toName;
    private MailTemplate template;
    private Map<String, Object> variables;
}
