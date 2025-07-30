package com.news.news_crawler.util;

import com.news.news_crawler.dto.NewsDetail;
import com.news.news_crawler.entity.NewsArticle;
import com.news.news_crawler.service.NewsArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseCrawler implements CommandLineRunner {
    
    private final NewsArticleService newsArticleService;
    
    // 카테고리 정보 정의
    private static final Map<Integer, String> CATEGORIES = Map.of(
        105, "IT/과학",
        100, "정치",
        101, "경제", 
        102, "사회",
        103, "생활/문화",
        104, "세계"
    );
    
    @Override
    public void run(String... args) throws Exception {
        log.info("=== 데이터베이스 크롤링 시작 ===");
        
        // Chrome 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images");
        
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
        
        int targetCountPerCategory = 2;
        List<NewsDetail> completeNewsList = new ArrayList<>();
        Set<String> allowedPresses = Set.of("연합뉴스", "동아일보", "중앙일보", "한겨레", "경향신문", "MBC", "파이낸셜뉴스", "국민일보", "서울경제", "한국일보","헤럴드경제", "YTN", "문화일보", "오마이뉴스", "SBS", "KBS");
        
        try {
            log.info("=== 네이버 뉴스 전체 카테고리 크롤링 시작 (최대 10개) ===");
            
            // 각 카테고리별로 순회
            for (Map.Entry<Integer, String> category : CATEGORIES.entrySet()) {
                int categoryId = category.getKey();
                String categoryName = category.getValue();
                
                // 최대 10개 제한 체크
                if (completeNewsList.size() >= 10) {
                    log.info("✅ 최대 10개 수집 완료!");
                    break;
                }
                
                log.info("\n🎯 [{}] {} 카테고리 크롤링 시작", categoryId, categoryName);
                
                Set<String> collectedLinks = new HashSet<>();
                int prevArticleSize = 0;
                
                // 카테고리별 URL 접속
                String categoryUrl = "https://news.naver.com/section/" + categoryId;
                driver.get(categoryUrl);
                Thread.sleep(1000);
                
                // 해당 카테고리에서 뉴스 수집
                while (collectedLinks.size() < targetCountPerCategory && completeNewsList.size() < 10) {
                    List<WebElement> articles = driver.findElements(
                            By.cssSelector("#newsct > div.section_latest > div > div.section_latest_article._CONTENT_LIST._PERSIST_META > div > ul > li")
                    );
                    
                    for (WebElement article : articles) {
                        try {
                            WebElement titleElement = article.findElement(By.cssSelector("div.sa_text > a"));
                            WebElement pressElement = article.findElement(By.cssSelector("div.sa_text_info > div.sa_text_info_left > div.sa_text_press"));
                            
                            String title = titleElement.getText();
                            String link = titleElement.getAttribute("href");
                            String press = pressElement.getText();
                            
                            if (allowedPresses.stream().noneMatch(p -> p.equalsIgnoreCase(press.trim()))) continue;
                            if (collectedLinks.contains(link)) continue;
                            
                            collectedLinks.add(link);
                            log.info("[{}] [{}/{}] 제목: {}", categoryName, collectedLinks.size(), targetCountPerCategory, title);
                            log.info("언론사: {}", press);
                            
                            // 빠른 상세 내용 크롤링
                            NewsDetail detail = crawlNewsDetailFast(link, title, press, categoryName, categoryId);
                            if (detail != null) {
                                completeNewsList.add(detail);
                                log.info("✅ 상세 내용 크롤링 완료");
                            } else {
                                log.info("❌ 상세 내용 크롤링 실패");
                            }
                            log.info("----------------------------");
                            
                            if (collectedLinks.size() >= targetCountPerCategory || completeNewsList.size() >= 10) break;
                            
                        } catch (Exception e) {
                            log.error("기사 파싱 실패: {}", e.getMessage());
                        }
                    }
                    
                    if (articles.size() == prevArticleSize) {
                        log.info("더 이상 새로운 기사가 없습니다.");
                        break;
                    }
                    prevArticleSize = articles.size();
                    
                    try {
                        WebElement moreBtn = wait.until(ExpectedConditions.elementToBeClickable(
                                By.cssSelector("#newsct > div.section_latest > div > div.section_more > a")
                        ));
                        moreBtn.click();
                        Thread.sleep(500);
                    } catch (Exception e) {
                        log.info("더보기 버튼 없음 또는 클릭 실패");
                        break;
                    }
                }
                
                log.info("✅ [{}] 카테고리 완료: {}개 수집", categoryName, collectedLinks.size());
            }
            
            log.info("\n🎉 전체 카테고리 크롤링 완료!");
            log.info("총 수집된 기사 수: {}", completeNewsList.size());
            
            // 데이터베이스에 저장
            List<NewsArticle> savedArticles = newsArticleService.saveNewsArticles(completeNewsList);
            log.info("데이터베이스 저장 완료: {}개 기사", savedArticles.size());
            
            // CSV 파일에 저장
            saveToCsv(completeNewsList);
            log.info("CSV 저장 완료");
            
            // 저장된 기사 수 확인
            long totalCount = newsArticleService.getNewsArticleCount();
            log.info("데이터베이스 총 기사 수: {}", totalCount);
            
        } catch (Exception e) {
            log.error("크롤링 중 오류 발생", e);
        } finally {
            driver.quit();
        }
    }
    
    // CSV 저장 메서드
    private void saveToCsv(List<NewsDetail> newsList) {
        try {
            // 타임스탬프로 파일명 생성
            DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_a_hh-mm-ss", Locale.ENGLISH);
            String formattedTime = LocalDateTime.now().format(fileNameFormatter);
            String fileName = "naver_news_all_categories_" + formattedTime + ".csv";
            
            // static 폴더에 저장
            File directory = new File("src/main/resources/static");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, fileName);
            
            try (
                FileOutputStream fos = new FileOutputStream(file, false);
                OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                BufferedWriter bw = new BufferedWriter(osw);
                PrintWriter writer = new PrintWriter(bw)
            ) {
                writer.println("\"category_id\",\"category_name\",\"press\",\"title\",\"content\",\"reporter\",\"date\",\"link\",\"timestamp\"");
                
                DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH);
                
                for (NewsDetail detail : newsList) {
                    String timestamp = LocalDateTime.now().format(timestampFormatter);
                    writer.printf("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                            detail.getCategoryId(),
                            detail.getCategoryName(),
                            escape(detail.getPress()),
                            escape(detail.getTitle()),
                            escape(detail.getContent()),
                            escape(detail.getReporter()),
                            escape(detail.getDate()),
                            escape(detail.getLink()),
                            timestamp);
                }
                
                log.info("CSV 저장 완료: {}", fileName);
                
            } catch (Exception e) {
                log.error("CSV 저장 실패: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("CSV 저장 중 오류 발생: {}", e.getMessage());
        }
    }
    
    // CSV 이스케이프 메서드
    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\""); // 큰따옴표 이스케이프
    }
    
    // 빠른 상세 내용 크롤링 (Jsoup 직접 사용)
    private NewsDetail crawlNewsDetailFast(String url, String title, String press, String categoryName, int categoryId) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000)
                    .get();
            
            // 기자 정보 추출
            String reporter = "";
            Element reporterElement = doc.selectFirst("#ct > div.media_end_head.go_trans > div.media_end_head_info.nv_notrans > div.media_end_head_journalist > a > em");
            if (reporterElement != null) {
                reporter = reporterElement.text();
            }
            
            // 네이버 뉴스 본문 추출
            String content = "";
            Element contentElement = doc.selectFirst("#dic_area");
            if (contentElement != null) {
                content = contentElement.text();
            }
            
            // 우선순위 2: 본문에서 기자 이름 추출
            if (reporter.isEmpty() && !content.isEmpty()) {
                int searchWindowSize = 100;
                int startIndex = Math.max(0, content.length() - searchWindowSize);
                String searchArea = content.substring(startIndex);
                
                Pattern pattern = Pattern.compile("([가-힣]{2,5}\\s*(기자|특파원|객원기자|통신원))");
                Matcher matcher = pattern.matcher(searchArea);
                
                String foundReporter = "";
                int matchPosInSearchArea = -1;
                
                while (matcher.find()) {
                    foundReporter = matcher.group(1).trim();
                    matchPosInSearchArea = matcher.start();
                }
                
                if (!foundReporter.isEmpty()) {
                    reporter = foundReporter;
                    int originalIndex = startIndex + matchPosInSearchArea;
                    content = content.substring(0, originalIndex).trim();
                }
            }
            
            // 날짜 정보 추출
            String date = "";
            Element dateElement = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            if (dateElement != null) {
                date = dateElement.attr("data-date-time");
            }
            
            return new NewsDetail(title, content, reporter, date, url, press, categoryId, categoryName);
            
        } catch (Exception e) {
            log.error("상세 크롤링 실패: {}", e.getMessage());
            return null;
        }
    }
} 