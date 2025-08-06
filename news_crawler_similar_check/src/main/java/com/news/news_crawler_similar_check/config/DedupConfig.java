package com.news.news_crawler_similar_check.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dedup")
public class DedupConfig {
    private Api api = new Api();
    
    @Data
    public static class Api {
        private String url;
    }
} 