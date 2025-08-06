package com.news.news_crawler_similar_check.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsListCrawlRequestDto {
    private Integer countPerCategory = 100;  // 카테고리당 크롤링할 기사 개수
    private List<String> categories;  // 특정 카테고리만 크롤링할 경우 (null이면 전체 카테고리)
} 