package tz.ppra.ppraAMS.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.ppra.ppraAMS.model.AssetAssignment;

import java.util.List;
import java.util.Optional;

public interface AssetAssignmentRepo extends JpaRepository<AssetAssignment, Long> {

    @Query("SELECT a FROM AssetAssignment a WHERE a.asset.id = :assetId AND a.returnedAt IS NULL")
    Optional<AssetAssignment> findOpenByAssetId(@Param("assetId") Long assetId);

    boolean existsByAssetIdAndReturnedAtIsNull(Long assetId);

    List<AssetAssignment> findByAssetIdOrderByAssignedAtDesc(Long assetId);

    List<AssetAssignment> findByUserIdOrderByAssignedAtDesc(Long userId);

    @Query("SELECT a FROM AssetAssignment a WHERE a.user.id = :userId AND a.returnedAt IS NULL")
    List<AssetAssignment> findOpenByUserId(@Param("userId") Long userId);

    List<AssetAssignment> findOpenByUserEmail(String email);

    List<AssetAssignment> findAllByUserEmail(String email);
}
