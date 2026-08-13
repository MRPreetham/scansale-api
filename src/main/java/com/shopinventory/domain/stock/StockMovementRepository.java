package com.shopinventory.domain.stock;

import com.shopinventory.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("select m.product.id as pid, sum(m.qtyDelta) as s from StockMovement m " +
            " where m.org.id = :orgId and m.createdAt >= :from group by m.product.id")
    List<Object[]> sumSince(@Param("orgId") UUID orgId, @Param("from") Instant from);

    @Query("select m.product.id as pid, sum(m.qtyDelta) as s from StockMovement m " +
            " where m.org.id = :orgId and m.createdAt >= :from and m.createdAt < :to group by m.product.id")
    List<Object[]> sumBetween(@Param("orgId") UUID orgId,
                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("select m.product.id as pid, sum(m.qtyDelta) as s from StockMovement m " +
            " where m.org.id = :orgId and m.createdAt >= :from and m.createdAt < :to and m.qtyDelta > 0 group by m.product.id")
    List<Object[]> placedBetween(@Param("orgId") UUID orgId,
                                 @Param("from") Instant from, @Param("to") Instant to);

    @Query("select m.product.id as pid, sum(-m.qtyDelta) as s from StockMovement m " +
            " where m.org.id = :orgId and m.type = 'SALE' and m.createdAt >= :from and m.createdAt < :to group by m.product.id")
    List<Object[]> soldBetween(@Param("orgId") UUID orgId,
                               @Param("from") Instant from, @Param("to") Instant to);

    List<StockMovement> findTop50ByOrgIdOrderByCreatedAtDesc(UUID orgId);

    boolean existsByOrgIdAndProductId(UUID orgId, UUID productId);
}