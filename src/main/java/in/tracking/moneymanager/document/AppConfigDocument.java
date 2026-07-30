package in.tracking.moneymanager.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "app_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppConfigDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @JsonProperty("key")
    private String configKey;

    @JsonProperty("value")
    private String configValue;

    private String category;

    private String description;

    @Builder.Default
    private Boolean isSecret = false;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private String updatedBy;
}
