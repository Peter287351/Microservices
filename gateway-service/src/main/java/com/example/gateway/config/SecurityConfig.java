package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 网关统一鉴权（模块 10）：网关作为"资源服务器"校验 JWT。
 *
 * 策略：所有路由默认需要合法令牌；只有 /api/auth/**（登录本身）放行。
 * 验签密钥来自 yml（spring.security.oauth2.resourceserver.jwt.secret-key），与 auth-service 一致。
 * 从此：user/order/account 的接口全部处于 JWT 保护之下——业务服务一行安全代码都不用写。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)          // 无状态 API 不需要 CSRF 令牌
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/**").permitAll()    // 登录入口放行
                        .anyExchange().authenticated())              // 其余一律验票
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))  // 用 yml 里的密钥自动装配 JWT 验签
                .build();
    }
}
