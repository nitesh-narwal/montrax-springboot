package in.tracking.moneymanager.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Published after an expense is persisted so interested services (budget
 * alerts, analytics, ...) can react without ExpenceService depending on them directly.
 */
@Getter
public class ExpenseCreatedEvent extends ApplicationEvent {

    private final Long profileId;
    private final Long categoryId;
    private final BigDecimal amount;

    public ExpenseCreatedEvent(Object source, Long profileId, Long categoryId, BigDecimal amount) {
        super(source);
        this.profileId = profileId;
        this.categoryId = categoryId;
        this.amount = amount;
    }
}
