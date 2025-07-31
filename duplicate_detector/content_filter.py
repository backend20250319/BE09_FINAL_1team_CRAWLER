# 본문 유사도 기반 대표 기사 선택 로직
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from config import THRESHOLD_CONTENT
import numpy as np
from preprocessing_content import preprocess_content

def filter_and_pick_representative_by_content(group, df, threshold=THRESHOLD_CONTENT):
    """
    그룹 내 본문 유사도를 기준으로 대표 기사 선택 (쌍 중 절반 이상이 임계값 넘을 경우만 중복으로 간주)
    :param group: set 또는 list of indices
    :param df: 전체 기사 DataFrame
    :param threshold: 유사도 임계값
    :return: (대표 인덱스 or None, 중복 그룹 여부, 로그 문자열)
    """
    log_lines = []
    indices = list(group)
    docs = [preprocess_content(df.loc[i, 'content']) for i in indices]

    if len(indices) == 1:
        return indices[0], False, ""

    # TF-IDF 벡터화 및 코사인 유사도 계산
    vectorizer = TfidfVectorizer()
    tfidf = vectorizer.fit_transform(docs)
    sim_matrix = cosine_similarity(tfidf)

    # 로그 및 유사도 쌍 기록
    log_lines.append(f"\n📌 본문 유사도 그룹: {[i + 1 for i in sorted(indices)]}")
    similar_count = 0
    total_pairs = 0

    for i in range(len(indices)):
        for j in range(i + 1, len(indices)):
            idx_i, idx_j = indices[i], indices[j]
            sim = sim_matrix[i][j]
            total_pairs += 1
            if sim >= threshold:
                similar_count += 1

            title_i = df.loc[idx_i, 'title']
            title_j = df.loc[idx_j, 'title']
            log_lines.extend([
                f" - ({idx_i + 1}, {idx_j + 1}) 본문 유사도: {sim:.4f}",
                f"   ① {title_i}",
                f"   ② {title_j}",
            ])

    # 절반 이상이 유사한 경우만 중복 처리
    if similar_count / total_pairs < 0.5:
        return None, False, "\n".join(log_lines)

    # 대표 기사 선택 (가장 중심에 가까운 기사)
    row_avg = sim_matrix.mean(axis=1)
    rep_idx = indices[int(row_avg.argmax())]

    return rep_idx, True, "\n".join(log_lines)

