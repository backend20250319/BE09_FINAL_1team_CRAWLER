package com.news.news_crawler.util;

import com.news.news_crawler.dto.NewsDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class NewsContentsCrawler {

    public static NewsDetail crawlNewsDetail(String url) {
        WebDriver driver = new ChromeDriver();
        try {
            // 페이지 로딩 (동적 렌더링 포함)
            driver.get(url);
            Thread.sleep(1000);  // JavaScript 렌더링 대기

            // 렌더링 완료된 HTML 가져오기
            String pageSource = driver.getPageSource();

            // Jsoup으로 파싱
            Document doc = Jsoup.parse(pageSource);

            String title = doc.selectFirst("#title_area > span").text();
            String content = doc.selectFirst("#dic_area").text();

            Element dateEl = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            String date = dateEl != null ? dateEl.attr("data-date-time") : "";

            String reporter = doc.selectFirst("#ct > div.media_end_head.go_trans > div.media_end_head_info.nv_notrans > div.media_end_head_journalist > a > em").text();

            return new NewsDetail(title, content, reporter, date, url);

        } catch (Exception e) {
            System.out.println("❌ 크롤링 실패: " + url);
            return null;
        } finally {
            driver.quit();
        }
    }
}
