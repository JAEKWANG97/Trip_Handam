# 🍳 여행한담
<img src="https://github.com/sangmin0806/Trip_Handam/blob/main/exec/images/메인페이지.png?raw=true" width="800" height="400">

## 📢 서비스 소개

 **"여행한담"** 은 여행일정을 짜는데 도움을 주는 웹서비스입니다. 여행계획을 구성할 때, 정보의 과부화, 시간 소모 등 사용자의 피로를 덜어주기 위해 개발되었습니다. 가벼운 성향테스트를 통해 내 여행 성향을 파악할 수 있고, 다른 사용자의 피드 게시물을 보고 좋아요와 댓글 등의 소통을 할 수 있는 SNS 기능을 겸하고 있습니다. 좋아요를 누른 피드 데이터를 기반으로 사용하기 쉬운 여행 계획 수립 및 자동맞춤 여행일정 수립 기능을 보유하고 있습니다.
 
 [📎 서비스 소개 UCC](https://youtube.com/shorts/xfRjP9bi9n4)
 
### 진행 기간

- 2024.08.26 ~ 2024.10.11 (7주)

### 팀 구성


| 고도연 | 강동형 | 고충원 | 김민주 | 유재광 | 이상민 |
| :--------------------------------------------------------------: | :--------------------------------------------------------------: | :--------------------------------------------------------------: | :--------------------------------------------------------------: | :-------------------------------------------------------------: | :-------------------------------------------------------------: |
| <img src="https://avatars.githubusercontent.com/u/156388715?v=4" width="100" height="100"> | <img src="https://avatars.githubusercontent.com/u/156388917?v=4" width="100" height="100"> | <img src="https://avatars.githubusercontent.com/u/156388848?v=4" width="100" height="100"> | <img src="https://avatars.githubusercontent.com/u/87603324?v=4" width="100" height="100"> | <img src="https://avatars.githubusercontent.com/u/65598179?v=4" width="100" height="100"> | <img src="https://avatars.githubusercontent.com/u/134148399?v=4" width="100" height="100"> |
| PM, FE | FE | FE | BE, Infra, AI | BE | BE |



## 🥳 서비스 설계

### 기술 스택

|               | Front                                   | Back                                     | AI                       |
| ------------- | --------------------------------------- | ---------------------------------------- | ------------------------ |
| **Language**  |    JavaScript(ES6+), TypeScript        |            Java17                         |          python          |
| **IDE**       |       Visual Studio Code             |             IntelliJ                        |        Jupyter Lab       |
| **Framework** |         React, Vite                   | Spring Boot, Fast API, Spark | Pytorch, fastAPI               |
| **Library**   | intersection-observer, daum-postcode, sweetalert2, tailwind, storybook    ||  transformers, diffusers, boto |   

| DB           |               CI/CD              |        Tools         |
| :----------- |  :-----------------------------: | :------------------: |
| MySQL, Hadoop, Redis, Elasticsearch | Jenkins, Docker, Dockerhub, Mattermost | GitLab, Jira, Notion, RestDoc |

### ERD

<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2FbiTDvs%2FbtsJ2uIzU9k%2F1pkZZWV6hMqXEmzdyayx30%2Fimg.png" width="600" height="300" />

### Wireframe

[📎 Figma Link](https://www.figma.com/design/CCGP6nyPBqx6LpUE4uEP3M/%ED%94%8C%EB%A1%9C%EC%9A%B0%EC%B0%A8%ED%8A%B8?node-id=19-2&node-type=canvas&t=spQ9HQoCBojIyKx0-0)

<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2FvIZvK%2FbtsJ0et1JBG%2Fb7MOijr6SrEI9Y6DaysykK%2Fimg.png" width="600" height="300" />


### Architecture

<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2FbNFwpM%2FbtsJ17Aae4V%2FCKp290W72UZNlrfqi5jxH1%2Fimg.png" width="700" height="300" />



## 🤗 기능 소개
### 여행 성향 진단

<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2Fb7HPhB%2FbtsJ102mgX5%2F29tjb6ZAS1DwrMWpfPnFA1%2Fimg.png" width="300" height="500"> |
<img src="https://github.com/sangmin0806/Trip_Handam/blob/main/exec/images/여행설문.png?raw=true" width="300" height="500">
<br>
<ul>
<li>
  간단한 진단 문항을 통해 사용자의 성향을 진단하고 진단 결과에 따라 16가지 결과물을 사용자에게 보여줍니다. <li>진단 과정에서 사용자에게 입력을 받아 사용자의 좋아요 데이터 기본값을 설정하도록 합니다.
</ul>

### 맞춤 및 지역별 여행지 추천정보
<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2F7gg2O%2FbtsJ1GC5sNQ%2FWjdcKNW11XxsX3G3UXXhc1%2Fimg.png" width="500" height="250">
<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2F9xjc2%2FbtsJ2mqqAbH%2FZQ0RFiaJw3UCc8PHpBs8g1%2Fimg.png" width="500" height="250">
<li>맞춤여행지들이 추천되고, 핫플여행지, 지역별 여행지, 축제 정보등을 표시합니다.
</li>

### 개인 여행 유형별, 좋아요 한 피드별 맞춤 추천
<img src="https://github.com/sangmin0806/Trip_Handam/blob/main/exec/images/탐색피드.png?raw=true" width="500" height="250">

<ul>
  <li>탐색페이지에서 자신의 여행유형과 좋아요한 피드들을 기준으로 여행지가 추천됩니다.
</ul>

### 다른 사용자의 피드 검색 기능
<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2FpUasD%2FbtsJ3a4g4i6%2FNJKj48q16olfBKvwD2OQ01%2Fimg.png" width="500" height="250"> | <img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2F9rBwM%2FbtsJ178slZS%2FTDDXJf7eQcQBg8TdAFL5a1%2Fimg.png" width="400" height="150">
<br>
<ul>
  <li>이 탐색페이지는 자신과 비슷한 유형의 사용자가 좋아요한 피드위주로 추천됩니다.
  <li>사용자 검색 후 팔로우 기능을 할 수 있고, 피드검색도 가능합니다.
</ul>

### 사용자의 데이터 기반 여행계획 작성기능
<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2F1N8ax%2FbtsJ1e8i8M9%2FbjOt08UVFRbRPyxnV276U0%2Fimg.png" width="500" height="250"> | <img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2Fbgy4tb%2FbtsJ2QSJUuN%2FSyKPzZWvz0OK1kb2b0dtQ0%2Fimg.png" width="500" height="250">
<ul>
  <li>자신이 좋아요 한 데이터들은 여행 계획 페이지에서 클러스터링을 거쳐 군집화됩니다.
  <li>군집화 된 목록을 누르면 해당좋아요를 포함해서 자동으로 일정을 생성합니다.
  <li>장소를 검색하여 일정에 가고싶은 곳을 추가하여 수정할 수 있습니다.
  <li>하단 바에서 군집 중심에서 주변 장소들을 추천해줍니다.
  <li>여행일정을 저장되고 일정 상세페이지를 확인할 수 있습니다.
</ul>

### 동행 구인
<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2FbHhLb0%2FbtsJ2Xxt6CB%2FSY1sIeZoZwG94EjzRDj5kk%2Fimg.png" width="500" height="250">
<ul>
  <li>동행 구인글들을 확인할 수 있고 제 일정을 공유하면 동행 구인글을 작성할 수 있습니다.
  <li>게시된 게시물을 클릭하면 다른 사용자의 여행일정이 보입니다.
</ul>

### 1대1 채팅방
<img src="https://github.com/sangmin0806/Trip_Handam/blob/main/exec/images/채팅방.png?raw=true" width="500" height="250">
<ul>
  <li> 내 팔로잉 목록의 사용자들과 1대1 채팅을 시작할 수 있습니다.
</ul>

### 마이페이지
<img src="https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdn%2FcwulPj%2FbtsJ28ZOO5I%2FdV0wH2AFcUdcZKSg1Xfc3k%2Fimg.png" width="500" height="320">
<li>
마이페이지에는 제가 작성한 피드, 좋아요목록 동행 게시글, 포토카드등을 확인할 수 있습니다.
</li>

## 🚩핵심 기능

<ol>
  <h4><li>Hadoop, Spark, Redis를 통한 주기적인 피드 추천 적용</h4>
    <ul>
      <li>배치 처리: Cron을 활용하여 2분마다 배치 처리로 유사도 계산을 실행합니다.</li>
      <li>협업 필터링: 사용자 기반 협업 필터링을 적용하며, 좋아요 이벤트에 시간 가중치와 여행 유형별 특성을 반영하여 추천 품질을 향상시켰습니다.</li>
      <li>추천 캐싱: 추천된 피드는 Redis에 캐싱되어, 빠른 응답성을 위해 TTL을 적용하고 있습니다.</li>
    </ul>
  </li>
  <h4><li>Kafka를 통한 최근 활동 사용자 수집</h4>
    <ul>
      <li>추천 최적화: 신규 좋아요 이벤트에 영향을 받는 사용자만을 추출하여 유사도 계산을 수행합니다. 불필요한 계산을 줄이고 효율적인 추천 기능을 제공합니다.</li>
    </ul>
  </li>
  <h4><li>WebSocket을 활용한 실시간 채팅</h4>
    <ul>
      <li>Stomp 프로토콜을 사용하여 안정적이고 실시간성 높은 채팅 서비스를 구현했습니다.</li>
    </ul>
  </li>
  <h4><li>ElasticSearch를 활용한 빠른 피드 검색</h4>
    <ul>
      <li>거리 기반 검색: ElasticSearch를 활용해 거리 기반의 가까운 피드를 빠르게 검색할 수 있습니다.</li>
      <li>데이터 동기화: Partial Update를 통해 DB와 ElasticSearch 간의 데이터 동기화를 유지하고, 서버 실행 시 전체 데이터 동기화를 수행합니다.</li>
    </ul>
  </li>
  <h4><li>동시성 제어</h4>
    <ul>
      <li>동시 요청 처리 개선: 동시 요청자 100명 기준으로, 요청 실패율을 28%에서 0%로 감소시키는 데 성공했습니다.</li>
      <li>부하 테스트: Locust를 사용하여 서버 부하 테스트를 수행하고 최적화를 진행했습니다.</li>
    </ul>
  </li>
  <h4><li>포토카드 생성</h4>
    <ul>
      <li>BLIP image captioning, OWL-ViT zero-shot object detection, Pix2Pix image to image translation 모델을 활용해 사용자들이 올린 피드 이미지로 고유한 포토카드를 생성할 수 있도록 기능을 구현했습니다.</li>
    </ul>
  </li>
  <h4><li>MSA 구조의 CI/CD 파이프라인 구축</h4>
    <ul>
      <li>독립적 파이프라인: 각 서버별로 독립적인 CI/CD 파이프라인을 구축하여 개발과 배포의 효율성을 극대화했습니다.</li>
      <li>자동 알림: 배포 성공 시 MM 알람을 통해 팀원들에게 자동으로 공지하여 생산성을 향상시켰습니다.</li>
    </ul>
  </li>
  <h4><li>네트워크 처리 요청 장치 적용</h4>
    <ul>
      <li>토큰 버킷 알고리즘: Spring Cloud Gateway에 토큰 버킷 알고리즘을 적용하여 네트워크 요청 처리를 효율적으로 관리하고 있습니다.</li>
    </ul>
  </li>
  <h4><li>실시간 좋아요 데이터 스트리밍 파이프라인 설계</h4>
    <ul>
      <li>Spark Streaming을 활용해 실시간으로 좋아요 데이터를 스트리밍 처리하여 최신 데이터를 반영합니다.</li>
    </ul>
  </li>
  <h4><li>군집화 알고리즘 적용</h4>
    <ul>
      <li>DBSCAN 알고리즘을 사용해 사용자 좋아요 데이터를 군집화하여, 비슷한 성향의 사용자를 묶고 그에 맞춘 피드 추천을 제공합니다.</li>
      <li>데이터 캐싱: 캐시 미스와 히트 개념을 도입하여 데이터 조회 속도를 높였습니다.</li>
    </ul>
  </li>
</ol>
