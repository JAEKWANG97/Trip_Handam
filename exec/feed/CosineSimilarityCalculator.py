from pyspark.sql import SparkSession
from pyspark.sql.functions import col
from pyspark.sql.types import StructType, LongType, StringType
from sklearn.metrics.pairwise import cosine_similarity
from pyspark.sql.functions import col, from_json, to_timestamp, current_timestamp, datediff,collect_list
from datetime import datetime
import pymysql
import numpy as np
import redis
import json

# Redis 클라이언트 생성
redis_client = redis.StrictRedis(host='j11c205a.p.ssafy.io', port=6379, db=0)

def get_mysql_connection():
    return pymysql.connect(
        host="3.38.92.229",
        user="handam",
        password="ssafy",
        db="feed_db",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor
    )

# 1. Spark 세션 시작 (하둡에서 데이터 처리)
spark = SparkSession.builder \
    .appName("HadoopFeedRecommendations") \
    .getOrCreate()

# 2. 좋아요 데이터를 Hadoop에서 읽어오기
raw_likes_df = spark.read \
    .option("header", "true") \
    .csv("hdfs:///user/likes_data/part-00000*.csv")

# 2. JSON 스키마 정의 (value 필드를 파싱하기 위한 스키마)
like_schema = StructType() \
    .add("user_id", LongType()) \
    .add("feed_id", LongType()) \
    .add("travel_type", StringType()) \
    .add("timestamp", StringType())

# 3. value 열을 JSON으로 파싱 (from_json 함수 사용)
parsed_likes_df = raw_likes_df.withColumn("parsed_value", from_json(col("value"), like_schema))

# 4. 필요한 필드 추출
likes_df = parsed_likes_df.select("parsed_value.*")

likes_df = likes_df.withColumn("timestamp", to_timestamp(col("timestamp"), "yyyy-MM-dd'T'HH:mm:ss"))

# 3. 시간 가중치 계산 함수
def calculate_time_weight(timestamp):

    current_time = datetime.now()
    like_time = timestamp
    time_diff = (current_time - like_time).total_seconds() / (60 * 60 * 24)  # 일 단위 차이 계산
    return np.exp(-time_diff / 30)  # 최근일수록 1에 가깝고, 오래될수록 0에 가까워지도록 설정

# 4. 사용자 유형별 가중치 계산 함수
def calculate_weight_based_on_type(user_type, other_user_type):
    match_count = 0
    max_weight = 4  # 네 가지 속성이 모두 일치하면 최고 가중치는 4
    attributes_user = list(user_type)
    attributes_other = list(other_user_type)

    for attr_user, attr_other in zip(attributes_user, attributes_other):
        if attr_user == attr_other:
            match_count += 1

    # 가중치를 계산 (0.2, 0.4, 0.6, 0.8, 1.0으로 설정)
    if match_count == 0:
        return 0.2  # 모두 다를 때
    elif match_count == 1:
        return 0.4
    elif match_count == 2:
        return 0.6
    elif match_count == 3:
        return 0.8
    elif match_count == 4:
        return 1.0  # 모두 같을 때

    return 0 # 나올수없는값 이므로 신경 x

# 5. 코사인 유사도 계산 함수 (시간과 속성 가중치 반영)
def calculate_cosine_similarity_with_weight(user_likes, other_user_likes, time_weights, travel_type_weight):
    print(f"user_likes: {user_likes}")
    print(f"other_user_likes: {other_user_likes}")

    if len(user_likes) == 0 or len(other_user_likes) == 0:
        return 0

    # 두 피드 목록을 합쳐서 유니크한 피드 ID 리스트 생성
    all_feed_ids = list(set(user_likes + other_user_likes))

    # 비교군 사용자의 여행 성향 가중치 및 현재 사용자의 시간 가중치 반영하여 벡터 생성
    other_vector = [
        (travel_type_weight ** 1.5) if feed_id in other_user_likes else 0
        for feed_id in all_feed_ids
    ]

    # 현재 사용자의 시간 가중치를 반영한 가중치 벡터 생성
    user_vector_weighted = [
        (time_weights.get(feed_id, 1) ** 1.5) if feed_id in user_likes else 0
        for feed_id in all_feed_ids
    ]

    print(f"user_vector_weighted: {user_vector_weighted}")
    print(f"other_vector: {other_vector}")

    # 코사인 유사도 계산
    similarity_score = cosine_similarity([user_vector_weighted], [other_vector])[0][0]
    print(f"similarity_score: {similarity_score}")

    return similarity_score


# 6. 좋아요 목록과 시간 가중치를 가져오는 함수
def get_user_likes_and_time_weights(user_id, likes_df):
    user_likes_df = likes_df.filter(col("user_id") == user_id)
    user_likes = user_likes_df.select("feed_id").rdd.flatMap(lambda x: x).collect()

    print(f"User ID {user_id} : {user_likes}에 대한 필터링된 좋아요 데이터:")
    # 시간 가중치 계산
    time_weights = {}
    for row in user_likes_df.collect():
        feed_id = row["feed_id"]
        timestamp = row["timestamp"]
        print(f"{timestamp}")
        time_weights[feed_id] = calculate_time_weight(timestamp)

    return user_likes, time_weights

# 7. 전체 좋아요 많은 피드 (상위 20%)
def get_top_liked_feeds(likes_df):
    feed_likes_df = likes_df.groupBy("feed_id").count().orderBy(col("count").desc())
    return feed_likes_df.select("feed_id").rdd.flatMap(lambda x: x).collect()

# 8. 트렌딩 피드 (상위 10%)
def get_trending_feeds(likes_df):
    # 최근 좋아요 많은 피드를 기준으로 추출 (최근 7일)
    trending_likes_df = likes_df.filter(datediff(current_timestamp(), col("timestamp")) < 7)

    # 피드별 좋아요 개수 집계 후 내림차순 정렬
    feed_trending_df = trending_likes_df.groupBy("feed_id").count().orderBy(col("count").desc())

    return feed_trending_df.select("feed_id").rdd.flatMap(lambda x: x).collect()
# 9. MySQL에서 랜덤 피드 가져오기
def get_mysql_feeds(connection):
    with connection.cursor() as cursor:
        # 랜덤 피드
        cursor.execute("SELECT id FROM feed ORDER BY RAND() LIMIT 50")
        random_feeds = [row["id"] for row in cursor.fetchall()]

    return random_feeds

# 10. 피드를 Redis에 카테고리별로 저장하는 함수 (카테고리별로 전체 저장만 함)
def save_recommendations_to_redis(user_id, recommended_feeds, top_liked_feeds, trending_feeds, random_feeds):
    recommended_feed_ids = list(set([feed_id for feed_info in recommended_feeds for feed_id in feed_info[2]]))
    top_liked_feeds = list(set(top_liked_feeds))
    trending_feeds = list(set(trending_feeds))
    random_feeds = list(set(random_feeds))

    if recommended_feed_ids:
        redis_client.delete(f"user:{user_id}:recommended_feeds")
        redis_client.lpush(f"user:{user_id}:recommended_feeds", *recommended_feed_ids)
    if top_liked_feeds:
        redis_client.delete(f"user:{user_id}:top_liked_feeds")
        redis_client.lpush(f"user:{user_id}:top_liked_feeds", *top_liked_feeds)
    if trending_feeds:
        redis_client.delete(f"user:{user_id}:trending_feeds")
        redis_client.lpush(f"user:{user_id}:trending_feeds", *trending_feeds)
    if random_feeds:
        redis_client.delete(f"user:{user_id}:random_feeds")
        redis_client.lpush(f"user:{user_id}:random_feeds", *random_feeds)

    # 확인용 출력
    print(f"{user_id} recommended_feeds: {recommended_feed_ids}")
    print(f"top_liked_feeds: {top_liked_feeds}")
    print(f"trending_feeds: {trending_feeds}")
    print(f"random_feeds: {random_feeds}")
# 11. 추천 피드 생성 로직 (Kafka에서 받은 사용자만 대상으로 처리)
def recommend_feeds_for_user(user_id, user_type, likes_df, all_users_likes_df, connection):
    user_likes, user_time_weights = get_user_likes_and_time_weights(user_id, likes_df)

    recommended_feeds = []
    other_users_likes_df = all_users_likes_df.filter(col("user_id") != user_id)

    # 다른 사용자들의 user_id별로 좋아요한 feed_id를 리스트로 수집
    other_users_grouped_likes = other_users_likes_df.groupBy("user_id", "travel_type").agg(
        collect_list("feed_id").alias("feed_ids")  # feed_id를 리스트로 묶음
    )

    # 다른 사용자들의 좋아요한 feed_id 리스트를 순회하면서 유사도 계산
    for row in other_users_grouped_likes.collect():
        other_user_id = row["user_id"]
        other_user_type = row["travel_type"]
        other_user_likes = row["feed_ids"]  # 다른 사용자의 전체 feed_id 리스트

        # 여행 스타일 가중치 계산 (비교군 사용자에게만 적용)
        travel_type_weight = calculate_weight_based_on_type(user_type, other_user_type)

        other_user_likes_filtered = [feed_id for feed_id in other_user_likes if feed_id not in user_likes]

        if len(other_user_likes_filtered) == 0:
            continue  # 추천할 피드가 없으면 건너뜀

        # 시간 가중치는 현재 사용자에게만, 여행 성향 가중치는 비교군 사용자에게만 적용한 코사인 유사도 계산
        similarity_score = calculate_cosine_similarity_with_weight(
            user_likes, other_user_likes, user_time_weights, travel_type_weight
        )

        # 유사도가 0보다 크면 추천 피드 목록에 추가
        if similarity_score > 0:
            recommended_feeds.append((similarity_score, other_user_id, other_user_likes))

    recommended_feeds.sort(reverse=True)
    print(f"User ID {user_id}에게 {len(recommended_feeds)}개의 피드를 추천합니다.")

    # 하둡에서 가져올 피드
    top_liked_feeds = get_top_liked_feeds(likes_df)
    trending_feeds = get_trending_feeds(likes_df)

    # MySQL에서 가져올 피드
    random_feeds = get_mysql_feeds(connection)

    # 피드를 카테고리별로 Redis에 저장 (조합 없이 카테고리별 저장)
    save_recommendations_to_redis(user_id, recommended_feeds, top_liked_feeds, trending_feeds, random_feeds)

def process_kafka_likes(kafka_messages):
    # travel_type별로 사용자 ID를 저장
    travel_type_map = {}

    for message in kafka_messages:
        user_id = message['user_id']
        travel_type = message['travel_type']

        if travel_type not in travel_type_map:
            travel_type_map[travel_type] = set()

        travel_type_map[travel_type].add(user_id)

    return travel_type_map

# 12. Kafka에서 받은 모든 메시지를 처리하여 추천 피드를 생성
def process_kafka_messages(messages, likes_df, connection):
    try:
        all_users_likes_df = likes_df
        travel_type_map = process_kafka_likes(messages)

        for travel_type, user_ids in travel_type_map.items():
            for user_id in user_ids:
                user_type = travel_type
                recommend_feeds_for_user(user_id, user_type, likes_df, all_users_likes_df, connection)

    except Exception as e:
        print(f"Error processing Kafka messages: {e}")

# Kafka에서 데이터를 읽어오기
kafka_servers = "localhost:9092"
kafka_topic = "like-events"

# Kafka에서 메시지를 읽어오는 로직 추가
kafka_df = spark.read \
    .format("kafka") \
    .option("kafka.bootstrap.servers", kafka_servers) \
    .option("subscribe", kafka_topic) \
    .load()

# Kafka 메시지를 JSON 형식으로 변환
parsed_df = kafka_df.selectExpr("CAST(value AS STRING)")
# JSON 포맷에 맞게 데이터를 추출하여 처리
schema = StructType() \
    .add("user_id", LongType()) \
    .add("feed_id", LongType()) \
    .add("travel_type", StringType()) \
    .add("timestamp", StringType())

# JSON 데이터를 가져와 파싱
parsed_messages = parsed_df.withColumn("json_data", from_json(col("value"), schema))

# 메시지 수집 및 처리
kafka_messages = parsed_messages.select("json_data.*").collect()  # json_data 내부의 필드 추출
kafka_messages = [row.asDict() for row in kafka_messages]

# MySQL 연결
connection = get_mysql_connection()

# Kafka 메시지를 처리
process_kafka_messages(kafka_messages, likes_df, connection)
# MySQL 연결 종료
connection.close()

# Spark 세션 종료
spark.stop()
