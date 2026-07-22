package com.financetracker.export;

import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.export.dto.ExportedTransaction;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
@SecurityRequirement(name = "bearer-jwt")
public class ExportController {

  private final ExportService exportService;

  public ExportController(ExportService exportService) {
    this.exportService = exportService;
  }

  @GetMapping("/transactions")
  public List<ExportedTransaction> json(@CurrentUser AuthUser user) {
    return exportService.transactions(user.id());
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
