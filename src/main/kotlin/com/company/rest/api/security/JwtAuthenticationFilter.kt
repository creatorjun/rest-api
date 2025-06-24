package com.company.rest.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    private val filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = getJwtFromRequest(request)

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                filterLogger.debug("JwtAuthenticationFilter: JWT is valid for URI: {}", request.requestURI)
                val userUid = jwtTokenProvider.getUserUidFromToken(jwt)

                if (userUid != null) {
                    if (SecurityContextHolder.getContext().authentication == null) {
                        val authorities = emptyList<SimpleGrantedAuthority>()
                        val authentication = UsernamePasswordAuthenticationToken(
                            userUid,
                            null,
                            authorities
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                        filterLogger.info(
                            "JwtAuthenticationFilter: Successfully authenticated user UID '{}' and set SecurityContext.",
                            userUid
                        )
                    }
                } else {
                    filterLogger.warn("JwtAuthenticationFilter: User UID could not be extracted from JWT, although token was considered valid.")
                }
            } else {
                // --- 여기부터가 수정된 부분입니다 ---
                // 토큰이 헤더에 존재하지만 유효하지 않은 경우 (만료, 손상 등)
                if (jwt != null) {
                    // 요청에 'token_error'라는 표식을 남깁니다.
                    request.setAttribute("token_error", true)
                    filterLogger.warn("JwtAuthenticationFilter: Invalid JWT token found. Marking request with 'token_error' attribute.")
                }
                // --- 수정 끝 ---
            }
        } catch (ex: Exception) {
            filterLogger.error("JwtAuthenticationFilter: Could not set user authentication in security context", ex)
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }
        return null
    }
}