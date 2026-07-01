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
import com.app.modules.users.entity.User;
import com.app.modules.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PostResponseAssemblerTest {

    @Mock private PostMediaAssetRepository postMediaAssetRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostMapper postMapper;

    private PostResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PostResponseAssembler(postMediaAssetRepository, userRepository, postMapper);
    }

    @Test
    void assemble_noMedia_skipsAssetLookup() {
        UUID authorId = UUID.randomUUID();
        Post post =
                Post.builder()
                        .id(UUID.randomUUID())
                        .userId(authorId)
                        .media(new ArrayList<>())
                        .build();
        when(userRepository.findAllById(Set.of(authorId))).thenReturn(List.of());

        assembler.assemble(post);

        verify(postMediaAssetRepository, never()).findAllById(any());
        verify(postMapper).toResponse(eq(post), eq(List.of()), isNull());
    }

    @Test
    void assemble_withMedia_batchesAssetLookupByDistinctIds() {
        UUID assetId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        PostMedia media = PostMedia.builder().id(UUID.randomUUID()).mediaAssetId(assetId).build();
        Post post =
                Post.builder().id(UUID.randomUUID()).userId(authorId).media(List.of(media)).build();
        when(postMediaAssetRepository.findAllById(Set.of(assetId))).thenReturn(List.of());
        when(userRepository.findAllById(Set.of(authorId))).thenReturn(List.of());

        assembler.assemble(List.of(post));

        verify(postMediaAssetRepository).findAllById(Set.of(assetId));
        // Asset map is empty, so the media row hydrates against a null asset.
        verify(postMapper).toMediaResponse(eq(media), isNull());
        // Author lookup miss hydrates against a null author.
        verify(postMapper).toResponse(eq(post), any(), isNull());
    }

    @Test
    void assemble_resolvesAuthor_batchesUserLookupByDistinctIds() {
        UUID authorId = UUID.randomUUID();
        User author = User.builder().id(authorId).username("jane_doe").build();
        Post post =
                Post.builder()
                        .id(UUID.randomUUID())
                        .userId(authorId)
                        .media(new ArrayList<>())
                        .build();
        when(userRepository.findAllById(Set.of(authorId))).thenReturn(List.of(author));

        assembler.assemble(post);

        verify(postMapper).toResponse(eq(post), eq(List.of()), eq(author));
    }
}
