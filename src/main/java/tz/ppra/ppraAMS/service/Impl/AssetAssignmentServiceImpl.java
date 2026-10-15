package tz.ppra.ppraAMS.service.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetAssignment;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.model.User;
import tz.ppra.ppraAMS.repo.AssetAssignmentRepo;
import tz.ppra.ppraAMS.repo.AssetRepo;
import tz.ppra.ppraAMS.repo.UserRepo;
import tz.ppra.ppraAMS.service.AssetAssignmentService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssetAssignmentServiceImpl implements AssetAssignmentService {

    private final AssetRepo assetRepo;
    private final UserRepo userRepo;
    private final AssetAssignmentRepo assignmentRepo;


    @Override
    @Transactional
    public AssetAssignment assign(Long assetId, Long userId, String adminEmail,
                                  String notes, AssetCondition conditionAtAssign) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (assignmentRepo.existsByAssetIdAndReturnedAtIsNull(assetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Asset is already assigned. Return it before reassigning.");
        }

        if (asset.getStatus() == AssetStatus.RETIRED || asset.getStatus() == AssetStatus.LOST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Asset is " + asset.getStatus() + " and cannot be assigned");
        }
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot assign to a disabled user");
        }

        AssetAssignment assignment = AssetAssignment.builder()
                .asset(asset)
                .user(user)
                .assignedAt(LocalDateTime.now())
                .assignedBy(adminEmail)
                .assignNotes(notes)
                .conditionAtAssign(conditionAtAssign != null ? conditionAtAssign : asset.getCondition())
                .build();

        asset.setStatus(AssetStatus.ASSIGNED);
        assetRepo.save(asset);

        return assignmentRepo.save(assignment);
    }

    @Override
    @Transactional
    public AssetAssignment returnAsset(Long assetId, String adminEmail,
                                       String notes, AssetCondition conditionAtReturn) {
        AssetAssignment assignment = assignmentRepo.findOpenByAssetId(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Asset is not currently assigned — nothing to return"));

        if (assignment.getReturnedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Asset has already been returned");
        }

        assignment.setReturnedAt(LocalDateTime.now());
        assignment.setReturnedBy(adminEmail);
        assignment.setReturnNotes(notes);
        assignment.setConditionAtReturn(conditionAtReturn);

        Asset asset = assignment.getAsset();
        if (conditionAtReturn != null) {
            asset.setCondition(conditionAtReturn);
        }
        asset.setStatus(AssetStatus.AVAILABLE);
        assetRepo.save(asset);

        return assignmentRepo.save(assignment);
    }

    @Override
    public Optional<AssetAssignment> findOpenByAsset(Long assetId) {
        return assignmentRepo.findOpenByAssetId(assetId);
    }

    @Override
    public List<AssetAssignment> historyByAsset(Long assetId) {
        return assignmentRepo.findByAssetIdOrderByAssignedAtDesc(assetId);
    }

    @Override
    public List<AssetAssignment> historyByUser(Long userId) {
        return assignmentRepo.findByUserIdOrderByAssignedAtDesc(userId);
    }

    @Override
    public List<AssetAssignment> currentByUser(Long userId) {
        return assignmentRepo.findOpenByUserId(userId);
    }

    @Override
    public List<AssetAssignment> currentForEmail(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptyList();
        }
        List<AssetAssignment> result = assignmentRepo.findOpenByUserEmail(email);
        return result != null ? result : Collections.emptyList();
    }

    @Override
    public List<AssetAssignment> historyForEmail(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptyList();
        }
        List<AssetAssignment> result = assignmentRepo.findAllByUserEmail(email);
        return result != null ? result : Collections.emptyList();
    }
}
