package com.ssafy.handam.feed.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ssafy.handam.feed.application.dto.FeedDetailDto;
import com.ssafy.handam.feed.application.dto.FeedImageInfoDto;
import com.ssafy.handam.feed.application.dto.FeedPreviewDto;
import com.ssafy.handam.feed.application.dto.UserDetailDto;
import com.ssafy.handam.feed.application.dto.request.feed.FeedCreationServiceRequest;
import com.ssafy.handam.feed.application.dto.request.feed.FeedsByTotalPlanIdServiceRequest;
import com.ssafy.handam.feed.application.dto.request.feed.NearByClusterCenterServiceReuqest;
import com.ssafy.handam.feed.application.dto.request.feed.RecommendedFeedsForUserServiceRequest;
import com.ssafy.handam.feed.domain.entity.Feed;
import com.ssafy.handam.feed.domain.entity.Like;
import com.ssafy.handam.feed.domain.service.FeedDomainService;
import com.ssafy.handam.feed.infrastructure.client.UserApiClient;
import com.ssafy.handam.feed.infrastructure.client.dto.UserDto;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedDocument;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedElasticsearchRepository;
import com.ssafy.handam.feed.infrastructure.jwt.JwtUtil;
import com.ssafy.handam.feed.presentation.response.cluster.ClusterResponse;
import com.ssafy.handam.feed.presentation.response.feed.CreatedFeedsByUserResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedDetailResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedLikeResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedsImageInfoResponse;
import com.ssafy.handam.feed.presentation.response.feed.LikedFeedsByUserResponse;
import com.ssafy.handam.feed.presentation.response.feed.NearbyClusterCenterResponse;
import com.ssafy.handam.feed.presentation.response.feed.RecommendedFeedsForUserResponse;
import com.ssafy.handam.feed.presentation.response.feed.SearchedFeedsResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class FeedService {

    private final FeedDomainService feedDomainService;
    private final UserApiClient userApiClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Gson gson;
    private final ElasticsearchOperations elasticsearchOperations;
    private final FeedElasticsearchRepository feedElasticsearchRepository;
    private final JwtUtil jwtUtil;

    public RecommendedFeedsForUserResponse getRecommendedFeedsForUser(RecommendedFeedsForUserServiceRequest request) {
        String createdDate = LocalDateTime.parse("2021-07-01T00:00:00")
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // FeedPreviewDto 생성 시 createdDate 추가
        FeedPreviewDto feedPreviewDto = new FeedPreviewDto(
                1L,
                1L,
                "placeName",
                "title",
                "content",
                "image-url",
                1L,
                0,
                0,
                "address1",
                "address2",
                Double.valueOf(32.1323),  // double을 명시적으로 Double로 변환
                Double.valueOf(127.123123),  // double을 명시적으로 Double로 변환
                "CAFE",
                "username",
                "profileImageUrl",
                true,
                createdDate // 생성일자
        );
        List<FeedPreviewDto> previewDtos = List.of(feedPreviewDto);
        return RecommendedFeedsForUserResponse.of(previewDtos, 0, false);
    }

    public SearchedFeedsResponse searchFeedsByKeywordSortedByLikeCount(String keyword, int page, int size,
                                                                       String accessToken) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "likeCount"));
        Page<FeedDocument> feedDocuments = feedDomainService.searchFeedsByKeywordSortedByLikeCount(keyword, pageable);
        UserDto userDto = userApiClient.getUserByToken(accessToken);
        List<FeedPreviewDto> feedPreviewDtos = feedDocuments.stream()
                .map(feedDocument -> {
                    return convertToFeedPreviewDto(feedDocument, userDto.id());
                }).toList();

        return SearchedFeedsResponse.of(feedPreviewDtos, feedDocuments.getNumber(), feedDocuments.hasNext());
    }

    public FeedDetailResponse getFeedDetails(Long feedId, String accessToken) {
        Feed feed = feedDomainService.findById(feedId);
        UserDetailDto userDetailDto = getUserDetailDto(feed.getUserId(), accessToken);
        UserDto userDto = userApiClient.getUserByToken(accessToken);
        return FeedDetailResponse.of(FeedDetailDto.of(feed, isLikedFeed(feed, userDto.id())),
                userDetailDto.nickname(), userDetailDto.profileImageUrl());
    }

    public FeedResponse createFeed(FeedCreationServiceRequest request, String savedImagePath, String accessToken) {
        UserDto userDto = userApiClient.getUserById(request.userId(), accessToken);
        Feed feed = feedDomainService.createFeed(request, savedImagePath, userDto);
        return FeedResponse.from(feed, userDto);
    }

    public String saveImage(MultipartFile imageFile) {
        String UPLOAD_PATH = "/app/photos/";
        String fileName = UUID.randomUUID().toString();
        Path path = Paths.get(UPLOAD_PATH + fileName);

        try {
            // 파일을 지정된 경로에 저장
            Files.copy(imageFile.getInputStream(), path);
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to save image";
        }

        return "https://j11c205a.p.ssafy.io/images/" + fileName;
    }

    /**
     * 동시에 같은 피드에 좋아요가 몰리면 낙관적 락(@Version) 충돌이 발생할 수 있으므로,
     * 충돌 시 새로운 트랜잭션에서 재시도한다.
     * (spring-retry의 advisor order 기본값은 LOWEST_PRECEDENCE - 1로,
     * 트랜잭션 advisor(LOWEST_PRECEDENCE)보다 바깥에서 감싸므로 재시도마다 새 트랜잭션이 열린다.)
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2.0, random = true)
    )
    @Transactional
    public FeedLikeResponse likeFeed(Long feedId, Long userId) {
        int likeCount = feedDomainService.likeFeed(feedId, userId);
        updateLikeCountInElasticsearch(feedId, likeCount);
        return FeedLikeResponse.of(feedId, true, likeCount);
    }

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2.0, random = true)
    )
    @Transactional
    public FeedLikeResponse unlikeFeed(Long feedId, Long userId) {
        int likeCount = feedDomainService.unlikeFeed(feedId, userId);
        updateLikeCountInElasticsearch(feedId, likeCount);
        return FeedLikeResponse.of(feedId, false, likeCount);
    }

    private void updateLikeCountInElasticsearch(Long feedId, int likeCount) {
        FeedDocument feedDocument = feedDomainService.getFeedDocumentById(feedId);
        feedDocument.setLikeCount(likeCount);
        elasticsearchOperations.save(feedDocument);
    }

    @Transactional(readOnly = true)
    public LikedFeedsByUserResponse getLikedFeedsByUser(Long userId, Pageable pageable, String accessToken) {
        Page<Like> likesBy = feedDomainService.getLikesBy(userId, pageable);
        List<Long> feedIds = likesBy.getContent().stream()
                .map(like -> like.getFeed().getId())
                .toList();
        List<FeedDocument> likedFeedsByUser = feedDomainService.getFeedDocumentsByIds(feedIds);
        boolean hasNextPage = likesBy.hasNext();
        int currentPage = likesBy.getNumber();
        List<FeedPreviewDto> likedFeeds = getFeedPreviewDtoList(likedFeedsByUser, accessToken);
        return LikedFeedsByUserResponse.of(likedFeeds, currentPage, hasNextPage);
    }

    @Transactional(readOnly = true)
    public CreatedFeedsByUserResponse getCreatedFeedsByUser(Long userId, Pageable pageable, String accessToken) {
        Page<Feed> createdFeedsByUser = feedDomainService.getCreatedFeedsByUser(userId, pageable);
        List<FeedPreviewDto> createdFeeds = getFeedPreviewDtoList(
                createdFeedsByUser.getContent(),
                userApiClient.getUserById(userId, accessToken));
        return CreatedFeedsByUserResponse.of(createdFeeds, createdFeedsByUser.getNumber(),
                createdFeedsByUser.hasNext());
    }

    public List<ClusterResponse> getClusteredFeeds(Long userId, String token) {
        String redisKey = "userCluster:" + userId;
        List<Object> cachedData = redisTemplate.opsForList().range(redisKey, 0, -1);
        List<ClusterResponse> clusteredFeeds = new ArrayList<>();

        if (cacheHit(cachedData)) {
            for (Object obj : cachedData) {
                try {
                    ClusterResponse cluster = objectMapper.readValue((String) obj, ClusterResponse.class);
                    clusteredFeeds.add(cluster);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse cached data");
                }
            }
        } else {
            clusteredFeeds = performClustering(userId, token);
            for (ClusterResponse cluster : clusteredFeeds) {
                try {
                    redisTemplate.opsForList().rightPush(redisKey, objectMapper.writeValueAsString(cluster));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse cached data");
                }
            }
            redisTemplate.expire(redisKey, Duration.ofHours(1));
        }

        return clusteredFeeds;
    }

    public List<ClusterResponse> refreshClusteredFeeds(Long userId, String token) {
        String redisKey = "userCluster:" + userId;
        redisTemplate.delete(redisKey);
        List<ClusterResponse> refreshedFeeds = performClustering(userId, token);
        for (ClusterResponse cluster : refreshedFeeds) {
            try {
                redisTemplate.opsForList().rightPush(redisKey, objectMapper.writeValueAsString(cluster));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        redisTemplate.expire(redisKey, Duration.ofHours(1));
        return refreshedFeeds;
    }

    public NearbyClusterCenterResponse getNearbyClusterCenter(
            NearByClusterCenterServiceReuqest nearByClusterCenterServiceReuqest) {
        int page = nearByClusterCenterServiceReuqest.page();
        int size = nearByClusterCenterServiceReuqest.size();
        double latitude = nearByClusterCenterServiceReuqest.latitude();
        double longitude = nearByClusterCenterServiceReuqest.longitude();
        int distance = nearByClusterCenterServiceReuqest.distance();
        String accessToken = nearByClusterCenterServiceReuqest.token();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "likeCount"));
        String distanceString = distance + "km";
        Page<FeedDocument> nearbyClusterCenter = feedDomainService.getNearbyClusterCenter(latitude, longitude,
                distanceString, pageable);
        UserDto userDto = userApiClient.getUserByToken(accessToken);
        List<FeedPreviewDto> feedPreviewDtoList = nearbyClusterCenter.stream()
                .map(feedDocument -> {

                    return convertToFeedPreviewDto(feedDocument, userDto.id());
                }).toList();

        return NearbyClusterCenterResponse.of(
                feedPreviewDtoList,
                nearbyClusterCenter.getNumber(),
                nearbyClusterCenter.hasNext()
        );
    }

    public FeedsImageInfoResponse getFeedsImageUrlsByTotalPlanId(FeedsByTotalPlanIdServiceRequest request) {
        return FeedsImageInfoResponse.of(
                getFeedsImageInfoList(
                        feedDomainService.getFeedsByTotalPlanId(
                                request.totalPlanId()
                        )
                ));
    }

    private boolean cacheHit(List<Object> cachedData) {
        return cachedData != null && !cachedData.isEmpty();
    }

    private List<ClusterResponse> performClustering(Long userId, String token) {
        Pageable allFeedsPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.unsorted());
        List<Feed> feeds = feedDomainService.getLikedFeedsByUser(userId, allFeedsPageable);

        DBSCANClusterer<DoublePoint> dbscanClusterer = new DBSCANClusterer<>(0.09, 2);
        List<DoublePoint> points = feeds.stream()
                .map(feed -> new DoublePoint(new double[]{feed.getLatitude(), feed.getLongitude()}))
                .toList();

        List<Cluster<DoublePoint>> clusters = dbscanClusterer.cluster(points);
        List<ClusterResponse> clusteredFeeds = new ArrayList<>();

        for (Cluster<DoublePoint> cluster : clusters) {
            double[] centroid = calculateCentroid(cluster);
            List<FeedPreviewDto> feedsInCluster = new ArrayList<>();

            feedsInCluster = cluster.getPoints().stream()
                    .map(point -> {
                        double latitude = point.getPoint()[0];
                        double longitude = point.getPoint()[1];
                        return searchFeedByLatitudeAndLongitude(latitude, longitude, feeds);
                    })
                    .filter(Objects::nonNull)
                    .map(feed -> {
                        UserDto userDto = userApiClient.getUserById(feed.getUserId(), token);
                        return FeedPreviewDto.from(feed, userDto.name(), userDto.profileImage(),
                                isLikedFeed(feed, userDto.id()));
                    })
                    .toList();
            String clusterId = UUID.randomUUID().toString();
            clusteredFeeds.add(new ClusterResponse(clusterId, centroid[0], centroid[1], feedsInCluster));
        }
        return clusteredFeeds;
    }

    private Feed searchFeedByLatitudeAndLongitude(double latitude, double longitude, List<Feed> feeds) {
        for (Feed feed : feeds) {
            if (feed.getLatitude() == latitude && feed.getLongitude() == longitude) {
                return feed;
            }
        }
        return null;
    }

    private double[] calculateCentroid(Cluster<DoublePoint> cluster) {
        double sumLat = 0;
        double sumLon = 0;
        int count = cluster.getPoints().size();

        for (DoublePoint point : cluster.getPoints()) {
            sumLat += point.getPoint()[0];
            sumLon += point.getPoint()[1];
        }

        return new double[]{sumLat / count, sumLon / count};
    }

    private List<FeedPreviewDto> getFeedPreviewDtoList(List<Feed> feeds, UserDto user) {
        return feeds.stream()
                .map(feed -> FeedPreviewDto.from(feed, user.name(), user.profileImage(),
                        isLikedFeed(feed, user.id())))
                .toList();
    }

    private List<FeedPreviewDto> getFeedPreviewDtoList(List<FeedDocument> feeds, String token) {
        UserDto userByToken = userApiClient.getUserByToken(token);
        return feeds.stream()
                .map(feed -> FeedPreviewDto.fromDocument(feed, isLikedFeed(feed, userByToken.id())))
                .toList();
    }

    private boolean isLikedFeed(Feed feed, Long userId) {
        return feedDomainService.isLikedFeed(feed.getId(), userId);
    }

    private boolean isLikedFeed(FeedDocument feed, Long userId) {
        return feedDomainService.isLikedFeed(feed.getId(), userId);
    }

    private UserDetailDto getUserDetailDto(Long userId, String accessToken) {
        return UserDetailDto.from(userApiClient.getUserById(userId, accessToken));
    }

    private FeedPreviewDto convertToFeedPreviewDto(FeedDocument feedDocument, Long userId) {
        return new FeedPreviewDto(
                feedDocument.getId(),
                feedDocument.getTotalPlanId(),
                feedDocument.getPlaceName(),
                feedDocument.getTitle(),
                feedDocument.getContent(),
                feedDocument.getImageUrl(),
                feedDocument.getUserId(),
                feedDocument.getLikeCount(),
                feedDocument.getCommentCount(),
                feedDocument.getAddress1(),
                feedDocument.getAddress2(),
                feedDocument.getLongitude(),
                feedDocument.getLatitude(),
                feedDocument.getPlaceType(),
                feedDocument.getUserNickname(),
                feedDocument.getProfileImageUrl(),
                feedDomainService.isLikedFeed(feedDocument.getId(), userId),
                feedDocument.getCreatedDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
    }


    public RecommendedFeedsForUserResponse getRecommendedFeeds(String token, int page, int pageSize) {
        Long userId = jwtUtil.extractUserId(token);

        // 카테고리별로 가져올 비율
        double recommendedRatio = 0.6;
        double topLikedRatio = 0.2;
        double trendingRatio = 0.1;
        double randomRatio = 0.1;

        // 카테고리별로 가져올 개수 결정 (총 페이지 기준)
        int numRecommended = (int) (pageSize * recommendedRatio);
        int numTopLiked = (int) (pageSize * topLikedRatio);
        int numTrending = (int) (pageSize * trendingRatio);
        int numRandom = (int) (pageSize * randomRatio);

        // Redis에서 피드 ID들을 가져옴
        List<String> recommendedFeedIds = getFeedIdsFromRedis("user:" + userId + ":recommended_feeds", page,
                numRecommended);
        List<String> topLikedFeedIds = getFeedIdsFromRedis("user:" + userId + ":top_liked_feeds", page, numTopLiked);
        List<String> trendingFeedIds = getFeedIdsFromRedis("user:" + userId + ":trending_feeds", page, numTrending);
        List<String> randomFeedIds = getFeedIdsFromRedis("user:" + userId + ":random_feeds", page, numRandom);

        // Feed IDs를 Long으로 변환
        List<Long> recommendedFeedIdsLong = convertStringIdsToLong(recommendedFeedIds);
        List<Long> topLikedFeedIdsLong = convertStringIdsToLong(topLikedFeedIds);
        List<Long> trendingFeedIdsLong = convertStringIdsToLong(trendingFeedIds);
        List<Long> randomFeedIdsLong = convertStringIdsToLong(randomFeedIds);

        // 중복을 제거한 피드를 관리하기 위해 Set 사용
        Set<Long> allFeedIds = new HashSet<>();
        List<Long> uniqueFeedIds = new ArrayList<>();

        // 중복되지 않는 피드들을 추가
        addUniqueFeeds(allFeedIds, uniqueFeedIds, recommendedFeedIdsLong);
        addUniqueFeeds(allFeedIds, uniqueFeedIds, topLikedFeedIdsLong);
        addUniqueFeeds(allFeedIds, uniqueFeedIds, trendingFeedIdsLong);
        addUniqueFeeds(allFeedIds, uniqueFeedIds, randomFeedIdsLong);

        // 부족한 피드 수 계산
        int feedsToFill = pageSize - uniqueFeedIds.size();

        int topLikePage = page; // 초기 페이지 설정

        // 중복되지 않게 랜덤 피드에서 추가로 가져오기

        while (feedsToFill > 0) {
            List<String> additionalTopLikeFeedIds = getFeedIdsFromRedis("user:" + userId + ":top_liked_feeds",
                    topLikePage, feedsToFill);
            List<Long> additionalTopLikeFeedIdsLong = convertStringIdsToLong(additionalTopLikeFeedIds);

            // 중복되지 않는 피드를 추가
            for (Long randomFeedId : additionalTopLikeFeedIdsLong) {
                if (allFeedIds.add(randomFeedId)) {  // 중복 검사 후 추가
                    uniqueFeedIds.add(randomFeedId);
                    feedsToFill--;
                }
                if (feedsToFill <= 0) {  // 필요한 수를 다 채우면 종료
                    break;
                }
            }

            topLikePage++;
            if (additionalTopLikeFeedIdsLong.isEmpty()) {
                break;
            }
        }

        // feedDomainService로 feedId들을 넘겨서 추천 피드를 가져옴
        return feedDomainService.getRecommendedFeeds(uniqueFeedIds, page);
    }

    // 중복되지 않는 피드들을 추가하는 헬퍼 메서드
    private void addUniqueFeeds(Set<Long> allFeedIds, List<Long> uniqueFeedIds, List<Long> feedIds) {
        for (Long feedId : feedIds) {
            if (allFeedIds.add(feedId)) {
                uniqueFeedIds.add(feedId);
            }
        }
    }

    // Redis에서 피드 ID를 가져오는 메서드
    private List<String> getFeedIdsFromRedis(String redisKey, int page, int count) {
        long start = (long) page * count;
        long end = start + count - 1;
        return redisTemplate.opsForList().range(redisKey, start, end)
                .stream()
                .map(Object::toString) // Object를 String으로 변환
                .collect(Collectors.toList());
    }

    // String ID를 Long으로 변환하는 메서드
    private List<Long> convertStringIdsToLong(List<String> stringIds) {
        return stringIds.stream()
                .flatMap(stringId -> {
                    if (stringId.startsWith("[")) {
                        List<Long> parsedIds = gson.fromJson(stringId, new TypeToken<List<Long>>() {
                        }.getType());
                        return parsedIds.stream();
                    } else {
                        return Stream.of(Long.parseLong(stringId));
                    }
                })
                .toList();
    }

    private List<FeedImageInfoDto> getFeedsImageInfoList(List<Feed> feedsBytotalPlanId) {
        return feedsBytotalPlanId.stream()
                .map(feed -> FeedImageInfoDto.of(feed.getId(), feed.getImageUrl()))
                .toList();
    }
}
