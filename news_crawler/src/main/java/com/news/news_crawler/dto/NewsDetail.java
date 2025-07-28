package com.news.news_crawler.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewsDetail {
    private String title;
    private String content;
    private String reporter;
    private String date;
    private String link;

    public NewsDetail(String title, String content, String reporter, String date, String link) {
        this.title = title;
        this.content = content;
        this.reporter = reporter;
        this.date = date;
        this.link = link;
    }

    // Getter/Setter 생략 가능 (Lombok 사용 추천)
}
