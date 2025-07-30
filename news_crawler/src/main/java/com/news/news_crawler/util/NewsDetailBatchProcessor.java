package com.news.news_crawler.util;

import com.news.news_crawler.dto.NewsDetail;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class NewsDetailBatchProcessor {

    public static void main(String[] args) throws IOException {
        String inputCsv = "C:/dev/BE09_FINAL_1team_CRAWLER/news_crawler/src/main/resources/static/naver_news_2025-07-29_PM_03-21-12.csv";
        List<String[]> rows = readCsv(inputCsv);
        List<NewsDetail> details = new ArrayList<>();

        for (String[] row : rows) {
            String title = row[0].replaceAll("^\"|\"$", "");
            String link = row[1].replaceAll("^\"|\"$", "");
            String press = row[2].replaceAll("^\"|\"$", "");
            // 본문, 기자, 날짜 크롤링
            NewsDetail detail = crawlNewsDetail(link, title, press);
            if (detail != null) {
                details.add(detail);
                System.out.println("✅ 크롤링 완료: " + detail.getTitle());
            } else {
                System.out.println("❌ 크롤링 실패: " + title);
            }
        }
        saveToCsv(details);
    }

    private static List<String[]> readCsv(String path) throws IOException {
        List<String[]> data = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                // 큰따옴표로 감싸진 CSV 필드 분리
                String[] fields = line.split(",", -1);
                for (int i = 0; i < fields.length; i++) fields[i] = fields[i].replaceAll("^\"|\"$", "");
                data.add(fields);
            }
        }
        return data;
    }

    private static NewsDetail crawlNewsDetail(String url, String title, String press) {
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000)
                    .get();
            String content = "";
            org.jsoup.nodes.Element contentElement = doc.selectFirst("#dic_area");
            if (contentElement != null) {
                content = contentElement.text();
            }
            String reporter = "";
            org.jsoup.nodes.Element reporterElement = doc.selectFirst("#ct > div.media_end_head.go_trans > div.media_end_head_info.nv_notrans > div.media_end_head_journalist > a > em");
            if (reporterElement != null) {
                reporter = reporterElement.text();
            }
            // 만약 태그에서 못 찾았으면 본문 마지막 줄에서 추출 시도
            if (reporter.isEmpty() && !content.isEmpty()) {
                String[] lines = content.split("\n");
                String lastLine = lines[lines.length - 1].trim();
                // 기자 이름 패턴 예시: "홍길동 기자", "홍길동 특파원" 등
                if (lastLine.matches(".*(기자|특파원)$")) {
                    reporter = lastLine;
                }
            }
            String date = "";
            org.jsoup.nodes.Element dateElement = doc.selectFirst("span.media_end_head_info_datestamp_time._ARTICLE_DATE_TIME");
            if (dateElement != null) {
                date = dateElement.attr("data-date-time");
            }
            // press도 저장
            return new NewsDetail(title, content, reporter, date, url, press, 0, "기타");
        } catch (Exception e) {
            System.out.println("❌ 본문 크롤링 실패: " + url);
            return null;
        }
    }

    private static void saveToCsv(List<NewsDetail> newsList) {
        java.time.format.DateTimeFormatter fileNameFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_a_hh-mm-ss", java.util.Locale.ENGLISH);
        String formattedTime = java.time.LocalDateTime.now().format(fileNameFormatter);
        String fileName = "naver_news_detail_" + formattedTime + ".csv";
        java.io.File file = new java.io.File("C:/dev/BE09_FINAL_1team_CRAWLER/news_crawler/src/main/resources/static/" + fileName);
        try (
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file, true);
            java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8);
            java.io.BufferedWriter bw = new java.io.BufferedWriter(osw);
            java.io.PrintWriter writer = new java.io.PrintWriter(bw)
        ) {
            writer.println("title,content,reporter,date,link,press");
            for (NewsDetail detail : newsList) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        escape(detail.getTitle()),
                        escape(detail.getContent()),
                        escape(detail.getReporter()),
                        escape(detail.getDate()),
                        escape(detail.getLink()),
                        escape(detail.getPress())
                );
            }
            System.out.println("상세 내용 CSV 저장 완료: " + fileName);
        } catch (Exception e) {
            System.out.println("CSV 저장 실패: " + e.getMessage());
        }
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\"");
    }
} 