package com.news.news_crawler.controller;

import com.news.news_crawler.service.NewsCrawlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/crawling")
public class NewsCrawlingController {
    
    private static final Logger logger = LoggerFactory.getLogger(NewsCrawlingController.class);
    
    @Autowired
    private NewsCrawlingService newsCrawlingService;
    
    /**
     * 전체 크롤링 프로세스를 수동으로 실행
     */
    @GetMapping("/start")
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCrawling() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🚀 수동 크롤링 요청 받음");
            
            // 비동기로 크롤링 실행
            CompletableFuture<Void> future = newsCrawlingService.runFullCrawlingProcessAsync();
            
            response.put("status", "success");
            response.put("message", "크롤링이 시작되었습니다. 백그라운드에서 실행 중입니다.");
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            logger.info("✅ 크롤링 시작 응답 전송");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ 크롤링 시작 실패: " + e.getMessage(), e);
            
            response.put("status", "error");
            response.put("message", "크롤링 시작 중 오류가 발생했습니다: " + e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 크롤링 상태 확인
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCrawlingStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("status", "running");
        response.put("message", "크롤링 서비스가 실행 중입니다.");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("currentTime", java.time.LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 특정 단계만 실행 (테스트용)
     */
    @GetMapping("/step/{stepNumber}")
    @PostMapping("/step/{stepNumber}")
    public ResponseEntity<Map<String, Object>> runSpecificStep(@PathVariable("stepNumber") int stepNumber) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String stepName = "";
            
            switch (stepNumber) {
                case 1:
                    stepName = "뉴스 목록 크롤링";
                    newsCrawlingService.runNewsListCrawling();
                    break;
                case 2:
                    stepName = "뉴스 상세 크롤링";
                    newsCrawlingService.runNewsDetailCrawling();
                    break;
                case 3:
                    stepName = "중복 제거 처리";
                    newsCrawlingService.runDeduplicationProcess();
                    break;
                case 4:
                    stepName = "데이터베이스 저장";
                    newsCrawlingService.runDatabaseInsertion();
                    break;
                default:
                    response.put("status", "error");
                    response.put("message", "잘못된 단계 번호입니다. 1-4 사이의 값을 입력하세요.");
                    return ResponseEntity.badRequest().body(response);
            }
            
            response.put("status", "success");
            response.put("message", stepName + " 단계가 실행되었습니다.");
            response.put("stepNumber", stepNumber);
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ 단계별 크롤링 실패: " + e.getMessage(), e);
            
            response.put("status", "error");
            response.put("message", "단계별 크롤링 중 오류가 발생했습니다: " + e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 크롤링 스케줄 정보 조회
     */
    @GetMapping("/schedule")
    public ResponseEntity<Map<String, Object>> getScheduleInfo() {
        Map<String, Object> response = new HashMap<>();
        
        Map<String, String> schedules = new HashMap<>();
        schedules.put("daily", "매일 자정 (0 0 0 * * *)");
        schedules.put("twiceDaily", "매일 오전 9시, 오후 6시 (0 0 9,18 * * *)");
        schedules.put("hourly", "매시간 정각 (0 0 * * * *)");
        
        response.put("status", "success");
        response.put("schedules", schedules);
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
}
