package com.shopinventory.web;

import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.Capabilities;
import com.shopinventory.service.CsvImportService;
import com.shopinventory.web.dto.Dtos.ImportCommitResponse;
import com.shopinventory.web.dto.Dtos.ImportHistoryPageResponse;
import com.shopinventory.web.dto.Dtos.ImportHistoryResponse;
import com.shopinventory.web.dto.Dtos.ImportPreviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final CsvImportService csvImportService;

    public ImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('" + Capabilities.IMPORT_EXECUTE + "')")
    public ImportPreviewResponse preview(@AuthenticationPrincipal AppPrincipal principal,
                                         @RequestPart("file") MultipartFile file) {
        return csvImportService.preview(principal.orgId(), principal, file);
    }

    @PostMapping("/{importId}/commit")
    @PreAuthorize("hasAuthority('" + Capabilities.IMPORT_EXECUTE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportCommitResponse commit(@AuthenticationPrincipal AppPrincipal principal,
                                       @PathVariable UUID importId) {
        return csvImportService.commit(principal.orgId(), principal, importId);
    }

    @GetMapping
    public ImportHistoryPageResponse history(@AuthenticationPrincipal AppPrincipal principal,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return csvImportService.history(principal.orgId(), page, size);
    }
}