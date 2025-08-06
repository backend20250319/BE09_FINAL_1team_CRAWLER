package com.news.news_crawler_similar_check.repository;

import com.news.news_crawler_similar_check.domain.PressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PressRepository extends JpaRepository<PressEntity, Long> {
    Optional<PressEntity> findByPressName(String pressName);
    List<PressEntity> findByBlacklistedFalse();
} 