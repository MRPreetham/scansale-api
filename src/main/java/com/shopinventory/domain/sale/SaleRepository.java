package com.shopinventory.domain.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByOrgIdAndId(UUID orgId, UUID id);

    org.springframework.data.domain.Page<Sale> findByOrgIdOrderBySoldAtDesc(UUID orgId,
                                                                            org.springframework.data.domain.Pageable pageable);

    @Query("select coalesce(max(s.numberSeq), 0) from Sale s where s.org.id = :orgId and s.year = :year")
    Integer maxNumberSeq(@Param("orgId") UUID orgId, @Param("year") int year);

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s where s.org.id = :orgId and s.status = 'SUBMITTED' " +
            " and s.soldAt >= :from and s.soldAt < :to")
    BigDecimal sumAmountBetween(@Param("orgId") UUID orgId,
                                @Param("from") Instant from, @Param("to") Instant to);

    @Query("select s.paymentMode, coalesce(sum(s.totalAmount), 0) from Sale s " +
            " where s.org.id = :orgId and s.status = 'SUBMITTED' and s.soldAt >= :from and s.soldAt < :to " +
            " group by s.paymentMode")
    List<Object[]> paymentModeBreakdown(@Param("orgId") UUID orgId,
                                        @Param("from") Instant from, @Param("to") Instant to);
}