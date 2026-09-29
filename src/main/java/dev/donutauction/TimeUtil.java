package dev.donutauction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting of durations, clock times and prices. */
public final class TimeUtil {
    private static final Pattern PART = Pattern.compile("(\\d{1,7})([hms])");
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern PRICE = Pattern.compile("^\\d+(\\.\\d+)?[kmbt]?$");
    public static final long MAX_DURATION_SEC = 30L * 24 * 3600;

    private TimeUtil() {}

    /** "15m", "1h30m", "90s", or a bare number (minutes). Returns -1 if invalid. */
    public static long parseDurationSeconds(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase().replace(" ", "");
        if (s.isEmpty()) return -1;
        long total = 0;
        try {
            if (s.matches("\\d{1,7}")) {
                total = Long.parseLong(s) * 60;
            } else {
                Matcher m = PART.matcher(s);
                int end = 0;
                while (m.find()) {
                    if (m.start() != end) return -1;
                    long v = Long.parseLong(m.group(1));
                    switch (m.group(2)) {
                        case "h" -> total += v * 3600;
                        case "m" -> total += v * 60;
                        default -> total += v;
                    }
                    end = m.end();
                }
                if (end != s.length()) return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return (total > 0 && total <= MAX_DURATION_SEC) ? total : -1;
    }

    /**
     * Start delay in milliseconds. Accepts blank/"now" (0), a duration ("10m"),
     * or a clock time ("14:30", next occurrence). Returns -1 if invalid.
     */
    public static long parseStartDelayMillis(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty() || s.equals("now") || s.equals("0")) return 0;
        Matcher m = CLOCK.matcher(s);
        if (m.matches()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h > 23 || min > 59) return -1;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime target = now.toLocalDate().atTime(LocalTime.of(h, min));
            if (!target.isAfter(now)) target = target.plusDays(1);
            return Duration.between(now, target).toMillis();
        }
        long sec = parseDurationSeconds(s);
        return sec < 0 ? -1 : sec * 1000;
    }

    /** Returns the lowercase price if it looks like 5000, 25k, 1.5m, 2b; otherwise null. */
    public static String normalizePrice(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase().replace(",", "");
        return PRICE.matcher(s).matches() ? s : null;
    }

    /** 75 -> "01:15", 3725 -> "1:02:05". */
    public static String formatClock(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    /** 90 -> "1m 30s", 3600 -> "1h". */
    public static String formatShort(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
