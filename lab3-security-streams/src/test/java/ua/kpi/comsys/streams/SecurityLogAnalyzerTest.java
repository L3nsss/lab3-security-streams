package ua.kpi.comsys.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SecurityLogAnalyzerTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 12, 0);
    private final SecurityLogAnalyzer analyzer = new SecurityLogAnalyzer();

    @Test
    void findsFirstCriticalEventInEncounterOrder() {
        SecurityLog warning = log("10.0.0.1", "WARN", 0);
        SecurityLog first = log("10.0.0.2", "CRITICAL", 1);
        SecurityLog second = log("10.0.0.3", "CRITICAL", 2);

        assertEquals(Optional.of(first),
                analyzer.findFirstCriticalEvent(Stream.of(warning, first, second)));
    }

    @Test
    void criticalSeverityComparisonIsExactAndCaseSensitive() {
        assertTrue(analyzer.findFirstCriticalEvent(Stream.of(
                log("A", "critical", 0), log("B", "Critical", 1),
                log("C", "CRITICAL ", 2), log("D", "ERROR", 3))).isEmpty());
    }

    @Test
    void finiteStreamWithoutCriticalEventReturnsEmptyOptional() {
        assertEquals(Optional.empty(), analyzer.findFirstCriticalEvent(
                Stream.of(log("A", "INFO", 0), log("B", "WARN", 1))));
    }

    @Test
    void emptyStreamHasNoCriticalEvent() {
        assertTrue(analyzer.findFirstCriticalEvent(Stream.empty()).isEmpty());
    }

    @Test
    void findsCriticalEventInInfiniteIteratedStreamWithoutOverread() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                .map(i -> {
                    assertTrue(consumed.incrementAndGet() <= 6,
                            "findFirst must stop at the first match");
                    return log("10.0.0.1", i == 5 ? "CRITICAL" : "INFO", i);
                });

        assertEquals(Optional.of(log("10.0.0.1", "CRITICAL", 5)),
                analyzer.findFirstCriticalEvent(infinite));
        assertEquals(6, consumed.get());
    }

    @Test
    void firstGeneratedCriticalEventStopsInfiniteSourceImmediately() {
        AtomicInteger consumed = new AtomicInteger();
        SecurityLog critical = log("A", "CRITICAL", 0);
        Stream<SecurityLog> infinite = Stream.generate(() -> {
            assertEquals(1, consumed.incrementAndGet());
            return critical;
        });

        assertEquals(Optional.of(critical), analyzer.findFirstCriticalEvent(infinite));
        assertEquals(1, consumed.get());
    }

    @Test
    void rejectsNullStreamWhenSearchingCriticalEvent() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.findFirstCriticalEvent(null));
    }

    @Test
    void collectsOnlyLogsStrictlyBeforeDeadline() {
        SecurityLog before = log("A", "INFO", 0);
        SecurityLog boundary = log("B", "WARN", 1);
        SecurityLog after = log("C", "ERROR", 2);

        assertEquals(List.of(before), analyzer.getLogsBeforeDeadline(
                Stream.of(before, boundary, after), START.plusSeconds(1)));
    }

    @Test
    void takeWhileKeepsPrefixRatherThanFilteringTheWholeStream() {
        SecurityLog before = log("A", "INFO", 0);
        SecurityLog boundary = log("B", "INFO", 2);
        SecurityLog laterEncounteredOlderLog = log("C", "INFO", 1);

        assertEquals(List.of(before), analyzer.getLogsBeforeDeadline(
                Stream.of(before, boundary, laterEncounteredOlderLog),
                START.plusSeconds(2)));
    }

    @Test
    void takesFinitePrefixFromInfiniteTimestampStream() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                .map(i -> {
                    assertTrue(consumed.incrementAndGet() <= 4,
                            "takeWhile may inspect only the first failing element");
                    return log("A", "INFO", i);
                });

        assertEquals(List.of(log("A", "INFO", 0), log("A", "INFO", 1),
                        log("A", "INFO", 2)),
                analyzer.getLogsBeforeDeadline(infinite, START.plusSeconds(3)));
        assertEquals(4, consumed.get());
    }

    @Test
    void firstLogAtDeadlineStopsInfiniteGeneratedSource() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> infinite = Stream.generate(() -> {
            assertEquals(1, consumed.incrementAndGet());
            return log("A", "INFO", 0);
        });

        assertTrue(analyzer.getLogsBeforeDeadline(infinite, START).isEmpty());
        assertEquals(1, consumed.get());
    }

    @Test
    void finiteStreamEndingBeforeDeadlineReturnsAllLogs() {
        List<SecurityLog> input = List.of(log("A", "INFO", 0), log("B", "WARN", 1));

        assertEquals(input,
                analyzer.getLogsBeforeDeadline(input.stream(), START.plusSeconds(3)));
    }

    @Test
    void emptyStreamReturnsNoLogsBeforeDeadline() {
        assertTrue(analyzer.getLogsBeforeDeadline(Stream.empty(), START).isEmpty());
    }

    @Test
    void rejectsNullArgumentsWhenCollectingLogs() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.getLogsBeforeDeadline(null, START));
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> source = Stream.generate(() -> {
            consumed.incrementAndGet();
            return log("A", "INFO", 0);
        });

        assertThrows(IllegalArgumentException.class,
                () -> analyzer.getLogsBeforeDeadline(source, null));
        assertEquals(0, consumed.get());
    }

    @Test
    void reportsEachBurstOnceEvenWhenTheRunExceedsThreshold() {
        Stream<SecurityLog> source = ips("A", "A", "A", "A", "A", "B", "B");

        assertEquals(List.of("A", "B"), analyzer.detectIpBursts(source, 2, 10));
    }

    @Test
    void resetsRunWhenIpChangesAndCanReportSameIpAgain() {
        assertEquals(List.of("A", "A"), analyzer.detectIpBursts(
                ips("A", "A", "B", "A", "A"), 2, 10));
    }

    @Test
    void isolatedEventsDoNotAccumulateAcrossDifferentRuns() {
        assertTrue(analyzer.detectIpBursts(
                ips("A", "B", "A", "B", "A"), 2, 10).isEmpty());
    }

    @Test
    void reportsFinalFiniteRunAtThresholdWithoutWaitingForNextIp() {
        assertEquals(List.of("A"), analyzer.detectIpBursts(ips("A", "A"), 2, 5));
    }

    @Test
    void incompleteFinalRunDoesNotProduceBurst() {
        assertTrue(analyzer.detectIpBursts(ips("A", "A"), 3, 5).isEmpty());
    }

    @Test
    void thresholdOneReportsOneIpPerContiguousRun() {
        assertEquals(List.of("A", "B", "A"), analyzer.detectIpBursts(
                ips("A", "A", "B", "B", "A"), 1, 10));
    }

    @Test
    void reportsBurstFromInfiniteGeneratedSourceAtExactThreshold() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> infinite = Stream.generate(() -> {
            assertTrue(consumed.incrementAndGet() <= 3,
                    "The first burst is complete at the third element");
            return log("192.168.0.1", "WARN", 0);
        });

        assertEquals(List.of("192.168.0.1"), analyzer.detectIpBursts(infinite, 3, 1));
        assertEquals(3, consumed.get());
    }

    @Test
    void limitsReportsOnInfiniteAlternatingRunsWithoutOverread() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                .map(i -> {
                    assertTrue(consumed.incrementAndGet() <= 8,
                            "The third burst is reached at the eighth element");
                    return log((i / 3) % 2 == 0 ? "A" : "B", "WARN", i);
                });

        assertEquals(List.of("A", "B", "A"), analyzer.detectIpBursts(infinite, 2, 3));
        assertEquals(8, consumed.get());
    }

    @Test
    void reportLimitStopsBeforeRemainingFiniteEvents() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<SecurityLog> source = ips("A", "A", "B", "B", "C", "C")
                .peek(ignored -> consumed.incrementAndGet());

        assertEquals(List.of("A", "B"), analyzer.detectIpBursts(source, 2, 2));
        assertEquals(4, consumed.get());
    }

    @Test
    void emptyStreamHasNoBursts() {
        assertTrue(analyzer.detectIpBursts(Stream.empty(), 2, 5).isEmpty());
    }

    @Test
    void burstStateIsIndependentForEachInvocation() {
        assertTrue(analyzer.detectIpBursts(ips("A"), 2, 3).isEmpty());
        assertTrue(analyzer.detectIpBursts(ips("A"), 2, 3).isEmpty());
        assertEquals(List.of("B"), analyzer.detectIpBursts(ips("B", "B"), 2, 3));
    }

    @Test
    void orderedParallelInputPreservesSequentialBurstSemantics() {
        assertEquals(List.of("A", "B", "A"), analyzer.detectIpBursts(
                ips("A", "A", "B", "B", "A", "A").parallel(), 2, 3));
    }

    @Test
    void rejectsNullStreamWhenDetectingBursts() {
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.detectIpBursts(null, 2, 3));
    }

    @Test
    void rejectsNonPositiveBurstThresholdWithoutConsumingSource() {
        for (int threshold : new int[] {0, -1, Integer.MIN_VALUE}) {
            AtomicInteger consumed = new AtomicInteger();
            Stream<SecurityLog> source = Stream.generate(() -> {
                consumed.incrementAndGet();
                return log("A", "WARN", 0);
            });

            assertThrows(IllegalArgumentException.class,
                    () -> analyzer.detectIpBursts(source, threshold, 2));
            assertEquals(0, consumed.get());
        }
    }

    @Test
    void rejectsNonPositiveReportLimitWithoutConsumingSource() {
        for (int reportLimit : new int[] {0, -1, Integer.MIN_VALUE}) {
            AtomicInteger consumed = new AtomicInteger();
            Stream<SecurityLog> source = Stream.generate(() -> {
                consumed.incrementAndGet();
                return log("A", "WARN", 0);
            });

            assertThrows(IllegalArgumentException.class,
                    () -> analyzer.detectIpBursts(source, 2, reportLimit));
            assertEquals(0, consumed.get());
        }
    }

    private static Stream<SecurityLog> ips(String... addresses) {
        return Stream.of(addresses).map(address -> log(address, "WARN", 0));
    }

    private static SecurityLog log(String ip, String severity, int seconds) {
        return new SecurityLog(ip, severity, "Test event", START.plusSeconds(seconds));
    }
}
