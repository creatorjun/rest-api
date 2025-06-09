package com.company.rest.api.config

import com.company.rest.api.security.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.IpAddressMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    // --- 바로 이 부분입니다! ---
                    // 1. 관리자용 엔드포인트에 대한 규칙을 먼저 정의합니다.
                    .requestMatchers("/api/v1/weather/admin/**", "/api/v1/luck/admin/**")
                    // 2. 접근 제어 로직을 직접 구현합니다.
                    .access { _, context ->
                        val request: HttpServletRequest = context.request
                        // 3. IP 주소 매처를 사용하여 로컬호스트(IPv4, IPv6)인지 확인합니다.
                        val isLocalhost = IpAddressMatcher("127.0.0.1").matches(request) ||
                                IpAddressMatcher("::1").matches(request)
                        // 4. 로컬호스트일 경우에만 접근을 허용(true)합니다.
                        AuthorizationDecision(isLocalhost)
                    }
                    // --- 기존 규칙들은 그대로 둡니다 ---
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // 5. 위에서 정의한 규칙 외의 모든 요청은 인증을 요구합니다.
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}