package com.news.news_crawler.util;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
//4
public class NaverInfiniteScrollCrawler {

    public static void main(String[] args) {

        WebDriver driver = new ChromeDriver();

        try {
            driver.get("https://www.naver.com");
            WebElement searchBox = driver.findElement(By.id("query"));
            searchBox.sendKeys("맛집");
            searchBox.submit();

            // (필요 시) 블로그/뉴스 탭으로 이동
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement blogTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("블로그")));
            blogTab.click();

            // 무한 스크롤 처리
            JavascriptExecutor js = (JavascriptExecutor) driver;
            int lastHeight = ((Number) js.executeScript("return document.body.scrollHeight")).intValue();

            while (true) {
                // 스크롤 맨 아래로 이동
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(2000); // 데이터 로딩 대기

                int newHeight = ((Number) js.executeScript("return document.body.scrollHeight")).intValue();
                if (newHeight == lastHeight) {
                    break; // 더 이상 로딩되는 데이터가 없으면 종료
                }
                lastHeight = newHeight;
            }

            // 스크롤 완료 후 데이터 수집 (예: 블로그 제목)
            List<WebElement> blogTitles = driver.findElements(By.cssSelector(".title_area"));
            for (WebElement title : blogTitles) {
                System.out.println(title.getText());
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
