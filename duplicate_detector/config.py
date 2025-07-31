# 카테고리, 날짜, 임계값, 경로 등 설정값 관리
            
# config.py
from pathlib import Path

# ===== 협업 환경을 위한 기본 설정 =====
# 이 config.py가 있는 위치: /duplicate_detector/config.py
# 프로젝트 루트 기준으로 BASE_DIR 설정
PROJECT_ROOT = Path(__file__).resolve().parent.parent  # => BE09_FINAL_1team_CRAWLER

# 크롤링 파일이 저장된 루트 경로
STATIC_BASE = PROJECT_ROOT / "news_crawler" / "src" / "main" / "resources" / "static"

# 동적으로 설정할 변수들 (다른 파일에서 import 후 설정 가능)
PERIOD = "pm"
DATE = "2025-07-31"
CATEGORY = "생활문화"
THRESHOLD_TITLE = 0.5
THRESHOLD_CONTENT = 0.4 

# 파일 경로 생성 함수 (절대경로로 반환됨)
def get_file_path(period=PERIOD, date=DATE, category=CATEGORY):
    return STATIC_BASE / period / f"{date}_{period}" / "detail" / f"naver_news_{category}_{period}_detailed.csv"

def get_dedup_dir(period=PERIOD, date=DATE):
    return STATIC_BASE / period / f"{date}_{period}" / "deduplicated"
