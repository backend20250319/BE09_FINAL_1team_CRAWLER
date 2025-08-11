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
    private int newsCategoryId;
    private String newsCategoryName;
    private String imageUrl;
    private int trusted;
    private String oidAid;

    public NewsDetail(String title, String reporter, String date, String link, String press, int categoryId, String categoryName, String content, String imageUrl) {
        this.title = title;
        this.reporter = reporter;
        this.date = date;
        this.link = link;
        this.press = press;
        this.newsCategoryId = categoryId;
        this.newsCategoryName = categoryName;
        this.content = content;
        this.imageUrl = imageUrl;
        this.trusted = 1;
        this.oidAid = "";
    }
}
