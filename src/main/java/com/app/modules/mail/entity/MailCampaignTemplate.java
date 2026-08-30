package com.app.modules.mail.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A read-only campaign sample.
 *
 * <p>Named MailCampaignTemplate rather than MailTemplate because {@code mail.enums.MailTemplate}
 * already names the auth mail template set, and two types called MailTemplate in one module would
 * be read wrong at every call site.
 *
 * <p>A template is a sample, not a document. An administrator opens one, its Markdown loads into
 * the editor, they change it, and the change is saved to the campaign. It is never written back, so
 * a second administrator opening the same template gets the original sample.
 *
 * <p>Every column is {@code updatable = false}: there is no administrator-facing create, update or
 * delete in this release, and the seeded rows are the whole catalogue.
 */
@Entity
@Table(name = "mail_templates")
@Getter
@Setter
public class MailCampaignTemplate {

    @Id
    @Column(name = "template_key", updatable = false, nullable = false, length = 100)
    private String templateKey;

    @Column(name = "display_name", nullable = false, length = 150, updatable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    private String description;

    /** Markdown, not HTML. Rendered by the shared pipeline, never by the browser. */
    @Column(name = "body", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private short sortOrder;

    @Column(name = "is_enabled", nullable = false, updatable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
