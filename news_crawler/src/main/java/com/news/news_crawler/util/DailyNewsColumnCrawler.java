package com.news.news_crawler.util;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class DailyNewsColumnCrawler {

    public static void main(String[] args) {

        WebDriver driver = new ChromeDriver();

        try {
            // 데일리 뉴스컬럼 페이지로 이동
            driver.get("http://www.xn--3e0bmoz6vw9pfpkh9g.kr/bbs/rwdboard");

            // 컬럼 목록이 로딩될 때까지 대기 (CSS 선택자도 실제 구조에 맞게 수정)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".list_webzine ul")));

            // 컬럼 기사 요소 모두 찾기
            List<WebElement> columns = driver.findElements(By.cssSelector(".list_webzine ul > li"));

            // 각 컬럼 기사에서 제목과 링크 추출
            for (WebElement column : columns) {
                WebElement linkElem = column.findElement(By.cssSelector("a"));
                String href = linkElem.getAttribute("href");
                String title = column.findElement(By.cssSelector(".subject span")).getText().trim();
                System.out.println("제목: " + title);
                System.out.println("링크: " + href);
                System.out.println("-----------------------");
            }
        } finally {
            driver.quit();
        }
    }
}
