package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 认证服务安全配置（模块 10）。
 * 登录接口放行，其余请求要求已认证；全程无状态（不建 HttpSession，令牌才是身份证）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 教学用内存账号：zhangsan / 123456。
     * {noop} 表示明文密码（学习用）——生产必须 BCrypt 加密存储，账号应查数据库。
     * zhangsan 对应的 userId=1，就是前面模块一直用的那个用户。
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername("zhangsan")
                        .password("{noop}123456")
                        .roles("USER")
                        .build());
    }

    /** 认证管理器：Controller 里用它校验"用户名+密码" */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())                                   // 无状态 API，无表单，不需要 CSRF 令牌
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login").permitAll()             // 登录本身放行
                        .anyRequest().authenticated())
                .build();
    }
}
