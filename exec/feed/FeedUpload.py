import pandas as pd
import os
import pymysql
from datetime import datetime

# MySQL 연결 설정 (feed_db)
feed_db_connection = pymysql.connect(
    host='3.38.92.229',
    port=3306,
    user='handam',
    password='ssafy',
    db='feed_db',
    charset='utf8mb4',
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=60000,
    read_timeout=60000,
    write_timeout=60000
)

# 사용자 데이터베이스 연결 설정 (user_database)
user_db_connection = pymysql.connect(
    host='43.203.193.46',
    port=3306,
    user='handam',
    password='ssafy',
    db='user_database',
    charset='utf8mb4',
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=60000,
    read_timeout=60000,
    write_timeout=60000
)

# CSV 파일 경로 및 로컬 이미지 경로
csv_file_path = '/home/ubuntu/matched_travel_data (3).csv'
image_directory = '/home/ubuntu/TS_photo'
image_base_url = 'http://j11c205a.p.ssafy.io/images/'  # 이미지 베이스 URL

# CSV 파일을 읽어서 데이터프레임으로 변환
df = pd.read_csv(csv_file_path)

# 총 행(row) 수 계산
total_rows = len(df)

# NaN 값을 None으로 변환하는 함수
def nan_to_none(value):
    if pd.isna(value):
        return None
    return value

# VISIT_AREA_TYPE_CD에 따른 place_type 매핑 함수
def map_place_type(code):
    tourist_attraction_codes = [1,2,3,4,5,6,7,8,13]
    restaurant_cafe_code = [11]
    accommodation_code = [24]

    if code in tourist_attraction_codes:
        return "TOURIST_ATTRACTION"  # 관광지
    elif code in restaurant_cafe_code:
        return "RESTAURANT"  # 음식점 및 카페
    elif code in accommodation_code:
        return "ACCOMMODATION"  # 숙박
    else:
        return "ETC"  # 기타

# MySQL에 데이터 삽입
try:
    with feed_db_connection.cursor() as feed_cursor, user_db_connection.cursor() as user_cursor:
        for index, row in df.iterrows():

            print(f"Processing row {index + 1}/{total_rows}...")  # 현재 처리 중인 행의 번호와 전체 행의 수 출력

            # TRAVELER_ID를 기준으로 user DB에서 nickname 매칭 후 user_id 가져오기
            traveler_id = row['TRAVELER_ID']
            user_cursor.execute("SELECT id FROM user WHERE name = %s", (traveler_id,))
            user_data = user_cursor.fetchone()

            if user_data is None:
                print(f"User with nickname {traveler_id} not found in user DB.")
                continue

            user_id = user_data['id']

            # 이미지 파일 매칭
            photo_file_name = str(row['PHOTO_FILE_ID']) + ".webp"
            local_image_path = os.path.join(image_directory, photo_file_name)

            if not os.path.exists(local_image_path):
                print(f"Image not found for {local_image_path}, skipping...")
                continue  # 이미지가 없으면 다음 행으로 넘어감

            # URL 경로로 img_url 설정
            img_url = f"{image_base_url}{photo_file_name}"
            current_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

            # VISIT_AREA_TYPE_CD를 기반으로 place_type 결정
            visit_area_type_cd = nan_to_none(row['VISIT_AREA_TYPE_CD'])
            if visit_area_type_cd is not None:
                place_type = map_place_type(int(visit_area_type_cd))
            else:
                place_type = "ETC"

            # feed 테이블에 데이터 삽입
            insert_feed_query = """
                INSERT INTO feed (user_id, title, address1, address2, image_url, like_count, latitude, longitude, place_type, content )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """
            feed_cursor.execute(insert_feed_query, (
                user_id,
                nan_to_none(row['VISIT_AREA_NM']),
                nan_to_none(row['ROAD_NM_ADDR']),
                nan_to_none(row['LOTNO_ADDR']),
                img_url,  # img_url에 이미지 경로 저장
                0,  # 좋아요 초기값
                nan_to_none(row['PHOTO_FILE_Y_COORD']),  # 위도 (latitude)
                nan_to_none(row['PHOTO_FILE_X_COORD']),  # 경도 (longitude)
                place_type,
                "",  # content는 빈 문자열로 처리
            ))

        feed_db_connection.commit()
        print(f"First 100 rows successfully inserted into MySQL.")
finally:
    feed_db_connection.close()
    user_db_connection.close()
