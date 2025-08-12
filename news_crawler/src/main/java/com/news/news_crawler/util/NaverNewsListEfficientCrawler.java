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
        
        // 자동차 카테고리 크롤링 추가
        System.out.println("\n자동차 카테고리 크롤링을 시작합니다.");
        processVehicleCategory();
        
        // 생활 카테고리 크롤링 추가
        System.out.println("\n생활 카테고리 크롤링을 시작합니다.");
        processLifeCategory();
        
        // 여행 카테고리 크롤링 추가
        System.out.println("\n여행 카테고리 크롤링을 시작합니다.");
        processTravelCategory();
        
        // 예술 카테고리 크롤링 추가
        System.out.println("\n예술 카테고리 크롤링을 시작합니다.");
        processArtCategory();
        
        // 패션뷰티티 카테고리 크롤링 추가
        System.out.println("\n패션뷰티 카테고리 크롤링을 시작합니다.");
        processFashionCategory();
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
            String[] filteredKeywords = {"운세", "시사", "칼럼", "컬럼", "Deep Read", "이우석의 푸드로지", "가정예배", "기고", "리포트", "프로젝트", "오늘의 운세"};
            
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

    /**
     * 자동차 카테고리에서 100개 기사를 수집하는 메서드
     * https://news.naver.com/breakingnews/section/103/239 (50개)
     * https://news.naver.com/breakingnews/section/103/240 (50개)
     */
    private static void processVehicleCategory() {
        System.out.println("[자동차] 크롤링 시작 - 목표: 40개 (자동차/시승기: 35개, 도로/교통: 5개개)");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> batch = new ArrayList<>();

        try {
            // 첫 번째 페이지 (239) - 35개 수집
            String url1 = "https://news.naver.com/breakingnews/section/103/239";
            System.out.println("[자동차] 첫 번째 페이지 크롤링 중: " + url1);
            
            driver.get(url1);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc1 = Jsoup.parse(driver.getPageSource());
            Elements articles1 = doc1.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles1) {
                if (collectedLinks.size() >= 35) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[자동차] 수집 %d/50: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            // 두 번째 페이지 (240) - 5개 수집
            String url2 = "https://news.naver.com/breakingnews/section/103/240";
            System.out.println("[자동차] 두 번째 페이지 크롤링 중: " + url2);
            
            driver.get(url2);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc2 = Jsoup.parse(driver.getPageSource());
            Elements articles2 = doc2.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles2) {
                if (collectedLinks.size() >= 40) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[자동차] 수집 %d/100: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            System.out.printf("[자동차] 수집 완료 - 총 %d개%n", batch.size());
            
            // 자동차 카테고리로 저장
            saveCarCategoryToCsv(batch);

        } catch (Exception e) {
            System.out.println("[오류] 자동차 카테고리: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    /**
     * 자동차 카테고리 기사를 CSV로 저장하는 메서드
     */
    private static void saveCarCategoryToCsv(List<NewsItem> newsList) {
        String ampm = DateTimeUtils.getCurrentPeriodLower();
        String dateFolderName = DateTimeUtils.getCurrentDatePeriod();
        String fileName = "naver_news_자동차_" + ampm + ".csv";

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
                    "자동차", timestamp);
            }
            System.out.println("자동차 CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
        } catch (Exception e) {
            System.out.println("자동차 CSV 저장 실패: " + e.getMessage());
        }
    }

    /**
     * 생활 카테고리에서 100개 기사를 수집하는 메서드
     * https://news.naver.com/breakingnews/section/103/241 (30개)
     * https://news.naver.com/breakingnews/section/103/248 (30개)
     * https://news.naver.com/breakingnews/section/103/245 (40개)
     */
    private static void processLifeCategory() {
        System.out.println("[생활] 크롤링 시작 - 목표: 100개 (241: 30개, 248: 30개, 245: 40개)");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> batch = new ArrayList<>();

        try {
            // 첫 번째 페이지 (241) - 30개 수집
            String url1 = "https://news.naver.com/breakingnews/section/103/241";
            System.out.println("[생활] 첫 번째 페이지 크롤링 중: " + url1);
            
            driver.get(url1);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc1 = Jsoup.parse(driver.getPageSource());
            Elements articles1 = doc1.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles1) {
                if (collectedLinks.size() >= 30) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[생활] 수집 %d/30: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            // 두 번째 페이지 (248) - 40개 수집
            String url2 = "https://news.naver.com/breakingnews/section/103/248";
            System.out.println("[생활] 두 번째 페이지 크롤링 중: " + url2);
            
            driver.get(url2);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc2 = Jsoup.parse(driver.getPageSource());
            Elements articles2 = doc2.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles2) {
                if (collectedLinks.size() >= 70) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[생활] 수집 %d/60: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            // 세 번째 페이지 (245) - 50개 수집
            String url3 = "https://news.naver.com/breakingnews/section/103/245";
            System.out.println("[생활] 세 번째 페이지 크롤링 중: " + url3);
            
            driver.get(url3);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc3 = Jsoup.parse(driver.getPageSource());
            Elements articles3 = doc3.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles3) {
                if (collectedLinks.size() >= 120) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[생활] 수집 %d/100: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            System.out.printf("[생활] 수집 완료 - 총 %d개%n", batch.size());
            
            // 생활 카테고리로 저장
            saveLifeCategoryToCsv(batch);

        } catch (Exception e) {
            System.out.println("[오류] 생활 카테고리: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    /**
     * 생활 카테고리 기사를 CSV로 저장하는 메서드
     */
    private static void saveLifeCategoryToCsv(List<NewsItem> newsList) {
        String ampm = DateTimeUtils.getCurrentPeriodLower();
        String dateFolderName = DateTimeUtils.getCurrentDatePeriod();
        String fileName = "naver_news_생활_" + ampm + ".csv";

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
                    "생활", timestamp);
            }
            System.out.println("생활 CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
        } catch (Exception e) {
            System.out.println("생활 CSV 저장 실패: " + e.getMessage());
        }
    }

    /**
     * 여행 카테고리에서 100개 기사를 수집하는 메서드
     * https://news.naver.com/breakingnews/section/103/237 (50개)
     * https://news.naver.com/breakingnews/section/103/238 (50개)
     */
    private static void processTravelCategory() {
        System.out.println("[여행] 크롤링 시작 - 목표: 50개 (여행/레저: 40개, 음식/맛집: 10개)");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> batch = new ArrayList<>();

        try {
            // 첫 번째 페이지 (237) - 50개 수집
            String url1 = "https://news.naver.com/breakingnews/section/103/237";
            System.out.println("[여행] 첫 번째 페이지 크롤링 중: " + url1);
            
            driver.get(url1);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc1 = Jsoup.parse(driver.getPageSource());
            Elements articles1 = doc1.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles1) {
                if (collectedLinks.size() >= 40) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[여행] 수집 %d/50: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            // 두 번째 페이지 (238) - 50개 수집
            String url2 = "https://news.naver.com/breakingnews/section/103/238";
            System.out.println("[여행] 두 번째 페이지 크롤링 중: " + url2);
            
            driver.get(url2);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc2 = Jsoup.parse(driver.getPageSource());
            Elements articles2 = doc2.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles2) {
                if (collectedLinks.size() >= 10) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[여행] 수집 %d/100: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            System.out.printf("[여행] 수집 완료 - 총 %d개%n", batch.size());
            
            // 여행 카테고리로 저장
            saveTravelCategoryToCsv(batch);

        } catch (Exception e) {
            System.out.println("[오류] 여행 카테고리: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    /**
     * 여행 카테고리 기사를 CSV로 저장하는 메서드
     */
    private static void saveTravelCategoryToCsv(List<NewsItem> newsList) {
        String ampm = DateTimeUtils.getCurrentPeriodLower();
        String dateFolderName = DateTimeUtils.getCurrentDatePeriod();
        String fileName = "naver_news_여행_" + ampm + ".csv";

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
                    "여행", timestamp);
            }
            System.out.println("여행 CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
        } catch (Exception e) {
            System.out.println("여행 CSV 저장 실패: " + e.getMessage());
        }
    }

    /**
     * 예술 카테고리에서 60개 기사를 수집하는 메서드
     * https://news.naver.com/breakingnews/section/103/242 (45개)
     * https://news.naver.com/breakingnews/section/103/243 (15개)
     */
    private static void processArtCategory() {
        System.out.println("[예술] 크롤링 시작 - 목표: 60개 (242: 45개, 243: 15개)");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> batch = new ArrayList<>();

        try {
            // 첫 번째 페이지 (242) - 45개 수집
            String url1 = "https://news.naver.com/breakingnews/section/103/242";
            System.out.println("[예술] 첫 번째 페이지 크롤링 중: " + url1);
            
            driver.get(url1);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc1 = Jsoup.parse(driver.getPageSource());
            Elements articles1 = doc1.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles1) {
                if (collectedLinks.size() >= 45) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[예술] 수집 %d/45: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            // 두 번째 페이지 (243) - 15개 수집
            String url2 = "https://news.naver.com/breakingnews/section/103/243";
            System.out.println("[예술] 두 번째 페이지 크롤링 중: " + url2);
            
            driver.get(url2);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc2 = Jsoup.parse(driver.getPageSource());
            Elements articles2 = doc2.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles2) {
                if (collectedLinks.size() >= 60) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[예술] 수집 %d/60: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            System.out.printf("[예술] 수집 완료 - 총 %d개%n", batch.size());
            
            // 예술 카테고리로 저장
            saveArtCategoryToCsv(batch);

        } catch (Exception e) {
            System.out.println("[오류] 예술 카테고리: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    /**
     * 예술 카테고리 기사를 CSV로 저장하는 메서드
     */
    private static void saveArtCategoryToCsv(List<NewsItem> newsList) {
        String ampm = DateTimeUtils.getCurrentPeriodLower();
        String dateFolderName = DateTimeUtils.getCurrentDatePeriod();
        String fileName = "naver_news_예술_" + ampm + ".csv";

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
                    "예술", timestamp);
            }
            System.out.println("예술 CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
        } catch (Exception e) {
            System.out.println("예술 CSV 저장 실패: " + e.getMessage());
        }
    }

    /**
     * 패션 카테고리에서 15개 기사를 수집하는 메서드
     * https://news.naver.com/breakingnews/section/103/376 (15개)
     */
    private static void processFashionCategory() {
        System.out.println("[패션뷰티] 크롤링 시작 - 목표: 15개");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        Set<String> collectedLinks = new HashSet<>();
        List<NewsItem> batch = new ArrayList<>();

        try {
            // 패션/뷰티 페이지 (376) - 15개 수집
            String url = "https://news.naver.com/breakingnews/section/103/376";
            System.out.println("[패션] 페이지 크롤링 중: " + url);
            
            driver.get(url);
            Thread.sleep(2000); // 페이지 로딩 대기
            
            Document doc = Jsoup.parse(driver.getPageSource());
            Elements articles = doc.select("#newsct div.section_latest_article ul li");
            
            for (Element article : articles) {
                if (collectedLinks.size() >= 15) break;
                NewsItem newsItem = extractNewsItem(article);
                
                if (newsItem != null && collectedLinks.add(newsItem.link)) {
                    batch.add(newsItem);
                    System.out.printf("[패션뷰티] 수집 %d/15: %s%n", collectedLinks.size(), newsItem.title);
                }
            }

            System.out.printf("[패션뷰티] 수집 완료 - 총 %d개%n", batch.size());
            
            // 패션 카테고리로 저장
            saveFashionCategoryToCsv(batch);

        } catch (Exception e) {
            System.out.println("[오류] 패션뷰티 카테고리: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    /**
     * 패션 카테고리 기사를 CSV로 저장하는 메서드
     */
    private static void saveFashionCategoryToCsv(List<NewsItem> newsList) {
        String ampm = DateTimeUtils.getCurrentPeriodLower();
        String dateFolderName = DateTimeUtils.getCurrentDatePeriod();
        String fileName = "naver_news_패션뷰티_" + ampm + ".csv";

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
                    "패션뷰티", timestamp);
            }
            System.out.println("패션뷰티 CSV 저장 완료: " + ampm + "/" + dateFolderName + "/" + fileName);
        } catch (Exception e) {
            System.out.println("패션뷰티 CSV 저장 실패: " + e.getMessage());
        }
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
