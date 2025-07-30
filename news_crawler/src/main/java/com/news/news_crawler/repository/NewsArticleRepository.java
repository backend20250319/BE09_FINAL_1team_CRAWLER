package com.news.news_crawler.repository;

import com.news.news_crawler.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    
    // 카테고리별 뉴스 조회
    List<NewsArticle> findByCategoryIdOrderByCreatedAtDesc(Integer categoryId);
    
    // 언론사별 뉴스 조회
    List<NewsArticle> findByPressOrderByCreatedAtDesc(String press);
    
    // 제목으로 검색
    List<NewsArticle> findByTitleContainingOrderByCreatedAtDesc(String title);
    
    // 최근 뉴스 조회 (최신순)
    List<NewsArticle> findTop10ByOrderByCreatedAtDesc();
    
    // 카테고리별 최근 뉴스 조회
    @Query("SELECT n FROM NewsArticle n WHERE n.categoryId = :categoryId ORDER BY n.createdAt DESC")
    List<NewsArticle> findRecentByCategory(@Param("categoryId") Integer categoryId);
    
    // 중복 링크 체크
    boolean existsByLink(String link);
} 