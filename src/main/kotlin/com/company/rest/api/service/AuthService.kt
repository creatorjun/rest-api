package com.company.rest.api.service

import com.company.rest.api.dto.AuthResponseDto
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.UserRepository
import com.company.rest.api.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${jwt.refresh-expiration-ms}") private val jwtRefreshExpirationMs: Long
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @Transactional
    fun refreshAccessToken(providedRefreshToken: String): Mono<AuthResponseDto> {
        logger.info(
            "Attempting to refresh access token with Refresh Token (first 10 chars): ${
                providedRefreshToken.take(
                    10
                )
            }..."
        )

        if (!jwtTokenProvider.validateToken(providedRefreshToken)) {
            logger.warn("Invalid Refresh Token received (validation failed): ${providedRefreshToken.take(10)}...")
            return Mono.error(CustomException(ErrorCode.INVALID_TOKEN))
        }

        val tokenType = jwtTokenProvider.getTokenTypeFromToken(providedRefreshToken)
        if (tokenType != "REFRESH") {
            logger.warn(
                "Invalid token type received for refresh. Expected REFRESH, got $tokenType. Token (first 10): ${
                    providedRefreshToken.take(
                        10
                    )
                }"
            )
            return Mono.error(CustomException(ErrorCode.INVALID_TOKEN))
        }

        val userUidFromToken = jwtTokenProvider.getUserUidFromToken(providedRefreshToken)
            ?: return Mono.error(CustomException(ErrorCode.INVALID_TOKEN))

        logger.info("User UID extracted from Refresh Token: $userUidFromToken")

        val user = userRepository.findByUidAndRefreshToken(userUidFromToken, providedRefreshToken)
            .orElseThrow {
                logger.warn(
                    "Refresh Token not found in DB or does not match for user UID: $userUidFromToken. Token (first 10): ${
                        providedRefreshToken.take(
                            10
                        )
                    }"
                )
                CustomException(ErrorCode.REFRESH_TOKEN_NOT_MATCH)
            }

        if (user.refreshTokenExpiryDate == null || user.refreshTokenExpiryDate!!.isBefore(LocalDateTime.now())) {
            logger.warn("Refresh Token expired in DB for user UID: $userUidFromToken. ExpiryDate in DB: ${user.refreshTokenExpiryDate}")
            user.refreshToken = null
            user.refreshTokenExpiryDate = null
            userRepository.save(user)
            return Mono.error(CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED))
        }

        // --- 여기부터가 수정된 부분입니다 ---

        // 1. 새로운 Access Token 발급
        val newAccessToken = jwtTokenProvider.generateAccessToken(
            userUid = user.uid,
            userSocialId = user.providerId,
            provider = user.loginProvider.name
        )

        // 2. 새로운 Refresh Token 발급
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(
            userUid = user.uid
        )

        // 3. DB에 새로운 Refresh Token과 만료 시간을 저장하여 이전 토큰을 무효화
        user.refreshToken = newRefreshToken
        val refreshTokenExpiry = LocalDateTime.ofInstant(
            Date(System.currentTimeMillis() + jwtRefreshExpirationMs).toInstant(),
            ZoneId.systemDefault()
        )
        user.refreshTokenExpiryDate = refreshTokenExpiry
        userRepository.save(user)

        logger.info("New Access & Refresh Tokens issued for user UID: {}", user.uid)

        // --- 수정 끝 ---

        var partnerNickname: String? = null
        user.partnerUserUid?.let { pUid ->
            if (pUid.isNotBlank()) {
                partnerNickname = userRepository.findById(pUid)
                    .map { it.nickname }
                    .orElse(null)
                if (partnerNickname == null) {
                    logger.warn("Partner user not found with UID: {} for user UID: {}", pUid, user.uid)
                }
            }
        }

        val authResponse = AuthResponseDto(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken, // 응답에 새로운 Refresh Token을 담아 전달
            isNew = false,
            uid = user.uid,
            nickname = user.nickname,
            loginProvider = user.loginProvider.value,
            createdAt = user.createdAt.format(dateTimeFormatter),
            partnerUid = user.partnerUserUid,
            partnerNickname = partnerNickname,
            appPasswordSet = user.appPasswordIsSet,
            fcmToken = user.fcmToken
        )

        logger.debug("Access token refresh AuthResponseDto for user UID {}: {}", user.uid, authResponse)

        return Mono.just(authResponse)
    }
}