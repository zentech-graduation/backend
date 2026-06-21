package com.app.modules.post.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** Spring Data Elasticsearch repository for {@link PostDocument}. */
public interface PostSearchRepository extends ElasticsearchRepository<PostDocument, String> {}
