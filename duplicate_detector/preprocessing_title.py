import pandas as pd
import re
from preprocess_config import okt, STOPWORDS, IMPORTANT_KEYWORDS

def preprocess_titles(text):
    if pd.isna(text):
        return ''
    
    text = str(text)
    text = re.sub(r'[^\w\s]', ' ', text)         # 특수문자 제거
    text = re.sub(r'\d+', '', text)              # 숫자 제거
    text = re.sub(r'\s+', ' ', text).strip()     # 공백 정리

    tokens = okt.nouns(text)                     # 명사 추출
    tokens = [
        t for t in tokens 
        if (len(t) > 1 or t in IMPORTANT_KEYWORDS) and t not in STOPWORDS
    ]
    return ' '.join(tokens)


