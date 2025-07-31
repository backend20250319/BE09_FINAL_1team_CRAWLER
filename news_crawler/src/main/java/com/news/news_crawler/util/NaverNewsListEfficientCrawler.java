package com.news.news_crawler.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
        "연합뉴스", "동아일보", "중앙일보", "한겨레", "경향신문", "MBC",
        "파이낸셜뉴스", "국민일보", "서울경제", "한국일보", "헤럴드경제", "문화일보", "오마이뉴스", "SBS", "KBS"
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
        options.addArguments("--window-size=1920,1080");
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

            return new NewsItem(title, link, press);
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveToCsv(List<NewsItem> newsList, int categoryCode) {
        LocalDateTime now = LocalDateTime.now();
        String ampm = now.getHour() < 12 ? "am" : "pm";
        String dateFolderName = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)) + "_" + ampm;
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
                writer.println("title,link,press,category,timestamp");
            }
            DateTimeFormatter tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH);
            for (NewsItem news : newsList) {
                String timestamp = now.format(tsFormat);
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    escape(news.title), escape(news.link), escape(news.press),
                    categoryName, timestamp);
            }
            System.out.println("CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
        } catch (Exception e) {
            System.out.println("CSV 저장 실패: " + e.getMessage());
        }
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
