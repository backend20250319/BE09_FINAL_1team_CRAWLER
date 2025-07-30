package com.news.news_crawler.util;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class NaverNewsListCrawler {

    // 카테고리 정보 정의
    private static final Map<Integer, String> CATEGORIES = Map.of(
        100, "정치",
        101, "경제", 
        102, "사회",
        103, "생활문화",
        104, "세계",
        105, "IT과학"
    );

    private static final Set<String> ALLOWED_PRESSES = Set.of(
        "연합뉴스", "동아일보", "중앙일보", "한겨레", "경향신문", "MBC", 
        "파이낸셜뉴스", "국민일보", "서울경제", "한국일보", "헤럴드경제", "문화일보", "오마이뉴스", "SBS", "KBS"
    );
        
    public static void main(String[] args) {
        // 사용 가능한 카테고리 출력
        System.out.println("=== 사용 가능한 카테고리 ===");
        CATEGORIES.forEach((code, name) -> 
            System.out.println(code + ": " + name));
        System.out.println("========================\n");
        
        // 모든 카테고리에 대해 크롤링 실행
        int targetCount = 100;
        
        // 명령행 인수로 목표 개수 받기
        if (args.length >= 1) {
            try {
                targetCount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("목표 개수는 숫자여야 합니다. 기본값(100)을 사용합니다.");
                targetCount = 100;
            }
        }
        
        System.out.println("모든 카테고리에 대해 각각 " + targetCount + "개씩 크롤링을 시작합니다.\n");
        
        // 모든 카테고리에 대해 순차적으로 크롤링
        for (Map.Entry<Integer, String> category : CATEGORIES.entrySet()) {
            int categoryCode = category.getKey();
            String categoryName = category.getValue();
            
            System.out.println("=== " + categoryName + " 카테고리 크롤링 시작 ===");
            List<NewsItem> newsList = crawlNewsList(categoryCode, targetCount);
            saveToCsv(newsList, categoryCode);
            System.out.println("=== " + categoryName + " 카테고리 크롤링 완료 ===\n");
            
            // 카테고리 간 잠시 대기 (서버 부하 방지)
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("모든 카테고리 크롤링이 완료되었습니다!");
    }
    
    /**
     * 네이버 뉴스 목록을 크롤링하는 메소드
     * @param categoryCode 카테고리 코드 (100~105)
     * @param targetCount 목표 수집 개수
     * @return 수집된 뉴스 아이템 리스트
     */
    public static List<NewsItem> crawlNewsList(int categoryCode, int targetCount) {
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> newsList = new ArrayList<>();
        
        try {
            // 카테고리별 URL 생성
            String categoryUrl = "https://news.naver.com/section/" + categoryCode;
            driver.get(categoryUrl);
            
            System.out.println("크롤링 시작: " + categoryUrl);
            System.out.println("카테고리: " + CATEGORIES.get(categoryCode) + "\n");
            
            int prevArticleSize = 0;

            while (collectedLinks.size() < targetCount) {
                List<WebElement> articles = driver.findElements(
                        By.cssSelector("#newsct > div.section_latest > div > div.section_latest_article._CONTENT_LIST._PERSIST_META > div > ul > li")
                );

                for (WebElement article : articles) {
                    try {
                        NewsItem newsItem = extractNewsItem(article);
                        
                        if (newsItem == null) continue;
                        if (collectedLinks.contains(newsItem.link)) continue;
                        
                        collectedLinks.add(newsItem.link);
                        newsList.add(newsItem);
                        
                        // 크롤링 현황 출력
                        displayCrawlingProgress(collectedLinks.size(), newsItem);

                        if (newsList.size() >= targetCount) break;

                    } catch (Exception e) {
                        System.out.println("기사 파싱 실패: " + e.getMessage());
                    }
                }

                if (articles.size() == prevArticleSize) {
                    System.out.println("더 이상 새로운 기사가 없습니다.");
                    break;
                }
                prevArticleSize = articles.size();

                if (!clickMoreButton(wait)) {
                    break;
                }
            }

            System.out.println("총 수집된 기사 수: " + newsList.size());
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
        
        return newsList;
    }
    
    /**
     * 웹 요소에서 뉴스 아이템을 추출하는 메소드
     * @param article 웹 요소
     * @return 뉴스 아이템 (조건에 맞지 않으면 null)
     */
    private static NewsItem extractNewsItem(WebElement article) {
        WebElement titleElement = article.findElement(By.cssSelector("div.sa_text > a"));
        WebElement pressElement = article.findElement(By.cssSelector("div.sa_text_info > div.sa_text_info_left > div.sa_text_press"));

        String title = titleElement.getText();
        String link = titleElement.getAttribute("href");
        String press = pressElement.getText();

        // 허용된 언론사인지 확인
        if (ALLOWED_PRESSES.stream().noneMatch(p -> p.equalsIgnoreCase(press.trim()))) {
            return null;
        }

        return new NewsItem(title, link, press);
    }
    
    /**
     * 크롤링 현황을 콘솔에 출력하는 메소드
     * @param currentCount 현재 수집된 개수
     * @param newsItem 수집된 뉴스 아이템
     */
    private static void displayCrawlingProgress(int currentCount, NewsItem newsItem) {
        System.out.println("번호: " + currentCount);
        System.out.println("제목: " + newsItem.title);   
        System.out.println("링크: " + newsItem.link);
        System.out.println("언론사: " + newsItem.press);
        System.out.println("----------------------------");
    }
    
    /**
     * 더보기 버튼을 클릭하는 메소드
     * @param wait WebDriverWait 객체
     * @return 클릭 성공 여부
     */
    private static boolean clickMoreButton(WebDriverWait wait) {
        try {
            WebElement moreBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#newsct > div.section_latest > div > div.section_more > a")
            ));
            moreBtn.click();
            Thread.sleep(1000);
            return true;
        } catch (Exception e) {
            System.out.println("더보기 버튼 없음 또는 클릭 실패");
            return false;
        }
    }

    /**
     * 뉴스 리스트를 CSV 파일로 저장하는 메소드
     * @param newsList 저장할 뉴스 리스트
     * @param categoryCode 카테고리 코드
     */
    private static void saveToCsv(List<NewsItem> newsList, int categoryCode) {
        // 현재 시간 가져오기
        LocalDateTime now = LocalDateTime.now();
    
        // AM/PM 구분
        String ampm = now.getHour() < 12 ? "am" : "pm";
    
        // 날짜 폴더명
        String dateFolderName = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_a", Locale.ENGLISH));
    
        // 카테고리 이름
        String categoryName = CATEGORIES.get(categoryCode);
        String fileName = "naver_news_" + categoryName + "_" + ampm + ".csv";
    
        // 최종 저장 경로: static/am/yyyy-MM-dd/ 또는 static/pm/yyyy-MM-dd/
        File baseDir = new File("news_crawler/src/main/resources/static");
        File ampmFolder = new File(baseDir, ampm);
        File dateFolder = new File(ampmFolder, dateFolderName);
        if (!dateFolder.exists()) {
            dateFolder.mkdirs();
        }
    
        File file = new File(dateFolder, fileName);
    
        try (
            FileOutputStream fos = new FileOutputStream(file, true);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter bw = new BufferedWriter(osw);
            PrintWriter writer = new PrintWriter(bw)
        ) {
            // 파일이 새로 만들어졌다면 헤더 추가
            if (file.length() == 0) {
                writer.println("title,link,press,category,timestamp");
            }
    
            // 시간 포맷: 2025-07-28 AM 10:15:23
            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH);
    
            for (NewsItem news : newsList) {
                String timestamp = now.format(timestampFormatter);
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        escape(news.title), escape(news.link), escape(news.press), 
                        categoryName, timestamp);
            }
    
            System.out.println("CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
    
        } catch (Exception e) {
            System.out.println("CSV 저장 실패: " + e.getMessage());
        }
    }
    

    /**
     * CSV에서 사용할 문자열 이스케이프 처리
     * @param text 원본 텍스트
     * @return 이스케이프된 텍스트
     */
    private static String escape(String text) {
        return text.replace("\"", "\"\"");
    }

    static class NewsItem {
        String title;
        String link;
        String press;

        public NewsItem(String title, String link, String press) {
            this.title = title;
            this.link = link;
            this.press = press;
        }
    }
}