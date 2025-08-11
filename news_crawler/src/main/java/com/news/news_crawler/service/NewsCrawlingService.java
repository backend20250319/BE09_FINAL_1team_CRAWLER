package com.news.news_crawler.service;

import com.news.news_crawler.util.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NewsCrawlingService {
    
    private static final Logger logger = LoggerFactory.getLogger(NewsCrawlingService.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    
    /**
     * 전체 크롤링 프로세스를 순차적으로 실행
     */
    public void runFullCrawlingProcess() {
        logger.info("🚀 전체 크롤링 프로세스 시작");
        
        try {
            // 1단계: 뉴스 목록 크롤링
            logger.info("📰 1단계: 뉴스 목록 크롤링 시작");
            runNewsListCrawling();
            
            // 2단계: 뉴스 상세 크롤링
            logger.info("📄 2단계: 뉴스 상세 크롤링 시작");
            runNewsDetailCrawling();
            
            // 3단계: 중복 제거 처리 (Python 스크립트 실행)
            logger.info("🔍 3단계: 중복 제거 처리 시작");
            runDeduplicationProcess();
            
            // 4단계: 데이터베이스 저장
            logger.info("💾 4단계: 데이터베이스 저장 시작");
            runDatabaseInsertion();
            
            logger.info("✅ 전체 크롤링 프로세스 완료!");
            
        } catch (Exception e) {
            logger.error("❌ 크롤링 프로세스 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * 뉴스 목록 크롤링 실행
     */
    public void runNewsListCrawling() {
        try {
            // NaverNewsListEfficientCrawler의 main 메서드 호출
            String[] args = {"100"}; // 목표 개수 100개
            NaverNewsListEfficientCrawler.main(args);
            logger.info("✅ 뉴스 목록 크롤링 완료");
        } catch (Exception e) {
            logger.error("❌ 뉴스 목록 크롤링 실패: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 뉴스 상세 크롤링 실행
     */
    public void runNewsDetailCrawling() {
        try {
            // NewsDetailBatchProcessor의 main 메서드 호출
            NewsDetailBatchProcessor.main(new String[]{});
            logger.info("✅ 뉴스 상세 크롤링 완료");
        } catch (Exception e) {
            logger.error("❌ 뉴스 상세 크롤링 실패: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 중복 제거 처리 실행 (Python 스크립트)
     */
    public void runDeduplicationProcess() {
        try {
            // Python 스크립트 실행을 위한 ProcessBuilder 사용
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // 현재 작업 디렉토리를 프로젝트 루트로 설정
            String projectRoot = System.getProperty("user.dir");
            processBuilder.directory(new File(projectRoot));
            
            // Python 스크립트 실행 명령어
            String pythonScript = "duplicate_detector/run_all_categories.py";
            
            // 운영체제에 따라 명령어 설정
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("python", pythonScript);
            } else {
                processBuilder.command("python3", pythonScript);
            }
            
            logger.info("🐍 Python 중복 제거 스크립트 실행: " + pythonScript);
            
            Process process = processBuilder.start();

            // 표준 출력과 오류 출력을 실시간으로 읽기
            java.io.BufferedReader outputReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            java.io.BufferedReader errorReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream())
            );
            
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            // 비동기로 출력 읽기
            Thread outputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        output.append(line).append("\n");
                        logger.info("🐍 Python 출력: " + line);
                    }
                } catch (Exception e) {
                    logger.error("Python 출력 읽기 오류: " + e.getMessage());
                }
            });
            
            Thread errorThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                        logger.error("🐍 Python 오류: " + line);
                    }
                } catch (Exception e) {
                    logger.error("Python 오류 출력 읽기 오류: " + e.getMessage());
                }
            });
            
            outputThread.start();
            errorThread.start();

            // 프로세스 완료 대기 (최대 30분)
            int exitCode = process.waitFor();
            
            // 스레드들이 완료될 때까지 대기
            outputThread.join(5000);
            errorThread.join(5000);

            if (exitCode == 0) {
                logger.info("✅ 중복 제거 처리 완료");
                logger.info("Python 스크립트 출력: " + output.toString());
            } else {
                logger.error("❌ 중복 제거 처리 실패 (종료 코드: " + exitCode + ")");
                logger.error("❌ Python 스크립트 오류 출력: " + errorOutput.toString());
                throw new RuntimeException("중복 제거 처리 실패 - Python 스크립트 오류: " + errorOutput.toString());
            }
            
        } catch (Exception e) {
            logger.error("❌ 중복 제거 처리 중 오류: " + e.getMessage(), e);
            throw new RuntimeException("중복 제거 처리 실패", e);
        }
    }
    
    /**
     * 데이터베이스 저장 실행
     */
    public void runDatabaseInsertion() {
        try {
            // CsvToDatabase의 main 메서드 호출
            CsvToDatabase.main(new String[]{});
            logger.info("✅ 데이터베이스 저장 완료");
        } catch (Exception e) {
            logger.error("❌ 데이터베이스 저장 실패: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 비동기로 크롤링 프로세스 실행
     */
    public CompletableFuture<Void> runFullCrawlingProcessAsync() {
        return CompletableFuture.runAsync(this::runFullCrawlingProcess, executorService);
    }
    
    /**
     * 서비스 종료 시 리소스 정리
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
