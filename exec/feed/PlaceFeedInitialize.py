import pandas as pd
import pymysql.cursors
import os
import subprocess
import logging

# PySpark의 경고 로그 레벨을 ERROR로 설정
os.environ["HADOOP_ROOT_LOGGER"] = "ERROR,console"

# 첫 번째 MySQL (user 테이블이 있는 MySQL 서버)
user_db_connection = pymysql.connect(
    host='43.203.193.46',
    port=3306,
    user='handam',
    password='ssafy',
    db='user_database',
    charset='utf8mb4',
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=60000,  # 연결 타임아웃 600초로 설정 (10분)
    read_timeout=60000,  # 읽기 타임아웃 600초로 설정
    write_timeout=60000
)

# 두 번째 MySQL (feed와 place 데이터베이스가 있는 MySQL 서버)
mysql_connection = pymysql.connect(
    host='3.38.92.229',
    port=3306,
    user='handam',
    password='ssafy',
    db='feed_db',
    charset='utf8mb4',
    cursorclass=pymysql.cursors.DictCursor,
    connect_timeout=60000,  # 연결 타임아웃 600초로 설정 (10분)
    read_timeout=60000,  # 읽기 타임아웃 600초로 설정
    write_timeout=60000
)

# EC2에 저장된 이미지 경로
image_directory = '/home/ubuntu/VS_photo/VS_photo'

# HDFS 경로
hdfs_directory = '/images/'

# CSV 파일 경로
travel_csv_path = '/home/ubuntu/tn_travel_여행_E.csv'
visit_csv_path = '/home/ubuntu/tn_visit_area_info_방문지정보_E.csv'
photo_csv_path = '/home/ubuntu/tn_tour_photo_관광사진_E.csv'
processed_travel_data_path = '/home/ubuntu/processed_user_data.csv'

# 데이터프레임으로 CSV 파일 읽기
travel_df = pd.read_csv(travel_csv_path)
visit_df = pd.read_csv(visit_csv_path)
photo_df = pd.read_csv(photo_csv_path)
processed_travel_df = pd.read_csv(processed_travel_data_path)
total_rows = len(processed_travel_df)

# NaN 값을 None으로 변환하는 함수
def nan_to_none(value):
    if pd.isna(value):
        return None
    return value


# HDFS에 파일이 있는지 확인하는 함수
def is_file_in_hdfs(hdfs_path):
    try:
        command = f"hdfs dfs -test -e {hdfs_path}"
        process = subprocess.run(command, shell=True, check=True)
        return process.returncode == 0  # 파일이 있으면 0을 반환
    except subprocess.CalledProcessError:
        return False


# HDFS에 파일을 업로드하는 함수
def upload_file_to_hdfs(local_path, hdfs_path):
    try:
        command = f"hdfs dfs -copyFromLocal -f {local_path} {hdfs_path}"
        process = subprocess.run(command, shell=True, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        print(f"Successfully uploaded {local_path} to HDFS at {hdfs_path}")
    except subprocess.CalledProcessError as e:
        print(f"Failed to upload {local_path} to HDFS: {e.stderr.decode('utf-8')}")


try:
    with mysql_connection.cursor() as cursor:
        for index, processed_row in processed_travel_df.iterrows():
            print(f"Processing {index + 1}/{total_rows} rows")
            traveler_id = processed_row['TRAVELER_ID']

            # TRAVELER_ID와 TRAVEL_ID 매핑
            travel_row = travel_df[travel_df['TRAVELER_ID'] == traveler_id]
            if travel_row.empty:
                print(f"No matching travel data for traveler_id: {traveler_id}")
                continue
            travel_id = travel_row.iloc[0]['TRAVEL_ID']

            # TRAVEL_ID와 VISIT_AREA_ID 매핑
            visit_rows = visit_df[visit_df['TRAVEL_ID'] == travel_id]
            if visit_rows.empty:
                print(f"No visit area data found for travel_id: {travel_id}")
                continue

            for _, visit_row in visit_rows.iterrows():
                visit_area_type_cd = visit_row['VISIT_AREA_TYPE_CD']

                # VISIT_AREA_TYPE_CD가 21, 22, 9일 경우 스킵
                if visit_area_type_cd in [21, 22, 9]:
                    print(f"Skipping VISIT_AREA_TYPE_CD: {visit_area_type_cd} for travel_id: {travel_id}")
                    continue

                visit_area_id = visit_row['VISIT_AREA_ID']

                # VISIT_AREA_ID로 사진 정보 가져오기
                photo_row = photo_df[photo_df['VISIT_AREA_ID'] == visit_area_id]
                if photo_row.empty:
                    continue

                # 사진 파일 처리 및 HDFS 업로드
                photo_file_name = photo_row.iloc[0]['PHOTO_FILE_ID'] + ".jpg"
                local_image_path = os.path.join(image_directory, photo_file_name)
                hdfs_image_path = os.path.join(hdfs_directory, photo_file_name)

                if not is_file_in_hdfs(hdfs_image_path):
                    if os.path.exists(local_image_path):
                        upload_file_to_hdfs(local_image_path, hdfs_image_path)
                    else:
                        print(f"Image not found for {local_image_path}, skipping...")
                        continue

                # user_id 가져오기
                with user_db_connection.cursor() as user_cursor:
                    user_cursor.execute("SELECT id FROM user WHERE nickname = %s", (traveler_id,))
                    user_data = user_cursor.fetchone()
                    if not user_data:
                        print(f"No user found for traveler_id: {traveler_id}")
                        continue
                    user_id = user_data['id']

                # MySQL에 장소 정보와 피드 정보 삽입
                insert_feed_query = """
                    INSERT INTO feed (user_id, title, address1, address2, img_url, like_count, mapx, mapy, type, content)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """
                cursor.execute(insert_feed_query, (
                    user_id,
                    nan_to_none(visit_row['VISIT_AREA_NM']),
                    nan_to_none(visit_row['ROAD_NM_ADDR']),
                    nan_to_none(visit_row['LOTNO_ADDR']),
                    hdfs_image_path,
                    0,  # 좋아요 초기값
                    nan_to_none(photo_row.iloc[0]['PHOTO_FILE_X_COORD']),
                    nan_to_none(photo_row.iloc[0]['PHOTO_FILE_Y_COORD']),
                    nan_to_none(visit_area_type_cd),
                    ""  # content
                ))

        mysql_connection.commit()

finally:
    mysql_connection.close()
    user_db_connection.close()
