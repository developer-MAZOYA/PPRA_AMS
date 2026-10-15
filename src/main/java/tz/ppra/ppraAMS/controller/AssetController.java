package tz.ppra.ppraAMS.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetAssignment;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.service.AssetAssignmentService;
import tz.ppra.ppraAMS.service.AssetService;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final AssetAssignmentService assetAssignmentService;

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Asset> assets() {
        return assetService.list();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Asset asset(@Argument("id") String id) {
        return assetService.findById(Long.parseLong(id)).orElse(null);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Asset assetByBarcode(@Argument("barcode") String barcode) {
        return assetService.findByBarcode(barcode).orElse(null);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Asset assetBySerialNumber(@Argument("serialNumber") String serialNumber) {
        return assetService.findBySerialNumber(serialNumber).orElse(null);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Asset> assetsByStatus(@Argument("status") AssetStatus status) {
        return assetService.listByStatus(status);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Asset> assetsByType(@Argument("assetType") String assetType) {
        return assetService.listByType(assetType);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Asset createAsset(@Argument("input") AssetInput input) {
        Asset asset = Asset.builder()
                .assetType(input.assetType())
                .model(input.model())
                .serialNumber(input.serialNumber())
                .barcode(input.barcode())
                .status(input.status() != null ? input.status() : AssetStatus.AVAILABLE)
                .condition(input.condition() != null ? input.condition() : AssetCondition.NEW)
                .build();
        return assetService.create(asset);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Asset updateAsset(@Argument("id") String id, @Argument("input") AssetInput input) {
        Asset patch = Asset.builder()
                .assetType(input.assetType())
                .model(input.model())
                .serialNumber(input.serialNumber())
                .barcode(input.barcode())
                .status(input.status())
                .condition(input.condition())
                .build();
        return assetService.update(Long.parseLong(id), patch);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Asset changeAssetStatus(@Argument("id") String id, @Argument("status") AssetStatus status) {
        return assetService.changeStatus(Long.parseLong(id), status);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public boolean deleteAsset(@Argument("id") String id) {
        assetService.delete(Long.parseLong(id));
        return true;
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AssetAssignment currentAssignment(@Argument("assetId") String assetId) {
        return assetAssignmentService.findOpenByAsset(Long.parseLong(assetId)).orElse(null);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AssetAssignment> assetHistory(@Argument("assetId") String assetId) {
        return assetAssignmentService.historyByAsset(Long.parseLong(assetId));
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AssetAssignment> userAssetHistory(@Argument("userId") String userId) {
        return assetAssignmentService.historyByUser(Long.parseLong(userId));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<AssetAssignment> myCurrentAssets() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
                return Collections.emptyList();
            }
            List<AssetAssignment> result = assetAssignmentService.currentForEmail(auth.getName());
            return result != null ? result : Collections.emptyList();
        } catch (Exception ex) {
            System.err.println(">>> myCurrentAssets error: " + ex.getMessage());
            ex.printStackTrace();
            return Collections.emptyList();
        }
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<AssetAssignment> myAssetHistory() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
                return Collections.emptyList();
            }
            List<AssetAssignment> result = assetAssignmentService.historyForEmail(auth.getName());
            return result != null ? result : Collections.emptyList();
        } catch (Exception ex) {
            System.err.println(">>> myAssetHistory error: " + ex.getMessage());
            ex.printStackTrace();
            return Collections.emptyList();
        }
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AssetAssignment assignAsset(@Argument("assetId") String assetId,
                                       @Argument("userId") String userId,
                                       @Argument("notes") String notes,
                                       @Argument("condition") AssetCondition condition) {
        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return assetAssignmentService.assign(
                Long.parseLong(assetId),
                Long.parseLong(userId),
                adminEmail,
                notes,
                condition
        );
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AssetAssignment returnAsset(@Argument("assetId") String assetId,
                                       @Argument("notes") String notes,
                                       @Argument("condition") AssetCondition condition) {
        String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return assetAssignmentService.returnAsset(
                Long.parseLong(assetId),
                adminEmail,
                notes,
                condition
        );
    }

    public record AssetInput(
            String assetType,
            String model,
            String serialNumber,
            String barcode,
            AssetStatus status,
            AssetCondition condition,
            String description
    ) {}
}
