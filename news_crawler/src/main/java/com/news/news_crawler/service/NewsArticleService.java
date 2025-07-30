package com.news.news_crawler.service;

import com.news.news_crawler.dto.NewsDetail;
import com.news.news_crawler.entity.NewsArticle;
import com.news.news_crawler.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NewsArticleService {
    
    private final NewsArticleRepository newsArticleRepository;
    
    /**
     * 뉴스 기사 저장
     */
    public NewsArticle saveNewsArticle(NewsDetail newsDetail) {
        // 중복 체크
        if (newsArticleRepository.existsByLink(newsDetail.getLink())) {
            log.info("이미 존재하는 뉴스 링크: {}", newsDetail.getLink());
            return null;
        }
        
        NewsArticle newsArticle = NewsArticle.builder()
                .categoryId(newsDetail.getCategoryId())
                .categoryName(newsDetail.getCategoryName())
                .press(newsDetail.getPress())
                .title(newsDetail.getTitle())
                .content(newsDetail.getContent())
                .reporter(newsDetail.getReporter())
                .date(newsDetail.getDate())
                .link(newsDetail.getLink())
                .build();
        
        NewsArticle saved = newsArticleRepository.save(newsArticle);
        log.info("뉴스 기사 저장 완료: {}", saved.getTitle());
        return saved;
    }
    
    /**
     * 여러 뉴스 기사 일괄 저장
     */
    public List<NewsArticle> saveNewsArticles(List<NewsDetail> newsDetails) {
        return newsDetails.stream()
                .map(this::saveNewsArticle)
                .filter(article -> article != null)
                .toList();
    }
    
    /**
     * 모든 뉴스 기사 조회
     */
    @Transactional(readOnly = true)
    public List<NewsArticle> getAllNewsArticles() {
        return newsArticleRepository.findAll();
    }
    
    /**
     * 최근 뉴스 기사 10개 조회
     */
    @Transactional(readOnly = true)
    public List<NewsArticle> getRecentNewsArticles() {
        return newsArticleRepository.findTop10ByOrderByCreatedAtDesc();
    }
    
    /**
     * 카테고리별 뉴스 기사 조회
     */
    @Transactional(readOnly = true)
    public List<NewsArticle> getNewsArticlesByCategory(Integer categoryId) {
        return newsArticleRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId);
    }
    
    /**
     * 언론사별 뉴스 기사 조회
     */
    @Transactional(readOnly = true)
    public List<NewsArticle> getNewsArticlesByPress(String press) {
        return newsArticleRepository.findByPressOrderByCreatedAtDesc(press);
    }
    
    /**
     * 제목으로 뉴스 기사 검색
     */
    @Transactional(readOnly = true)
    public List<NewsArticle> searchNewsArticlesByTitle(String title) {
        return newsArticleRepository.findByTitleContainingOrderByCreatedAtDesc(title);
    }
    
    /**
     * ID로 뉴스 기사 조회
     */
    @Transactional(readOnly = true)
    public Optional<NewsArticle> getNewsArticleById(Long id) {
        return newsArticleRepository.findById(id);
    }
    
    /**
     * 저장된 뉴스 기사 수 조회
     */
    @Transactional(readOnly = true)
    public long getNewsArticleCount() {
        return newsArticleRepository.count();
    }
} 