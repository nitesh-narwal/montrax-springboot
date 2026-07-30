package in.tracking.moneymanager.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditEvent implements Serializable {
    private String userEmail;
    private String method;
    private String path;
    private int statusCode;
    private String ipAddress;
    private long durationMs;
    private LocalDateTime timestamp;
}
