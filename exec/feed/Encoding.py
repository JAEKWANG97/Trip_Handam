import pandas as pd

# CSV 파일을 UTF-8 with BOM으로 저장
input_file = '/home/ubuntu/matched_travel_data (2).csv'
output_file = '/home/ubuntu/matched_travel_data (2).csv'

# pandas를 이용해 파일을 읽고
df = pd.read_csv(input_file)

# UTF-8 with BOM으로 저장
df.to_csv(output_file, index=False, encoding='utf-8-sig')
