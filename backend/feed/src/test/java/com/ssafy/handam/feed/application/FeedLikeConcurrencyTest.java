package com.ssafy.handam.feed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ssafy.handam.feed.IntegrationTestSupport;
import com.ssafy.handam.feed.domain.PlaceType;
import com.ssafy.handam.feed.domain.entity.Feed;
import com.ssafy.handam.feed.infrastructure.client.UserApiClient;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedDocument;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedElasticsearchRepository;
import com.ssafy.handam.feed.infrastructure.jpa.FeedJpaRepository;
import com.ssafy.handam.feed.infrastructure.jpa.LikeJpaRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;

/**
 * 좋아요 동시성 통합 테스트.
 *
 * <p>동일 피드에 여러 스레드가 동시에 좋아요를 실행해도 likeCount 갱신이 유실되지 않는지 검증한다.
 * 동시성 제어는 {@code Feed.@Version}(낙관적 락) + {@code FeedService.likeFeed()}의
 * {@code @Retryable} 재시도로 이루어진다.
 *
 * <p>기존 통합 테스트({@code FeedControllerIntegrationTest})와 같은 방식으로
 * H2 인메모리 DB(test 프로필)를 사용하고, 외부 인프라(Elasticsearch, user 서비스 Feign)는
 * {@code @MockBean}으로 대체한다. (Kafka/Redis는 이 경로에서 사용되지 않는다.)
 */
@ActiveProfiles("test")
class FeedLikeConcurrencyTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 100;

    @Autowired
    private FeedService feedService;

    @Autowired
    private FeedJpaRepository feedJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @MockBean
    private UserApiClient userApiClient;

    @MockBean
    private FeedElasticsearchRepository feedElasticsearchRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    private Long feedId;

    @BeforeEach
    void setUp() {
        likeJpaRepository.deleteAll();
        feedJpaRepository.deleteAll();

        Feed feed = Feed.builder()
                .placeName("Test Place")
                .title("Concurrency Test Feed")
                .content("Test Content")
                .imageUrl("http://example.com/feed.jpg")
                .address1("Test Address1")
                .address2("Test Address2")
                .longitude(127.123123)
                .latitude(32.1323)
                .placeType(PlaceType.CAFE)
                .userId(1L)
                .build();
        feedId = feedJpaRepository.save(feed).getId();

        // 좋아요 시 Elasticsearch 문서의 likeCount도 갱신하므로, ES 조회를 스텁으로 대체한다.
        when(feedElasticsearchRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(
                        FeedDocument.builder().id(invocation.getArgument(0)).build()));
    }

    @AfterEach
    void tearDown() {
        likeJpaRepository.deleteAll();
        feedJpaRepository.deleteAll();
    }

    @DisplayName("동시성 테스트 - 100개의 스레드가 동시에 같은 피드에 좋아요를 눌러도 likeCount 갱신이 유실되지 않는다")
    @Test
    void likeFeed_concurrently_doesNotLoseAnyUpdate() throws Exception {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        // when : 100명의 사용자가 동시에 같은 피드에 좋아요를 누른다
        for (int i = 0; i < THREAD_COUNT; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    feedService.likeFeed(feedId, userId);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finished = doneLatch.await(120, TimeUnit.SECONDS);
        executorService.shutdown();

        // then : 갱신 유실 없이 likeCount가 정확히 스레드 수와 일치한다
        Feed likedFeed = feedJpaRepository.findById(feedId).orElseThrow();
        System.out.printf("[concurrency] success=%d, failures=%d, likeCount=%d, likeRows=%d%n",
                successCount.get(), failures.size(), likedFeed.getLikeCount(), likeJpaRepository.count());
        failures.forEach(t -> System.out.println("[concurrency] failure: " + t));

        assertThat(finished).as("모든 스레드가 제한 시간 내에 종료되어야 한다").isTrue();
        assertThat(failures).as("낙관적 락 충돌은 재시도로 모두 흡수되어야 한다").isEmpty();
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(likedFeed.getLikeCount()).as("갱신 유실이 없어야 한다").isEqualTo(THREAD_COUNT);
        assertThat(likeJpaRepository.count()).isEqualTo(THREAD_COUNT);
    }
}
