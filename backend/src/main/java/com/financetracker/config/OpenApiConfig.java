package com.financetracker.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The generated spec at {@code /v3/api-docs} is the source of truth for the
 * frontend's generated TS types; Swagger UI is served at {@code /swagger-ui.html}.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Personal Finance Tracker API",
            version = "v1",
            description = "REST API for the Personal Finance Tracker."),
    servers = @Server(url = "/", description = "Default"))
@SecurityScheme(
    name = "bearer-jwt",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
