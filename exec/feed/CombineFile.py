import pandas as pd

# 두 CSV 파일을 읽기
file1 = '/home/ubuntu/tn_travel_여행_F.csv'
file2 = '/home/ubuntu/tn_travel_여행_F1.csv'

# 데이터프레임으로 변환
df1 = pd.read_csv(file1, encoding='utf-8-sig')
df2 = pd.read_csv(file2, encoding='utf-8-sig')

# 두 데이터프레임을 같은 컬럼을 기준으로 합치기 (행 기준으로 병합)
df_combined = pd.concat([df1, df2])

# 합친 데이터를 UTF-8 with BOM 형식으로 새로운 파일로 저장
output_file = '/home/ubuntu/tn_travel_여행_F.csv'
df_combined.to_csv(output_file, index=False, encoding='utf-8-sig')

print(f"두 파일이 성공적으로 합쳐졌습니다. 합쳐진 파일: {output_file}")
