package com.news.news_crawler_similar_check.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// FastAPI에 넘길 요청 값 (category, date, period 등)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DedupRequestDto {

    private String category;
    private String period;
    private String date;
    private float thresholdTitle = 0.3f;
    private float thresholdContent = 0.8f;
    private float thresholdRelatedMin = 0.4f;

}
