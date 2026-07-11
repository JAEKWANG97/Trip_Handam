package com.ssafy.handam.feed.presentation.api.feed;

import static com.ssafy.handam.feed.presentation.api.ApiUtils.success;

import com.ssafy.handam.feed.application.CommentService;
import com.ssafy.handam.feed.application.FeedService;
import com.ssafy.handam.feed.application.LikeService;
import com.ssafy.handam.feed.application.dto.request.comment.CreateCommentServiceRequest;
import com.ssafy.handam.feed.application.dto.request.feed.FeedCreationServiceRequest;
import com.ssafy.handam.feed.application.dto.request.feed.FeedsByTotalPlanIdServiceRequest;
import com.ssafy.handam.feed.application.dto.request.feed.NearByClusterCenterServiceReuqest;
import com.ssafy.handam.feed.domain.service.FeedDomainService;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedDocument;
import com.ssafy.handam.feed.presentation.api.ApiUtils.ApiResult;
import com.ssafy.handam.feed.presentation.request.comment.CreateCommentRequest;
import com.ssafy.handam.feed.presentation.request.feed.FeedCreationRequest;
import com.ssafy.handam.feed.presentation.request.feed.FeedsByTotalPlanIdRequest;
import com.ssafy.handam.feed.presentation.request.feed.RecommendedFeedsForUserRequest;
import com.ssafy.handam.feed.presentation.response.cluster.ClusterResponse;
import com.ssafy.handam.feed.presentation.response.comment.CreateCommentResponse;
import com.ssafy.handam.feed.presentation.response.feed.CommentsResponse;
import com.ssafy.handam.feed.presentation.response.feed.CreatedFeedsByUserResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedDetailResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedLikeResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedResponse;
import com.ssafy.handam.feed.presentation.response.feed.FeedsImageInfoResponse;
import com.ssafy.handam.feed.presentation.response.feed.LikedFeedsByUserResponse;
import com.ssafy.handam.feed.presentation.response.feed.NearbyClusterCenterResponse;
import com.ssafy.handam.feed.presentation.response.feed.RecommendedFeedsForUserResponse;
import com.ssafy.handam.feed.presentation.response.feed.SearchedFeedsResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final FeedDomainService feedDomainService;

    @PostMapping("/user/recommended")
    public ApiResult<RecommendedFeedsForUserResponse> getRecommendedFeedsForUser(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestBody RecommendedFeedsForUserRequest request) {
        return success(feedService.getRecommendedFeedsForUser(
                RecommendedFeedsForUserRequest.toServiceRequest(request)
        ));
    }

    @GetMapping("/search")
    public ApiResult<SearchedFeedsResponse> searchFeeds(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return success(feedService.searchFeedsByKeywordSortedByLikeCount(keyword, page, size, token));
    }

    @PostMapping(value = "/create", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public ApiResult<FeedResponse> createFeed(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestPart("data") FeedCreationRequest request,
            @RequestPart("image") MultipartFile imageFile) {

        String savedImagePath = feedService.saveImage(imageFile);

        FeedCreationServiceRequest serviceRequest = FeedCreationRequest.toServiceRequest(request);

        return success(feedService.createFeed(serviceRequest, savedImagePath, token));
    }

    @GetMapping("/{feedId}")
    public ApiResult<FeedDetailResponse> getFeedDetails(
            @CookieValue(value = "accessToken", required = false) String token,
            @PathVariable Long feedId) {
        return success(feedService.getFeedDetails(feedId, token));
    }

    @PostMapping("/like/{feedId}")
    public ApiResult<FeedLikeResponse> likeFeed(
            @CookieValue(value = "accessToken", required = false) String token,
            @PathVariable Long feedId,
            @RequestParam Long userId) {
        likeService.sendLikeEvent(feedId, userId, token);
        return success(feedService.likeFeed(feedId, userId));
    }

    @PostMapping("/unlike/{feedId}")
    public ApiResult<FeedLikeResponse> unlikeFeed(
            @CookieValue(value = "accessToken", required = false) String token,
            @PathVariable Long feedId,
            @RequestParam Long userId) {
        return success(feedService.unlikeFeed(feedId, userId));
    }

    @GetMapping("/liked")
    public ApiResult<LikedFeedsByUserResponse> getLikedByUser(
            @CookieValue(value = "accessToken", required = false) String token,
            Pageable pageable,
            @RequestParam Long userId) {
        return success(feedService.getLikedFeedsByUser(userId, pageable, token));
    }

    @GetMapping("/users/created")
    public ApiResult<CreatedFeedsByUserResponse> getCreatedFeedsByUser(
            @CookieValue(value = "accessToken", required = false) String token, Pageable pageable,
            @RequestParam Long userId) {
        return success(feedService.getCreatedFeedsByUser(userId, pageable, token));
    }

    @PostMapping("/{feedId}/comments")
    public ApiResult<CreateCommentResponse> createComment(
            @CookieValue(name = "accessToken", required = false) String accessToken,
            @PathVariable Long feedId,
            @RequestBody CreateCommentRequest request) {
        return success(commentService.save(CreateCommentServiceRequest.of(feedId, request)));
    }

    @GetMapping("/{feedId}/comments")
    public ApiResult<CommentsResponse> getComments(
            @CookieValue(name = "accessToken", required = false) String accessToken,
            @PathVariable Long feedId) {
        return success(commentService.findAllByFeedId(feedId, accessToken));
    }

    @GetMapping("/like/clustering")
    public ApiResult<List<ClusterResponse>> getClusteredFeeds(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestParam Long userId) {
        return success(feedService.getClusteredFeeds(userId, token));
    }

    @GetMapping("/like/clustering/refresh")
    public ApiResult<List<ClusterResponse>> refreshClusteredFeeds(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestParam Long userId) {
        return success(feedService.refreshClusteredFeeds(userId, token));
    }

    @GetMapping("/cluster/center/nearby")
    public ApiResult<NearbyClusterCenterResponse> getNearbyClusterCenter(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam Integer distance,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        {
            return success(feedService
                    .getNearbyClusterCenter(
                            NearByClusterCenterServiceReuqest.of(
                                    latitude,
                                    longitude,
                                    distance,
                                    page,
                                    size,
                                    token)
                    )
            );
        }
    }

    @GetMapping("/recommended")
    public ApiResult<RecommendedFeedsForUserResponse> getRecommendedFeeds(
            @CookieValue(value = "accessToken", required = false) String token,
            @RequestParam int page,
            @RequestParam int pageSize) {
        RecommendedFeedsForUserResponse response = feedService.getRecommendedFeeds(token, page, pageSize);
        return success(response);
    }

    @GetMapping("/search/images/{totalPlanId}")
    public ApiResult<FeedsImageInfoResponse> getFeedsImageUrlsByTotalPlanId(@PathVariable Long totalPlanId) {

        FeedsByTotalPlanIdServiceRequest request = FeedsByTotalPlanIdRequest.toService(totalPlanId);
        return success(feedService.getFeedsImageUrlsByTotalPlanId(request));
    }

    @GetMapping("/elastic/{feedId}")
    public ApiResult<FeedDocument> getFeedDetailsFromElastic(@PathVariable Long feedId) {
        return success(feedDomainService.getFeedDocumentById(feedId));
    }
}

