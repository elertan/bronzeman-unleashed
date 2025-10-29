package com.elertan.utils;

import lombok.NonNull;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class OffsetDateTimeUtils {
    public static String formatRelativeTime(@NonNull OffsetDateTime now, @NonNull OffsetDateTime time) {
        Duration d = Duration.between(time, now);
        long seconds = d.getSeconds();

        if (seconds < 60) return "Just now";
        if (seconds < 3600) {
            int minutes = (int) (seconds / 60);
            if (minutes == 1) {
                return "1 minute ago";
            }
            return minutes + " minutes ago";
        }
        if (seconds < 86400) {
            int hours = (int) (seconds / 3600);
            if (hours == 1) {
                return "1 hour ago";
            }
            return hours + " hours ago";
        }
        if (seconds < 172800) return "Yesterday at " + time.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (seconds < 604800) {
            String dayOfWeek = time.format(DateTimeFormatter.ofPattern("EEEE"));
            return dayOfWeek + " at " + time.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
