package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 网关统一鉴权（模块 10）：网关作为"资源服务器"校验 JWT。
 *
 * 策略：所有路由默认需要合法令牌；只有 /api/auth/**（登录本身）放行。
 * 注意（E10-3）：响应式资源服务器【不会】从 secret-key 配置自动装配验签器，
 * 必须自己声明 ReactiveJwtDecoder Bean（Servlet 版才有那个自动装配）。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** 与 auth-service 完全一致的验签密钥（HS256 对称密钥） */
    @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}")
    private String secret;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)          // 无状态 API 不需要 CSRF 令牌
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/**").permitAll()    // 登录入口放行
                        .anyExchange().authenticated())              // 其余一律验票
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))  // 用上面声明的 Decoder 验票
                .build();
    }
}
