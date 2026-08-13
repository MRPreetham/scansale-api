package com.shopinventory.web.dto;

import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.PlatformRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Dtos {

    private Dtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record AuthResponse(
            String token, UUID userId, String email, String name,
            UUID orgId, String orgName, OrgRole role, String orgStatus, PlatformRole platformRole,
            String currency) {
    }

    public record MeResponse(
            UUID userId, String email, String name,
            UUID orgId, String orgName, OrgRole role, String orgStatus, PlatformRole platformRole,
            String currency) {
    }

    public record ProductRequest(
            String sku,
            @NotBlank String name,
            @NotBlank String barcode,
            String unit,
            BigDecimal sellingPrice,
            BigDecimal reorderLevel,
            BigDecimal openingQty,
            String notes) {
    }

    public record ProductResponse(
            UUID id, String sku, String name, String barcode, String unit,
            BigDecimal sellingPrice, BigDecimal openingQty, BigDecimal availableQty,
            BigDecimal reorderLevel, boolean lowStock, String notes,
            java.time.Instant createdAt, java.time.Instant updatedAt) {
    }

    public record ProductPageResponse(List<ProductResponse> items, long total, int page, int size) {
    }

    public record AdjustStockRequest(java.math.BigDecimal newQuantity, String reason) {
    }

    public record SaleLineRequest(@NotBlank String barcode, java.math.BigDecimal qty) {
    }

    public record SaleRequest(
            List<SaleLineRequest> lines,
            com.shopinventory.domain.sale.PaymentMode paymentMode,
            String notes) {
    }

    public record SaleLineResponse(
            UUID productId, String barcode, String name,
            BigDecimal qty, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record SaleResponse(
            UUID id, String saleNumber,
            java.time.Instant soldAt, String cashierName,
            com.shopinventory.domain.sale.PaymentMode paymentMode,
            String status, BigDecimal totalQty, BigDecimal totalAmount,
            List<SaleLineResponse> lines) {
    }

    public record SalePageResponse(List<SaleResponse> items, long total, int page, int size) {
    }

    public record RowError(int row, String message) {
    }

    public record ImportPreviewResponse(
            UUID importId, String filename,
            int newCount, int updateCount, int skipCount,
            List<RowError> errors) {
    }

    public record ImportCommitResponse(
            UUID importId, int newCount, int updateCount, int skipCount) {
    }

    public record ImportHistoryResponse(
            UUID id, String filename, String status,
            java.time.Instant importedAt, int newCount, int updateCount, int skipCount) {
    }

    public record ImportHistoryPageResponse(
            List<ImportHistoryResponse> items, long total, int page, int size) {
    }

    public record DailyRow(
            UUID productId, String sku, String name, String barcode,
            BigDecimal openingQty, BigDecimal placedQty, BigDecimal soldQty,
            BigDecimal endQty, BigDecimal reorderLevel, boolean lowStock) {
    }

    public record DailyReportResponse(
            String date, BigDecimal totalSalesAmount, BigDecimal totalUnitsSold,
            Map<String, BigDecimal> paymentBreakdown, List<DailyRow> rows) {
    }

    public record OrgUserRequest(
            @NotBlank @Email String email,
            @NotBlank String name,
            @NotBlank String password,
            com.shopinventory.domain.user.OrgRole role) {
    }

    public record OrgUserResponse(
            UUID userId, String email, String name,
            com.shopinventory.domain.user.OrgRole role,
            com.shopinventory.domain.user.MembershipStatus status) {
    }

    public record SettingsRequest(String orgName, String currency) {
    }

    public record SettingsResponse(String orgName, String currency) {
    }

    public record OnboardOrgRequest(
            @NotBlank String orgName,
            String currency,
            @NotBlank @Email String adminEmail,
            @NotBlank String adminName,
            @NotBlank String adminPassword) {
    }

    public record OnboardOrgResponse(
            UUID orgId, String orgName, String currency, String orgStatus,
            UUID adminId, String adminEmail, String adminName) {
    }

    public record PlatformOrgSummary(
            UUID orgId, String orgName,
            com.shopinventory.domain.organization.OrgStatus status, String currency,
            java.time.Instant createdAt, String adminEmail,
            long userCount) {
    }

    public record OrgStatusRequest(com.shopinventory.domain.organization.OrgStatus status) {
    }

    public record UpdateAdminRequest(String email, String name, String password) {
    }

    public record PlatformAdminResponse(UUID userId, String email, String name) {
    }

    public record CreatePlatformUserRequest(
            @NotBlank @Email String email,
            @NotBlank String name,
            @NotBlank String password,
            PlatformRole platformRole) {
    }

    public record UpdatePlatformUserRequest(String email, String name, String password, PlatformRole platformRole) {
    }

    public record PlatformUserResponse(UUID userId, String email, String name, PlatformRole platformRole) {
    }

    public record PlatformAuditResponse(
            UUID id, String actorEmail, String action, String entityType, String entityId,
            String detailsJson, java.time.Instant createdAt) {
    }
}