package com.company.rest.api.security

// import org.springframework.security.core.userdetails.User // UserDetails 직접 사용 안 함
// import org.springframework.security.core.userdetails.UserDetails // UserDetails 직접 사용 안 함
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
        filterLogger.debug("JwtAuthenticationFilter: Intercepting request for URI: {}", request.requestURI)
        try {
            val jwt = getJwtFromRequest(request)

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                filterLogger.debug("JwtAuthenticationFilter: JWT is valid.")
                val userUid = jwtTokenProvider.getUserUidFromToken(jwt)

                if (userUid != null) {
                    filterLogger.debug("JwtAuthenticationFilter: User UID '{}' extracted from JWT.", userUid)
                    if (SecurityContextHolder.getContext().authentication == null) {
                        // Principal을 userUid (String) 자체로 설정
                        // 필요하다면 여기서 DB를 조회하여 사용자의 실제 권한(role)을 가져와 GrantedAuthority 리스트를 만들 수 있음
                        // 예시: val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                        val authorities = emptyList<SimpleGrantedAuthority>() // 현재는 권한 없음

                        val authentication = UsernamePasswordAuthenticationToken(
                            userUid,     // Principal을 String (userUid)으로 직접 설정
                            null,        // Credentials
                            authorities  // 권한 목록
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                        filterLogger.info(
                            "JwtAuthenticationFilter: Successfully authenticated user UID '{}' (as String principal) and set SecurityContext.",
                            userUid
                        )
                    } else {
                        filterLogger.debug(
                            "JwtAuthenticationFilter: SecurityContextHolder already contains an authentication object for user '{}'. Skipping.",
                            SecurityContextHolder.getContext().authentication.name
                        )
                    }
                } else {
                    filterLogger.warn("JwtAuthenticationFilter: User UID could not be extracted from JWT, although token was considered valid.")
                }
            } else {
                if (jwt == null) {
                    filterLogger.debug("JwtAuthenticationFilter: No JWT token found in request header 'Authorization'.")
                } else {
                    filterLogger.warn("JwtAuthenticationFilter: JWT token validation failed.")
                }
            }
        } catch (ex: Exception) {
            filterLogger.error("JwtAuthenticationFilter: Could not set user authentication in security context", ex)
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            filterLogger.debug("JwtAuthenticationFilter: 'Authorization' header found with Bearer token.")
            return bearerToken.substring(7)
        }
        filterLogger.debug("JwtAuthenticationFilter: No 'Authorization' header or not a Bearer token.")
        return null
    }
}