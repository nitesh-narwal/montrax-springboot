package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row per profile per day - powers the net worth trend chart.
 * Populated by a daily scheduled job (see AccountService's caller
 * NetWorthSnapshotScheduler), not written on every account change.
 */
@Entity
@Data
@Table(name = "tbl_net_worth_snapshots", indexes = {
        @Index(name = "idx_networth_profile_date", columnList = "profile_id,snapshot_date")
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NetWorthSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "networth_seq")
    @SequenceGenerator(name = "networth_seq", sequenceName = "seq_net_worth_snapshots", allocationSize = 1)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_net_worth", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalNetWorth;
}
