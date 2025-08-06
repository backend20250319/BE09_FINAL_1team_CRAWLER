package com.news.news_crawler_similar_check.repository;

import com.news.news_crawler_similar_check.domain.NewsCrawlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

// 뉴스 상세 크롤링 데이터 DB 저장용 Repository
@Repository
public interface NewsCrawlRepository extends JpaRepository<NewsCrawlEntity, Long> {
    List<NewsCrawlEntity> findByLinkId(Long linkId);
    List<NewsCrawlEntity> findByDedupStatus(NewsCrawlEntity.DedupStatus dedupStatus);
    List<NewsCrawlEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<NewsCrawlEntity> findByCreatedAtAfter(LocalDateTime after);
}