package tz.ppra.ppraAMS.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "asset_assignments",
        indexes = {
                @Index(name = "idx_asset_open", columnList = "asset_id, returned_at")
        }
)
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ---------- Assignment tracking ----------
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by", nullable = false)
    private String assignedBy;               // email of admin who assigned

    // ---------- Return tracking ----------
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "returned_by")
    private String returnedBy;               // email of admin who processed return

    @Column(length = 500)
    private String assignNotes;

    @Column(length = 500)
    private String returnNotes;

    public boolean isOpen() {
        return returnedAt == null;
    }
    // In AssetAssignment.java
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_at_assign")
    private AssetCondition conditionAtAssign;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_at_return")
    private AssetCondition conditionAtReturn;
    @PrePersist
    void onCreate() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}
