package com.likelion.basecode.post.application;

import com.likelion.basecode.common.client.TagRecommendationClient;
import com.likelion.basecode.common.error.ErrorCode;
import com.likelion.basecode.common.exception.BusinessException;
import com.likelion.basecode.member.domain.Member;
import com.likelion.basecode.member.domain.repository.MemberRepository;
import com.likelion.basecode.post.api.dto.response.PostInfoResponseDto;
import com.likelion.basecode.post.api.dto.response.PostListResponseDto;
import com.likelion.basecode.post.api.dto.request.PostSaveRequestDto;
import com.likelion.basecode.post.api.dto.request.PostUpdateRequestDto;
import com.likelion.basecode.post.domain.Post;
import com.likelion.basecode.post.domain.repository.PostRepository;
import com.likelion.basecode.posttag.domain.PostTag;
import com.likelion.basecode.posttag.domain.repository.PostTagRepository;
import com.likelion.basecode.tag.domain.Tag;
import com.likelion.basecode.tag.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final TagRecommendationClient tagClient;

    // 게시물 저장
    @Transactional
    public PostInfoResponseDto postSave(PostSaveRequestDto postSaveRequestDto) {
        Member member = memberRepository.findById(postSaveRequestDto.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND_EXCEPTION,
                        ErrorCode.MEMBER_NOT_FOUND_EXCEPTION.getMessage() + postSaveRequestDto.memberId()));


        Post post = Post.builder()
                .title(postSaveRequestDto.title())
                .contents(postSaveRequestDto.contents())
                .member(member)
                .build();

        postRepository.save(post);

        List<String> tagNames = tagClient.getRecommendedTags(post.getContents());
        registerTagsToPost(post, tagNames);

        Post postWithTags = postRepository.findByIdWithTags(post.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND_EXCEPTION,
                        ErrorCode.POST_NOT_FOUND_EXCEPTION.getMessage() + post.getPostId()));

        return PostInfoResponseDto.from(post);
    }

    // 특정 작성자가 작성한 게시글 목록을 조회
    public PostListResponseDto postFindMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND_EXCEPTION,
                        ErrorCode.MEMBER_NOT_FOUND_EXCEPTION.getMessage() + memberId));

        List<Post> posts = postRepository.findByMember(member);
        List<PostInfoResponseDto> postInfoResponseDtos = posts.stream()
                .map(PostInfoResponseDto::from)
                .toList();

        return PostListResponseDto.from(postInfoResponseDtos);
    }

    // 게시물 수정
    @Transactional
    public PostInfoResponseDto postUpdate(Long postId,
                           PostUpdateRequestDto postUpdateRequestDto) {
        Post postWithTags = postRepository.findByIdWithTags(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND_EXCEPTION,
                        ErrorCode.POST_NOT_FOUND_EXCEPTION.getMessage() + postId));

        postWithTags.update(postUpdateRequestDto);

        // 기존 태그(PostTag) 제거
        postTagRepository.deleteAllByPost(postWithTags);
        postWithTags.getPostTags().clear(); // 양방향 관계 유지

        // 수정된 내용으로 추천 태그 재생성 및 등록
        List<String> tagNames = tagClient.getRecommendedTags(postWithTags.getContents());
        registerTagsToPost(postWithTags, tagNames);

        return PostInfoResponseDto.from(postWithTags);
    }

    // 게시물 삭제
    @Transactional
    public void postDelete(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND_EXCEPTION,
                        ErrorCode.POST_NOT_FOUND_EXCEPTION.getMessage() + postId));

        postRepository.delete(post);
    }

    // 게시물 추천 태그 목록 등록 및 PostTag 연관 엔티티 저장
    private void registerTagsToPost(Post post, List<String> tagNames) {
        for (String tagName : tagNames) {
            // 기존 태그가 있다면 사용, 없으면 새로 생성
            Tag tag = tagRepository.findByName(tagName)
                    .orElseGet(() -> tagRepository.save(new Tag(tagName)));

            // PostTag 생성 및 연관 관계 추가
            PostTag postTag = new PostTag(post, tag);
            post.getPostTags().add(postTag);   // 양방향 매핑 유지
            postTagRepository.save(postTag);
        }
    }
}
