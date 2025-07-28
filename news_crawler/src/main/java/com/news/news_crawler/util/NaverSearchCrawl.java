package com.news.news_crawler.util;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

// 2
public class NaverSearchCrawl {

    public static void main(String[] args) {
        // 크롬드라이버 자동 설치
        WebDriver driver = new ChromeDriver();

        try {
            // 네이버 메인 접속
            driver.get("https://www.naver.com");

            // 검색창 찾기 (id="query")
            WebElement searchBox = driver.findElement(By.id("query"));
            searchBox.sendKeys("날씨");
            searchBox.submit(); // 엔터

            // 페이지 로딩 대기
            Thread.sleep(2000);

            // 뉴스 탭 클릭
            WebElement newsTab = driver.findElement(By.linkText("뉴스"));
            newsTab.click();

            Thread.sleep(2000);

            // 뉴스
            WebElement newsData = driver.findElement(By.cssSelector(".sds-comps-vertical-layout.sds-comps-full-layout"));
            System.out.println("뉴스 내용: " + newsData.getText());

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            driver.quit();
        }
    }
}
