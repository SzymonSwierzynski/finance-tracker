package com.financetracker.reporting;

import com.financetracker.category.CategoryKind;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.reporting.dto.BreakdownResponse;
import com.financetracker.reporting.dto.SummaryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearer-jwt")
public class ReportingController {

  private final ReportingService reportingService;

  public ReportingController(ReportingService reportingService) {
    this.reportingService = reportingService;
  }

  @GetMapping("/summary")
  public SummaryResponse summary(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return reportingService.summary(user.id(), from, to);
  }

  @GetMapping("/breakdown")
  public BreakdownResponse breakdown(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "expense") CategoryKind kind,
      @RequestParam(required = false) Long parentId) {
    return reportingService.breakdown(user.id(), from, to, kind, parentId);
  }
}
