package com.news.news_crawler.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.news.news_crawler.dto.NewsDetail;

public class NewsDetailBatchProcessor {

    public static void main(String[] args) throws IOException {
        List<String[]> rows = readCsv("your_file.csv");

        for (String[] row : rows) {
            String link = row[1]; // 0: title, 1: link, 2: press, 3: timestamp
            NewsDetail detail = NewsContentsCrawler.crawlNewsDetail(link);
            if (detail != null) {
                System.out.println("✅ 크롤링 완료: " + detail.getTitle());
                // 저장 or DB 처리
            }
        }
    }

    private static List<String[]> readCsv(String path) throws IOException {
        List<String[]> data = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                data.add(line.split("\",\"")); // 간단 처리. 필요 시 CSV 파서 권장
            }
        }
        return data;
    }
}
