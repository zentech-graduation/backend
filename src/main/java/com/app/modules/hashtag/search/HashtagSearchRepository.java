package com.app.modules.hashtag.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** Spring Data Elasticsearch repository for {@link HashtagDocument}. */
public interface HashtagSearchRepository extends ElasticsearchRepository<HashtagDocument, String> {}
