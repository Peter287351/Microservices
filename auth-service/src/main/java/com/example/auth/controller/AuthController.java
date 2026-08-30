package com.example.auth.controller;

import com.example.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 登录接口（模块 10）：账号密码 → JWT 令牌。
 *
 * 流程：AuthenticationManager 校验账号密码（Spring Security 的标准认证流程）
 * → 通过后用 NimbusJwtEncoder 签发 HS256 令牌（Spring Authorization Server 底层同一套组件）。
 * 令牌里编入了 userId 等"声明（claims）"，网关验签后即可信任，无需查库。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final NimbusJwtEncoder jwtEncoder;
    private final long expireMinutes;

    public AuthController(AuthenticationManager authenticationManager,
                          @Value("${jwt.secret}") String secret,
                          @Value("${jwt.expire-minutes:120}") long expireMinutes) {
        this.authenticationManager = authenticationManager;
        this.expireMinutes = expireMinutes;
        // 对称密钥（HS256）：签发与验签用同一把——所以网关必须配置同一个 secret
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 登录：POST /auth/login {"username":"zhangsan","password":"123456"} */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username(), request.password()));
        } catch (AuthenticationException e) {
            // 统一提示，不区分"用户不存在"和"密码错误"——防止撞库探测
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 教学简化：userId 内存映射（zhangsan = 模块里一直用的 userId=1）。
        // 真实项目应查数据库/用户服务。
        long userId = "zhangsan".equals(authentication.getName()) ? 1L : 0L;

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-service")                                  // 谁签发的
                .issuedAt(now)                                           // 签发时间
                .expiresAt(now.plus(expireMinutes, ChronoUnit.MINUTES))  // 过期时间
                .subject(authentication.getName())                       // 用户名
                .claim("userId", userId)                                 // 业务声明：下单时知道是谁
                .claim("scope", "USER")
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));

        return Result.ok(Map.of(
                "token", jwt.getTokenValue(),
                "tokenType", "Bearer",
                "expiresIn", expireMinutes * 60));
    }
}
