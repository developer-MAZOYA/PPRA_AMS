package tz.ppra.ppraAMS.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetStatus;

import java.util.List;
import java.util.Optional;

public interface AssetRepo extends JpaRepository<Asset, Long> {

    Optional<Asset> findByBarcode(String barcode);

    Optional<Asset> findBySerialNumber(String serialNumber);

    List<Asset> findByStatus(AssetStatus status);

    List<Asset> findByAssetType(String assetType);

    boolean existsByBarcode(String barcode);

    boolean existsBySerialNumber(String serialNumber);
}
