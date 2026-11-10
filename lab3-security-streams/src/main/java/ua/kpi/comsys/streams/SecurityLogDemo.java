package ua.kpi.comsys.streams;

import java.time.LocalDateTime;
import java.util.stream.Stream;

/** Reproducible examples using infinite sources. */
public final class SecurityLogDemo {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 10, 0);

    private SecurityLogDemo() {
    }

    public static void main(String[] args) {
        SecurityLogAnalyzer analyzer = new SecurityLogAnalyzer();

        String critical = analyzer.findFirstCriticalEvent(events())
                .map(log -> log.ipAddress() + " @ " + log.timestamp())
                .orElse("No critical event");
        System.out.println("First CRITICAL: " + critical);

        var prefix = analyzer.getLogsBeforeDeadline(events(), START.plusSeconds(4));
        System.out.println("Events before 2026-11-10T10:00:04: " + prefix.size());

        var bursts = analyzer.detectIpBursts(events(), 3, 3);
        System.out.println("First 3 bursts (threshold 3): " + bursts);

        String emptyResult = analyzer.findFirstCriticalEvent(Stream.empty())
                .map(SecurityLog::eventDescription)
                .orElse("No critical event");
        System.out.println("Empty input: " + emptyResult);
    }

    private static Stream<SecurityLog> events() {
        return Stream.iterate(0L, index -> index + 1)
                .map(index -> new SecurityLog(
                        index / 3 % 2 == 0 ? "192.0.2.10" : "198.51.100.20",
                        index == 5 ? "CRITICAL" : "INFO",
                        "Event " + index,
                        START.plusSeconds(index)));
    }
}
