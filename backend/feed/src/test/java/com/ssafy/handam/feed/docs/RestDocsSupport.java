package com.ssafy.handam.feed.docs;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.handam.feed.application.CommentService;
import com.ssafy.handam.feed.application.FeedService;
import com.ssafy.handam.feed.application.LikeService;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedElasticsearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
public abstract class RestDocsSupport {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ObjectMapper objectMapper;

    protected MockMvc mockMvc;

    @MockBean
    protected FeedService feedService;

    @MockBean
    protected CommentService commentService;

    @MockBean
    protected LikeService likeService;

    // 외부 인프라(Elasticsearch, Kafka)는 문서화 테스트에서 연결하지 않도록 mock 처리한다.
    @MockBean
    private FeedElasticsearchRepository feedElasticsearchRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext, RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }
}