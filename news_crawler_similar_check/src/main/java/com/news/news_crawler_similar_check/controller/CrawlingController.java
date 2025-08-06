package com.news.news_crawler_similar_check.controller;

import com.news.news_crawler_similar_check.dto.DedupRequestDto;
import com.news.news_crawler_similar_check.dto.DedupResponseDto;
import com.news.news_crawler_similar_check.dto.NewsListCrawlRequestDto;
import com.news.news_crawler_similar_check.service.DedupService;
import com.news.news_crawler_similar_check.service.NewsListCrawlingService;
import com.news.news_crawler_similar_check.service.NewsDetailCrawlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crawl")
public class CrawlingController {

    private final DedupService dedupService;
    private final NewsListCrawlingService newsListCrawlingService;
    private final NewsDetailCrawlingService newsDetailCrawlingService;

    @PostMapping("/dedup")
    public ResponseEntity<DedupResponseDto> runDedup(@RequestBody DedupRequestDto request) {
        log.info("중복 제거 요청: {}", request);
        String result = dedupService.triggerDedup(request);
        
        DedupResponseDto response = new DedupResponseDto();
        response.setMessage("중복 제거 완료");
        response.setResult(result);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dedup/status")
    public ResponseEntity<String> getDedupStatus() {
        return ResponseEntity.ok("중복 제거 서비스 정상 작동 중");
    }

    @PostMapping("/news-list")
    public ResponseEntity<String> crawlNewsList(@RequestBody NewsListCrawlRequestDto request) {
        log.info("뉴스 리스트 크롤링 요청: {}", request);
        newsListCrawlingService.crawlNewsList(request);
        return ResponseEntity.ok("뉴스 리스트 크롤링이 시작되었습니다.");
    }

    @PostMapping("/news-detail")
    public ResponseEntity<String> crawlNewsDetail() {
        log.info("뉴스 상세 크롤링 요청");
        newsDetailCrawlingService.crawlNewsDetails();
        return ResponseEntity.ok("뉴스 상세 크롤링이 시작되었습니다.");
    }

    @PostMapping("/full-crawl")
    public ResponseEntity<String> crawlFull(@RequestBody NewsListCrawlRequestDto request) {
        log.info("전체 크롤링 요청: {}", request);
        
        // 뉴스 리스트 크롤링 후 상세 크롤링
        newsListCrawlingService.crawlNewsList(request);
        
        // 잠시 대기 후 상세 크롤링
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 5초 대기
                newsDetailCrawlingService.crawlNewsDetails();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        return ResponseEntity.ok("전체 크롤링이 시작되었습니다.");
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, String>> getCategories() {
        return ResponseEntity.ok(Map.of(
            "정치", "정치",
            "경제", "경제", 
            "사회", "사회",
            "생활/문화", "생활/문화",
            "세계", "세계",
            "IT/과학", "IT/과학"
        ));
    }
}
