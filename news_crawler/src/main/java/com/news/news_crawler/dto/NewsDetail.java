package com.news.news_crawler.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class NewsDetail {
    private String title;
    private String content;
    private String reporter;
    private String date;
    private String link;
    private String press;         // 추가
    private int categoryId;
    private String categoryName;

    public NewsDetail(String title, String reporter, String date, String link, String press, int categoryId, String categoryName, String content) {
        this.title = title;
        this.reporter = reporter;
        this.date = date;
        this.link = link;
        this.press = press;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.content = content;
    }
}
