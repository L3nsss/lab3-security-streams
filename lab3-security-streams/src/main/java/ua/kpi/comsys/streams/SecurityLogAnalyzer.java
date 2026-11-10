package ua.kpi.comsys.streams;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

/** Lazy analysis of security events in encounter order. */
public class SecurityLogAnalyzer {

    /** Finds the first CRITICAL event, or an empty Optional for exhausted input. */
    public Optional<SecurityLog> findFirstCriticalEvent(Stream<SecurityLog> stream) {
        requireArgument(stream, "stream");
        return stream.sequential()
                .filter(log -> "CRITICAL".equals(log.severityLevel()))
                .findFirst();
    }

    /**
     * Returns the prefix strictly before deadline. The caller supplies events
     * ordered by nondecreasing timestamp; the first event at/after deadline stops input.
     */
    public List<SecurityLog> getLogsBeforeDeadline(
            Stream<SecurityLog> stream, LocalDateTime deadline) {
        requireArgument(stream, "stream");
        requireArgument(deadline, "deadline");
        return stream.sequential()
                .takeWhile(log -> log.timestamp().isBefore(deadline))
                .toList();
    }

    /**
     * Reports an IP once when its consecutive run reaches burstThreshold.
     * Separate runs of the same IP can produce separate reports.
     */
    public List<String> detectIpBursts(
            Stream<SecurityLog> stream, int burstThreshold, int maxIpsToReport) {
        requireArgument(stream, "stream");
        if (burstThreshold <= 0) {
            throw new IllegalArgumentException("burstThreshold must be positive");
        }
        if (maxIpsToReport <= 0) {
            throw new IllegalArgumentException("maxIpsToReport must be positive");
        }

        return stream.sequential()
                .gather(burstGatherer(burstThreshold))
                .limit(maxIpsToReport)
                .toList();
    }

    private static Gatherer<SecurityLog, BurstState, String> burstGatherer(int threshold) {
        return Gatherer.ofSequential(BurstState::new, (state, log, downstream) -> {
            String ip = log.ipAddress();
            if (!Objects.equals(state.currentIp, ip)) {
                state.currentIp = ip;
                state.runLength = 0;
            }

            // Saturation prevents integer overflow and duplicate reports for a long run.
            if (state.runLength < threshold) {
                state.runLength++;
                if (state.runLength == threshold) {
                    // Propagate downstream cancellation, including limit(maxIpsToReport).
                    return downstream.push(ip);
                }
            }
            return true;
        });
    }

    private static void requireArgument(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    private static final class BurstState {
        private String currentIp;
        private int runLength;
    }
}
