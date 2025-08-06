package com.news.news_crawler_similar_check.service;

import com.news.news_crawler_similar_check.domain.NewsLinksEntity;
import com.news.news_crawler_similar_check.domain.PressEntity;
import com.news.news_crawler_similar_check.domain.CategoryEntity;
import com.news.news_crawler_similar_check.repository.NewsLinksRepository;
import com.news.news_crawler_similar_check.repository.PressRepository;
import com.news.news_crawler_similar_check.repository.CategoryRepository;
import com.news.news_crawler_similar_check.dto.NewsListCrawlRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsListCrawlingService {

    private final NewsLinksRepository newsLinksRepository;
    private final PressRepository pressRepository;
    private final CategoryRepository categoryRepository;

    // 성능 최적화를 위한 상수들
    private static final int BATCH_SIZE = 50;
    private static final int MAX_CONCURRENT_CATEGORIES = 3;
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY = 1000;

    // 카테고리별 URL 매핑 (네이버 뉴스 섹션 페이지)
    private static final Map<String, String> CATEGORY_URLS = Map.of(
        "정치", "https://news.naver.com/section/100",
        "경제", "https://news.naver.com/section/101", 
        "사회", "https://news.naver.com/section/102",
        "생활/문화", "https://news.naver.com/section/103",
        "세계", "https://news.naver.com/section/104",
        "IT/과학", "https://news.naver.com/section/105"
    );

    // 네이버 뉴스 카테고리 코드 (URL용)
    private static final Map<String, Integer> NAVER_CATEGORY_CODES = Map.of(
        "정치", 100,
        "경제", 101,
        "사회", 102,
        "생활/문화", 103,
        "세계", 104,
        "IT/과학", 105
    );

    @Transactional
    public void crawlNewsList(NewsListCrawlRequestDto request) {
        log.info("뉴스 리스트 크롤링 시작: {}", request);
        
        List<String> targetCategories = request.getCategories() != null && !request.getCategories().isEmpty() 
            ? request.getCategories() 
            : new ArrayList<>(CATEGORY_URLS.keySet());
        
        int targetCount = request.getCountPerCategory() != null ? request.getCountPerCategory() : 10;
        
        log.info("대상 카테고리: {}, 카테고리당 수집 개수: {}", targetCategories, targetCount);
        
        // 순차적으로 카테고리별 크롤링
        for (String categoryName : targetCategories) {
            if (!CATEGORY_URLS.containsKey(categoryName)) {
                log.warn("알 수 없는 카테고리: {}", categoryName);
                continue;
            }
            
            String categoryUrl = CATEGORY_URLS.get(categoryName);
            Integer categoryId = NAVER_CATEGORY_CODES.get(categoryName);
            
            log.info("[{}] 카테고리 크롤링 시작", categoryName);
            crawlCategoryWithRetry(categoryName, categoryUrl, categoryId, targetCount);
        }
        
        log.info("뉴스 리스트 크롤링 완료");
    }

    private void crawlCategoryWithRetry(String categoryName, String categoryUrl, Integer categoryId, int targetCount) {
        for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
            try {
                crawlCategoryWithSelenium(categoryName, categoryUrl, targetCount);
                return; // 성공하면 종료
            } catch (Exception e) {
                log.error("[{}] 카테고리 크롤링 실패 (시도 {}/{}): {}", 
                    categoryName, attempt, RETRY_ATTEMPTS, e.getMessage());
                
                if (attempt < RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void crawlCategoryWithSelenium(String categoryName, String categoryUrl, int targetCount) {
        // DB에서 실제 카테고리 ID 조회
        Long dbCategoryId = getCategoryIdFromDB(categoryName);
        if (dbCategoryId == null) {
            log.warn("[{}] DB에 등록되지 않은 카테고리: {}", categoryName, categoryName);
            return;
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        AtomicInteger nullItems = new AtomicInteger(0);
        AtomicInteger duplicateLinks = new AtomicInteger(0);
        AtomicInteger blacklistedPress = new AtomicInteger(0);

        try {
            log.info("[{}] 페이지 로딩: {}", categoryName, categoryUrl);
            driver.get(categoryUrl);

            // 더보기 버튼을 클릭하면서 목표 개수까지 수집
            while (collectedLinks.size() < targetCount) {
                if (!clickMoreButton(wait, categoryName)) break;
            }

            // 페이지 소스를 Jsoup으로 파싱
            Document doc = Jsoup.parse(driver.getPageSource());
            Elements articles = doc.select("#newsct div.section_latest_article ul li");
            log.info("[{}] 파싱된 기사 개수: {}", categoryName, articles.size());

            // 기사 추출 및 배치 수집
            List<NewsLinksEntity> batch = new ArrayList<>();
            for (Element article : articles) {
                if (collectedLinks.size() >= targetCount) break;
                
                NewsItem newsItem = extractNewsItem(article);
                if (newsItem == null) {
                    nullItems.incrementAndGet();
                    continue;
                }
                
                if (!collectedLinks.add(newsItem.link)) {
                    duplicateLinks.incrementAndGet();
                    continue;
                }
                
                // 언론사 ID 찾기 (blacklisted=false인 언론사만)
                Long pressId = getOrCreatePressId(newsItem.press);
                if (pressId == null) {
                    blacklistedPress.incrementAndGet();
                    log.debug("블랙리스트 언론사 제외: {}", newsItem.press);
                    continue;
                }
                
                NewsLinksEntity entity = NewsLinksEntity.builder()
                    .categoryId(dbCategoryId)
                    .pressId(pressId)
                    .title(newsItem.title)
                    .sourceUrl(newsItem.link)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                
                // 배치에 추가
                batch.add(entity);
                
                int currentCount = collectedLinks.size();
                if (currentCount % 10 == 0 || currentCount == targetCount) {
                    log.info("[{}] 수집 진행률: {}/{}", categoryName, currentCount, targetCount);
                }
            }
            
            // 배치 저장
            if (!batch.isEmpty()) {
                newsLinksRepository.saveAll(batch);
                log.info("[{}] 배치 저장 완료: {}개", categoryName, batch.size());
            }
            
            log.info("[{}] 크롤링 완료: {}개 수집 (파싱실패: {}, 중복: {}, 블랙리스트: {})", 
                categoryName, collectedLinks.size(), nullItems.get(), duplicateLinks.get(), blacklistedPress.get());

        } catch (Exception e) {
            log.error("[{}] 카테고리 크롤링 실패: {}", categoryName, e.getMessage());
            throw e;
        } finally {
            driver.quit();
        }
    }

    private boolean clickMoreButton(WebDriverWait wait, String categoryName) {
        try {
            WebElement moreBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#newsct > div.section_latest > div > div.section_more > a")
            ));
            moreBtn.click();
            Thread.sleep(1000); // 로딩 대기
            log.debug("[{}] 더보기 버튼 클릭 성공", categoryName);
            return true;
        } catch (Exception e) {
            log.debug("[{}] 더보기 버튼 없음 또는 클릭 실패: {}", categoryName, e.getMessage());
            return false;
        }
    }

    private Long getCategoryIdFromDB(String categoryName) {
        Optional<CategoryEntity> category = categoryRepository.findByCategoryName(categoryName);
        if (category.isPresent()) {
            log.debug("카테고리 조회 성공: {} (ID: {})", categoryName, category.get().getCategoryId());
            return category.get().getCategoryId();
        } else {
            log.warn("DB에 등록되지 않은 카테고리: {}", categoryName);
            return null;
        }
    }



    private Long getOrCreatePressId(String pressName) {
        // 기존 언론사 찾기 (blacklisted=false인 것만)
        Optional<PressEntity> existingPress = pressRepository.findByPressName(pressName);
        if (existingPress.isPresent()) {
            PressEntity press = existingPress.get();
            if (press.getBlacklisted()) {
                log.debug("블랙리스트 언론사 제외: {} (ID: {})", pressName, press.getPressId());
                return null;
            }
            return press.getPressId();
        }
        
        // 등록되지 않은 언론사는 제외
        log.debug("등록되지 않은 언론사 제외: {}", pressName);
        return null;
    }

    private NewsItem extractNewsItem(Element article) {
        try {
            Element titleEl = article.selectFirst("div.sa_text > a");
            Element pressEl = article.selectFirst("div.sa_text_info_left > div.sa_text_press");
            
            if (titleEl == null || pressEl == null) {
                log.debug("파싱 실패 - titleEl: {}, pressEl: {}", 
                    titleEl != null ? "존재" : "null", 
                    pressEl != null ? "존재" : "null");
                return null;
            }
            
            String title = titleEl.text();
            String link = titleEl.absUrl("href");
            String press = pressEl.text();
            
            if (title.isEmpty() || link.isEmpty() || press.isEmpty()) {
                log.debug("빈 데이터 - title: '{}', link: '{}', press: '{}'", title, link, press);
                return null;
            }
            
            return new NewsItem(title, link, press);
        } catch (Exception e) {
            log.debug("파싱 중 예외 발생: {}", e.getMessage());
            return null;
        }
    }

    private static class NewsItem {
        final String title;
        final String link;
        final String press;

        NewsItem(String title, String link, String press) {
            this.title = title;
            this.link = link;
            this.press = press;
        }
    }
} 