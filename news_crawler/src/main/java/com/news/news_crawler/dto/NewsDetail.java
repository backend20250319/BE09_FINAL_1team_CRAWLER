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
    private String press;         // 추가
    private int categoryId;
    private String categoryName;

    public NewsDetail(String title, String content, String reporter, String date, String link, String press, int categoryId, String categoryName) {
        this.title = title;
        this.content = content;
        this.reporter = reporter;
        this.date = date;
        this.link = link;
        this.press = press;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }
}
