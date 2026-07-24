package com.financetracker.rule;

import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.rule.dto.ApplyRulesResponse;
import com.financetracker.rule.dto.CreateRuleRequest;
import com.financetracker.rule.dto.RuleResponse;
import com.financetracker.rule.dto.UpdateRuleRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
@SecurityRequirement(name = "bearer-jwt")
public class RuleController {

  private final RuleService ruleService;

  public RuleController(RuleService ruleService) {
    this.ruleService = ruleService;
  }

  @GetMapping
  public List<RuleResponse> list(@CurrentUser AuthUser user) {
    return ruleService.list(user.id());
  }

  @PostMapping
  public ResponseEntity<RuleResponse> create(
      @CurrentUser AuthUser user, @Valid @RequestBody CreateRuleRequest request) {
    RuleResponse created = ruleService.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/rules/" + created.id())).body(created);
  }

  @PatchMapping("/{id}")
  public RuleResponse update(
      @CurrentUser AuthUser user,
      @PathVariable long id,
      @Valid @RequestBody UpdateRuleRequest request) {
    return ruleService.update(user.id(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUser AuthUser user, @PathVariable long id) {
    ruleService.delete(user.id(), id);
  }

  @PostMapping("/apply")
  public ApplyRulesResponse apply(@CurrentUser AuthUser user) {
    return ruleService.apply(user.id());
  }
}
