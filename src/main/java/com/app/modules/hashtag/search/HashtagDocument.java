package com.app.modules.hashtag.search;

import java.time.OffsetDateTime;

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
 * Elasticsearch projection of {@code hashtags} for fuzzy name search.
 *
 * <p>The {@code name} field has a keyword main type for exact lookups and an {@code ngram}
 * sub-field using {@code ngram_analyzer} for prefix/fuzzy matching.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// createIndex = false: the repository must not connect to Elasticsearch at startup (that would
// fail every application context that has no Elasticsearch). The index is created with its ngram
// mapping by HashtagIndexSeedRunner when Elasticsearch is reachable.
@Document(indexName = "hashtags", createIndex = false)
@Setting(settingPath = "/elasticsearch/settings/hashtags.json")
public class HashtagDocument {

    @Id private String id;

    @MultiField(
            mainField = @Field(type = FieldType.Keyword),
            otherFields = {
                @InnerField(
                        suffix = "ngram",
                        type = FieldType.Text,
                        analyzer = "ngram_analyzer",
                        searchAnalyzer = "standard")
            })
    private String name;

    @Field(type = FieldType.Integer, name = "post_count")
    private int postCount;

    @Field(type = FieldType.Date, name = "created_at", format = DateFormat.date_time)
    private OffsetDateTime createdAt;
}
