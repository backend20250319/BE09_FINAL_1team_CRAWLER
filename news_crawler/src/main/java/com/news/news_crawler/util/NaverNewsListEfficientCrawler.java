package com.news.news_crawler.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import com.news.news_crawler.util.DateTimeUtils;

public class NaverNewsListEfficientCrawler {



    private static final Map<Integer, String> CATEGORIES = Map.of(
        100, "정치",
        101, "경제",
        102, "사회",
        103, "생활문화",
        104, "세계",
        105, "IT과학"
    );

    private static final Set<String> ALLOWED_PRESSES = Set.of(
        "경향신문", "국민일보", "동아일보", "문화일보", "서울신문", "조선일보", "중앙일보", "한겨레", "한국일보", "뉴스1", "뉴시스", "연합뉴스", "연합뉴스TV", "채널A", "한국경제TV", "JTBC", "KBS", "MBC", "MBN", "SBS", "SBS Biz", "TV조선", "YTN", "매일경제", "머니투데이", "비즈워치", "서울경제", "아시아경제", "이데일리", "조선비즈", "파이낸셜뉴스", "한국경제", "헤럴드경제", "디지털데일리", "디지털타임스", "블로터", "전자신문", "지디넷코리아"
    );

    public static void main(String[] args) {
        int targetCount = args.length >= 1 ? parseTargetCount(args[0]) : 100;

        System.out.println("모든 카테고리에 대해 각각 " + targetCount + "개씩 크롤링을 시작합니다.\n");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();

        for (Map.Entry<Integer, String> category : CATEGORIES.entrySet()) {
            final int categoryCode = category.getKey();
            futures.add(executor.submit(() -> processCategory(categoryCode, targetCount)));
        }

        executor.shutdown();
        try {
            for (Future<?> future : futures) future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("모든 카테고리 크롤링이 완료되었습니다!");
    }

    private static int parseTargetCount(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            System.out.println("목표 개수는 숫자여야 합니다. 기본값(100)을 사용합니다.");
            return 100;
        }
    }

    private static void processCategory(int categoryCode, int targetCount) {

        String categoryName = CATEGORIES.get(categoryCode);
        System.out.printf("[%s] 크롤링 시작 - 목표: %d개%n", categoryName, targetCount);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--headless"); // 

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> batch = new ArrayList<>();

        try {
            String url = "https://news.naver.com/section/" + categoryCode;
            driver.get(url);

            while (collectedLinks.size() < targetCount) {
                if (!clickMoreButton(wait)) break;
            }

            Document doc = Jsoup.parse(driver.getPageSource());

            Elements articles = doc.select("#newsct div.section_latest_article ul li");
            for (Element article : articles) {
                
                if (collectedLinks.size() >= targetCount) break;
                NewsItem newsItem = extractNewsItem(article);

                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);

                System.out.printf("[%s] 수집 %d/%d: %s%n", categoryName, collectedLinks.size(), targetCount, newsItem.title);
                }

            }
            System.out.printf("[%s] 수집 완료 - 총 %d개%n", categoryName, batch.size());
            
            saveToCsv(batch, categoryCode);

        } catch (Exception e) {
            System.out.println("[오류] 카테고리 " + categoryCode + ": " + e.getMessage());
        } finally {
            driver.quit();
        }
    }

    private static boolean clickMoreButton(WebDriverWait wait) {
        try {
            WebElement moreBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#newsct > div.section_latest > div > div.section_more > a")
            ));
            moreBtn.click();
            Thread.sleep(1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static NewsItem extractNewsItem(Element article) {
        try {
            Element titleEl = article.selectFirst("div.sa_text > a");
            Element pressEl = article.selectFirst("div.sa_text_info_left > div.sa_text_press");
            if (titleEl == null || pressEl == null) return null;

            String title = titleEl.text();
            String link = titleEl.absUrl("href");
            String press = pressEl.text();

            if (!ALLOWED_PRESSES.contains(press.trim())) return null;
            
            // 대괄호 안에 "시사", "칼럼", "컬럼"이 포함된 기사 필터링
            if (containsFilteredKeywords(title)) {
                return null;
            }

            return new NewsItem(title, link, press);
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveToCsv(List<NewsItem> newsList, int categoryCode) {
        String ampm = DateTimeUtils.getCurrentPeriodLower();
        String dateFolderName = DateTimeUtils.getCurrentDatePeriod();
        String categoryName = CATEGORIES.get(categoryCode);
        String fileName = "naver_news_" + categoryName + "_" + ampm + ".csv";

        File baseDir = new File("news_crawler/src/main/resources/static");
        File ampmFolder = new File(baseDir, ampm);
        File dateFolder = new File(ampmFolder, dateFolderName);
        if (!dateFolder.exists()) dateFolder.mkdirs();

        File file = new File(dateFolder, fileName);
        try (
            FileOutputStream fos = new FileOutputStream(file, true);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter bw = new BufferedWriter(osw);
            PrintWriter writer = new PrintWriter(bw)
        ) {
            if (file.length() == 0) {
                writer.println("title,link,press,news_category,published_at");
            }
            for (NewsItem news : newsList) {
                String timestamp = DateTimeUtils.getCurrentTimestamp();
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
     * 제목에서 대괄호 안에 필터링할 키워드가 포함되어 있는지 확인
     * [시사], [*시사], [*시사*], [시사*] 등 모든 패턴을 감지
     */
    private static boolean containsFilteredKeywords(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        
        // 대괄호 안의 내용을 찾는 정규식 패턴
        java.util.regex.Pattern bracketPattern = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]");
        java.util.regex.Matcher matcher = bracketPattern.matcher(title);
        
        while (matcher.find()) {
            String bracketContent = matcher.group(1).toLowerCase().trim();
            
            // 필터링할 키워드들 (와일드카드 패턴도 고려)
            String[] filteredKeywords = {"시사", "칼럼", "컬럼", "Deep Read", "이우석의 푸드로지", "가정예배", "기고", "리포트", "프로젝트"};
            
            for (String keyword : filteredKeywords) {
                // 키워드가 대괄호 내용에 포함되어 있는지 확인
                // 예: [시사], [*시사], [시사*], [*시사*], [시사칼럼] 등 모두 감지
                if (bracketContent.contains(keyword)) {
                    System.out.println("[필터링] 제외된 기사: " + title);
                    return true;
                }
            }
        }
        
        return false;
    }

    private static String escape(String text) {
        return text.replace("\"", "\"\"");
    }

    static class NewsItem {
        String title, link, press;
        NewsItem(String title, String link, String press) {
            this.title = title;
            this.link = link;
            this.press = press;
        }
    }
}
