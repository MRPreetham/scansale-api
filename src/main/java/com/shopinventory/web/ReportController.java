package com.shopinventory.web;

import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.Capabilities;
import com.shopinventory.service.ReportService;
import com.shopinventory.web.dto.Dtos.DailyReportResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAuthority('" + Capabilities.REPORT_READ + "')")
    public DailyReportResponse daily(@AuthenticationPrincipal AppPrincipal principal,
                                     @RequestParam String date) {
        return reportService.daily(principal.orgId(), date);
    }

    @GetMapping("/period")
    @PreAuthorize("hasAuthority('" + Capabilities.REPORT_READ + "')")
    public DailyReportResponse period(@AuthenticationPrincipal AppPrincipal principal,
                                      @RequestParam String from,
                                      @RequestParam String to) {
        return reportService.period(principal.orgId(), from, to);
    }
}