# 전체 실행 로직 통합: 전처리 → 유사도 분석 → 대표기사 선정 및 저장 
import os
import pandas as pd
from konlpy.tag import Okt
from itertools import combinations
from config import CATEGORY, PERIOD, DATE, THRESHOLD_TITLE, get_file_path, get_dedup_dir
from preprocessing_title import preprocess_titles
from grouping import build_title_similarity_groups
from content_filter import filter_and_pick_representative_by_content

# ----- 경로 설정 -----
file_path = get_file_path(PERIOD, DATE, CATEGORY)
dedup_dir = get_dedup_dir(PERIOD, DATE)
dedup_dir.mkdir(parents=True, exist_ok=True)  # 안전하게 디렉토리 생성

# ----- 데이터 로드 -----
df = pd.read_csv(file_path)
df['title'] = df['title'].fillna('')
df['content'] = df['content'].fillna('')

# ----- 제목 전처리 -----
df['clean_title'] = df['title'].apply(preprocess_titles)

# ----- 제목 기반 유사 그룹 생성 -----
groups, title_similar_pairs = build_title_similarity_groups(df, threshold=THRESHOLD_TITLE)
print(f"\n🔗 유사 그룹 수: {len(groups)}")

# 제목 유사도 출력
print("\n📌 제목 유사도:")
for i, j, sim in title_similar_pairs:
    index_i = df.index[i] + 1
    index_j = df.index[j] + 1
    title_i = df.iloc[i]["title"]
    title_j = df.iloc[j]["title"]

    print(f" - (index {index_i}, {index_j}) 제목 유사도: {sim:.4f}")
    print(f"   ① {title_i}")
    print(f"   ② {title_j}")
    print ("\n")

# ----- 대표 기사 선택 (본문 기반) -----
df.reset_index(drop=True, inplace=True)

rep_ids = []
dup_ids = []
related_info = []  # (rep_id, related_id, similarity)
group_reps = {}
group_logs = {}

grouped_indices = set()

for group in groups:
    rep, removed, related, content_log = filter_and_pick_representative_by_content(group, df)
    group_key = frozenset(group)
    group_logs[group_key] = content_log

    if rep is not None:
        rep_ids.append(rep)
        dup_ids.extend(removed)
        related_info.extend(related)
        group_reps[group_key] = rep
    else:
        rep_ids.extend(group)
        for i in group:
            group_reps[frozenset({i})] = i
        group_reps[group_key] = None

    grouped_indices.update(group)
    
# ✅ 유사도 비교 대상 외 기사들도 추가
grouped_indices = set(i for group in groups for i in group)
ungrouped_indices = set(df.index) - grouped_indices
rep_ids.extend(ungrouped_indices)

# ✅ 최종 deduplicated DataFrame 생성
df_dedup = df.loc[sorted(set(rep_ids))].copy()

# 결과 저장
dedup_filename = f" deduplicated_{CATEGORY}_{DATE}_{PERIOD}.csv"
output_path = os.path.join(dedup_dir, dedup_filename)
df_dedup.to_csv(output_path, index=False)

print(f"\n✅ 본문 유사도 기반 대표 기사 저장 완료: {output_path}")
print(f"✅ 중복 제거 결과: {len(df)} → {len(df_dedup)}개")

# ----- 연관 뉴스 CSV 저장 -----
related_df = pd.DataFrame(related_info, columns=["rep_news_id", "related_news_id", "similarity"])
related_csv_path = os.path.join(dedup_dir, f"related_{CATEGORY}_{DATE}_{PERIOD}.csv")
related_df.to_csv(related_csv_path, index=False)

print(f"📎 연관 뉴스 {len(related_df)}건 저장 완료: {related_csv_path}")

# ----- 로그 저장 -----
log_path = os.path.join(dedup_dir, f"logs_{CATEGORY}_{DATE}_{PERIOD}.txt")
print("\n📦 유사 기사 그룹 내용:")

with open(log_path, "w", encoding="utf-8") as f:
    for idx, group in enumerate(groups, 1):
        group_key = frozenset(group)
        rep = group_reps.get(group_key)
        content_log = group_logs.get(group_key, "")

        f.write("=======================================================\n")
        f.write(f"[그룹 {idx} - 총 {len(group)}건]\n")
        print(f"[그룹 {idx} - 총 {len(group)}건]")

        for i in sorted(group):
            if rep is not None and i == rep:
                mark = "✅ 대표"
            elif any(r == i and rp == rep for rp, r, _ in related_info):
                mark = "↔️ 연관"
            elif rep is not None and i in dup_ids:
                mark = "❌ 제거"
            else:
                mark = "☑️ 유지"
        
            title = df.loc[i, 'title']
            f.write(f" - {mark} {title}\n")
            print(f" - {mark} {title}")

        if content_log:
            f.write(content_log + "\n")
        print()
