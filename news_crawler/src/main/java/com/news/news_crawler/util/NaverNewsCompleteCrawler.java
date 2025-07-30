package com.news.news_crawler.util;

import com.news.news_crawler.dto.NewsDetail;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaverNewsCompleteCrawler {
    
    // 카테고리 정보 정의
    private static final Map<Integer, String> CATEGORIES = Map.of(
        100, "정치",
        101, "경제", 
        102, "사회",
        103, "생활/문화",
        104, "세계",
        105, "IT/과학"
    );
    
    public static void main(String[] args) {
        // Chrome 옵션 최적화
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // 브라우저 창 숨기기 (성능 향상)
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images"); // 이미지 로딩 비활성화
        
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3)); // 대기 시간 단축

        int targetCountPerCategory = 1; // 카테고리별 20개씩
        List<NewsDetail> completeNewsList = new ArrayList<>();
        Set<String> allowedPresses = Set.of("연합뉴스", "동아일보", "중앙일보", "한겨레", "경향신문", "MBC", "파이낸셜뉴스", "국민일보", "서울경제", "한국일보","헤럴드경제", "YTN", "문화일보", "오마이뉴스", "SBS", "KBS");

        try {
            System.out.println("=== 네이버 뉴스 전체 카테고리 크롤링 시작 (최적화 버전) ===");
            
            // 각 카테고리별로 순회
            for (Map.Entry<Integer, String> category : CATEGORIES.entrySet()) {
                int categoryId = category.getKey();
                String categoryName = category.getValue();
                
                System.out.println("\n🎯 [" + categoryId + "] " + categoryName + " 카테고리 크롤링 시작");
                
                Set<String> collectedLinks = new HashSet<>();
                int prevArticleSize = 0;
                
                // 카테고리별 URL 접속
                String categoryUrl = "https://news.naver.com/section/" + categoryId;
                driver.get(categoryUrl);
                Thread.sleep(1000); // 페이지 로딩 대기 시간 단축
                
                // 해당 카테고리에서 뉴스 수집
                while (collectedLinks.size() < targetCountPerCategory) {
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
                            System.out.println("[" + categoryName + "] [" + collectedLinks.size() + "/" + targetCountPerCategory + "] 제목: " + title);
                            System.out.println("언론사: " + press);

                            // 빠른 상세 내용 크롤링 (Jsoup 직접 사용)
                            NewsDetail detail = crawlNewsDetailFast(link, title, press, categoryName, categoryId);
                            if (detail != null) {
                                completeNewsList.add(detail);
                                System.out.println("✅ 상세 내용 크롤링 완료");
                            } else {
                                System.out.println("❌ 상세 내용 크롤링 실패");
                            }
                            System.out.println("----------------------------");

                            if (collectedLinks.size() >= targetCountPerCategory) break;

                        } catch (Exception e) {
                            // 에러 무시하고 계속 진행
                            System.out.println("기사 파싱 실패: " + e.getMessage());
                        }
                    }

                    if (articles.size() == prevArticleSize) {
                        System.out.println("더 이상 새로운 기사가 없습니다.");
                        break;
                    }
                    prevArticleSize = articles.size();

                    try {
                        WebElement moreBtn = wait.until(ExpectedConditions.elementToBeClickable(
                                By.cssSelector("#newsct > div.section_latest > div > div.section_more > a")
                        ));
                        moreBtn.click();
                        Thread.sleep(500); // 대기 시간 단축
                    } catch (Exception e) {
                        System.out.println("더보기 버튼 없음 또는 클릭 실패");
                        break;
                    }
                }
                
                System.out.println("✅ [" + categoryName + "] 카테고리 완료: " + collectedLinks.size() + "개 수집");
            }

            System.out.println("\n🎉 전체 카테고리 크롤링 완료!");
            System.out.println("총 수집된 기사 수: " + completeNewsList.size());

            // CSV 저장
            saveCompleteToCsv(completeNewsList);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    // 빠른 상세 내용 크롤링 (Jsoup 직접 사용)
    private static NewsDetail crawlNewsDetailFast(String url, String title, String press, String categoryName, int categoryId) {
        try {
            // Jsoup으로 직접 크롤링 (Selenium보다 훨씬 빠름)
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000) // 5초 타임아웃
                    .get();

            // 기자 정보 추출 (우선순위 1: 지정된 필드)
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

            // 우선순위 2: 본문에서 기자 이름 추출 (지정된 필드에 정보가 없을 경우)
            if (reporter.isEmpty() && !content.isEmpty()) {
                // 본문 마지막 100자에서 기자 이름 검색
                int searchWindowSize = 100;
                int startIndex = Math.max(0, content.length() - searchWindowSize);
                String searchArea = content.substring(startIndex);

                Pattern pattern = Pattern.compile("([가-힣]{2,5}\s*(기자|특파원|객원기자|통신원))");
                Matcher matcher = pattern.matcher(searchArea);

                String foundReporter = "";
                int matchPosInSearchArea = -1;

                // 검색 영역 내에서 마지막으로 일치하는 항목 찾기
                while (matcher.find()) {
                    foundReporter = matcher.group(1).trim();
                    matchPosInSearchArea = matcher.start();
                }

                // 일치하는 항목을 찾았다면, 본문에서 해당 내용을 정리
                if (!foundReporter.isEmpty()) {
                    reporter = foundReporter;
                    // 원본 본문에서 실제 위치를 찾아 제거
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
            // 실패 시 null 반환 (에러 무시하고 계속 진행)
            return null;
        }
    }

    // 기존 방식 (백업용, 필요시 사용)
    private static NewsDetail crawlNewsDetail(WebDriver driver, String url, String title, String press, String categoryName, int categoryId) {
        try {
            // 새 탭에서 열기 (메인 페이지 유지)
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.open('" + url + "', '_blank');");
            
            // 새 탭으로 전환
            String originalWindow = driver.getWindowHandle();
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // 페이지 로딩 대기
            Thread.sleep(1000);

            // 렌더링 완료된 HTML 가져오기
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            // 네이버 뉴스 본문 추출
            String content = "";
            Element contentElement = doc.selectFirst("#dic_area");
            if (contentElement != null) {
                content = contentElement.text();
            }

            // 기자 정보 추출
            String reporter = "";
            Element reporterElement = doc.selectFirst("#ct > div.media_end_head.go_trans > div.media_end_head_info.nv_notrans > div.media_end_head_journalist > a > em");
            if (reporterElement != null) {
                reporter = reporterElement.text();
            }

            // 날짜 정보 추출
            String date = "";
            Element dateElement = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            if (dateElement != null) {
                date = dateElement.attr("data-date-time");
            }

            // 원래 탭으로 돌아가기
            driver.close();
            driver.switchTo().window(originalWindow);

            return new NewsDetail(title, "", "", "", url, press, categoryId, categoryName);

        } catch (Exception e) {
            System.out.println("상세 크롤링 실패: " + e.getMessage());
            // 실패 시에도 원래 탭으로 돌아가기
            try {
                String originalWindow = driver.getWindowHandle();
                for (String windowHandle : driver.getWindowHandles()) {
                    if (!windowHandle.equals(originalWindow)) {
                        driver.switchTo().window(windowHandle);
                        driver.close();
                        break;
                    }
                }
                driver.switchTo().window(originalWindow);
            } catch (Exception ex) {
                // 탭 전환 실패 무시
            }
            return null;
        }
    }

    private static void saveCompleteToCsv(List<NewsDetail> newsList) {
        DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_a_hh-mm-ss", Locale.ENGLISH);
        String formattedTime = LocalDateTime.now().format(fileNameFormatter);
        String fileName = "naver_news_all_categories_" + formattedTime + ".csv";

        // 상대 경로로 수정하여 OS 독립적으로 만듦
        File directory = new File("src/main/resources/static");
        if (!directory.exists()) {
            directory.mkdirs(); // 폴더가 없으면 생성
        }
        File file = new File(directory, fileName);

        try (
            FileOutputStream fos = new FileOutputStream(file, true);
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

            System.out.println("전체 카테고리 뉴스 데이터 CSV 저장 완료: " + fileName);

        } catch (Exception e) {
            System.out.println("CSV 저장 실패: " + e.getMessage());
        }
    }
    
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\""); // 큰따옴표 이스케이프
    }
} 