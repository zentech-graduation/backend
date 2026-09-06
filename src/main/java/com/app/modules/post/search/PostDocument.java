package com.app.modules.post.search;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Elasticsearch projection of {@code posts} for caption full-text search.
 *
 * <p>The {@code caption} field has a text main type and an {@code ngram} sub-field using {@code
 * ngram_analyzer} for fuzzy matching. {@code status} stores lowercase Postgres enum values so the
 * search filter can target {@code published} directly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// createIndex = false: the repository must not connect to Elasticsearch at startup (that would
// fail every application context that has no Elasticsearch). The index is created with its ngram
// mapping by PostIndexSeedRunner when Elasticsearch is reachable.
@Document(indexName = "posts", createIndex = false)
@Setting(settingPath = "/elasticsearch/settings/posts.json")
public class PostDocument {

    @Id private String id;

    @Field(type = FieldType.Keyword, name = "user_id")
    private String userId;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = {
                @InnerField(
                        suffix = "ngram",
                        type = FieldType.Text,
                        analyzer = "ngram_analyzer",
                        searchAnalyzer = "standard")
            })
    private String caption;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword, name = "hashtag_ids")
    private List<String> hashtagIds;

    @Field(type = FieldType.Date, name = "created_at", format = DateFormat.date_time)
    private OffsetDateTime createdAt;
}
