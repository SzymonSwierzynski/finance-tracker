package com.financetracker.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Strict CORS allowlist — only the frontend origin(s) may call the API with credentials. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}
