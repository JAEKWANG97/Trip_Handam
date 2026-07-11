package com.ssafy.handam.feed.domain.repository;

import com.ssafy.handam.feed.domain.entity.Feed;
import com.ssafy.handam.feed.infrastructure.client.dto.UserDto;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedDocument;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedElasticsearchRepository;
import com.ssafy.handam.feed.infrastructure.jpa.FeedJpaRepository;
import com.ssafy.handam.feed.infrastructure.jpa.LikeJpaRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedRepositoryImpl implements FeedRepository {

    private final FeedJpaRepository feedJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final FeedElasticsearchRepository feedElasticsearchRepository;

    @Override
    public Optional<Feed> findById(Long id) {
        return feedJpaRepository.findById(id);
    }

    @Override
    public Feed save(Feed feed, UserDto userDto) {
        Feed savedFeed = feedJpaRepository.save(feed);
        feedElasticsearchRepository.save(FeedDocument.from(savedFeed, userDto));
        return savedFeed;
    }

    @Override
    public Feed save(Feed feed) {
        return feedJpaRepository.save(feed);
    }

    @Override
    public Page<Feed> findByUserId(Long userId, Pageable pageable) {
        return feedJpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public Page<FeedDocument> searchFeedsByKeywordSortedByLikeCount(String keyword, Pageable pageable) {
        return feedElasticsearchRepository.findByTitleContainingOrContentContainingOrAddress1ContainingOrAddress2ContainingOrPlaceTypeOrderByLikeCountDesc(
                keyword, keyword, keyword, keyword, keyword, pageable);
    }

    @Override
    public List<Feed> findByIdIn(List<Long> feedIds) {
        return feedJpaRepository.findByIdIn(feedIds);
    }

    @Override
    public Page<FeedDocument> getNearbyClusterCenter(GeoPoint geoPoint, String distance, Pageable pageable) {
        return feedElasticsearchRepository.findByLocationNear(geoPoint, distance, pageable);
    }

    @Override
    public Iterable<FeedDocument> findAllById(List<Long> feedIds) {
        return feedElasticsearchRepository.findAllById(feedIds);
    }

    @Override
    public List<Feed> findByTotalPlanId(Long totalPlanId) {
        return feedJpaRepository.findByTotalPlanId(totalPlanId);
    }

    @Override
    public Optional<FeedDocument> findFeedDocumentById(Long feedId) {
        return feedElasticsearchRepository.findById(feedId);
    }
}