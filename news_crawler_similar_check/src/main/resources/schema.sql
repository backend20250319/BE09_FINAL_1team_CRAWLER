-- 테이블 생성 SQL

-- 카테고리 테이블
CREATE TABLE IF NOT EXISTS news_category (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL
);

-- 언론사 테이블
CREATE TABLE IF NOT EXISTS press (
    press_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    press_name VARCHAR(255) NOT NULL,
    blacklisted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 뉴스 링크 테이블
CREATE TABLE IF NOT EXISTS news_links (
    link_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    press_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    source_url TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    FOREIGN KEY (category_id) REFERENCES news_category(category_id),
    FOREIGN KEY (press_id) REFERENCES press(press_id)
);

-- 뉴스 크롤 테이블
CREATE TABLE IF NOT EXISTS news_crawl (
    raw_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    link_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    published_at VARCHAR(255),
    reporter_name VARCHAR(255),
    dedup_status BOOLEAN NOT NULL DEFAULT FALSE,
    image_url TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (link_id) REFERENCES news_links(link_id)
);

-- 뉴스 테이블
CREATE TABLE IF NOT EXISTS news (
    news_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_id BIGINT NOT NULL,
    trusted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    FOREIGN KEY (raw_id) REFERENCES news_crawl(raw_id)
);
