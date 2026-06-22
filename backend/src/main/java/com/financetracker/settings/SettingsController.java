package com.financetracker.settings;

import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import com.financetracker.settings.dto.SettingsResponse;
import com.financetracker.settings.dto.UpdateSettingsRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@SecurityRequirement(name = "bearer-jwt")
public class SettingsController {

  private final SettingsService settingsService;

  public SettingsController(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @GetMapping
  public SettingsResponse get(@CurrentUser AuthUser user) {
    return settingsService.get(user.id());
  }

  @PutMapping
  public SettingsResponse update(
      @CurrentUser AuthUser user, @Valid @RequestBody UpdateSettingsRequest request) {
    return settingsService.update(user.id(), request);
  }
}
