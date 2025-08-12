# 카테고리, 날짜, 임계값, 경로 등 설정값 관리
            
# config.py
from pathlib import Path

# ===== 협업 환경을 위한 기본 설정 =====
# 이 config.py가 있는 위치: /duplicate_detector/config.py
# 프로젝트 루트 기준으로 BASE_DIR 설정
PROJECT_ROOT = Path(__file__).resolve().parent.parent  # => BE09_FINAL_1team_CRAWLER

# 크롤링 파일이 저장된 루트 경로
STATIC_BASE = PROJECT_ROOT / "news_crawler" / "src" / "main" / "resources" / "static"

# 동적으로 설정할 변수들 (현재 시간 기반으로 자동 설정)
import datetime

def get_current_period():
    now = datetime.datetime.now()
    return "am" if now.hour < 12 else "pm"

def get_current_date():
    return datetime.datetime.now().strftime("%Y-%m-%d")

# 현재 시간 기반으로 자동 설정
PERIOD = get_current_period()
DATE = get_current_date()
CATEGORY = "세계"
THRESHOLD_TITLE = 0.3
THRESHOLD_CONTENT = 0.8
THRESHOLD_RELATED_MIN = 0.4  # 또는 0.4 등 적절한 값

# 모든 카테고리 리스트
ALL_CATEGORIES = ["정치", "경제", "사회", "생활", "세계", "IT과학", "자동차", "여행", "예술", "패션뷰티"]

# 파일 경로 생성 함수 (절대경로로 반환됨)
def get_file_path(period=PERIOD, date=DATE, category=CATEGORY):
    return STATIC_BASE / period / f"{date}_{period}" / "detail" / f"naver_news_{category}_{period}_detailed.csv"

def get_dedup_dir(period=PERIOD, date=DATE):
    return STATIC_BASE / period / f"{date}_{period}" / "deduplicated-related"
