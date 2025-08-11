package com.news.news_crawler.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateTimeUtils {
    
    /**
     * 현재 시간을 기반으로 날짜 문자열을 반환 (yyyy-MM-dd 형식)
     */
    public static String getCurrentDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
        /**
     * 현재 시간을 기반으로 AM/PM을 반환 (소문자로 통일)
     */
    public static String getCurrentPeriod() {
        LocalDateTime now = LocalDateTime.now();
        return now.getHour() < 12 ? "am" : "pm";
    }

    /**
     * 현재 시간을 기반으로 am/pm을 반환 (소문자)
     */
    public static String getCurrentPeriodLower() {
        LocalDateTime now = LocalDateTime.now();
        return now.getHour() < 12 ? "am" : "pm";
    }

    /**
     * 현재 시간을 기반으로 날짜-시간대 폴더명을 반환 (yyyy-MM-dd_am 형식)
     */
    public static String getCurrentDatePeriod() {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String period = now.getHour() < 12 ? "am" : "pm";
        return date + "_" + period;
    }
    
    /**
     * 현재 시간을 기반으로 타임스탬프를 반환
     */
    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss", Locale.ENGLISH));
    }
    
        /**
     * 가장 최근의 날짜-시간대 폴더를 찾기 위한 기준 시간을 반환
     * 현재 시간에서 가장 가까운 시간대를 계산
     */
    public static String getNearestDatePeriod() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        // 오전(0-11시)이면 am, 오후(12-23시)이면 pm
        String period = hour < 12 ? "am" : "pm";
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return date + "_" + period;
    }
}
