package com.news.news_crawler.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.Environment;
import com.news.news_crawler.util.DateTimeUtils;

public class CsvToDatabase {

    // CSV 경로 (가장 최신 파일을 찾기 위한 동적 생성)
    private static String getCsvBasePath() {
        return "news_crawler/src/main/resources/static/";
    }
    
    // 가장 최신 파일의 경로를 찾는 메서드
    private static String findLatestCsvBasePath() {
        String basePath = getCsvBasePath();
        File baseDir = new File(basePath);
        
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.err.println("기본 디렉토리를 찾을 수 없습니다: " + basePath);
            return null;
        }
        
        // 재귀적으로 모든 deduplicated-related 폴더 찾기
        List<String> dedupPaths = new ArrayList<>();

        System.out.println("기본 디렉토리: " + baseDir.getAbsolutePath());
        findDeduplicatedRelatedFolders(baseDir, dedupPaths);
        System.out.println("찾은 deduplicated-related 폴더 수: " + dedupPaths.size());
        for (String path : dedupPaths) {
            System.out.println("  - " + path);
        }
        
        if (dedupPaths.isEmpty()) {
            System.err.println("deduplicated-related 폴더를 찾을 수 없습니다.");
            return null;
        }
        
        // 가장 최신 날짜 찾기
        String latestPath = null;
        LocalDateTime latestDateTime = null;
        
        for (String path : dedupPaths) {
            // 경로에서 날짜 추출
            String datePeriod = extractDateFromPath(path);
            System.out.println("날짜: " + datePeriod);
            if (datePeriod != null) {
                try {
                    String dateStr = datePeriod.substring(0, 10); // yyyy-MM-dd
                    String periodStr = datePeriod.substring(11).toLowerCase(); // am/pm
                    
                    int hour = periodStr.equals("pm") ? 18 : 6;
                    LocalDateTime dateTime = LocalDateTime.parse(dateStr + " " + hour + ":00:00", 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"));
                    if (latestDateTime == null || dateTime.isAfter(latestDateTime)) {
                        latestDateTime = dateTime;
                        latestPath = path;
                        System.out.println("최신: " + datePeriod + " → " + path);
                    }
                } catch (Exception e) {
                    System.err.println("날짜 파싱 실패: " + datePeriod);
                }
            }
        }
        
        return latestPath;
    }
    
    // 재귀적으로 deduplicated-related 폴더들 찾기
    private static void findDeduplicatedRelatedFolders(File dir, List<String> results) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    System.out.println("검색 : " + file.getAbsolutePath());
                    if (file.getName().equals("deduplicated-related")) {
                        System.out.println("deduplicated-related 폴더 발견: " + file.getAbsolutePath());
                        results.add(file.getAbsolutePath() + "/");
                    } else {
                        // 재귀적으로 하위 폴더 검색
                        findDeduplicatedRelatedFolders(file, results);
                    }
                }
            }
        }
    }
    
    // 경로에서 날짜 추출
    private static String extractDateFromPath(String path) {
        // Windows 경로와 Unix 경로 모두 처리
        String[] parts = path.split("[\\\\/]");
        for (String part : parts) {
            if (part.toLowerCase().matches("\\d{4}-\\d{2}-\\d{2}_(am|pm)")) {
                return part;
            }
        }
        return null;
    }

    // 카테고리 매핑
    private static final Map<String, String> CATEGORY_MAPPING = Map.of(
        "정치", "POLITICS",
        "경제", "ECONOMY",
        "사회", "SOCIETY",
        "생활", "LIFE",
        "세계", "INTERNATIONAL",
        "IT과학", "IT_SCIENCE",
        "자동차", "VEHICLE",
        "여행", "TRAVEL_FOOD",
        "예술", "ART"
    );

    private static final String[] CATEGORIES = {"정치", "경제", "사회", "생활", "세계", "IT과학", "자동차", "여행", "예술"};

    /**
     * 메인 메서드
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("=== CSV to DB 시작 ===");
        
        // Spring 컨텍스트를 통해 설정값 가져오기
        String dbUrl, dbUser, dbPassword;
        try {
            // Spring Boot 애플리케이션 컨텍스트 생성
            org.springframework.boot.SpringApplication app = new org.springframework.boot.SpringApplication(com.news.news_crawler.NewsCrawlerApplication.class);
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
            org.springframework.context.ConfigurableApplicationContext context = app.run(args);
            
            // Environment에서 설정값 가져오기
            org.springframework.core.env.Environment env = context.getEnvironment();
            dbUrl = env.getProperty("spring.datasource.url");
            dbUser = env.getProperty("spring.datasource.username");
            dbPassword = env.getProperty("spring.datasource.password");
            
            context.close();
        } catch (Exception e) {
            System.err.println("Spring 설정 로드 실패: " + e.getMessage());
            return;
        }
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            System.out.println("DB 연결 성공");
            
            // 테이블 존재 여부 확인
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet tables = metaData.getTables(null, null, "news", null);
                if (tables.next()) {
                    System.out.println("news 테이블 존재 확인");
                } else {
                    System.err.println("news 테이블이 존재하지 않습니다!");
                }
                
                tables = metaData.getTables(null, null, "related_news", null);
                if (tables.next()) {
                    System.out.println("related_news 테이블 존재 확인");
                } else {
                    System.err.println("related_news 테이블이 존재하지 않습니다!");
                }
            } catch (Exception e) {
                System.err.println("테이블 확인 중 오류: " + e.getMessage());
            }
            
            // 가장 최신 파일의 CSV 경로 찾기
            String csvBasePath = findLatestCsvBasePath();
            if (csvBasePath == null) {
                System.err.println("최신 CSV 파일을 찾을 수 없습니다.");
                return;
            }
            
            // 파일 경로에서 날짜와 시간 추출
            String[] pathParts = csvBasePath.split("[\\\\/]");
            String datePeriod = pathParts[pathParts.length - 2]; // yyyy-MM-dd_am/pm
            String period = datePeriod.substring(11); // am/pm
            String date = datePeriod.substring(0, 10); // yyyy-MM-dd
            
            System.out.println("처리할 최신 파일: " + datePeriod);
            System.out.println("날짜: " + date + " (" + period.toUpperCase() + ")");
            System.out.println("경로: " + csvBasePath);
            
            // 실제 파일 목록 확인
            File baseDir = new File(csvBasePath);
            if (baseDir.exists() && baseDir.isDirectory()) {
                System.out.println("\n실제 CSV 파일 목록:");
                File[] files = baseDir.listFiles((dir, name) -> name.endsWith(".csv"));
                if (files != null) {
                    for (File file : files) {
                        System.out.println("  - " + file.getName());
                    }
                }
            } else {
                System.err.println("기본 디렉토리를 찾을 수 없습니다: " + csvBasePath);
                return;
            }

            for (String category : CATEGORIES) {
                String newsFile = "deduplicated_" + category + "_" + date + "_" + period + ".csv";
                String relatedFile = "related_" + category + "_" + date + "_" + period + ".csv";

                String newsPath = csvBasePath + newsFile;
                String relatedPath = csvBasePath + relatedFile;

                System.out.println("\n[" + category + "] 처리 시작");
                
                // 파일 존재 여부 확인
                File newsFileObj = new File(newsPath);
                File relatedFileObj = new File(relatedPath);
                
                if (!newsFileObj.exists()) {
                    System.err.println("뉴스 파일을 찾을 수 없습니다: " + newsPath);
                    continue;
                }
                
                if (!relatedFileObj.exists()) {
                    System.err.println("연관 뉴스 파일을 찾을 수 없습니다: " + relatedPath);
                }
                
                Map<Integer, Long> indexToNewsId = insertNewsCsv(conn, newsPath, category);
                if (relatedFileObj.exists()) {
                    insertRelatedNewsCsv(conn, relatedPath, indexToNewsId);
                } else {
                    System.out.println("연관 뉴스 파일이 없어서 스킵합니다.");
                }
            }

            conn.close();
            System.out.println("\n모든 삽입 완료!");

        } catch (Exception e) {
            System.err.println("오류 발생: " + e.getMessage());
            System.err.println("오류 타입: " + e.getClass().getSimpleName());
            e.printStackTrace();
        }
    }

    // 1. 뉴스 insert 및 인덱스 매핑 반환 - 중복 무시
    private static Map<Integer, Long> insertNewsCsv(Connection conn, String filePath, String categoryName) {
        Map<Integer, Long> indexToNewsId = new HashMap<>();
        String sql = "INSERT IGNORE INTO news (title, content, press, published_at, reporter, dedup_state, created_at, updated_at, trusted, image_url, oid_aid, category_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
             BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {

            String line = reader.readLine(); // 헤더 스킵
            if (line == null) return indexToNewsId;

            int count = 0;
            int csvIndex = 0;
            LocalDateTime now = LocalDateTime.now();

            while ((line = reader.readLine()) != null) {
                try {
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 12) continue; // 컬럼 수 증가로 인한 최소 필드 수 조정

                    // CSV 헤더: "news_category_id","press","title","reporter","published_at","link","created_at","image_url","trusted","oid_aid","content","mark", "dedup_state"
   
                    String press = fields[1];
                    String title = fields[2];
                    String reporter = fields[3];
                    String publishedAt = fields[4];
                    String imageUrl = fields[7];
                    String trusted = fields[8];
                    String oidAid = fields[9];
                    String content = fields[10];
                    String dedupState = fields[12]; // dedup_state는 이미 ENUM 값으로 저장됨

                    LocalDateTime parsedPublishedAt = parseDate(publishedAt);
                    boolean trustedValue = "1".equals(trusted) || "true".equalsIgnoreCase(trusted);
                    
                    // 카테고리 매핑: CSV의 카테고리명 → DB용 카테고리명
                    String categoryForDb = CATEGORY_MAPPING.get(categoryName);

                    pstmt.setString(1, title);
                    pstmt.setString(2, content);
                    pstmt.setString(3, press);
                    pstmt.setTimestamp(4, Timestamp.valueOf(parsedPublishedAt));
                    pstmt.setString(5, reporter);
                    pstmt.setString(6, dedupState); // 매핑된 ENUM 값 사용
                    pstmt.setTimestamp(7, Timestamp.valueOf(now));
                    pstmt.setNull(8, Types.TIMESTAMP);
                    pstmt.setBoolean(9, trustedValue);
                    pstmt.setString(10, imageUrl.isEmpty() ? null : imageUrl);
                    pstmt.setString(11, oidAid.isEmpty() ? null : oidAid);
                    pstmt.setString(12, categoryForDb); // 카테고리 추가

                    pstmt.executeUpdate();

                    ResultSet keys = pstmt.getGeneratedKeys();
                    if (keys.next()) {
                        indexToNewsId.put(csvIndex, keys.getLong(1));
                    }

                    csvIndex++;
                    count++;
                } catch (Exception e) {
                    System.err.println("뉴스 삽입 실패: " + line);
                    System.err.println(" - " + e.getMessage());
                }
            }

            System.out.printf("[%s] 뉴스 %d개 삽입 완료\n", categoryName, count);
        } catch (Exception e) {
            System.err.println("뉴스 처리 중 오류: " + e.getMessage());
        }

        return indexToNewsId;
    }

    // 2. 연관 뉴스 insert (oid_aid 기반) - 중복 무시
    private static void insertRelatedNewsCsv(Connection conn, String filePath, Map<Integer, Long> indexToIdMap) {
        String insertSql = "INSERT IGNORE INTO related_news (rep_oid_aid, related_oid_aid, similarity, created_at) VALUES (?, ?, ?, NOW())";

        try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql);
             BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {

            String line = reader.readLine(); // 헤더 스킵
            if (line == null) return;

            int count = 0;

            while ((line = reader.readLine()) != null) {
                String[] fields = parseCsvLine(line);
                if (fields.length < 3) continue;

                // CSV 헤더: "rep_oid_aid","related_oid_aid","similarity"
                String repOidAid = fields[0];
                String relatedOidAid = fields[1];
                float similarity = Float.parseFloat(fields[2]);

                // 직접 oid_aid 값으로 삽입 (news.id 조회 불필요)
                insertPstmt.setString(1, repOidAid);
                insertPstmt.setString(2, relatedOidAid);
                insertPstmt.setFloat(3, similarity);
                insertPstmt.executeUpdate();
                count++;
            }

            System.out.printf("📎 연관 뉴스 %d개 삽입 완료 (%s)\n", count, filePath);

        } catch (Exception e) {
            System.err.println("연관 뉴스 처리 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 3. AM/PM 날짜 파싱
    private static LocalDateTime parseDate(String publishedAt) {
        try {
            // 2025-08-07 11:43:12 형태의 날짜를 직접 파싱
            return LocalDateTime.parse(publishedAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            System.err.println("날짜 파싱 실패: " + publishedAt);
            return LocalDateTime.now();
        }
    }

    // 4. CSV 한 줄 파싱
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
