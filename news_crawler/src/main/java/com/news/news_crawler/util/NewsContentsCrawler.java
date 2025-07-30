package com.news.news_crawler.util;

import com.news.news_crawler.dto.NewsDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewsContentsCrawler {

    public static void main(String[] args) {
        // CSV 파일에서 링크 읽어와서 상세 내용 크롤링
        processCsvFilesAndCrawlDetails();
    }

    public static void processCsvFilesAndCrawlDetails() {
        try {
            // static 폴더 경로
            File staticDirectory = new File("news_crawler/src/main/resources/static");
            if (!staticDirectory.exists()) {
                System.out.println("static 폴더가 존재하지 않습니다: " + staticDirectory.getAbsolutePath());
                return;
            }

            // CSV 파일들 찾기
            File[] csvFiles = staticDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
            if (csvFiles == null || csvFiles.length == 0) {
                System.out.println("CSV 파일을 찾을 수 없습니다.");
                return;
            }

            // 가장 최신 파일 찾기 (수정 시간 기준)
            File latestFile = Arrays.stream(csvFiles)
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);

            if (latestFile == null) {
                System.out.println("최신 CSV 파일을 찾을 수 없습니다.");
                return;
            }

            System.out.println("발견된 CSV 파일들:");
            for (File csvFile : csvFiles) {
                String indicator = (csvFile.equals(latestFile)) ? " (최신)" : "";
                System.out.println("- " + csvFile.getName() + indicator);
            }

            // 가장 최신 파일만 처리
            System.out.println("\n=== " + latestFile.getName() + " 처리 시작 (최신 파일) ===");
            processSingleCsvFile(latestFile);

        } catch (Exception e) {
            System.err.println("CSV 파일 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processSingleCsvFile(File csvFile) {    
        try {
            List<NewsLinkInfo> newsLinks = readLinksFromCsv(csvFile);
            System.out.println("총 " + newsLinks.size() + "개의 링크를 읽었습니다.");

            if (newsLinks.isEmpty()) {
                System.out.println("크롤링할 링크가 없습니다.");
                return;
            }

            // 상세 내용 크롤링
            List<NewsDetail> detailedNewsList = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < newsLinks.size(); i++) {
                NewsLinkInfo linkInfo = newsLinks.get(i);
                System.out.println("\n[" + (i + 1) + "/" + newsLinks.size() + "] 크롤링 중: " + linkInfo.title);

                try {
                    NewsDetail detail = crawlNewsDetail(linkInfo.link, linkInfo.title, linkInfo.press, 
                                                      linkInfo.categoryName, linkInfo.categoryId);
                    
                    if (detail != null) {
                        detailedNewsList.add(detail);
                        successCount++;
                        System.out.println("✅ 크롤링 성공: " + detail.getTitle());
                    } else {
                        failCount++;
                        System.out.println("❌ 크롤링 실패: " + linkInfo.title);
                    }

                    // 서버 부하 방지를 위한 짧은 대기
                    Thread.sleep(500);

                } catch (Exception e) {
                    failCount++;
                    System.out.println("❌ 크롤링 중 오류: " + e.getMessage());
                }
            }

            System.out.println("\n=== 크롤링 결과 ===");
            System.out.println("성공: " + successCount + "개");
            System.out.println("실패: " + failCount + "개");
            System.out.println("성공률: " + (successCount * 100.0 / newsLinks.size()) + "%");

            // 결과를 새로운 CSV 파일로 저장
            if (!detailedNewsList.isEmpty()) {
                saveDetailedNewsToCsv(detailedNewsList, csvFile.getName());
            }

        } catch (Exception e) {
            System.err.println("CSV 파일 처리 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
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
                            // 기본 카테고리 정보 (실제 CSV에는 없으므로 기본값 사용)
                            String categoryName = "정치"; // 기본값
                            int categoryId = 100; // 기본값
                            
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

    public static NewsDetail crawlNewsDetail(String url, String title, String press, String categoryName, int categoryId) {
        try {
            // Jsoup으로 직접 크롤링 (Selenium보다 훨씬 빠름)
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000) // 5초 타임아웃
                    .get();

            // 기자 정보 추출 (우선순위 1: 지정된 필드)
            String reporter = "";
            Element reporterElement = doc.selectFirst("#ct > div.media_end_head.go_trans > div.media_end_head_info.nv_notrans > div.media_end_head_journalist > a > em");
            if (reporterElement != null) {
                reporter = reporterElement.text();
            }

            // 네이버 뉴스 본문 추출
            String content = "";
            Element contentElement = doc.selectFirst("#dic_area");
            if (contentElement != null) {
                content = contentElement.text();
            }

            // 우선순위 2: 본문에서 기자 이름 추출 (지정된 필드에 정보가 없을 경우)
            if (reporter.isEmpty() && !content.isEmpty()) {
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
                    reporter = foundReporter;
                    // 원본 본문에서 실제 위치를 찾아 제거
                    int originalIndex = startIndex + matchPosInSearchArea;
                    content = content.substring(0, originalIndex).trim();
                }
            }

            // 날짜 정보 추출
            String date = "";
            Element dateElement = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            if (dateElement != null) {
                date = dateElement.attr("data-date-time");
            }

            return new NewsDetail(title, content, reporter, date, url, press, categoryId, categoryName);

        } catch (Exception e) {
            // 실패 시 null 반환 (에러 무시하고 계속 진행)
            return null;
        }
    }

    private static void saveDetailedNewsToCsv(List<NewsDetail> newsList, String originalFileName) {
        // DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_a_hh-mm-ss", Locale.ENGLISH);
        // String formattedTime = LocalDateTime.now().format(fileNameFormatter);
        String baseName = originalFileName.replaceFirst("[.][^.]+$", ""); // 확장자 제거
        String fileName = baseName + "_detailed" + ".csv";

        // 기존 static 폴더 안에 detail 폴더 생성
        File directory = new File("news_crawler/src/main/resources/static/detail");
        if (!directory.exists()) {
            directory.mkdirs(); // detail 폴더가 없으면 생성
        }
        File file = new File(directory, fileName);

        try (
            FileOutputStream fos = new FileOutputStream(file, true);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter bw = new BufferedWriter(osw);
            PrintWriter writer = new PrintWriter(bw)
        ) {
            writer.println("\"category_id\",\"category_name\",\"press\",\"title\",\"content\",\"reporter\",\"date\",\"link\",\"timestamp\"");

            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH);

            for (NewsDetail detail : newsList) {
                String timestamp = LocalDateTime.now().format(timestampFormatter);
                writer.printf("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        detail.getCategoryId(),
                        detail.getCategoryName(),
                        escape(detail.getPress()),
                        escape(detail.getTitle()),
                        escape(detail.getContent()),
                        escape(detail.getReporter()),
                        escape(detail.getDate()),
                        escape(detail.getLink()),
                        timestamp);
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

    

