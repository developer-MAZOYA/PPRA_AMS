package tz.ppra.ppraAMS.service.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetCondition;
import tz.ppra.ppraAMS.model.AssetStatus;
import tz.ppra.ppraAMS.repo.AssetRepo;
import tz.ppra.ppraAMS.service.AssetService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepo assetRepo;

    // ---------- Create ----------
    @Override
    public Asset create(Asset dto) {
        validateRequired(dto);

        if (assetRepo.existsByBarcode(dto.getBarcode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barcode already exists");
        }
        if (assetRepo.existsBySerialNumber(dto.getSerialNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial number already exists");
        }

        Asset asset = Asset.builder()
                .assetType(dto.getAssetType().trim().toUpperCase())
                .model(dto.getModel().trim())
                .serialNumber(dto.getSerialNumber().trim())
                .barcode(dto.getBarcode().trim())
                .status(dto.getStatus() != null ? dto.getStatus() : AssetStatus.AVAILABLE)
                .condition(dto.getCondition() != null ? dto.getCondition() : AssetCondition.NEW)
                .build();

        return assetRepo.save(asset);
    }

    // ---------- Read ----------
    @Override
    public Optional<Asset> findById(Long id) {
        return assetRepo.findById(id);
    }

    @Override
    public Optional<Asset> findByBarcode(String barcode) {
        return assetRepo.findByBarcode(barcode);
    }

    @Override
    public Optional<Asset> findBySerialNumber(String serialNumber) {
        return assetRepo.findBySerialNumber(serialNumber);
    }

    @Override
    public List<Asset> list() {
        return assetRepo.findAll();
    }

    @Override
    public List<Asset> listByStatus(AssetStatus status) {
        return assetRepo.findByStatus(status);
    }

    @Override
    public List<Asset> listByType(String assetType) {
        return assetRepo.findByAssetType(assetType.toUpperCase());
    }

    // ---------- Update ----------
    @Override
    public Asset update(Long id, Asset patch) {
        Asset asset = assetRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        if (patch.getAssetType() != null && !patch.getAssetType().isBlank()) {
            asset.setAssetType(patch.getAssetType().trim().toUpperCase());
        }
        if (patch.getModel() != null && !patch.getModel().isBlank()) {
            asset.setModel(patch.getModel().trim());
        }
        if (patch.getSerialNumber() != null && !patch.getSerialNumber().isBlank()
                && !patch.getSerialNumber().equals(asset.getSerialNumber())) {
            if (assetRepo.existsBySerialNumber(patch.getSerialNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial number already in use");
            }
            asset.setSerialNumber(patch.getSerialNumber().trim());
        }
        if (patch.getBarcode() != null && !patch.getBarcode().isBlank()
                && !patch.getBarcode().equals(asset.getBarcode())) {
            if (assetRepo.existsByBarcode(patch.getBarcode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barcode already in use");
            }
            asset.setBarcode(patch.getBarcode().trim());
        }
        if (patch.getStatus() != null) {
            asset.setStatus(patch.getStatus());
        }
        if (patch.getCondition() != null) {
            asset.setCondition(patch.getCondition());
        }

        return assetRepo.save(asset);
    }

    // ---------- Status change ----------
    @Override
    public Asset changeStatus(Long id, AssetStatus status) {
        Asset asset = assetRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
        asset.setStatus(status);
        return assetRepo.save(asset);
    }

    // ---------- Delete ----------
    @Override
    public void delete(Long id) {
        if (!assetRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found");
        }
        assetRepo.deleteById(id);
    }

    // ---------- Validation ----------
    private void validateRequired(Asset dto) {
        if (dto.getAssetType() == null || dto.getAssetType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset type is required");
        }
        if (dto.getModel() == null || dto.getModel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model is required");
        }
        if (dto.getSerialNumber() == null || dto.getSerialNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial number is required");
        }
        if (dto.getBarcode() == null || dto.getBarcode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barcode is required");
        }
    }
}
