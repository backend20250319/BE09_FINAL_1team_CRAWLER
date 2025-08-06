import re
from duplicate_detector.preprocess_config import okt, STOPWORDS, IMPORTANT_KEYWORDS  

def preprocess_content(text):
    """
    뉴스 본문 전처리 (Okt.morphs 기반)
    - 특수문자, 숫자 제거
    - 형태소 분석 후 조사/불용어 제거
    """
    if not isinstance(text, str):
        return ''
    
    # 특수문자/숫자 제거 및 정리
    text = re.sub(r'[^\w\s]', ' ', text)
    text = re.sub(r'\d+', '', text)
    text = re.sub(r'\s+', ' ', text).strip()

    # 형태소 분석 (전체 토큰화) + 불용어 제거 + 1글자 필터링
    tokens = [
        t for t in okt.morphs(text)
        if (len(t) > 1 or t in IMPORTANT_KEYWORDS) and t not in STOPWORDS
    ]
    return ' '.join(tokens)
