package com.app.modules.post.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostMedia;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.repository.PostMediaAssetRepository;

@ExtendWith(MockitoExtension.class)
class PostResponseAssemblerTest {

    @Mock private PostMediaAssetRepository postMediaAssetRepository;
    @Mock private PostMapper postMapper;

    private PostResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PostResponseAssembler(postMediaAssetRepository, postMapper);
    }

    @Test
    void assemble_noMedia_skipsAssetLookup() {
        Post post = Post.builder().id(UUID.randomUUID()).media(new ArrayList<>()).build();

        assembler.assemble(post);

        verify(postMediaAssetRepository, never()).findAllById(any());
        verify(postMapper).toResponse(eq(post), eq(List.of()));
    }

    @Test
    void assemble_withMedia_batchesAssetLookupByDistinctIds() {
        UUID assetId = UUID.randomUUID();
        PostMedia media = PostMedia.builder().id(UUID.randomUUID()).mediaAssetId(assetId).build();
        Post post = Post.builder().id(UUID.randomUUID()).media(List.of(media)).build();
        when(postMediaAssetRepository.findAllById(Set.of(assetId))).thenReturn(List.of());

        assembler.assemble(List.of(post));

        verify(postMediaAssetRepository).findAllById(Set.of(assetId));
        // Asset map is empty, so the media row hydrates against a null asset.
        verify(postMapper).toMediaResponse(eq(media), isNull());
        verify(postMapper).toResponse(eq(post), any());
    }
}
