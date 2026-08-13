package com.shopinventory.domain.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<Product> findByOrgIdAndBarcode(UUID orgId, String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.org.id = :orgId and p.id = :id")
    Optional<Product> findForUpdate(@Param("orgId") UUID orgId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.org.id = :orgId and p.barcode = :barcode")
    Optional<Product> findForUpdateByBarcode(@Param("orgId") UUID orgId, @Param("barcode") String barcode);

    List<Product> findAllByOrgIdOrderByNameAsc(UUID orgId);

    List<Product> findByOrgIdAndAvailableQtyLessThanEqualOrderByNameAsc(UUID orgId, java.math.BigDecimal threshold);

    @Query("select p from Product p where p.org.id = :orgId" +
            " and (:q = '' or lower(p.name) like lower(concat('%', :q, '%'))" +
            " or lower(p.barcode) like lower(concat('%', :q, '%'))" +
            " or lower(coalesce(p.sku, '')) like lower(concat('%', :q, '%')))" +
            " and (:lowOnly = false or p.availableQty <= p.reorderLevel)" +
            " order by p.name asc")
    org.springframework.data.domain.Page<Product> searchPage(@Param("orgId") UUID orgId,
                                                              @Param("q") String q,
                                                              @Param("lowOnly") boolean lowOnly,
                                                              org.springframework.data.domain.Pageable pageable);
}