# 본문 유사도 기반 대표 기사 선택 로직
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from config import THRESHOLD_CONTENT, THRESHOLD_RELATED_MIN
import numpy as np
from preprocessing_content import preprocess_content
from sentence_transformers import SentenceTransformer, util

# 모델 로드 (전역에서 한 번만 로드)
model = SentenceTransformer("snunlp/KR-SBERT-V40K-klueNLI-augSTS")

def filter_and_pick_representative_by_content(group, df, threshold=THRESHOLD_CONTENT, threshold_related_min=THRESHOLD_RELATED_MIN):
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
# ------------------------------------------------------------

    # # TF-IDF 벡터화 및 코사인 유사도 계산
    # vectorizer = TfidfVectorizer()
    # tfidf = vectorizer.fit_transform(docs)
    # sim_matrix = cosine_similarity(tfidf)

    # 문장 → 벡터 임베딩
    embeddings = model.encode(docs, convert_to_tensor=True)

    # 코사인 유사도 계산 (동일한 구조)
    sim_matrix = util.pytorch_cos_sim(embeddings, embeddings).cpu().numpy()
# ------------------------------------------------------------
# 대표 기사 선정 (가장 중심에 가까운 기사)
    row_avg = sim_matrix.mean(axis=1)
    rep_idx = indices[int(row_avg.argmax())]

    removed_ids = []  # 중복 기사 (제거 대상)
    related_articles = []  # 연관 뉴스 (rep_id, related_id, similarity)
# ------------------------------------------------------------
    # 로그 및 유사도 쌍 기록    
    log_lines.append(f"\n➡️ 본문 유사도 그룹: {[i + 1 for i in sorted(indices)]}")
    similar_count = 0
    total_pairs = 0

    for i in range(len(indices)):
            idx = indices[i]
            if idx == rep_idx:
                continue
            sim = sim_matrix[i][indices.index(rep_idx)]

            if sim >= THRESHOLD_CONTENT:
                removed_ids.append(idx)  # 중복 제거
            elif sim >= THRESHOLD_RELATED_MIN:
                related_articles.append((rep_idx, idx, round(float(sim), 4)))  # 연관 뉴스
            else:
                pass

            title_i = df.loc[idx, 'title']
            title_r = df.loc[rep_idx, 'title']
            log_lines.extend([
                f" - ({rep_idx + 1}, {idx + 1}) 본문 유사도: {sim:.4f}",
                f"   ① {title_r}",
                f"   ② {title_i}",
            ])

    return rep_idx, removed_ids, related_articles, "\n".join(log_lines)

