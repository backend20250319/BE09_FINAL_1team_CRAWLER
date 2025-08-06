package com.news.news_crawler_similar_check.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// news_links 테이블: 기사 목록 (링크 정보)
@Entity
@Table(name = "news_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsLinksEntity {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long linkId;
    
    @Column(name = "category_id", nullable = false)
    private Long categoryId;
    
    @Column(name = "press_id", nullable = false)
    private Long pressId;
    
    @Column(name = "title", columnDefinition = "TEXT", nullable = false)
    private String title;
    
    @Column(name = "source_url", columnDefinition = "TEXT", nullable = false)
    private String sourceUrl;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
} 