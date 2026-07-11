package com.ssafy.handam.feed.infrastructure.jpa;

import com.ssafy.handam.feed.domain.entity.Like;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    Like save(Like like);

    List<Like> findByFeedId(Long feedId);

    Page<Like> findByUserId(Long userId, Pageable pageable);

    List<Like> findByFeedIdAndUserId(Long feedId, Long userId);
}