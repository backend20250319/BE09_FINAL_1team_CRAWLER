# run_dedup.py
import pandas as pd
from duplicate_detector.config import (
    get_file_path, get_dedup_dir,
    get_dedup_file_path, get_related_file_path, get_log_file_path
)
from duplicate_detector.preprocessing_title import preprocess_titles
from duplicate_detector.grouping import build_title_similarity_groups
from duplicate_detector.content_filter import filter_and_pick_representative_by_content

def run_dedup_main(category: str, period: str, date: str, *, 
                   threshold_title: float, threshold_content: float, threshold_related_min: float):
    """
    중복 제거 전체 파이프라인 실행 함수
    - category: 예) 정치, 사회
    - period: am / pm
    - date: yyyy-mm-dd
    """
    # ----- 경로 설정 -----
    file_path = get_file_path(period, date, category)
    dedup_dir = get_dedup_dir(period, date)
    dedup_dir.mkdir(parents=True, exist_ok=True)

    dedup_path = get_dedup_file_path(category, date, period)
    related_path = get_related_file_path(category, date, period)
    log_path = get_log_file_path(category, date, period)

    # ----- 데이터 로드 -----
    df = pd.read_csv(file_path)
    df['title'] = df['title'].fillna('')
    df['content'] = df['content'].fillna('')

    # ----- 제목 전처리 -----
    df['clean_title'] = df['title'].apply(preprocess_titles)

    # ----- 제목 기반 유사 그룹 생성 -----
    groups, _ = build_title_similarity_groups(df, threshold=threshold_title)

    # ----- 본문 기반 대표 기사 선택 -----
    df.reset_index(drop=True, inplace=True)
    rep_ids = []
    dup_ids = []
    related_info = []  # (rep_id, related_id, similarity)
    group_reps = {}
    group_logs = {}

    for group in groups:
        rep, removed, related, content_log = filter_and_pick_representative_by_content(
            group, df,
            threshold=threshold_content,
            threshold_related_min=threshold_related_min
        )
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

    # ✅ 그룹 외 기사 추가
    grouped_indices = set(i for group in groups for i in group)
    ungrouped_indices = set(df.index) - grouped_indices
    rep_ids.extend(ungrouped_indices)

    # ✅ 최종 deduplicated DataFrame 생성
    df_dedup = df.loc[sorted(set(rep_ids))].copy()

    # ----- 결과 저장 -----
    df_dedup.to_csv(dedup_path, index=False)

    related_df = pd.DataFrame(related_info, columns=["rep_news_id", "related_news_id", "similarity"])
    related_df.to_csv(related_path, index=False)

    # ----- 로그 저장 -----
    with open(log_path, "w", encoding="utf-8") as f:
        for idx, group in enumerate(groups, 1):
            group_key = frozenset(group)
            rep = group_reps.get(group_key)
            content_log = group_logs.get(group_key, "")

            f.write("=======================================================\n")
            f.write(f"[그룹 {idx} - 총 {len(group)}건]\n")

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

            if content_log:
                f.write(content_log + "\n")

    # ✅ 결과 리턴
    return {
        "deduplicated_count": len(df_dedup),
        "related_count": len(related_df),
        "dedup_file": str(dedup_path),
        "related_file": str(related_path),
        "log_file": str(log_path)
    }
