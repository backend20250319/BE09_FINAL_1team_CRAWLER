package com.news.news_crawler_similar_check.repository;

import com.news.news_crawler_similar_check.domain.NewsLinksEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsLinksRepository extends JpaRepository<NewsLinksEntity, Long> {
    List<NewsLinksEntity> findBySourceUrl(String sourceUrl);
    List<NewsLinksEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<NewsLinksEntity> findByCreatedAtAfter(LocalDateTime after);
    List<NewsLinksEntity> findByUpdatedAtAfter(LocalDateTime after);
} 