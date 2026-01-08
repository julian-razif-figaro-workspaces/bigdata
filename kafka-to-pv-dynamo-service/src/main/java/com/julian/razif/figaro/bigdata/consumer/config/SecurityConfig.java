package com.julian.razif.figaro.bigdata.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Security configuration for the Kafka to DynamoDB service.
 * <p>
 * This configuration establishes security headers and basic web security settings
 * following OWASP best practices. It configures:
 * <ul>
 * <li>Content Security Policy (CSP) to prevent XSS attacks</li>
 * <li>X-XSS-Protection header for additional XSS protection</li>
 * <li>X-Frame-Options to prevent clickjacking</li>
 * <li>X-Content-Type-Options to prevent MIME sniffing</li>
 * <li>Strict-Transport-Security (HSTS) for HTTPS enforcement</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Security Note:</strong> This configuration provides defense-in-depth for
 * web-based attacks. For production deployments, review and adjust CSP directives
 * based on your specific requirements.
 * </p>
 *
 * @author Julian Razif Figaro
 * @version 1.0
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Configures HTTP security with comprehensive security headers.
   * <p>
   * Implements OWASP recommended security headers to protect against common
   * web vulnerabilities including XSS, clickjacking, and MIME sniffing attacks.
   * </p>
   * <p>
   * <strong>Headers Configured:</strong>
   * <ul>
   * <li>Content-Security-Policy: Restricts resource loading to same origin</li>
   * <li>X-XSS-Protection: Enables browser XSS filtering with blocking mode</li>
   * <li>X-Frame-Options: Prevents page from being framed (clickjacking protection)</li>
   * <li>X-Content-Type-Options: Prevents MIME type sniffing</li>
   * <li>Strict-Transport-Security: Enforces HTTPS for 1 year</li>
   * </ul>
   * </p>
   *
   * @param http the HttpSecurity to configure
   * @return the configured SecurityFilterChain
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
      // Configure security headers
      .headers(headers -> headers
        // Content Security Policy - restricts resource loading
        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; " + "script-src 'self'; " + "style-src 'self' 'unsafe-inline'; " + "img-src 'self' data:; " + "font-src 'self'; " + "connect-src 'self'; " + "frame-ancestors 'none'")
        )
        // XSS Protection - enables browser's XSS filtering
        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
        )
        // Frame Options - prevents clickjacking
        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
        // Content Type Options - prevents MIME sniffing
        .contentTypeOptions(contentType -> {
        })
        // HTTP Strict Transport Security - enforces HTTPS
        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000) // 1 year
        )
      )
      // Authorize HTTP requests
      .authorizeHttpRequests(auth -> auth
        // Allow actuator endpoints for health checks and monitoring
        .requestMatchers("/actuator/health", "/actuator/info").permitAll().requestMatchers("/actuator/**").permitAll()
        // All other requests are permitted (this is a Kafka consumer service)
        .anyRequest().permitAll()
      );

    return http.build();
  }
}
