package com.financetracker.export;

import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.export.dto.BackupResponse;
import com.financetracker.export.dto.ExportedTransaction;
import com.financetracker.export.dto.RestoreSummary;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
@SecurityRequirement(name = "bearer-jwt")
public class ExportController {

  private final ExportService exportService;
  private final RestoreService restoreService;

  public ExportController(ExportService exportService, RestoreService restoreService) {
    this.exportService = exportService;
    this.restoreService = restoreService;
  }

  @GetMapping("/transactions")
  public List<ExportedTransaction> json(@CurrentUser AuthUser user) {
    return exportService.transactions(user.id());
  }

  /** Full-data backup: reporting currency + accounts + categories + all transactions as JSON. */
  @GetMapping("/backup")
  public BackupResponse backup(@CurrentUser AuthUser user) {
    return exportService.backup(user.id());
  }

  /**
   * Restore a backup produced by {@link #backup}. Additive and idempotent: accounts/categories are
   * matched by name, transactions deduped by hash — re-posting the same backup imports nothing new.
   */
  @PostMapping("/restore")
  public RestoreSummary restore(@CurrentUser AuthUser user, @RequestBody BackupResponse backup) {
    return restoreService.restore(user.id(), backup);
  }

  @GetMapping(value = "/transactions/csv", produces = "text/csv")
  public ResponseEntity<String> csv(@CurrentUser AuthUser user) {
    String csv = exportService.toCsv(exportService.transactions(user.id()));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
        .contentType(MediaType.valueOf("text/csv"))
        .body(csv);
  }
}
