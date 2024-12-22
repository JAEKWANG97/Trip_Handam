import pandas as pd

# CSV 파일 경로
travel_csv_path = '/home/ubuntu/tn_travel_여행_F.csv'
visit_csv_path = '/home/ubuntu/tn_visit_area_info_방문지정보_F.csv'
photo_csv_path = '/home/ubuntu/tn_tour_photo_관광사진_F.csv'
processed_travel_data_path = '/home/ubuntu/processed_user_data (1).csv'
output_csv_path = '/home/ubuntu/matched_travel_data (3).csv'  # 결과를 저장할 CSV 파일 경로

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

# 결과를 저장할 빈 리스트
matched_data = []

for index, processed_row in processed_travel_df.iterrows():
    traveler_id = processed_row['TRAVELER_ID']

    # TRAVELER_ID와 TRAVEL_ID 매핑
    travel_row = travel_df[travel_df['TRAVELER_ID'] == traveler_id]
    if travel_row.empty:
        continue
    travel_id = travel_row.iloc[0]['TRAVEL_ID']

    # TRAVEL_ID와 VISIT_AREA_ID 매핑
    visit_rows = visit_df[visit_df['TRAVEL_ID'] == travel_id]
    if visit_rows.empty:
        continue

    for _, visit_row in visit_rows.iterrows():
        visit_area_type_cd = visit_row['VISIT_AREA_TYPE_CD']

        # VISIT_AREA_TYPE_CD가 21, 22, 9일 경우 스킵
        # if visit_area_type_cd in [21, 22, 9]:
        #     continue

        visit_area_id = visit_row['VISIT_AREA_ID']

        # VISIT_AREA_ID로 사진 정보 가져오기
        photo_rows = photo_df[(photo_df['VISIT_AREA_ID'] == visit_area_id) & (photo_df['TRAVEL_ID'] == travel_id)]
        if photo_rows.empty:
            continue

        # 매칭된 데이터를 리스트에 추가
        for _, photo_row in photo_rows.iterrows():
            # 매칭된 데이터를 리스트에 추가
            matched_data.append({
                'TRAVELER_ID': traveler_id,
                'TRAVEL_ID': travel_id,
                'VISIT_AREA_ID': visit_area_id,
                'VISIT_AREA_NM': nan_to_none(visit_row['VISIT_AREA_NM']),
                'ROAD_NM_ADDR': nan_to_none(visit_row['ROAD_NM_ADDR']),
                'LOTNO_ADDR': nan_to_none(visit_row['LOTNO_ADDR']),
                'PHOTO_FILE_ID': photo_row['PHOTO_FILE_ID'],
                'PHOTO_FILE_X_COORD': nan_to_none(photo_row['PHOTO_FILE_X_COORD']),
                'PHOTO_FILE_Y_COORD': nan_to_none(photo_row['PHOTO_FILE_Y_COORD']),
                'VISIT_AREA_TYPE_CD': nan_to_none(visit_area_type_cd)
            })

# 매칭된 데이터를 데이터프레임으로 변환
matched_df = pd.DataFrame(matched_data)

# 결과를 CSV 파일로 저장
matched_df.to_csv(output_csv_path, index=False)

print(f"Matched data saved to {output_csv_path}")
