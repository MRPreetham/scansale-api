package com.shopinventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.stock.AuditLog;
import com.shopinventory.domain.stock.AuditLogRepository;
import com.shopinventory.domain.stock.PlatformAudit;
import com.shopinventory.domain.stock.PlatformAuditRepository;
import com.shopinventory.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final PlatformAuditRepository platformAuditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        PlatformAuditRepository platformAuditRepository,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.platformAuditRepository = platformAuditRepository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID orgId, User actor, String action, String entityType, String entityId, Map<String, Object> details) {
        try {
            AuditLog entry = new AuditLog();
            Organization org = new Organization();
            org.setId(orgId);
            entry.setOrg(org);
            entry.setActor(actor);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetailsJson(objectMapper.writeValueAsString(details == null ? Map.of() : details));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }

    /** Platform-level audit trail (no org context) — super admin / support actions. */
    public void logPlatform(User actor, String action, String entityType, String entityId, Map<String, Object> details) {
        try {
            PlatformAudit entry = new PlatformAudit();
            entry.setActor(actor);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetailsJson(objectMapper.writeValueAsString(details == null ? Map.of() : details));
            platformAuditRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write platform audit log for action {}: {}", action, e.getMessage());
        }
    }
}