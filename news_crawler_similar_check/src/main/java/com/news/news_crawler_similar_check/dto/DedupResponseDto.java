package com.news.news_crawler_similar_check.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// FastAPI 응답 결과 (dedup_file, related_file, log_file, count 등)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DedupResponseDto {
    private String message;
    private Object result;
    private String dedupFile;
    private String relatedFile;
    private String logFile;
    private Integer count;
}
