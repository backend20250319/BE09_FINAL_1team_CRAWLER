# 전체 실행 로직 통합: 전처리 → 유사도 분석 → 대표기사 선정 및 저장 
import os
import pandas as pd
from konlpy.tag import Okt
from itertools import combinations
from config import CATEGORY, PERIOD, DATE, THRESHOLD_TITLE, get_file_path, get_dedup_dir
from preprocessing_title import preprocess_titles
from grouping import build_title_similarity_groups
from content_filter import filter_and_pick_representative_by_content

def process_single_category(category, period=PERIOD, date=DATE, threshold_title=THRESHOLD_TITLE):
    """단일 카테고리 처리 함수"""
    print(f"{category} 카테고리 중복 제거 시작...")
    
    # ----- 경로 설정 -----
    file_path = get_file_path(period, date, category)
    dedup_dir = get_dedup_dir(period, date)
    dedup_dir.mkdir(parents=True, exist_ok=True)  # 안전하게 디렉토리 생성

    # ----- 데이터 로드 -----
    df = pd.read_csv(file_path)
    df['title'] = df['title'].fillna('')
    df['content'] = df['content'].fillna('')

    # ----- 제목 전처리 -----
    df['clean_title'] = df['title'].apply(preprocess_titles)

    # ----- 제목 기반 유사 그룹 생성 -----
    groups, title_similar_pairs = build_title_similarity_groups(df, threshold=threshold_title)
    print(f"\n유사 그룹 수: {len(groups)}")

    # 제목 유사도 출력
    print("\n제목 유사도:")
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
        
    # 유사도 비교 대상 외 기사들도 추가
    grouped_indices = set(i for group in groups for i in group)
    ungrouped_indices = set(df.index) - grouped_indices
    rep_ids.extend(ungrouped_indices)

    # 최종 deduplicated DataFrame 생성
    # related_info가 이제 (rep_oid_aid, related_oid_aid, similarity) 형태이므로 
    # 인덱스가 아닌 oid_aid에서 인덱스를 찾아야 함
    related_indices = []
    if 'oid_aid' in df.columns:
        for _, related_oid_aid, _ in related_info:
            # oid_aid가 "idx_숫자" 형태인 경우 (fallback)
            if related_oid_aid.startswith("idx_"):
                related_indices.append(int(related_oid_aid.split("_")[1]))
            else:
                # 실제 oid_aid로부터 인덱스 찾기
                matching_indices = df[df['oid_aid'] == related_oid_aid].index.tolist()
                related_indices.extend(matching_indices)
    else:
        # oid_aid 컬럼이 없는 경우 기존 방식 사용
        related_indices = [r for _, r, _ in related_info]

    final_ids = sorted(set(rep_ids + related_indices))  # 중복 제거
    df_dedup = df.loc[final_ids].copy()

    # ----- 연관 뉴스 CSV 저장 -----
    related_df = pd.DataFrame(related_info, columns=["rep_oid_aid", "related_oid_aid", "similarity"])
    related_csv_path = os.path.join(dedup_dir, f"related_{category}_{date}_{period}.csv")
    related_df.to_csv(related_csv_path, index=False)

    print(f"연관 뉴스 {len(related_df)}건 저장 완료: {related_csv_path}")

    # 결과 저장 경로 미리 정의
    dedup_filename = f"deduplicated_{category}_{date}_{period}.csv"
    output_path = os.path.join(dedup_dir, dedup_filename)

    # ----- 로그 저장 -----
    log_folder = os.path.join(dedup_dir, "logs")
    os.makedirs(log_folder, exist_ok=True)  # logs 폴더 생성
    log_path = os.path.join(log_folder, f"logs_{category}_{date}_{period}.txt")
    print("\n유사 기사 그룹 내용:")

    dedup_state_map = {
        "대표": "REPRESENTATIVE",
        "연관": "RELATED",
        "유지": "KEPT",
        "제거": "REMOVED"
    }

    with open(log_path, "w", encoding="utf-8") as f:
        mark_dict = {}

        for idx, group in enumerate(groups, 1):
            group_key = frozenset(group)
            rep = group_reps.get(group_key)
            content_log = group_logs.get(group_key, "")

            f.write("=======================================================\n")
            f.write(f"[그룹 {idx} - 총 {len(group)}건]\n")
            print(f"[그룹 {idx} - 총 {len(group)}건]")

            for i in sorted(group):
                if rep is not None and i == rep:
                    mark = "대표"
                elif i in related_indices:
                    mark = "연관"
                elif rep is not None and i in dup_ids:
                    mark = "제거"
                else:
                    mark = "유지"
            
                mark_dict[i] = mark  # 마킹 정보를 저장

                title = df.loc[i, 'title']
                f.write(f" - {mark} {title}\n")
                print(f" - {mark} {title}")

            if content_log:
                f.write(content_log + "\n")
            print()
        
        # 그룹 외 기사 처리
        for i in ungrouped_indices:
            mark_dict[i] = "유지"  # 그룹화되지 않은 기사들도 유지로 마킹

        # 연관 뉴스 중 마킹 안 된 기사 처리
        for i in related_indices:
            if i not in mark_dict:
                mark_dict[i] = "연관"

        # df_dedup에 mark 컬럼 추가
        df_dedup["mark"] = df_dedup.index.map(mark_dict).fillna("유지")  # 혹시 누락된 인덱스도 기본값 넣기

        df_dedup["dedup_state"] = df_dedup["mark"].map(dedup_state_map)
        
        # 불필요한 컬럼 제거
        columns_to_remove = ['clean_title', 'news_category_name', 'category_name']
        for col in columns_to_remove:
            if col in df_dedup.columns:
                df_dedup = df_dedup.drop(col, axis=1)
        
        df_dedup.to_csv(output_path, index=False)

    print(f"\n본문 유사도 기반 대표 기사 저장 완료: {output_path}")
    print(f"중복 제거 결과: {len(df)} → {len(df_dedup)}개")
    
    return output_path

# 기존 스크립트 호환성을 위한 메인 실행
if __name__ == "__main__":
    process_single_category(CATEGORY, PERIOD, DATE, THRESHOLD_TITLE)