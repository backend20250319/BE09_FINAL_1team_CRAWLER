package com.news.news_crawler_similar_check.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_crawl")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsCrawlEntity {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "raw_id")
    private Long rawId;
    
    @Column(name = "link_id", nullable = false)
    private Long linkId;
    
    @Column(columnDefinition = "TEXT")
    private String link;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String puhblished_at;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "reporter_name", columnDefinition = "TEXT")
    private String reporterName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dedup_status")
    private DedupStatus dedupStatus;
    
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;
    
    public enum DedupStatus {
        PENDING, PROCESSED, DUPLICATE, UNIQUE
    }
}