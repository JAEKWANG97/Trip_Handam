# Trip_Handam 프로젝트 기술 분석 및 포트폴리오 연계 가이드

## 1. 프로젝트 개요
- 프로젝트 한 줄 요약  
  여행 추천, 일정 관리, 채팅, 포토카드 생성을 분리된 서비스로 제공하는 Spring Boot 기반 백엔드 프로젝트이며, Gateway와 별도 FastAPI GPU 서버까지 포함한 분산형 구성이 확인됩니다.

- 어떤 문제를 해결하는 서비스인지 2~4줄  
  이 서비스는 여행 정보를 탐색하고, 좋아요한 피드와 여행 성향을 바탕으로 여행 계획을 만들고, 다른 사용자와 소통하는 흐름을 하나의 플랫폼 안에서 처리하려는 목적을 가집니다.  
  코드 기준으로는 사용자 인증, 피드 검색/추천, 일정 조회/생성, 실시간 채팅, AI 포토카드 생성이 각각 별도 백엔드 서비스로 나뉘어 있습니다.

- 백엔드 관점에서 이 프로젝트가 왜 의미 있는지 2~4줄  
  단순 CRUD 수준이 아니라 Gateway, OAuth2/JWT 인증, Redis/Elasticsearch/Kafka, HDFS 설정, WebSocket/STOMP, 별도 AI 추론 서버 연동까지 포함하고 있어 백엔드 포트폴리오 소재로 폭이 넓습니다.  
  특히 `feed` 서비스는 검색·추천·캐시·이벤트 발행을 함께 다루고, 운영 문서 기준으로는 별도 서버의 Spark-Hadoop 파이프라인과 연결돼 추천 결과를 서빙하는 구조를 설명할 수 있습니다.

## 2. 백엔드 아키텍처 요약
- 서버 구성  
  `SCG`, `eureka`, `user`, `feed`, `plan`, `chat`, `photocard`, `gpu-server`가 확인됩니다.  
  `SCG`는 `/api/v1/users`, `/api/v1/feeds`, `/api/v1/plans`, `/api/v1/chat`, `/api/v1/photocards` 등을 라우팅하고, `chat-websocket` 경로도 별도로 전달합니다.

- 저장소/캐시/메시징/외부 API  
  MySQL은 `user`, `feed`, `plan`, `photocard` 설정에서 확인됩니다.  
  Redis는 `SCG`와 `feed`에서 사용되며, `feed`는 추천 결과와 군집 캐시 저장에 사용합니다.  
  Elasticsearch는 `feed` 서비스에서 피드 검색과 위치 기반 검색에 사용됩니다.  
  Kafka는 `feed`의 좋아요 이벤트 발행에 사용되며, HDFS URI도 `feed` 설정에 존재합니다. 운영 문서에는 별도 서버에서 Spark 잡으로 Kafka 데이터를 Hadoop/HDFS로 넘기고 유사도 계산 결과를 Redis에 반영하는 흐름이 남아 있습니다.  
  외부 연동으로는 Naver OAuth2, 사용자 서비스 간 HTTP 호출, 계획 서비스 호출, GPU FastAPI 호출, GPU 서버의 S3 업로드가 확인됩니다.

- 서비스 분리 여부와 책임  
  `user`는 OAuth2 로그인, JWT 발급, 설문/팔로우/유저 조회를 담당합니다.  
  `feed`는 피드 생성, 좋아요/댓글, 검색, 추천, 군집화, Elasticsearch 동기화를 담당합니다.  
  `plan`은 일정 생성과 `TotalPlan -> DayPlan -> Plan` 조회/집계를 담당합니다.  
  `chat`은 WebSocket/STOMP 채팅과 채팅 메시지 저장을 담당합니다.  
  `photocard`는 포토카드 생성 요청을 받아 GPU 서버와 plan 서비스를 호출한 뒤 결과를 저장합니다.

- 배포/운영 방식  
  `SCG`, `feed`, `user`는 서비스별 `Dockerfile`과 `Jenkinsfile`이 있고, Jenkins에서 빌드 후 Docker Registry에 push하고 EC2에서 `docker run`으로 재배포하는 흐름이 확인됩니다.  
  `feed`는 멀티스테이지 Docker 빌드로 Asciidoctor 문서까지 포함한 jar를 만들고, `SCG`/`user`도 별도 이미지로 운영하도록 구성돼 있습니다.  
  다만 Eureka는 존재하지만 현재 Gateway 설정은 `lb://`가 아니라 고정 host:port URI를 사용합니다.

## 3. 핵심 기술 분석
### A. Gateway와 중앙 인증 경계
- `SCG`는 Spring Cloud Gateway와 WebFlux 기반으로 각 도메인 서비스를 중앙 진입점에서 라우팅합니다.
- `JwtAuthFilter`는 요청에서 토큰을 읽어 유효성을 검사하고, 인증된 경우에만 downstream 서비스로 전달합니다.
- 이 구조는 서비스별 인증 로직을 분산시키지 않고 ingress 레벨에서 접근 제어를 걸 수 있다는 점에서 의미가 있습니다.
- 코드 근거  
  `backend/SCG/build.gradle`에 `spring-cloud-starter-gateway`, `spring-boot-starter-webflux`, `spring-boot-starter-security`, `spring-boot-starter-data-redis-reactive`가 포함돼 있습니다.  
  `backend/SCG/src/main/resources/application-prod.yml`에 user/feed/plan/chat/photocard 라우트와 websocket 라우트가 정의돼 있습니다.  
  `backend/SCG/src/main/java/com/ssafy/handam/scg/filters/JwtAuthFilter.java`에서 JWT 검사 후 요청을 통과시키는 흐름이 구현돼 있습니다.
- 주의할 점  
  Eureka 서버는 별도로 존재하지만, 현재 Gateway 라우팅은 서비스 디스커버리 기반이 아니라 직접 URI 지정 방식입니다.

### B. Naver OAuth2 + JWT 쿠키 기반 user 서비스
- `user` 서비스는 Naver OAuth2 로그인 후 내부 사용자 등록/조회 처리와 JWT 발급을 담당합니다.
- 로그인 성공 시 JWT를 `accessToken` HttpOnly 쿠키로 저장하고, 설문 완료 여부에 따라 다른 화면으로 리다이렉트합니다.
- 이후 사용자 정보 조회, 설문 저장, 팔로우/언팔로우, 사용자 검색은 쿠키 기반 토큰에서 사용자 정보를 추출해 처리합니다.
- 코드 근거  
  `backend/user/src/main/java/com/ssafy/handam/user/infrastructure/security/SecurityConfig.java`에서 OAuth2 login과 `CustomOAuth2UserService`, `OAuth2LoginSuccessHandler`를 연결합니다.  
  `backend/user/src/main/java/com/ssafy/handam/user/infrastructure/oauth/OAuth2LoginSuccessHandler.java`에서 `userService.handleUserLogin`, JWT 생성, 쿠키 저장, 설문/메인 리다이렉트를 수행합니다.  
  `backend/user/src/main/java/com/ssafy/handam/user/infrastructure/util/CookieUtil.java`에서 `accessToken` HttpOnly 쿠키를 읽고 씁니다.  
  `backend/user/src/main/java/com/ssafy/handam/user/application/service/UserApplicationService.java`와 `.../presentation/api/UserController.java`에서 `myInfo`, `survey`, `follow`, `unfollow`, `search` 흐름이 구현돼 있습니다.  
  `backend/user/src/main/resources/application-prod.yml`에 Naver OAuth2 클라이언트, JWT secret, MySQL, Eureka 등록 설정이 존재합니다.

### C. feed 서비스의 검색/추천/캐시/이벤트 발행 결합
- `feed` 서비스는 MySQL에 원본 피드를 저장하고, 동시에 Elasticsearch 문서를 유지해 검색과 위치 기반 조회를 수행합니다.
- 좋아요 이벤트는 Kafka `like-events` 토픽으로 발행하고, 추천 API는 Redis 리스트 키(`recommended_feeds`, `top_liked_feeds`, `trending_feeds`, `random_feeds`)에서 읽어 조합합니다.
- 운영 문서 기준으로는 별도 서버의 Spark 잡이 Kafka와 Hadoop/HDFS를 거쳐 추천 데이터를 계산하고, 그 결과가 Redis 서빙 키에 반영되는 구조로 해석할 수 있습니다.
- 사용자가 좋아요한 피드 목록은 위경도 기준 DBSCAN으로 군집화해 여행 계획 생성 보조 데이터로 사용하며, 결과는 Redis에 1시간 TTL로 캐시합니다.
- 코드 근거  
  `backend/feed/build.gradle`에 JPA, Redis, Elasticsearch, Kafka, Hadoop, OpenFeign, QueryDSL, REST Docs가 함께 설정돼 있습니다.  
  `backend/feed/src/main/resources/application.yml`에 MySQL, Elasticsearch, Kafka bootstrap server, Redis, HDFS URI가 정의돼 있습니다.  
  `backend/feed/src/main/java/com/ssafy/handam/feed/application/LikeService.java`는 좋아요 이벤트를 JSON으로 만들어 Kafka `like-events` 토픽으로 전송합니다.  
  `backend/feed/src/main/java/com/ssafy/handam/feed/application/FeedService.java`는 Redis 추천 키 조합, DBSCAN 군집화, Redis TTL 캐시, Elasticsearch 검색/주변 검색을 수행합니다.  
  `backend/feed/src/main/java/com/ssafy/handam/feed/domain/repository/FeedRepositoryImpl.java`는 feed 저장 시 MySQL과 Elasticsearch를 함께 갱신하고, 검색/geo-near 조회를 Elasticsearch에 위임합니다.  
  `backend/feed/src/main/java/com/ssafy/handam/feed/infrastructure/elasticsearch/FeedDocument.java`는 `GeoPoint location`을 포함한 ES 문서 스키마를 정의합니다.  
  `Trip_Handam/exec/Spark.md`에는 별도 서버에서 `KafkaToHadoop.py`, `CosineSimilarityCalculator.py`를 `spark-submit`으로 실행한 운영 명령이 남아 있습니다.
- 왜 중요한지  
  하나의 서비스 안에서 저장소 역할을 분리하고, 검색과 추천 응답 경로를 분리한 구조를 설명할 수 있습니다.  
  포트폴리오에서는 "RDB 원본 + ES 검색 인덱스 + Redis 서빙 캐시 + Kafka 이벤트 + 별도 Spark-Hadoop 추천 파이프라인 연동" 경험으로 정리할 수 있습니다.

### D. plan 서비스의 JWT 기반 일정 관리와 계층화 구조
- `plan` 서비스는 JWT에서 사용자 ID를 추출해 사용자별 여행 계획을 생성/조회합니다.
- 코드 구조가 `application / domain / infrastructure / presentation`으로 분리돼 있고, `TotalPlan`, `DayPlan`, `Plan` 계층을 응답으로 조립합니다.
- 일정 상세 조회 시 첫 번째 장소의 주소와 이미지 URL을 요약 정보로 추출하는 등, 도메인 조회 결과를 API 응답으로 가공하는 로직이 명확합니다.
- 코드 근거  
  `backend/plan/src/main/java/com/ssafy/handam/plan/application/service/PlanApplicationService.java`에서 JWT에서 userId를 추출해 계획 생성 요청을 만들고, `TotalPlan -> DayPlan -> Plan`을 응답 DTO로 변환합니다.  
  `backend/plan/src/main/resources/application-prod.yml`에 plan 서비스의 MySQL과 JWT secret이 설정돼 있습니다.  
  `backend/plan/src/main/java/com/ssafy/handam/plan` 하위 디렉터리에 `application`, `domain`, `infrastructure`, `presentation` 계층이 분리돼 있습니다.
- 왜 중요한지  
  복잡한 일정 데이터를 단순 CRUD 테이블이 아니라 집계/응답 단위로 재구성하는 백엔드 설계 경험으로 설명할 수 있습니다.

### E. WebSocket/STOMP 기반 실시간 채팅과 메시지 영속화
- `chat` 서비스는 STOMP 기반 웹소켓 엔드포인트를 제공하고, 수신한 메시지를 채팅방 단위 토픽으로 브로드캐스트합니다.
- 메시지는 DB에 저장되며, 채팅방 목록 조회 시 상대방 정보와 최신 메시지/시각을 함께 조합합니다.
- 코드 근거  
  `backend/chat/build.gradle`에 `spring-boot-starter-websocket`, JPA, OpenFeign, REST Docs가 포함돼 있습니다.  
  `backend/chat/src/main/java/com/ssafy/handam/chat/config/WebSocketConfig.java`는 `/chat-websocket` SockJS endpoint, `/app` prefix, `/topic` broker를 설정합니다.  
  `backend/chat/src/main/java/com/ssafy/handam/chat/controller/ChatController.java`는 `@MessageMapping("/api/v1/chat/{roomId}")`, `@SendTo("/topic/chatroom/{roomId}")`로 메시지를 처리합니다.  
  `backend/chat/src/main/java/com/ssafy/handam/chat/service/ChatService.java`는 채팅방/메시지를 JPA로 저장하고, 사용자 서비스와 연동해 채팅방 목록 응답을 구성합니다.
- 왜 중요한지  
  HTTP CRUD와 별개로 실시간 양방향 통신과 영속화 흐름을 모두 설명할 수 있어 백엔드 포트폴리오에서 분리 포인트가 됩니다.

### F. photocard 서비스와 FastAPI GPU 서버의 분리형 AI 생성 파이프라인
- `photocard` 서비스는 포토카드 생성 요청을 받으면 중복 생성 여부를 먼저 검사하고, GPU 서버에 생성 요청을 보내 결과 URL을 받은 뒤 DB에 저장합니다.
- 동시에 plan 서비스에서 일정 제목을 조회해 포토카드 메타데이터를 완성합니다.
- GPU 서버는 FastAPI에서 모델을 미리 초기화하고, 이미지 캡셔닝(BLIP) → 객체 탐지(OWL-ViT) → 이미지 변환(InstructPix2Pix) → S3 업로드 순서로 결과를 생성합니다.
- 코드 근거  
  `backend/photocard/src/main/java/com/ssafy/handam/photocard/application/PhotoCardService.java`는 `existsPhotoCardByFeedId` 검사 후 `GpuApiClient`와 `PlanApiClient`를 호출합니다.  
  `backend/photocard/src/main/java/com/ssafy/handam/photocard/infrastructure/client/GpuApiClient.java`는 `gpu.service.url + "/generate"`로 JSON 요청을 보냅니다.  
  `backend/photocard/src/main/resources/application.yml`에 GPU 서버, feed 서비스, plan 서비스 URL이 명시돼 있습니다.  
  `backend/gpu-server/app/api.py`는 `/api/v1/gpu/generate` 엔드포인트를 제공하고 결과를 S3 경로로 반환합니다.  
  `backend/gpu-server/models/modelInit.py`는 BLIP, OWL-ViT, InstructPix2Pix 모델을 초기화합니다.  
  `backend/gpu-server/models/photoCardGenerator.py`는 입력 이미지를 다운로드해 캡션 생성, 객체 탐지, crop, 이미지 변환 후 파일을 저장합니다.
- 왜 중요한지  
  웹 서비스와 GPU 추론 서버를 분리해 메인 API와 무거운 모델 실행을 분리한 구조로 설명할 수 있습니다.  
  백엔드 포트폴리오에서는 "Spring 서비스가 AI 추론 서버를 orchestration하는 구조" 자체가 설명 포인트가 됩니다.

## 4. 포트폴리오용 프로젝트 소개
여행한담은 여행 추천, 일정 생성, 실시간 채팅, 포토카드 생성 기능을 도메인별로 분리한 백엔드 중심 프로젝트입니다. 저장소 기준으로 `Gateway`, `user`, `feed`, `plan`, `chat`, `photocard`, `gpu-server`가 나뉘어 있으며, 인증은 OAuth2/JWT, 피드 탐색은 Elasticsearch, 추천 결과 서빙은 Redis, 좋아요 이벤트는 Kafka, 포토카드 생성은 별도 FastAPI GPU 서버 연동으로 처리됩니다. 운영 문서 기준으로는 좋아요 이벤트를 별도 서버의 Spark-Hadoop 파이프라인으로 넘겨 추천 데이터를 계산하는 흐름도 함께 설명할 수 있습니다.

포트폴리오에서는 이 프로젝트를 "여러 종류의 백엔드 문제를 한 시스템 안에서 다룬 사례"로 소개하는 것이 적절합니다. 단일 서비스 CRUD가 아니라 중앙 인증 경계, 분리된 도메인 서비스, 검색과 추천 캐시, 실시간 메시징, AI 추론 서버 연동까지 포함되므로, 백엔드 설계와 운영 관점의 설명 재료가 충분합니다.

## 5. 포트폴리오용 내 기여/기술적 의사결정
- 저장소만으로 개인별 작성자 구분은 불가능하므로, 아래 항목은 본인이 실제 담당한 범위와 일치할 때만 사용해야 합니다.
- Gateway에서 JWT 검사를 공통 필터로 처리해 서비스별 인증 중복을 줄이고, API 진입점을 하나로 관리한 점
- `feed` 서비스에서 MySQL 원본 데이터와 Elasticsearch 검색 문서를 함께 유지하고, 검색/주변 추천 API는 ES로 분리한 점
- 추천 결과를 Redis 키 단위로 분리 저장하고, 응답 시 `recommended / top liked / trending / random`을 조합해 페이지를 구성한 점
- 좋아요 이벤트를 Kafka로 발행하고, 별도 서버에서 운영한 Spark-Hadoop 추천 파이프라인 결과를 Redis 서빙 경로와 연결한 점
- 좋아요 좌표 데이터를 DBSCAN으로 군집화해 여행 계획 작성 보조 데이터로 활용하고, 군집 결과를 TTL 캐시한 점
- 포토카드 생성 기능을 Spring 서비스와 FastAPI GPU 서버로 분리해, 웹 API 서버와 무거운 모델 추론 서버를 분리한 점

## 6. 이력서에 쓸 수 있는 bullet 초안
- Spring Cloud Gateway 기반 통합 진입점과 OAuth2/JWT 쿠키 인증 흐름을 구성해 사용자·피드·일정·채팅·포토카드 API의 접근 경계를 일원화했습니다.
- 피드 서비스에서 Kafka 좋아요 이벤트 발행, 별도 서버 Spark-Hadoop 추천 파이프라인 연동, Redis 추천 캐시 서빙, Elasticsearch 키워드·위치 검색, DBSCAN 군집화 로직을 결합해 탐색/추천 API를 구성했습니다.
- Spring Boot 포토카드 서비스와 FastAPI GPU 서버를 분리해 BLIP·OWL-ViT·InstructPix2Pix 기반 이미지 생성 파이프라인을 서비스 API로 통합했습니다.

## 7. 면접에서 강조할 기술 포인트
- Gateway가 실제로 어떤 서비스 경로를 라우팅하고, JWT 검사를 어디서 수행하는지 설명할 수 있어야 합니다.
- `feed` 서비스에서 RDB, Elasticsearch, Redis, Kafka, HDFS가 각각 어떤 역할을 하는지와, 앱 코드와 외부 Spark-Hadoop 운영 스크립트의 책임 경계를 나눠 설명할 수 있어야 합니다.
- 추천 API 중 Redis에서 읽어오는 온라인 서빙 경로와, 별도 서버 Spark 스크립트가 Redis 키를 채우는 계산 경로를 구분해서 설명해야 합니다.
- 채팅은 단순 WebSocket 연결이 아니라 STOMP destination, 토픽 브로드캐스트, 메시지 저장 흐름까지 연결해 설명하는 것이 좋습니다.
- AI 기능은 "모델을 직접 학습했다"보다 "별도 추론 서버를 분리하고 Spring 서비스가 이를 호출해 결과를 저장하는 백엔드 통합 구조를 설계했다"는 식으로 설명하는 편이 정확합니다.

## 8. 확인 필요 사항 / 과장 금지 포인트
- README에는 Eureka와 Gateway, Rate Limiting, 추천 파이프라인이 크게 강조되지만, 현재 코드 기준 Gateway는 `lb://` 기반이 아니라 고정 host:port URI를 사용합니다.
- Eureka 서버는 존재하고 `user` 프로덕션 설정의 등록도 확인되지만, 다른 서비스들의 Eureka 등록 여부는 이번 확인 범위에서 명확히 보이지 않았습니다.
- README의 "Gateway token bucket rate limiting"은 실제 Gateway 설정/필터 코드에서 확인하지 못했습니다.
- `feed`의 `getRecommendedFeedsForUser(...)`는 하드코딩된 샘플 `FeedPreviewDto`를 반환하고 있어, 사용자 맞춤 추천의 핵심 구현으로 보기 어렵습니다. 실제 Redis 기반 추천 조합은 별도 `getRecommendedFeeds(token, page, pageSize)` 경로에 있습니다.
- Spark/Hadoop 연동은 서비스 아키텍처와 운영 문서로는 설명 가능하지만, 관련 Python 잡 스크립트는 현재 저장소에 포함돼 있지 않아 저장소 단독 기준으로 전체 구현을 재현하기는 어렵습니다.
- `feed`의 `DataMigrationRunner`는 실행 메서드와 스케줄링이 주석 처리돼 있어, 현재 스냅샷 기준 Elasticsearch 전체 동기화 자동 실행은 비활성 상태입니다.
- `feed`의 `FeedQueryDSLRepository`는 클래스만 있고 실제 쿼리 구현은 비어 있습니다.
- `chat`은 WebSocket 구현은 명확하지만, 확인된 설정 파일은 H2 메모리 DB 기준이며 별도 프로덕션 DB 설정은 이번 확인 범위에서 보지 못했습니다.
- `backend/user` 루트에는 `Dockerfile`, `Jenkinsfile`만 있고 `build.gradle`, `gradlew`는 저장소에서 확인되지 않습니다. 반면 Jenkinsfile은 `./gradlew build`를 실행하도록 되어 있어, 저장소 스냅샷과 CI 스크립트 사이에 불일치가 있습니다.
- 일부 설정과 배치 스크립트에 DB 접속 정보와 운영 주소가 직접 포함돼 있어, 운영 보안 관점에서는 별도 정리가 필요합니다.

## 9. 참고한 핵심 파일
- `/Users/yujaegwang/Documents/projects/Trip_Handam/ReadMe.md`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/SCG/build.gradle`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/SCG/src/main/resources/application-prod.yml`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/SCG/src/main/java/com/ssafy/handam/scg/filters/JwtAuthFilter.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/SCG/Jenkinsfile`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/eureka/src/main/java/com/ssafy/handam/eureka/EurekaApplication.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/eureka/src/main/java/com/ssafy/handam/eureka/security/SecurityConfig.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/user/src/main/resources/application-prod.yml`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/user/src/main/java/com/ssafy/handam/user/infrastructure/security/SecurityConfig.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/user/src/main/java/com/ssafy/handam/user/infrastructure/oauth/OAuth2LoginSuccessHandler.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/user/src/main/java/com/ssafy/handam/user/application/service/UserApplicationService.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/user/src/main/java/com/ssafy/handam/user/presentation/api/UserController.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/user/Jenkinsfile`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/build.gradle`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/src/main/resources/application.yml`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/src/main/java/com/ssafy/handam/feed/application/FeedService.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/src/main/java/com/ssafy/handam/feed/application/LikeService.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/src/main/java/com/ssafy/handam/feed/domain/repository/FeedRepositoryImpl.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/src/main/java/com/ssafy/handam/feed/infrastructure/elasticsearch/FeedDocument.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/feed/src/main/java/com/ssafy/handam/feed/infrastructure/DataMigration/DataMigrationRunner.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/plan/src/main/java/com/ssafy/handam/plan/application/service/PlanApplicationService.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/chat/src/main/java/com/ssafy/handam/chat/config/WebSocketConfig.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/chat/src/main/java/com/ssafy/handam/chat/controller/ChatController.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/chat/src/main/java/com/ssafy/handam/chat/service/ChatService.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/photocard/src/main/java/com/ssafy/handam/photocard/application/PhotoCardService.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/photocard/src/main/java/com/ssafy/handam/photocard/infrastructure/client/GpuApiClient.java`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/photocard/src/main/resources/application.yml`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/gpu-server/app/api.py`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/gpu-server/models/modelInit.py`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/backend/gpu-server/models/photoCardGenerator.py`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/exec/Spark.md`
- `/Users/yujaegwang/Documents/projects/Trip_Handam/exec/Hadoop.md`
