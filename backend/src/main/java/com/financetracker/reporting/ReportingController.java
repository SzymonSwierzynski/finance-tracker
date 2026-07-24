package com.financetracker.reporting;

import com.financetracker.category.CategoryKind;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.reporting.dto.BreakdownResponse;
import com.financetracker.reporting.dto.CashflowResponse;
import com.financetracker.reporting.dto.CategoryTrendResponse;
import com.financetracker.reporting.dto.ComparisonResponse;
import com.financetracker.reporting.dto.SummaryResponse;
import com.financetracker.reporting.dto.TrendResponse;
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

  @GetMapping("/comparison")
  public ComparisonResponse comparison(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "month") String mode) {
    return reportingService.comparison(user.id(), from, to, mode);
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

  @GetMapping("/trend")
  public TrendResponse trend(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "month") String interval) {
    return reportingService.trend(user.id(), from, to, interval);
  }

  @GetMapping("/cashflow")
  public CashflowResponse cashflow(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "month") String interval) {
    return reportingService.cashflow(user.id(), from, to, interval);
  }

  @GetMapping("/category-trend")
  public CategoryTrendResponse categoryTrend(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "month") String interval,
      @RequestParam(defaultValue = "expense") CategoryKind kind) {
    return reportingService.categoryTrend(user.id(), from, to, interval, kind);
  }
}
