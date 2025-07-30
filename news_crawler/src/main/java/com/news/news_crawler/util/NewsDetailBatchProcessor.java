package com.news.news_crawler.util;

import com.news.news_crawler.dto.NewsDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

public class NewsDetailBatchProcessor {

    // 성능 최적화를 위한 상수들
    private static final int BATCH_SIZE = 8; // 배치 크기 (적당한 크기)
    private static final int INITIAL_CONCURRENT_REQUESTS = 3; // 초기 동시 요청 수
    private static final int MAX_CONCURRENT_REQUESTS = 6; // 최대 동시 요청 수
    private static final int MIN_CONCURRENT_REQUESTS = 1; // 최소 동시 요청 수
    private static final int CONNECTION_TIMEOUT = 20000; // 연결 타임아웃 (20초)
    private static final int READ_TIMEOUT = 40000; // 읽기 타임아웃 (40초)
    private static final int RETRY_ATTEMPTS = 2; // 재시도 횟수
    private static final long RETRY_DELAY = 2000; // 재시도 간격 (2초)
    private static final long REQUEST_DELAY = 500; // 요청 간격 (500ms)
    private static final double SUCCESS_RATE_THRESHOLD = 0.7; // 성공률 임계값 (70%)

    // 연결 풀 관리를 위한 ExecutorService (동적 크기 조절)
    private static ExecutorService executorService;
    
    // 진행 상황 추적을 위한 Atomic 변수들
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);
    private static final AtomicInteger processedCount = new AtomicInteger(0);
    private static final AtomicInteger currentConcurrency = new AtomicInteger(INITIAL_CONCURRENT_REQUESTS);

    public static void main(String[] args) {
    try {
        executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        System.out.println("스마트 병렬 크롤러 시작 - 초기 동시 요청: " + INITIAL_CONCURRENT_REQUESTS);
        processCsvFilesAndCrawlDetails();
    } finally {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }
}


    public static void processCsvFilesAndCrawlDetails() {
        try {
            // static 폴더 경로
            File staticDirectory = new File("news_crawler/src/main/resources/static");
            if (!staticDirectory.exists()) {
                System.out.println("static 폴더가 존재하지 않습니다: " + staticDirectory.getAbsolutePath());
                return;
            }

            // 날짜 폴더 탐색, 최신 폴더 선정
            List<File> allDateFolders = new ArrayList<>();
            for (File ampmFolder : staticDirectory.listFiles(File::isDirectory)) {
                File[] dateFolders = ampmFolder.listFiles(File::isDirectory);
                if (dateFolders != null) {
                    allDateFolders.addAll(Arrays.asList(dateFolders));
                }
            }

            if (allDateFolders.isEmpty()) {
                System.out.println("날짜/시간 폴더를 찾을 수 없습니다.");
                return;
            }

            File latestFolder = allDateFolders.stream()
                    .filter(folder -> folder.getName().matches("\\d{4}-\\d{2}-\\d{2}_[AP]M"))
                    .max(Comparator.comparing(File::getName))
                    .orElse(null);

            if (latestFolder == null) {
                System.out.println("최신 날짜/시간 폴더를 찾을 수 없습니다.");
                return;
            }

            System.out.println("발견된 날짜/시간 폴더들:");
            for (File folder : allDateFolders) {
                String indicator = (folder.equals(latestFolder)) ? " (최신)" : "";
                System.out.println("- " + folder.getParentFile().getName() + "/" + folder.getName() + indicator);
            }

            // 가장 최신 폴더 안의 모든 CSV 파일 처리
            File[] csvFiles = latestFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
            if (csvFiles == null || csvFiles.length == 0) {
                System.out.println("CSV 파일을 찾을 수 없습니다.");
                return;
            }

            for (File csvFile : csvFiles) {
                System.out.println("\n=== " + csvFile.getName() + " 처리 시작 ===");
                processSingleCsvFileOptimized(csvFile);
                System.out.println("=== " + csvFile.getName() + " 처리 완료 ===\n");

                try {
                    Thread.sleep(1000); // 부하 방지용 딜레이
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("모든 CSV 파일 처리가 완료되었습니다!");
        } catch (Exception e) {
            System.err.println("CSV 파일 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static void processSingleCsvFileOptimized(File csvFile) {    
        try {
            List<NewsLinkInfo> newsLinks = readLinksFromCsv(csvFile);
            System.out.println("총 " + newsLinks.size() + "개의 링크를 읽었습니다.");

            if (newsLinks.isEmpty()) {
                System.out.println("크롤링할 링크가 없습니다.");
                return;
            }

            // 배치 단위로 병렬 처리
            List<NewsDetail> detailedNewsList = processNewsLinksInBatches(newsLinks);

            System.out.println("\n=== 크롤링 결과 ===");
            System.out.println("성공: " + successCount.get() + "개");
            System.out.println("실패: " + failCount.get() + "개");
            System.out.println("성공률: " + (successCount.get() * 100.0 / newsLinks.size()) + "%");

            // 결과를 새로운 CSV 파일로 저장
            if (!detailedNewsList.isEmpty()) {
                saveDetailedNewsToCsv(detailedNewsList, csvFile);
            }

        } catch (Exception e) {
            System.err.println("CSV 파일 처리 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 뉴스 링크들을 스마트 배치 단위로 병렬 처리
     */
    private static List<NewsDetail> processNewsLinksInBatches(List<NewsLinkInfo> newsLinks) {
        List<NewsDetail> allResults = Collections.synchronizedList(new ArrayList<>());
        int totalBatches = (int) Math.ceil((double) newsLinks.size() / BATCH_SIZE);
        
        System.out.println("배치 크기: " + BATCH_SIZE + ", 총 배치 수: " + totalBatches);
        System.out.println("스마트 병렬 처리 시작...\n");

        // 배치별로 스마트 병렬 처리
        for (int i = 0; i < totalBatches; i++) {
            int startIndex = i * BATCH_SIZE;
            int endIndex = Math.min(startIndex + BATCH_SIZE, newsLinks.size());
            List<NewsLinkInfo> batch = newsLinks.subList(startIndex, endIndex);
            
            final int batchNumber = i + 1;
            System.out.println("배치 " + batchNumber + " 시작 (링크 " + (startIndex + 1) + "-" + endIndex + ")");
            System.out.println("현재 동시 요청 수: " + currentConcurrency.get());
            
            // 스마트 배치 처리
            processBatchSmart(batch, allResults, batchNumber);
            
            // 성공률에 따른 동시성 조절
            adjustConcurrency();
            
            System.out.println("배치 " + batchNumber + " 완료");
            
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

        System.out.println("모든 배치 처리 완료!");
        return allResults;
    }

    /**
     * 스마트 배치 처리 (동적 병렬 처리)
     */
    private static void processBatchSmart(List<NewsLinkInfo> batch, List<NewsDetail> allResults, int batchNumber) {
        System.out.println("배치 " + batchNumber + ": " + batch.size() + "개 링크 스마트 병렬 처리 시작");
        
        // 현재 동시성 수만큼 병렬 처리
        int concurrency = currentConcurrency.get();
        List<CompletableFuture<NewsDetail>> futures = new ArrayList<>();
        
        for (int i = 0; i < batch.size(); i++) {
            NewsLinkInfo linkInfo = batch.get(i);
            final int linkIndex = i + 1;
            
            CompletableFuture<NewsDetail> future = CompletableFuture.supplyAsync(() -> {
                System.out.println("배치 " + batchNumber + " - 링크 " + linkIndex + "/" + batch.size() + " 크롤링 중: " + 
                                 linkInfo.title.substring(0, Math.min(30, linkInfo.title.length())) + "...");
                return crawlNewsDetailWithRetry(linkInfo);
            }, executorService);
            
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
        
        System.out.println("배치 " + batchNumber + " 완료: 성공 " + successCount.get() + "개, 실패 " + failCount.get() + "개");
    }

    /**
     * Future들 완료 대기 및 결과 처리
     */
    private static void waitForFutures(List<CompletableFuture<NewsDetail>> futures, List<NewsDetail> allResults, int batchNumber) {
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<NewsDetail> future = futures.get(i);
            try {
                NewsDetail detail = future.get(45, TimeUnit.SECONDS); // 45초 타임아웃
                
                if (detail != null) {
                    allResults.add(detail);
                    successCount.incrementAndGet();
                    System.out.println("✅ 배치 " + batchNumber + " - 링크 성공");
                } else {
                    failCount.incrementAndGet();
                    System.out.println("❌ 배치 " + batchNumber + " - 링크 실패");
                }
                
                int processed = processedCount.incrementAndGet();
                if (processed % 5 == 0) {
                    System.out.println("전체 진행률: " + processed + "개 처리 완료");
                }
                
            } catch (InterruptedException | ExecutionException e) {
                failCount.incrementAndGet();
                System.err.println("❌ 배치 " + batchNumber + " - 링크 처리 중 오류: " + e.getMessage());
            } catch (TimeoutException e) {
                failCount.incrementAndGet();
                System.err.println("❌ 배치 " + batchNumber + " - 링크 타임아웃 (45초 초과)");
                future.cancel(true);
            }
        }
    }

    /**
     * 성공률에 따른 동시성 조절
     */
    private static void adjustConcurrency() {
        int total = successCount.get() + failCount.get();
        if (total < 5) return; // 최소 5개 처리 후 조절
        
        double successRate = (double) successCount.get() / total;
        int current = currentConcurrency.get();
        
        if (successRate >= SUCCESS_RATE_THRESHOLD) {
            // 성공률이 높으면 동시성 증가
            if (current < MAX_CONCURRENT_REQUESTS) {
                currentConcurrency.incrementAndGet();
                System.out.println("성공률 " + String.format("%.1f", successRate * 100) + "% - 동시성 증가: " + current + " → " + (current + 1));
            }
        } else {
            // 성공률이 낮으면 동시성 감소
            if (current > MIN_CONCURRENT_REQUESTS) {
                currentConcurrency.decrementAndGet();
                System.out.println("성공률 " + String.format("%.1f", successRate * 100) + "% - 동시성 감소: " + current + " → " + (current - 1));
            }
        }
    }

    /**
     * 재시도 로직이 포함된 뉴스 상세 크롤링
     */
    private static NewsDetail crawlNewsDetailWithRetry(NewsLinkInfo linkInfo) {
        for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
            try {
                NewsDetail detail = crawlNewsDetailOptimized(linkInfo.link, linkInfo.title, 
                                                           linkInfo.press, linkInfo.categoryName, linkInfo.categoryId);
                
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

    /**
     * 최적화된 뉴스 상세 크롤링
     */
    public static NewsDetail crawlNewsDetailOptimized(String url, String title, String press, 
                                                    String categoryName, int categoryId) {
        try {
            // 연결 설정 최적화 (더 보수적으로)
            Connection connection = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windowsave NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .maxBodySize(0) // 제한 없음
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1");

            Document doc = connection.get();
            
            // 기자 정보 추출 (우선순위 1: 일반적인 기자 정보 필드)
            String reporter = "";
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
                    reporter = String.join(", ", reporterNames);
                }
            } else {
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
                        reporter = String.join(", ", reporterNames);
                    }
                } else {
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
                                    // 띄어쓰기와 괄호 둘 다 있으면 더 앞에 있는 것 선택
                                    endIndex = Math.min(spaceIndex, parenthesisIndex);
                                } else if (spaceIndex > 0) {
                                    // 띄어쓰기만 있으면
                                    endIndex = spaceIndex;
                                } else if (parenthesisIndex > 0) {
                                    // 괄호만 있으면
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
                            reporter = String.join(", ", reporterParts);
                        }
                    }
                }
            }

            // 네이버 뉴스 본문 추출
            String content = "";
            Element contentElement = doc.selectFirst("#dic_area");
            if (contentElement != null) {
                content = contentElement.text();
            }

            // 우선순위 2: 본문에서 기자 이름 추출 (지정된 필드에 정보가 없을 경우)
            if (reporter.isEmpty() && !content.isEmpty()) {
                reporter = extractReporterFromContent(content);
                if (!reporter.isEmpty()) {
                    // 기자 정보가 본문에서 제거되었으므로 content는 그대로 유지
                }
            }

            // 날짜 정보 추출
            String date = "";
            Element dateElement = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            if (dateElement != null) {
                date = dateElement.attr("data-date-time");
            }

            return NewsDetail.builder()
                .title(title)
                .reporter(reporter)
                .date(date)
                .link(url)
                .press(press)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .content(content)
                .build();

        } catch (Exception e) {
            System.err.println("크롤링 실패 (" + url + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * 기자 이름에서 "기자" 텍스트를 안전하게 제거
     */
    private static String cleanReporterName(String reporterName) {
        if (reporterName == null || reporterName.trim().isEmpty()) {
            return "";
        }
        
        String cleaned = reporterName.trim();
        
        // "기자"로 끝나는 경우만 제거 (이름에 "기자"가 포함된 경우는 보존)
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

    /**
     * 본문에서 기자 정보 추출 (최적화된 버전)
     */
    private static String extractReporterFromContent(String content) {
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

        // 일치하는 항목을 찾았다면, 본문에서 해당 내용을 정리
        if (!foundReporter.isEmpty()) {
            int originalIndex = startIndex + matchPosInSearchArea;
            content = content.substring(0, originalIndex).trim();
        }

        return foundReporter;
    }

    private static List<NewsLinkInfo> readLinksFromCsv(File csvFile) {
        List<NewsLinkInfo> newsLinks = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 헤더 건너뛰기
                }
                
                try {
                    // CSV 파싱 (큰따옴표로 감싸진 필드 처리)
                    String[] fields = parseCsvLine(line);
                    
                    if (fields.length >= 4) { // 최소 4개 필드 필요 (title, link, press, timestamp)
                        String title = fields[0].replaceAll("^\"|\"$", "");
                        String link = fields[1].replaceAll("^\"|\"$", "");
                        String press = fields[2].replaceAll("^\"|\"$", "");
                        String timestamp = fields[3].replaceAll("^\"|\"$", "");
                        
                        // 링크가 유효한지 확인
                        if (link != null && !link.trim().isEmpty() && link.startsWith("http")) {
                            // 카테고리 정보 추출 (CSV 형식에 맞춤)
                            String categoryName = "정치"; // 기본값
                            int categoryId = 100; // 기본값
                            
                            if (fields.length >= 5) {
                                // 5개 컬럼 CSV: title,link,press,category,timestamp
                                // 4번째 필드(fields[3])가 카테고리, 5번째 필드(fields[4])가 타임스탬프
                                String categoryField = fields[3].replaceAll("^\"|\"$", "");
                                categoryName = categoryField;
                                // 카테고리명으로 ID 추출
                                categoryId = getCategoryIdByName(categoryName);
                            }
                            
                            newsLinks.add(new NewsLinkInfo(title, link, press, categoryName, categoryId));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("CSV 라인 파싱 실패: " + line + " - " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("CSV 파일 읽기 실패: " + e.getMessage());
        }
        
        return newsLinks;
    }

    /**
     * 카테고리명으로 카테고리 ID 반환
     */
    private static int getCategoryIdByName(String categoryName) {
        Map<String, Integer> categoryMap = Map.of(
            "정치", 100,
            "경제", 101,
            "사회", 102,
            "생활/문화", 103,
            "세계", 104,
            "IT/과학", 105
        );
        return categoryMap.getOrDefault(categoryName, 100);
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 이스케이프된 큰따옴표
                    currentField.append('"');
                    i++; // 다음 큰따옴표 건너뛰기
                } else {
                    // 따옴표 시작/끝
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 필드 구분자
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // 마지막 필드 추가
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }

    private static void saveDetailedNewsToCsv(List<NewsDetail> newsList, File originalFile) {
        String baseName = originalFile.getName().replaceFirst("[.][^.]+$", ""); // 확장자 제거
        String fileName = baseName + "_detailed" + ".csv";

        // 원본 파일이 있는 폴더 안에 detail 폴더 생성
        File parentDirectory = originalFile.getParentFile();
        File detailFolder = new File(parentDirectory, "detail");
        if (!detailFolder.exists()) {
            detailFolder.mkdirs();
        }
        File file = new File(detailFolder, fileName);

        try (
            FileOutputStream fos = new FileOutputStream(file, true);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter bw = new BufferedWriter(osw);
            PrintWriter writer = new PrintWriter(bw)
        ) {
            writer.println("\"category_id\",\"category_name\",\"press\",\"title\",\"reporter\",\"date\",\"link\",\"timestamp\",\"content\"");

            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH);

            for (NewsDetail detail : newsList) {
                String timestamp = LocalDateTime.now().format(timestampFormatter);
                writer.printf("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        detail.getCategoryId(),
                        detail.getCategoryName(),
                        escape(detail.getPress()),
                        escape(detail.getTitle()),
                        escape(detail.getReporter()),
                        escape(detail.getDate()),
                        escape(detail.getLink()),
                        timestamp,
                        escape(detail.getContent())); 
            }
            

            System.out.println("상세 뉴스 데이터 CSV 저장 완료: " + fileName);

        } catch (Exception e) {
            System.out.println("CSV 저장 실패: " + e.getMessage());
        }
    }
    
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\""); // 큰따옴표 이스케이프
    }

    // 뉴스 링크 정보를 담는 내부 클래스
    private static class NewsLinkInfo {
        final String title;
        final String link;
        final String press;
        final String categoryName;
        final int categoryId;
        
        NewsLinkInfo(String title, String link, String press, String categoryName, int categoryId) {
            this.title = title;
            this.link = link;
            this.press = press;
            this.categoryName = categoryName;
            this.categoryId = categoryId;
        }
    }
} 

    

