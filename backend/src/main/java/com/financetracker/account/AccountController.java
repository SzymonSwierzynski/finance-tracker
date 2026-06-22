package com.financetracker.account;

import com.financetracker.account.dto.AccountBalanceResponse;
import com.financetracker.account.dto.AccountResponse;
import com.financetracker.account.dto.CreateAccountRequest;
import com.financetracker.account.dto.UpdateAccountRequest;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@SecurityRequirement(name = "bearer-jwt")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping
  public List<AccountResponse> list(
      @CurrentUser AuthUser user,
      @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
    return accountService.list(user.id(), includeArchived);
  }

  @PostMapping
  public ResponseEntity<AccountResponse> create(
      @CurrentUser AuthUser user, @Valid @RequestBody CreateAccountRequest request) {
    AccountResponse created = accountService.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public AccountResponse get(@CurrentUser AuthUser user, @PathVariable long id) {
    return accountService.get(user.id(), id);
  }

  @PatchMapping("/{id}")
  public AccountResponse update(
      @CurrentUser AuthUser user,
      @PathVariable long id,
      @Valid @RequestBody UpdateAccountRequest request) {
    return accountService.update(user.id(), id, request);
  }

  @PostMapping("/{id}/archive")
  public ResponseEntity<Void> archive(@CurrentUser AuthUser user, @PathVariable long id) {
    accountService.archive(user.id(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/balance")
  public AccountBalanceResponse balance(@CurrentUser AuthUser user, @PathVariable long id) {
    return accountService.balance(user.id(), id);
  }
}
