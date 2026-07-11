package com.ssafy.handam.feed.presentation.api.feed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.Optional;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.handam.feed.IntegrationTestSupport;
import com.ssafy.handam.feed.application.FeedService;
import com.ssafy.handam.feed.domain.PlaceType;
import com.ssafy.handam.feed.domain.entity.Feed;
import com.ssafy.handam.feed.domain.entity.Like;
import com.ssafy.handam.feed.domain.repository.FeedRepository;
import com.ssafy.handam.feed.domain.repository.LikeRepository;
import com.ssafy.handam.feed.infrastructure.client.Gender;
import com.ssafy.handam.feed.infrastructure.client.UserApiClient;
import com.ssafy.handam.feed.infrastructure.client.dto.UserDto;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedDocument;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedElasticsearchRepository;
import com.ssafy.handam.feed.infrastructure.jpa.FeedJpaRepository;
import com.ssafy.handam.feed.infrastructure.jpa.LikeJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class FeedControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedJpaRepository feedJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private LikeRepository likeRepository;

    @MockBean
    private UserApiClient userApiClient;

    // 외부 인프라(Elasticsearch, Kafka)는 테스트에서 연결하지 않도록 mock 처리한다.
    @MockBean
    private FeedElasticsearchRepository feedElasticsearchRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private FeedService feedService;

    private Long savedFeedId;

    @BeforeEach
    void setup() {
        Feed feed = Feed.builder()
                .title("Test Title")
                .content("Test Content")
                .imageUrl("http://example.com/feed.jpg")
                .address1("Test Address")
                .address2("Test Address")
                .longitude(127.123123)
                .latitude(32.1323)
                .placeType(PlaceType.CAFE)
                .userId(1L)
                .build();

        Feed savedFeed = feedJpaRepository.save(feed);
        savedFeedId = savedFeed.getId();

        UserDto mockUserDto = createUserDto();
        when(userApiClient.getUserById(anyLong(), any())).thenReturn(mockUserDto);

        // 좋아요/좋아요 취소 시 Elasticsearch 문서의 likeCount 갱신 경로를 스텁으로 대체한다.
        when(feedElasticsearchRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(
                        FeedDocument.builder().id(invocation.getArgument(0)).build()));
    }

    @AfterEach
    void tearDown() {
        feedJpaRepository.deleteAll();
        likeJpaRepository.deleteAll();
    }


    @Test
    @DisplayName("통합 테스트 - 실제 서비스, DB와 통합된 FeedDetail 조회")
    @Disabled
    void getFeedDetailsTest() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/" + savedFeedId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.id").value(savedFeedId))
                .andExpect(jsonPath("$.response.userId").value(1L))
                .andExpect(jsonPath("$.response.nickName").value("testUser"))
                .andExpect(jsonPath("$.response.profileImageUrl").value("http://example.com/profile.jpg"))
                .andExpect(jsonPath("$.response.title").value("Test Title"))
                .andExpect(jsonPath("$.response.content").value("Test Content"))
                .andExpect(jsonPath("$.response.address1").value("Test Address"))
                .andExpect(jsonPath("$.response.address2").value("Test Address"))
                .andExpect(jsonPath("$.response.longitude").value(127.123123))
                .andExpect(jsonPath("$.response.latitude").value(32.1323))
                .andExpect(jsonPath("$.response.placeType").value("CAFE"))
                .andExpect(jsonPath("$.response.likeCount").value(0));
    }

    @DisplayName("통합 테스트 - 실제 서비스, DB와 통합된 Like 요청")
    @Test
    void likeFeedTest() throws Exception {
        mockMvc.perform(post("/api/v1/feeds/like/" + savedFeedId + "?userId=1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.feedId").value(savedFeedId))
                .andExpect(jsonPath("$.response.isLiked").value(true))
                .andExpect(jsonPath("$.response.likeCount").value(1));
    }

    @DisplayName("통합 테스트 - 실제 서비스, DB와 통합된 Unlike 요청")
    @Test
    void unlikeFeedTest() throws Exception {
        Feed feed = feedRepository.findById(savedFeedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found"));

        likeRepository.save(Like.builder().userId(1L).feed(feed).build());

        mockMvc.perform(post("/api/v1/feeds/unlike/" + savedFeedId + "?userId=1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.feedId").value(savedFeedId))
                .andExpect(jsonPath("$.response.isLiked").value(false))
                .andExpect(jsonPath("$.response.likeCount").value(0));
    }

    @DisplayName("통합 테스트 - 실제 서비스, DB와 통합된 사용자가 누른 좋아요 FeedList 조회")
    @Test
    @Disabled
    void getLikedFeedListTest() throws Exception {
        Feed feed = feedRepository.findById(savedFeedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found"));
        System.out.println("savedFeedId :" + savedFeedId);
        System.out.println("feed.id :" + feed.getId());
        feedService.likeFeed(savedFeedId, 1L);
        likeRepository.save(Like.builder().userId(1L).feed(feed).build());
        mockMvc.perform(get("/api/v1/feeds/liked?userId=1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.feeds[0].id").value(savedFeedId))
                .andExpect(jsonPath("$.response.feeds[0].userId").value(1L))
                .andExpect(jsonPath("$.response.feeds[0].nickName").value("testUser"))
                .andExpect(jsonPath("$.response.feeds[0].profileImageUrl").value("http://example.com/profile.jpg"))
                .andExpect(jsonPath("$.response.feeds[0].title").value("Test Title"))
                .andExpect(jsonPath("$.response.feeds[0].address1").value("Test Address"))
                .andExpect(jsonPath("$.response.feeds[0].address2").value("Test Address"))
                .andExpect(jsonPath("$.response.feeds[0].longitude").value(127.123123))
                .andExpect(jsonPath("$.response.feeds[0].latitude").value(32.1323))
                .andExpect(jsonPath("$.response.feeds[0].placeType").value("CAFE"))
                .andExpect(jsonPath("$.response.feeds[0].likeCount").value(1))
                .andExpect(jsonPath("$.response.currentPage").value(0))
                .andExpect(jsonPath("$.response.hasNextPage").value(false));
    }

    @DisplayName("통합 테스트 - 실제 서비스, DB와 통합된 사용자가 생성한 FeedList 조회")
    @Test
    void getCreatedFeedListTest() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/users/created?userId=1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.feeds[0].id").value(savedFeedId))
                .andExpect(jsonPath("$.response.feeds[0].userId").value(1L))
                .andExpect(jsonPath("$.response.feeds[0].nickName").value("testUser"))
                .andExpect(jsonPath("$.response.feeds[0].profileImageUrl").value("http://example.com/profile.jpg"))
                .andExpect(jsonPath("$.response.feeds[0].title").value("Test Title"))
                .andExpect(jsonPath("$.response.feeds[0].address1").value("Test Address"))
                .andExpect(jsonPath("$.response.feeds[0].address2").value("Test Address"))
                .andExpect(jsonPath("$.response.feeds[0].longitude").value(127.123123))
                .andExpect(jsonPath("$.response.feeds[0].latitude").value(32.1323))
                .andExpect(jsonPath("$.response.feeds[0].placeType").value("CAFE"))
                .andExpect(jsonPath("$.response.feeds[0].likeCount").value(0));
    }

//    @DisplayName("통합 테스트 - 실제 서비스, DB와 통합된 피드 생성")
//    @Test
//    void createFeedTest() throws Exception {
//        byte[] content = Files.readAllBytes(Paths.get("C:/Users/JK/Documents/원룸 사진/KakaoTalk_20231222_175652125_02.jpg"));
//        MockMultipartFile imageFile = new MockMultipartFile(
//                "image",
//                "feed.jpg",
//                MediaType.IMAGE_JPEG_VALUE,
//                content
//        );
//
//        String requestJson = "{\n" +
//                "  \"userId\": 1,\n" +
//                "  \"title\": \"Test Title\",\n" +
//                "  \"content\": \"Test Content\",\n" +
//                "  \"address1\": \"Test Address\",\n" +
//                "  \"address2\": \"Test Address\",\n" +
//                "  \"longitude\": 127.123123,\n" +
//                "  \"latitude\": 32.1323,\n" +
//                "  \"placeType\": \"CAFE\"\n" +
//                "}";
//
//        MockMultipartFile jsonPart = new MockMultipartFile(
//                "data",
//                "",
//                MediaType.APPLICATION_JSON_VALUE,
//                requestJson.getBytes()
//        );

    // Multipart 요청을 구성하고 기대하는 응답 값을 테스트
//        mockMvc.perform(multipart("/api/v1/feeds/create")
//                        .file(imageFile) // 'image' 파트로 이미지 파일 전송
//                        .file(jsonPart)  // 'data' 파트로 FeedCreationRequest JSON 전송
//                        .contentType(MediaType.MULTIPART_FORM_DATA) // multipart/form-data 형식
//                        .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.response.userId").value(1L))
//                .andExpect(jsonPath("$.response.nickName").value("testUser"))
//                .andExpect(jsonPath("$.response.profileImageUrl").value("http://example.com/profile.jpg"))
//                .andExpect(jsonPath("$.response.title").value("Test Title"))
//                .andExpect(jsonPath("$.response.content").value("Test Content"))
//                .andExpect(jsonPath("$.response.address1").value("Test Address"))
//                .andExpect(jsonPath("$.response.address2").value("Test Address"))
//                .andExpect(jsonPath("$.response.longitude").value(127.123123))
//                .andExpect(jsonPath("$.response.latitude").value(32.1323))
//                .andExpect(jsonPath("$.response.placeType").value("CAFE"))
//                .andExpect(jsonPath("$.response.likeCount").value(0));
//    }

    private UserDto createUserDto() {
        return UserDto.builder().
                id(1L).
                email("testUser").
                name("testUser").
                nickname("testUser").
                gender(Gender.FEMALE).
                age("20").
                profileImage("http://example.com/profile.jpg").
                residence("Test Address").
                introduction("Test Introduction").
                travelStyl1("Test Travel Style").
                travelStyl2("Test Travel Style").
                travelStyl3("Test Travel Style").
                travelStyl4("Test Travel Style").
                accompanyTemperature(36.5).
                build();
    }
}

