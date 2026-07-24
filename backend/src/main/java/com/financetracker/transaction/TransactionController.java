package com.financetracker.transaction;

import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.common.web.PageResponse;
import com.financetracker.transaction.dto.CreateTransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.transaction.dto.UpdateTransactionRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@SecurityRequirement(name = "bearer-jwt")
public class TransactionController {

  private final TransactionService transactionService;

  public TransactionController(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @GetMapping
  public PageResponse<TransactionResponse> list(
      @CurrentUser AuthUser user,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long accountId,
      @RequestParam(required = false) TransactionType type,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return transactionService.list(
        user.id(), from, to, accountId, type, categoryId, q, sort, page, size);
  }

  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @CurrentUser AuthUser user, @Valid @RequestBody CreateTransactionRequest request) {
    TransactionResponse created = transactionService.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/transactions/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public TransactionResponse get(@CurrentUser AuthUser user, @PathVariable long id) {
    return transactionService.get(user.id(), id);
  }

  @PatchMapping("/{id}")
  public TransactionResponse update(
      @CurrentUser AuthUser user,
      @PathVariable long id,
      @Valid @RequestBody UpdateTransactionRequest request) {
    return transactionService.update(user.id(), id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@CurrentUser AuthUser user, @PathVariable long id) {
    transactionService.delete(user.id(), id);
    return ResponseEntity.noContent().build();
  }
}
