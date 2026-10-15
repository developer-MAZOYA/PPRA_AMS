package tz.ppra.ppraAMS.service;

import tz.ppra.ppraAMS.model.AssetAssignment;
import tz.ppra.ppraAMS.model.AssetCondition;

import java.util.List;
import java.util.Optional;

public interface AssetAssignmentService {
    AssetAssignment assign(Long assetId, Long userId, String adminEmail,
                           String notes, AssetCondition conditionAtAssign);

    AssetAssignment returnAsset(Long assetId, String adminEmail,
                                String notes, AssetCondition conditionAtReturn);

    Optional<AssetAssignment> findOpenByAsset(Long assetId);

    List<AssetAssignment> historyByAsset(Long assetId);

    List<AssetAssignment> historyByUser(Long userId);

    List<AssetAssignment> currentByUser(Long userId);

    List<AssetAssignment> currentForEmail(String name);

    List<AssetAssignment> historyForEmail(String name);
}
