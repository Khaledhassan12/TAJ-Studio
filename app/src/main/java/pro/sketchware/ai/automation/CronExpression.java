package pro.sketchware.ai.automation;

import java.util.Calendar;

/**
 * [WHAT] Minimal honest 5-field cron expression (P2-AU, D17).
 * Fields: minute hour day-of-month month day-of-week.
 * Supported per field: "*" , single values, lists (a,b,c), ranges (a-b),
 * steps ("*&#47;n" and "a-b/n"). Parse failures throw IllegalArgumentException
 * with the offending field and position (honest errors, never silent).
 * [WHY] DOM and DOW combine with AND (both must match) — documented, minimal.
 * nextFire() iterates minute-by-minute with a bounded lookahead (~366 days).
 */
public class CronExpression {

    private static final String[] FIELD_NAMES = {"minute", "hour", "day-of-month", "month", "day-of-week"};
    private static final int[][] FIELD_RANGES = {{0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 6}};
    /** Bounded lookahead: one year of minutes — never an infinite loop. */
    private static final int MAX_MINUTES_LOOKAHEAD = 366 * 24 * 60;

    private final boolean[] minutes = new boolean[60];
    private final boolean[] hours = new boolean[24];
    private final boolean[] daysOfMonth = new boolean[32]; // index 1..31
    private final boolean[] months = new boolean[13];      // index 1..12
    private final boolean[] daysOfWeek = new boolean[7];   // 0=Sunday .. 6=Saturday

    public CronExpression(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron expression is empty");
        }
        String[] fields = expr.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("Cron must have exactly 5 fields"
                    + " (minute hour day-of-month month day-of-week), got " + fields.length + ": '" + expr + "'");
        }
        parseField(fields[0], FIELD_NAMES[0], FIELD_RANGES[0], minutes);
        parseField(fields[1], FIELD_NAMES[1], FIELD_RANGES[1], hours);
        parseField(fields[2], FIELD_NAMES[2], FIELD_RANGES[2], daysOfMonth);
        parseField(fields[3], FIELD_NAMES[3], FIELD_RANGES[3], months);
        parseField(fields[4], FIELD_NAMES[4], FIELD_RANGES[4], daysOfWeek);
    }

    private static void parseField(String field, String name, int[] range, boolean[] target) {
        int min = range[0];
        int max = range[1];
        for (String element : field.split(",")) {
            String e = element.trim();
            if (e.isEmpty()) {
                throw new IllegalArgumentException("Empty list element in cron " + name + " field: '" + field + "'");
            }
            int step = 1;
            String body = e;
            int slash = e.indexOf('/');
            if (slash >= 0) {
                body = e.substring(0, slash);
                String stepStr = e.substring(slash + 1);
                try {
                    step = Integer.parseInt(stepStr);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Invalid step '" + stepStr + "' in cron " + name + " field: '" + field + "'");
                }
                if (step <= 0) {
                    throw new IllegalArgumentException("Step must be positive in cron " + name + " field: '" + field + "'");
                }
            }
            int lo;
            int hi;
            if ("*".equals(body)) {
                lo = min;
                hi = max;
            } else if (body.contains("-")) {
                String[] parts = body.split("-");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid range '" + body + "' in cron " + name + " field: '" + field + "'");
                }
                lo = parseValue(parts[0], name, field, min, max);
                hi = parseValue(parts[1], name, field, min, max);
                if (lo > hi) {
                    throw new IllegalArgumentException("Range start > end in cron " + name + " field: '" + field + "'");
                }
            } else {
                lo = parseValue(body, name, field, min, max);
                hi = (slash >= 0) ? max : lo; // "a/n" means a..max step n
            }
            for (int v = lo; v <= hi; v += step) {
                target[v] = true;
            }
        }
    }

    private static int parseValue(String s, String name, String field, int min, int max) {
        int v;
        try {
            v = Integer.parseInt(s.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid value '" + s + "' in cron " + name + " field: '" + field + "'");
        }
        if (v < min || v > max) {
            throw new IllegalArgumentException("Value " + v + " out of range [" + min + "-" + max
                    + "] in cron " + name + " field: '" + field + "'");
        }
        return v;
    }

    /**
     * @return millis of the next fire strictly after fromMillis, or -1 if
     * none within the bounded lookahead (~366 days).
     */
    public long nextFire(long fromMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMillis);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MINUTE, 1); // strictly after

        for (int i = 0; i < MAX_MINUTES_LOOKAHEAD; i++) {
            int month = cal.get(Calendar.MONTH) + 1;
            int dom = cal.get(Calendar.DAY_OF_MONTH);
            int dow = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY; // 0=Sunday
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            if (months[month] && daysOfMonth[dom] && daysOfWeek[dow] && hours[hour] && minutes[minute]) {
                return cal.getTimeInMillis();
            }
            cal.add(Calendar.MINUTE, 1);
        }
        return -1;
    }
}
