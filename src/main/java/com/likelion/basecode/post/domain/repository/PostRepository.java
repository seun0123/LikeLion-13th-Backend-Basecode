package com.likelion.basecode.post.domain.repository;

import com.likelion.basecode.member.domain.Member;
import com.likelion.basecode.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByMember(Member member);
}
