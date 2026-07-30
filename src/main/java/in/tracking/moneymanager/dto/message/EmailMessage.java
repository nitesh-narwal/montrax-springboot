package in.tracking.moneymanager.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailMessage implements Serializable {
    private String to;
    private String subject;
    private String body;
}
