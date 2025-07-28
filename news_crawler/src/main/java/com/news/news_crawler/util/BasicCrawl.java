package com.news.news_crawler.util;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
// 1
public class BasicCrawl {

    public static void main(String[] args) {
        WebDriver driver = new ChromeDriver();
        driver.get("https://w3schools.com");

        WebElement heading = driver.findElement(By.tagName("h1"));
        System.out.println("제목: " + heading.getText());

        driver.quit();
    }
}
