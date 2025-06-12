package com.company.rest.api.security

import com.company.rest.api.config.JwtProperties // 임포트 추가
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties // JwtProperties 주입
) {

    private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()) // 프로퍼티 사용
    }

    /**
     * Access Token 생성
     */
    fun generateAccessToken(userUid: String, userSocialId: String, provider: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.expirationMs) // 프로퍼티 사용

        return Jwts.builder()
            .subject(userUid)
            .claim("socialId", userSocialId)
            .claim("provider", provider)
            .claim("type", "ACCESS")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    /**
     * Refresh Token 생성
     */
    fun generateRefreshToken(userUid: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshExpirationMs) // 프로퍼티 사용

        return Jwts.builder()
            .subject(userUid)
            .claim("type", "REFRESH")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    // ... (이하 validateToken, getClaimsFromToken 등 나머지 코드는 동일)
    fun validateToken(token: String): Boolean {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
            return true
        } catch (ex: SignatureException) {
            logger.error("Invalid JWT signature: {}", ex.message)
        } catch (ex: MalformedJwtException) {
            logger.error("Invalid JWT token: {}", ex.message)
        } catch (ex: ExpiredJwtException) {
            logger.error("Expired JWT token: {}", ex.message)
        } catch (ex: UnsupportedJwtException) {
            logger.error("Unsupported JWT token: {}", ex.message)
        } catch (ex: IllegalArgumentException) {
            logger.error("JWT claims string is empty: {}", ex.message)
        }
        return false
    }

    private fun getClaimsFromToken(token: String): Claims? {
        return try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
        } catch (e: Exception) {
            logger.error("Could not get claims from token: {}, Token: {}", e.message, token.take(20))
            null
        }
    }

    fun getUserUidFromToken(token: String): String? {
        return getClaimsFromToken(token)?.subject
    }

    fun getTokenTypeFromToken(token: String): String? {
        return getClaimsFromToken(token)?.get("type", String::class.java)
    }

    fun getSocialIdFromToken(token: String): String? {
        return getClaimsFromToken(token)?.get("socialId", String::class.java)
    }
}