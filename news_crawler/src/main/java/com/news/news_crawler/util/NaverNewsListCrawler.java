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
    
    public static void main(String[] args) {

        // String csvPath = "C:\\dev\\BE09_FINAL_1team_CRAWLER\\news_crawler\\src\\main\\resources\\static\\naver_news.csv";

        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        // todo : switch문 

        int targetCount = 100;
        Set<String> collectedLinks = new HashSet<>(); // 현재 수집 실행 중 링크만 추적
        List<NewsItem> newsList = new ArrayList<>();
        Set<String> allowedPresses = Set.of("연합뉴스", "동아일보", "중앙일보", "한겨레", "경향신문", "MBC", "파이낸셜뉴스", "국민일보", "서울경제", "한국일보","헤럴드경제", "YTN", "문화일보", "오마이뉴스", "SBS", "KBS");


        try {
            driver.get("https://news.naver.com/section/100");

            int prevArticleSize = 0;

            while (collectedLinks.size() < targetCount) {
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
                        if (collectedLinks.contains(link)) continue; // 이번 실행에서만 중복 제거

                        collectedLinks.add(link);

                        System.out.println("번호: " + collectedLinks.size());
                        System.out.println("제목: " + title);   
                        System.out.println("링크: " + link);
                        System.out.println("언론사: " + press);
                        System.out.println("----------------------------");

                        newsList.add(new NewsItem(title, link, press));

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

                try {
                    WebElement moreBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector("#newsct > div.section_latest > div > div.section_more > a")
                    ));
                    moreBtn.click();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.out.println("더보기 버튼 없음 또는 클릭 실패");
                    break;
                }
            }

            System.out.println("총 수집된 기사 수: " + newsList.size());

            // CSV 저장
            saveToCsv(newsList);            

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
    

        private static void saveToCsv(List<NewsItem> newsList) {
            // 날짜+시간 기반 파일명 생성
            DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_a_hh-mm-ss", Locale.ENGLISH);
            String formattedTime = LocalDateTime.now().format(fileNameFormatter);
            String fileName = "naver_news_" + formattedTime + ".csv";

            File file = new File("C:\\dev\\BE09_FINAL_1team_CRAWLER\\n" + //
                                "ews_crawler\\src\\main\\resources\\static\\" + fileName); // 경로는 원하는 폴더로 설정

            try (
                FileOutputStream fos = new FileOutputStream(file, true); // append = true
                OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                BufferedWriter bw = new BufferedWriter(osw);
                PrintWriter writer = new PrintWriter(bw)
            ) {
                writer.println("title,link,press,timestamp");

                // 시간 포맷: 2025-07-28 AM 10:15:23
                DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH);

                for (NewsItem news : newsList) {
                    String timestamp = LocalDateTime.now().format(timestampFormatter);
                    writer.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n",
                            escape(news.title), escape(news.link), escape(news.press), timestamp);
                }

                System.out.println("CSV 저장 완료: " + fileName);

            } catch (Exception e) {
                System.out.println("CSV 저장 실패: " + e.getMessage());
            }
        }

    private static String escape(String text) {
        return text.replace("\"", "\"\""); // 큰따옴표 이스케이프
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