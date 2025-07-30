package com.news.news_crawler.controller;

import com.news.news_crawler.entity.NewsArticle;
import com.news.news_crawler.service.NewsArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {
    
    private final NewsArticleService newsArticleService;
    
    /**
     * 모든 뉴스 기사 조회
     */
    @GetMapping
    public ResponseEntity<List<NewsArticle>> getAllNews() {
        List<NewsArticle> news = newsArticleService.getAllNewsArticles();
        return ResponseEntity.ok(news);
    }
    
    /**
     * 최근 뉴스 기사 10개 조회
     */
    @GetMapping("/recent")
    public ResponseEntity<List<NewsArticle>> getRecentNews() {
        List<NewsArticle> news = newsArticleService.getRecentNewsArticles();
        return ResponseEntity.ok(news);
    }
    
    /**
     * ID로 뉴스 기사 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<NewsArticle> getNewsById(@PathVariable Long id) {
        Optional<NewsArticle> news = newsArticleService.getNewsArticleById(id);
        return news.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 카테고리별 뉴스 기사 조회
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<NewsArticle>> getNewsByCategory(@PathVariable Integer categoryId) {
        List<NewsArticle> news = newsArticleService.getNewsArticlesByCategory(categoryId);
        return ResponseEntity.ok(news);
    }
    
    /**
     * 언론사별 뉴스 기사 조회
     */
    @GetMapping("/press/{press}")
    public ResponseEntity<List<NewsArticle>> getNewsByPress(@PathVariable String press) {
        List<NewsArticle> news = newsArticleService.getNewsArticlesByPress(press);
        return ResponseEntity.ok(news);
    }
    
    /**
     * 제목으로 뉴스 기사 검색
     */
    @GetMapping("/search")
    public ResponseEntity<List<NewsArticle>> searchNewsByTitle(@RequestParam String title) {
        List<NewsArticle> news = newsArticleService.searchNewsArticlesByTitle(title);
        return ResponseEntity.ok(news);
    }
    
    /**
     * 저장된 뉴스 기사 수 조회
     */
    @GetMapping("/count")
    public ResponseEntity<Long> getNewsCount() {
        long count = newsArticleService.getNewsArticleCount();
        return ResponseEntity.ok(count);
    }
    
    /**
     * 카테고리별 뉴스 기사 수 조회
     */
    @GetMapping("/category/{categoryId}/count")
    public ResponseEntity<Long> getNewsCountByCategory(@PathVariable Integer categoryId) {
        List<NewsArticle> news = newsArticleService.getNewsArticlesByCategory(categoryId);
        return ResponseEntity.ok((long) news.size());
    }
} 