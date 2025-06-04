package com.company.rest.api.service

import com.company.rest.api.dto.AuthResponseDto
import com.company.rest.api.repository.UserRepository
import com.company.rest.api.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
            return Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token 입니다."))
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
            return Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "잘못된 타입의 토큰입니다. Refresh Token이 아닙니다."))
        }

        val userUidFromToken = jwtTokenProvider.getUserUidFromToken(providedRefreshToken)
            ?: return Mono.error(
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh Token에서 사용자 식별자(uid)를 찾을 수 없습니다."
                )
            )

        logger.info("User UID extracted from Refresh Token: $userUidFromToken")

        val user = userRepository.findByUidAndRefreshToken(userUidFromToken, providedRefreshToken)
            .orElseGet {
                logger.warn(
                    "Refresh Token not found in DB or does not match for user UID: $userUidFromToken. Token (first 10): ${
                        providedRefreshToken.take(
                            10
                        )
                    }"
                )
                null
            } ?: return Mono.error(
            ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh Token이 DB 정보와 일치하지 않거나 해당 사용자가 존재하지 않습니다."
            )
        )

        if (user.refreshTokenExpiryDate == null || user.refreshTokenExpiryDate!!.isBefore(LocalDateTime.now())) {
            logger.warn("Refresh Token expired in DB for user UID: $userUidFromToken. ExpiryDate in DB: ${user.refreshTokenExpiryDate}")
            user.refreshToken = null
            user.refreshTokenExpiryDate = null
            userRepository.save(user)
            return Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다. 다시 로그인해주세요."))
        }

        val newAccessToken = jwtTokenProvider.generateAccessToken(
            userUid = user.uid,
            userSocialId = user.providerId, // user.providerId (해싱된 소셜 ID) 전달
            provider = user.loginProvider.name // JWT에는 여전히 Enum의 name (대문자) 사용 가능, 또는 소문자 value 사용도 고려
        )

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

        logger.info("New Access Token issued for user UID: {}. AppPasswordIsSet: {}. PartnerUID: {}, PartnerNickname: {}",
            user.uid, user.appPasswordIsSet, user.partnerUserUid ?: "N/A", partnerNickname ?: "N/A")

        val authResponse = AuthResponseDto(
            accessToken = newAccessToken,
            refreshToken = providedRefreshToken, // 기존 리프레시 토큰을 그대로 반환
            isNew = false, // Access Token 재발급이므로 새로운 사용자는 아님
            uid = user.uid,
            nickname = user.nickname,
            loginProvider = user.loginProvider.value, // .name -> .value 로 변경
            createdAt = user.createdAt.format(dateTimeFormatter),
            partnerUid = user.partnerUserUid,
            partnerNickname = partnerNickname,
            appPasswordSet = user.appPasswordIsSet
        )

        // 디버그 로깅 추가
        logger.debug("Access token refresh AuthResponseDto for user UID {}: {}", user.uid, authResponse)

        return Mono.just(authResponse)
    }
}