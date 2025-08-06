package com.news.news_crawler_similar_check.service;

import com.news.news_crawler_similar_check.domain.NewsLinksEntity;
import com.news.news_crawler_similar_check.domain.NewsCrawlEntity;
import com.news.news_crawler_similar_check.repository.NewsLinksRepository;
import com.news.news_crawler_similar_check.repository.NewsCrawlRepository;
import com.news.news_crawler_similar_check.repository.PressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsDetailCrawlingService {

    private final NewsLinksRepository newsLinksRepository;
    private final NewsCrawlRepository newsCrawlRepository;
    private final PressRepository pressRepository;

    // 성능 최적화를 위한 상수들
    private static final int BATCH_SIZE = 8;
    private static final int INITIAL_CONCURRENT_REQUESTS = 3;
    private static final int MAX_CONCURRENT_REQUESTS = 6;
    private static final int MIN_CONCURRENT_REQUESTS = 1;
    private static final int CONNECTION_TIMEOUT = 20000;
    private static final int RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY = 2000;
    private static final double SUCCESS_RATE_THRESHOLD = 0.7;

    // 진행 상황 추적을 위한 Atomic 변수들
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger currentConcurrency = new AtomicInteger(INITIAL_CONCURRENT_REQUESTS);

    @Async
    @Transactional
    public void crawlNewsDetails() {
        log.info("뉴스 상세 크롤링 시작");
        
        // DB에서 최근 크롤링된 뉴스 링크 가져오기 (news_links 테이블)
        List<NewsLinksEntity> newsLinks = getRecentNewsLinks();
        
        if (newsLinks.isEmpty()) {
            log.warn("크롤링할 뉴스 링크가 없습니다.");
            return;
        }

        log.info("총 {}개의 뉴스 링크를 읽었습니다.", newsLinks.size());

        // 배치 단위로 병렬 처리
        List<NewsCrawlEntity> detailedNewsList = processNewsLinksInBatches(newsLinks);

        log.info("=== 크롤링 결과 ===");
        log.info("성공: {}개", successCount.get());
        log.info("실패: {}개", failCount.get());
        log.info("성공률: {}%", (successCount.get() * 100.0 / newsLinks.size()));

        // 결과를 DB에 저장 (news_crawl 테이블)
        if (!detailedNewsList.isEmpty()) {
            newsCrawlRepository.saveAll(detailedNewsList);
            log.info("상세 뉴스 데이터 DB 저장 완료: {}개 (news_crawl)", detailedNewsList.size());
        }
    }

    private List<NewsLinksEntity> getRecentNewsLinks() {
        // 최근 1시간 내 크롤링된 뉴스 링크 가져오기
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        return newsLinksRepository.findByCreatedAtAfter(oneHourAgo);
    }

    private List<NewsCrawlEntity> processNewsLinksInBatches(List<NewsLinksEntity> newsLinks) {
        List<NewsCrawlEntity> allResults = Collections.synchronizedList(new ArrayList<>());
        int totalBatches = (int) Math.ceil((double) newsLinks.size() / BATCH_SIZE);
        
        log.info("배치 크기: {}, 총 배치 수: {}", BATCH_SIZE, totalBatches);
        log.info("스마트 병렬 처리 시작...");

        // 배치별로 스마트 병렬 처리
        for (int i = 0; i < totalBatches; i++) {
            int startIndex = i * BATCH_SIZE;
            int endIndex = Math.min(startIndex + BATCH_SIZE, newsLinks.size());
            List<NewsLinksEntity> batch = newsLinks.subList(startIndex, endIndex);
            
            final int batchNumber = i + 1;
            log.info("배치 {} 시작 (링크 {}-{})", batchNumber, (startIndex + 1), endIndex);
            log.info("현재 동시 요청 수: {}", currentConcurrency.get());
            
            // 스마트 배치 처리
            processBatchSmart(batch, allResults, batchNumber);
            
            // 성공률에 따른 동시성 조절
            adjustConcurrency();
            
            log.info("배치 {} 완료", batchNumber);
            
            // 배치 간 짧은 대기 (서버 부하 방지)
            if (i < totalBatches - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("모든 배치 처리 완료!");
        return allResults;
    }

    private void processBatchSmart(List<NewsLinksEntity> batch, List<NewsCrawlEntity> allResults, int batchNumber) {
        log.info("배치 {}: {}개 링크 스마트 병렬 처리 시작", batchNumber, batch.size());
        
        // 현재 동시성 수만큼 병렬 처리
        int concurrency = currentConcurrency.get();
        List<CompletableFuture<NewsCrawlEntity>> futures = new ArrayList<>();
        
        for (int i = 0; i < batch.size(); i++) {
            NewsLinksEntity newsLink = batch.get(i);
            final int linkIndex = i + 1;
            
            CompletableFuture<NewsCrawlEntity> future = CompletableFuture.supplyAsync(() -> {
                log.info("배치 {} - 링크 {}/{} 크롤링 중: {}...", 
                    batchNumber, linkIndex, batch.size(), 
                    newsLink.getSourceUrl().substring(0, Math.min(30, newsLink.getSourceUrl().length())));
                return crawlNewsDetailWithRetry(newsLink);
            });
            
            futures.add(future);
            
            // 동시성 제한: 현재 설정된 동시성 수만큼만 동시 실행
            if (futures.size() >= concurrency) {
                waitForFutures(futures, allResults, batchNumber);
                futures.clear();
            }
        }
        
        // 남은 futures 처리
        if (!futures.isEmpty()) {
            waitForFutures(futures, allResults, batchNumber);
        }
        
        log.info("배치 {} 완료: 성공 {}개, 실패 {}개", batchNumber, successCount.get(), failCount.get());
    }

    private void waitForFutures(List<CompletableFuture<NewsCrawlEntity>> futures, 
                               List<NewsCrawlEntity> allResults, int batchNumber) {
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<NewsCrawlEntity> future = futures.get(i);
            try {
                NewsCrawlEntity detail = future.get(45, TimeUnit.SECONDS); // 45초 타임아웃
                
                if (detail != null) {
                    allResults.add(detail);
                    successCount.incrementAndGet();
                    log.info("✅ 배치 {} - 링크 성공", batchNumber);
                } else {
                    failCount.incrementAndGet();
                    log.info("❌ 배치 {} - 링크 실패", batchNumber);
                }
                
                int processed = processedCount.incrementAndGet();
                if (processed % 5 == 0) {
                    log.info("전체 진행률: {}개 처리 완료", processed);
                }
                
            } catch (InterruptedException | ExecutionException e) {
                failCount.incrementAndGet();
                log.error("❌ 배치 {} - 링크 처리 중 오류: {}", batchNumber, e.getMessage());
            } catch (TimeoutException e) {
                failCount.incrementAndGet();
                log.error("❌ 배치 {} - 링크 타임아웃 (45초 초과)", batchNumber);
                future.cancel(true);
            }
        }
    }

    private void adjustConcurrency() {
        int total = successCount.get() + failCount.get();
        if (total < 5) return; // 최소 5개 처리 후 조절
        
        double successRate = (double) successCount.get() / total;
        int current = currentConcurrency.get();
        
        if (successRate >= SUCCESS_RATE_THRESHOLD) {
            // 성공률이 높으면 동시성 증가
            if (current < MAX_CONCURRENT_REQUESTS) {
                currentConcurrency.incrementAndGet();
                log.info("성공률 {}% - 동시성 증가: {} → {}", 
                    String.format("%.1f", successRate * 100), current, (current + 1));
            }
        } else {
            // 성공률이 낮으면 동시성 감소
            if (current > MIN_CONCURRENT_REQUESTS) {
                currentConcurrency.decrementAndGet();
                log.info("성공률 {}% - 동시성 감소: {} → {}", 
                    String.format("%.1f", successRate * 100), current, (current - 1));
            }
        }
    }

    private NewsCrawlEntity crawlNewsDetailWithRetry(NewsLinksEntity newsLink) {
        for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
            try {
                NewsCrawlEntity detail = crawlNewsDetailOptimized(newsLink);
                
                if (detail != null) {
                    return detail;
                }
                
                // 실패 시 재시도 전 대기
                if (attempt < RETRY_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY * attempt); // 지수 백오프
                }
                
            } catch (Exception e) {
                if (attempt < RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return null;
    }

    private NewsCrawlEntity crawlNewsDetailOptimized(NewsLinksEntity newsLink) {
        try {
            // 연결 설정 최적화
            Connection connection = Jsoup.connect(newsLink.getSourceUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1");

            Document doc = connection.get();
            
            // 기자 정보 추출
            String reporter = extractReporter(doc);
            
            // 네이버 뉴스 본문 추출
            String content = "";
            Element contentElement = doc.selectFirst("#dic_area");
            if (contentElement != null) {
                content = contentElement.text();
            }

            // 본문에서 기자 이름 추출 (지정된 필드에 정보가 없을 경우)
            if (reporter.isEmpty() && !content.isEmpty()) {
                reporter = extractReporterFromContent(content);
            }

            // 날짜 정보 추출
            String date = "";
            Element dateElement = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            if (dateElement != null) {
                date = dateElement.attr("data-date-time");
            }

            return NewsCrawlEntity.builder()
                .linkId(newsLink.getLinkId())  // news_links.link_id 참조
                .link(newsLink.getSourceUrl())
                .content(content)
                .createdAt(LocalDateTime.now())
                .reporterName(reporter)
                .dedupStatus(NewsCrawlEntity.DedupStatus.PENDING)
                .imageUrl("")
                .build();

        } catch (Exception e) {
            log.error("크롤링 실패 ({}): {}", newsLink.getSourceUrl(), e.getMessage());
            return null;
        }
    }

    private String extractReporter(Document doc) {
        // 우선순위 1: 일반적인 기자 정보 필드
        Elements reporterElements = doc.select("#ct > div.media_end_head.go_trans > div.media_end_head_info.nv_notrans > div.media_end_head_journalist > a > em");
        if (!reporterElements.isEmpty()) {
            List<String> reporterNames = new ArrayList<>();
            for (Element element : reporterElements) {
                String reporterName = element.text().trim();
                if (!reporterName.isEmpty()) {
                    reporterNames.add(cleanReporterName(reporterName));
                }
            }
            if (!reporterNames.isEmpty()) {
                return String.join(", ", reporterNames);
            }
        }

        // 우선순위 2: 여러 기자인 경우의 선택자
        Elements multiReporterElements = doc.select("#_JOURNALIST_BUTTON > em");
        if (!multiReporterElements.isEmpty()) {
            List<String> reporterNames = new ArrayList<>();
            for (Element element : multiReporterElements) {
                String reporterName = element.text().trim();
                if (!reporterName.isEmpty()) {
                    reporterNames.add(cleanReporterName(reporterName));
                }
            }
            if (!reporterNames.isEmpty()) {
                return String.join(", ", reporterNames);
            }
        }

        // 우선순위 3: 대체 선택자에서 기자 정보 추출
        Elements bylineSpans = doc.select("#contents > div.byline > p > span");
        if (!bylineSpans.isEmpty()) {
            List<String> reporterParts = new ArrayList<>();
            for (Element span : bylineSpans) {
                String spanText = span.text().trim();
                if (!spanText.isEmpty()) {
                    // 첫 번째 띄어쓰기 또는 괄호까지의 글자만 추출
                    int spaceIndex = spanText.indexOf(' ');
                    int parenthesisIndex = spanText.indexOf('(');
                    
                    int endIndex = -1;
                    if (spaceIndex > 0 && parenthesisIndex > 0) {
                        endIndex = Math.min(spaceIndex, parenthesisIndex);
                    } else if (spaceIndex > 0) {
                        endIndex = spaceIndex;
                    } else if (parenthesisIndex > 0) {
                        endIndex = parenthesisIndex;
                    }
                    
                    if (endIndex > 0) {
                        reporterParts.add(cleanReporterName(spanText.substring(0, endIndex)));
                    } else {
                        reporterParts.add(cleanReporterName(spanText));
                    }
                }
            }
            if (!reporterParts.isEmpty()) {
                return String.join(", ", reporterParts);
            }
        }

        return "";
    }

    private String cleanReporterName(String reporterName) {
        if (reporterName == null || reporterName.trim().isEmpty()) {
            return "";
        }
        
        String cleaned = reporterName.trim();
        
        // "기자"로 끝나는 경우만 제거
        if (cleaned.endsWith(" 기자")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        } else if (cleaned.endsWith("기자")) {
            cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
        }
        
        // 다른 직책들도 제거
        String[] titles = {" 특파원", "특파원", " 객원기자", "객원기자", " 통신원", "통신원"};
        for (String title : titles) {
            if (cleaned.endsWith(title)) {
                cleaned = cleaned.substring(0, cleaned.length() - title.length()).trim();
                break;
            }
        }
        
        return cleaned;
    }

    private String extractReporterFromContent(String content) {
        if (content.isEmpty()) return "";
        
        // 본문 마지막 100자에서 기자 이름 검색
        int searchWindowSize = 100;
        int startIndex = Math.max(0, content.length() - searchWindowSize);
        String searchArea = content.substring(startIndex);

        Pattern pattern = Pattern.compile("([가-힣]{2,5}\\s*(기자|특파원|객원기자|통신원))");
        Matcher matcher = pattern.matcher(searchArea);

        String foundReporter = "";
        int matchPosInSearchArea = -1;

        // 검색 영역 내에서 마지막으로 일치하는 항목 찾기
        while (matcher.find()) {
            foundReporter = matcher.group(1).trim();
            matchPosInSearchArea = matcher.start();
        }

        return foundReporter;
    }

    public String getPressName(Long pressId) {
        return pressRepository.findById(pressId)
                .map(press -> press.getPressName())
                .orElse("알 수 없음");
    }
} 