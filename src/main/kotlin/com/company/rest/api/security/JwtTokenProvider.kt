package com.company.rest.api.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider {

    private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    @Value("\${jwt.secret}")
    private lateinit var jwtSecretString: String

    @Value("\${jwt.expiration-ms}")
    private var jwtAccessExpirationMs: Long = 0

    @Value("\${jwt.refresh-expiration-ms}")
    private var jwtRefreshExpirationMs: Long = 0

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecretString.toByteArray())
    }

    /**
     * Access Token 생성
     * @param userUid User 엔티티의 PK인 uid (String, UUID)
     * @param userSocialId DB에 저장된 해싱된 소셜 ID (user.providerId)
     * @param provider 로그인 제공자 이름 (예: "NAVER", "KAKAO")
     * @return 생성된 Access Token 문자열
     */
    fun generateAccessToken(userUid: String, userSocialId: String, provider: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtAccessExpirationMs)

        return Jwts.builder()
            .subject(userUid) // User의 PK (uid)를 subject로 사용
            .claim("socialId", userSocialId) // 이 클레임을 사용할 것임 (user.providerId)
            .claim("provider", provider)
            .claim("type", "ACCESS")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    /**
     * Refresh Token 생성
     * @param userUid User 엔티티의 PK인 uid (String, UUID)
     * @return 생성된 Refresh Token 문자열
     */
    fun generateRefreshToken(userUid: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtRefreshExpirationMs)

        return Jwts.builder()
            .subject(userUid) // User의 PK (uid)를 subject로 사용
            .claim("type", "REFRESH")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

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

    /**
     * JWT에서 User의 uid (subject) 추출
     * @param token JWT 문자열
     * @return User의 uid (String, UUID) 또는 null
     */
    fun getUserUidFromToken(token: String): String? {
        return getClaimsFromToken(token)?.subject
    }

    /**
     * JWT에서 토큰 타입 추출
     * @param token JWT 문자열
     * @return 토큰 타입 문자열 (예: "ACCESS", "REFRESH") 또는 null
     */
    fun getTokenTypeFromToken(token: String): String? {
        return getClaimsFromToken(token)?.get("type", String::class.java)
    }

    /**
     * JWT에서 "socialId" 클레임 (user.providerId) 추출
     * @param token JWT 문자열
     * @return socialId (user.providerId) 문자열 또는 null
     */
    fun getSocialIdFromToken(token: String): String? {
        return getClaimsFromToken(token)?.get("socialId", String::class.java)
    }
}