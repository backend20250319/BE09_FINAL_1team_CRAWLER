package com.news.news_crawler_similar_check.service;

import com.news.news_crawler_similar_check.dto.DedupRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DedupService {

    private final RestTemplate restTemplate;
    
    // 하드코딩된 URL 사용
    private static final String DEDUP_API_URL = "http://localhost:8084/api/dedup/run";

    public String triggerDedup(DedupRequestDto request) {
        try {
            log.info("중복 제거 API 호출: {}", DEDUP_API_URL);
            
            String result = restTemplate.postForObject(DEDUP_API_URL, request, String.class);
            log.info("중복 제거 API 응답: {}", result);
            
            return result != null ? result : "중복 제거 완료";
        } catch (Exception e) {
            log.error("중복 제거 API 호출 실패: {}", e.getMessage());
            return "중복 제거 실패: " + e.getMessage();
        }
    }
}

