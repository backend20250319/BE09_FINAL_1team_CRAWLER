package com.news.news_crawler_similar_check.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "press")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PressEntity {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "press_id")
    private Long pressId;
    
    @Column(name = "press_name", columnDefinition = "TEXT", nullable = false)
    private String pressName;
    
    @Column(name = "blacklisted", nullable = false)
    private Boolean blacklisted = false;
} 