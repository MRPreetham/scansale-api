package com.shopinventory.domain.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaleLineRepository extends JpaRepository<SaleLine, UUID> {
}