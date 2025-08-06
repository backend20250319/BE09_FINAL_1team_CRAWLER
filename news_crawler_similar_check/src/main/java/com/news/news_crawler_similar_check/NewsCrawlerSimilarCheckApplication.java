package com.news.news_crawler_similar_check;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class NewsCrawlerSimilarCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsCrawlerSimilarCheckApplication.class, args);
    }

}
