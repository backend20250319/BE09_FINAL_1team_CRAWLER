# 자동화된 뉴스 크롤링 시스템

이 프로젝트는 네이버 뉴스를 자동으로 크롤링하고, 중복을 제거하여 데이터베이스에 저장하는 시스템입니다.

## 🚀 주요 기능

- **현재 시간 기반 자동화**: 하드코딩된 날짜 대신 현재 시간을 기반으로 동작
- **스케줄링**: 정기적인 크롤링 자동 실행
- **REST API**: 수동 크롤링 실행 및 상태 확인
- **전체 프로세스 자동화**: 뉴스 수집 → 상세 크롤링 → 중복 제거 → DB 저장

## 📋 시스템 구성

### 1. 크롤링 단계

1. **NaverNewsListEfficientCrawler**: 네이버 뉴스 목록 크롤링
2. **NewsDetailBatchProcessor**: 뉴스 상세 내용 크롤링
3. **Python 중복 제거**: `duplicate_detector/run_all_categories.py`
4. **CsvToDatabase**: 데이터베이스 저장

### 2. 자동화 컴포넌트

- **DateTimeUtils**: 현재 시간 기반 유틸리티
- **NewsCrawlingService**: 전체 프로세스 관리
- **NewsCrawlingScheduler**: 스케줄링
- **NewsCrawlingController**: REST API

## 🛠️ 설치 및 실행

### 1. 의존성 설치

```bash
# Java 11+ 필요
# Python 3.7+ 필요
pip install -r requirements.txt  # Python 의존성
```

### 2. 스프링 부트 애플리케이션 실행

```bash
cd news_crawler
./gradlew bootRun
```

### 3. 서버 시작 후 자동 크롤링

- 서버가 시작되면 스케줄러가 자동으로 활성화됩니다
- 기본 스케줄: 매일 오전 9시, 오후 6시

## 📡 REST API 사용법

### 크롤링 시작

```bash
curl -X POST http://localhost:8080/api/crawling/start
```

### 크롤링 상태 확인

```bash
curl http://localhost:8080/api/crawling/status
```

### 스케줄 정보 조회

```bash
curl http://localhost:8080/api/crawling/schedule
```

### 특정 단계 실행 (테스트용)

```bash
curl -X POST http://localhost:8080/api/crawling/step/1  # 뉴스 목록 크롤링
curl -X POST http://localhost:8080/api/crawling/step/2  # 뉴스 상세 크롤링
curl -X POST http://localhost:8080/api/crawling/step/3  # 중복 제거
curl -X POST http://localhost:8080/api/crawling/step/4  # DB 저장
```

## ⏰ 스케줄 설정

### 기본 스케줄

- **매일 자정**: `0 0 0 * * *`
- **매일 오전 9시, 오후 6시**: `0 0 9,19 * * *`
- **매시간 정각**: `0 0 * * * *`

### 스케줄 수정

`NewsCrawlingScheduler.java`에서 cron 표현식을 수정하여 스케줄을 변경할 수 있습니다.

## 📁 파일 구조

```
BE09_FINAL_1team_CRAWLING_EXAMPLE/
├── news_crawler/
│   └── src/main/java/com/news/news_crawler/
│       ├── util/
│       │   ├── DateTimeUtils.java          # 시간 유틸리티
│       │   ├── NaverNewsListEfficientCrawler.java
│       │   ├── NewsDetailBatchProcessor.java
│       │   └── CsvToDatabase.java
│       ├── service/
│       │   └── NewsCrawlingService.java    # 크롤링 서비스
│       ├── scheduler/
│       │   └── NewsCrawlingScheduler.java  # 스케줄러
│       ├── controller/
│       │   └── NewsCrawlingController.java # REST API
│       └── NewsCrawlerApplication.java
├── duplicate_detector/
│   ├── config.py                           # 현재 시간 기반 설정
│   └── run_all_categories.py
└── README.md
```

## 🔧 설정

### 데이터베이스 설정

`application.properties`에서 데이터베이스 연결 정보를 설정하세요.

### 크롤링 설정

- **목표 개수**: `NaverNewsListEfficientCrawler`에서 기본 100개
- **카테고리**: 정치, 경제, 사회, 생활문화, 세계, IT과학
- **중복 제거 임계값**: `config.py`에서 설정

## 📊 로그 확인

크롤링 진행 상황은 스프링 부트 로그에서 확인할 수 있습니다:

```bash
tail -f logs/application.log
```

## 🚨 주의사항

1. **Python 환경**: `duplicate_detector` 스크립트 실행을 위해 Python이 필요합니다.
2. **Chrome WebDriver**: Selenium 크롤링을 위해 Chrome WebDriver가 필요합니다.
3. **데이터베이스 연결**: MySQL 데이터베이스 연결이 필요합니다.
4. **파일 권한**: CSV 파일 생성 및 읽기 권한이 필요합니다.

## 🔄 수동 실행 (기존 방식)

기존처럼 각 파일을 개별적으로 실행할 수도 있습니다:

```bash
# 1. 뉴스 목록 크롤링
java -cp . com.news.news_crawler.util.NaverNewsListEfficientCrawler 100

# 2. 뉴스 상세 크롤링
java -cp . com.news.news_crawler.util.NewsDetailBatchProcessor

# 3. 중복 제거 (Python)
cd duplicate_detector
python run_all_categories.py

# 4. 데이터베이스 저장
java -cp . com.news.news_crawler.util.CsvToDatabase
```

## 📝 변경 사항

### 현재 시간 기반 동작

- 하드코딩된 날짜 제거
- `DateTimeUtils` 클래스로 시간 관리
- 모든 컴포넌트가 현재 시간을 기반으로 동작

### 자동화

- 스프링 부트 서버 시작 시 자동 스케줄링
- REST API로 수동 실행 가능
- 백그라운드 비동기 실행

### 에러 처리

- 각 단계별 에러 로깅
- 실패 시 상세한 에러 메시지
- 비동기 실행으로 서버 안정성 확보
