package tz.ppra.ppraAMS.service;

import tz.ppra.ppraAMS.model.Asset;
import tz.ppra.ppraAMS.model.AssetStatus;

import java.util.List;
import java.util.Optional;

public interface AssetService {

    Asset create(Asset asset);

    Optional<Asset> findById(Long id);

    Optional<Asset> findByBarcode(String barcode);

    Optional<Asset> findBySerialNumber(String serialNumber);

    List<Asset> list();

    List<Asset> listByStatus(AssetStatus status);

    List<Asset> listByType(String assetType);

    Asset update(Long id, Asset patch);

    Asset changeStatus(Long id, AssetStatus status);

    void delete(Long id);
}
