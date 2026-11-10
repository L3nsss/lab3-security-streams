package ua.kpi.comsys.streams;

import java.time.LocalDateTime;

/** An immutable security event. */
public record SecurityLog(
        String ipAddress,
        String severityLevel,
        String eventDescription,
        LocalDateTime timestamp) {
}
