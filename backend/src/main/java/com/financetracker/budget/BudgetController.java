package com.financetracker.budget;

import com.financetracker.budget.dto.BudgetResponse;
import com.financetracker.budget.dto.BudgetsResponse;
import com.financetracker.budget.dto.CreateBudgetRequest;
import com.financetracker.budget.dto.UpdateBudgetRequest;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
@SecurityRequirement(name = "bearer-jwt")
public class BudgetController {

  private final BudgetService budgetService;

  public BudgetController(BudgetService budgetService) {
    this.budgetService = budgetService;
  }

  /**
   * The user's budgets with progress for {@code month} ({@code YYYY-MM}; defaults to this month).
   */
  @GetMapping
  public BudgetsResponse list(
      @CurrentUser AuthUser user, @RequestParam(required = false) String month) {
    return budgetService.list(user.id(), month);
  }

  @PostMapping
  public ResponseEntity<BudgetResponse> create(
      @CurrentUser AuthUser user, @Valid @RequestBody CreateBudgetRequest request) {
    BudgetResponse created = budgetService.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/budgets/" + created.id())).body(created);
  }

  @PatchMapping("/{id}")
  public BudgetResponse update(
      @CurrentUser AuthUser user,
      @PathVariable long id,
      @Valid @RequestBody UpdateBudgetRequest request) {
    return budgetService.update(user.id(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUser AuthUser user, @PathVariable long id) {
    budgetService.delete(user.id(), id);
  }
}
