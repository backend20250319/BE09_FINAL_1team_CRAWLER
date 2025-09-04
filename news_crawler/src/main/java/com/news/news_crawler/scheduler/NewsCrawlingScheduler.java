package com.news.news_crawler.scheduler;

import com.news.news_crawler.service.NewsCrawlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class NewsCrawlingScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(NewsCrawlingScheduler.class);
    
    @Autowired
    private NewsCrawlingService newsCrawlingService;
    
    /**
     * 매일 오전 9시와 오후 6시에 크롤링 실행
     * cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 9,19 * * *") // 매일 오전 9시, 오후 6시
    public void scheduledCrawling() {
        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        logger.info("⏰ 스케줄된 크롤링 시작: " + formattedTime);
        
        try {
            // 비동기로 크롤링 실행 (백그라운드에서 실행)
            newsCrawlingService.runFullCrawlingProcessAsync()
                .thenRun(() -> {
                    LocalDateTime endTime = LocalDateTime.now();
                    String endFormattedTime = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logger.info("스케줄된 크롤링 완료: " + endFormattedTime);
                })
                .exceptionally(throwable -> {
                    logger.error("스케줄된 크롤링 실패: " + throwable.getMessage(), throwable);
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("스케줄된 크롤링 시작 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 매일 자정에 크롤링 실행 (테스트용)
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void dailyCrawling() {
        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        logger.info("일일 크롤링 시작: " + formattedTime);
        
        try {
            newsCrawlingService.runFullCrawlingProcessAsync()
                .thenRun(() -> {
                    LocalDateTime endTime = LocalDateTime.now();
                    String endFormattedTime = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logger.info("일일 크롤링 완료: " + endFormattedTime);
                })
                .exceptionally(throwable -> {
                    logger.error("일일 크롤링 실패: " + throwable.getMessage(), throwable);
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("일일 크롤링 시작 실패: " + e.getMessage(), e);
        }
    }
    
}
