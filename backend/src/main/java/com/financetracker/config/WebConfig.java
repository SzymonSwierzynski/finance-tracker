package com.financetracker.config;

import com.financetracker.category.CategoryKind;
import com.financetracker.common.security.CurrentUserArgumentResolver;
import com.financetracker.transaction.TransactionType;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the {@code @CurrentUser} argument resolver and lowercase enum query-param binding. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final CurrentUserArgumentResolver currentUserArgumentResolver;

  public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
    this.currentUserArgumentResolver = currentUserArgumentResolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentUserArgumentResolver);
  }

  @Override
  public void addFormatters(FormatterRegistry registry) {
    // Query params speak the lowercase token (e.g. ?type=expense), like the JSON body does — the
    // default enum binder uses Enum.valueOf (upper-case names), so register the value()-based
    // parse.
    registry.addConverter(String.class, TransactionType.class, TransactionType::fromValue);
    registry.addConverter(String.class, CategoryKind.class, CategoryKind::fromValue);
  }
}
