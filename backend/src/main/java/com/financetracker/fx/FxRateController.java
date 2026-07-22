package com.financetracker.fx;

import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.fx.dto.FxRateResponse;
import com.financetracker.fx.dto.FxRatesResponse;
import com.financetracker.fx.dto.UpsertFxRateRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx/rates")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class FxRateController {

  private static final String CURRENCY_PATTERN = "^[A-Za-z]{3}$";

  private final FxRateService fxRateService;

  public FxRateController(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  @GetMapping
  public FxRatesResponse list(@CurrentUser AuthUser user) {
    return fxRateService.list(user.id());
  }

  @PutMapping("/{currency}")
  public FxRateResponse upsert(
      @CurrentUser AuthUser user,
      @PathVariable
          @Pattern(regexp = CURRENCY_PATTERN, message = "must be a 3-letter ISO 4217 currency code")
          String currency,
      @Valid @RequestBody UpsertFxRateRequest request) {
    return fxRateService.upsert(user.id(), currency, request);
  }

  @DeleteMapping("/{currency}")
  public ResponseEntity<Void> delete(
      @CurrentUser AuthUser user,
      @PathVariable
          @Pattern(regexp = CURRENCY_PATTERN, message = "must be a 3-letter ISO 4217 currency code")
          String currency) {
    fxRateService.delete(user.id(), currency);
    return ResponseEntity.noContent().build();
  }
}
