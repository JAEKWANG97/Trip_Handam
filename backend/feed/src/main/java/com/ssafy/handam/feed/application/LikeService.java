package com.ssafy.handam.feed.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.handam.feed.infrastructure.client.UserApiClient;
import com.ssafy.handam.feed.infrastructure.client.dto.UserDto;
import com.ssafy.handam.feed.infrastructure.elasticsearch.FeedDocument;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserApiClient userApiClient;


    public void sendLikeEvent(Long feedId, Long userId, String token) {
        Map<String, Object> message = new HashMap<>();
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String formattedTimestamp = currentTime.format(formatter);

        message.put("feed_id", feedId);
        message.put("user_id", userId);
        message.put("travel_type", getUserTravelStyle(userId, token));
        message.put("timestamp", formattedTimestamp);

        try {
            String messageAsString = objectMapper.writeValueAsString(message);
            kafkaTemplate.send("like-events", messageAsString);
        } catch (JsonProcessingException e) {
            log.error("좋아요 이벤트 직렬화에 실패하여 Kafka 발행을 건너뜁니다. feedId={}, userId={}", feedId, userId, e);
        }
    }

    private String getUserTravelStyle(Long userId, String token) {
        UserDto user = userApiClient.getUserById(userId, token);
        return String.join("", user.travelStyl1(), user.travelStyl2(), user.travelStyl3(), user.travelStyl4());
    }

    public List<FeedDocument> preformClustering(Long userId) {
        // 클러스터링 로직 (예시로 빈 데이터 반환))
        return List.of();
    }
}