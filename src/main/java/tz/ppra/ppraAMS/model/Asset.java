package tz.ppra.ppraAMS.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(
        name = "assets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_asset_barcode", columnNames = "barcode"),
                @UniqueConstraint(name = "uk_asset_serial", columnNames = "serial_number")
        }
)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_type", nullable = false)
    private String assetType;              // e.g., LAPTOP, PRINTER, DESK

    @Column(nullable = false)
    private String model;                  // e.g., "Dell Latitude 5420"

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false, unique = true)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AssetCondition condition = AssetCondition.NEW;  // e.g. NEW, VERYGOOD, GOOD, FAIR

    /**
     * Free-form description of the asset.
     * Populated from the Excel "DESCRIPTION (MS-OFFICE | ANTI-VIRUS | OS)" column.
     * Example: "MS - OFFICE 2024 | KASPERSKY-JUNE 2027 | WINDOWS 11 HOME"
     */
    @Column(name = "description", length = 500)
    private String description;

    // ---------- Auditing ----------
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
