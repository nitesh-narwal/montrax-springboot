package in.tracking.moneymanager.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDocument {

    @Id
    private String id;

    @Indexed
    private String userEmail;

    private String method;

    private String path;

    private int statusCode;

    private String ipAddress;

    private long durationMs;

    @Indexed
    private LocalDateTime timestamp;
}
