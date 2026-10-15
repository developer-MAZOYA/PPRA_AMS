package tz.ppra.ppraAMS.model;

public enum AssetStatus {
    AVAILABLE,       // in stock, not assigned
    ASSIGNED,        // currently assigned to a user
    UNDER_MAINTENANCE,
    RETIRED,
    LOST,
    STOLEN

}
