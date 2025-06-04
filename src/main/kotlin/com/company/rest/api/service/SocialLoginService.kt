package com.company.rest.api.service

import com.company.rest.api.dto.LoginProvider
import com.company.rest.api.dto.SocialLoginRequestDto
import com.company.rest.api.dto.AuthResponseDto
import com.company.rest.api.dto.social.KakaoUserResponse
import com.company.rest.api.dto.social.NaverUserResponse
import com.company.rest.api.entity.User
import com.company.rest.api.repository.UserRepository
import com.company.rest.api.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.nio.charset.StandardCharsets

@Service
class SocialLoginService(
    @Qualifier("naverWebClient") private val naverWebClient: WebClient,
    @Qualifier("kakaoWebClient") private val kakaoWebClient: WebClient,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${jwt.refresh-expiration-ms}") private val jwtRefreshExpirationMs: Long
) {
    private val logger = LoggerFactory.getLogger(SocialLoginService::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private data class VerifiedSocialUser(val originalId: String, val nickname: String?)

    private fun sha256(input: String): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    @Transactional
    fun processLogin(request: SocialLoginRequestDto): Mono<AuthResponseDto> {
        return verifySocialToken(request)
            .flatMap { verifiedUser ->
                if (request.id != verifiedUser.originalId) {
                    logger.warn(
                        "Mismatch between client-sent ID ({}) and token-derived original ID ({}). Provider: {}",
                        request.id, verifiedUser.originalId, request.platform
                    )
                }

                val hashedOriginalId = sha256(verifiedUser.originalId)
                logger.info(
                    "Social login attempt. Provider: {}, ClientSentOriginalID: {}, HashedOriginalIDForDB: {}, VerifiedOriginalID: {}",
                    request.platform,
                    request.id,
                    hashedOriginalId.take(10) + "...",
                    verifiedUser.originalId.take(10) + "..."
                )

                var isNewUser = false
                val userOptional = userRepository.findByProviderIdAndLoginProvider(hashedOriginalId, request.platform)

                val userEntity = userOptional.orElseGet {
                    isNewUser = true
                    logger.info(
                        "Creating new user. Provider: {}, OriginalSocialID: {}, HashedProviderIdForDB: {}",
                        request.platform,
                        verifiedUser.originalId.take(10) + "...",
                        hashedOriginalId.take(10) + "..."
                    )
                    val finalNickname = request.nickname ?: verifiedUser.nickname ?: "User_${verifiedUser.originalId.take(6)}"
                    val newUser = User(
                        nickname = finalNickname,
                        loginProvider = request.platform,
                        providerId = hashedOriginalId
                    )
                    userRepository.save(newUser)
                }

                if (!isNewUser && request.nickname != null && request.nickname != userEntity.nickname) {
                    userEntity.nickname = request.nickname
                }

                val appAccessToken = jwtTokenProvider.generateAccessToken(
                    userUid = userEntity.uid,
                    userSocialId = userEntity.providerId,
                    provider = userEntity.loginProvider.name // JWT에는 여전히 Enum의 name (대문자) 사용 가능, 또는 소문자 value 사용도 고려
                )
                val appRefreshToken = jwtTokenProvider.generateRefreshToken(
                    userUid = userEntity.uid
                )

                userEntity.refreshToken = appRefreshToken
                val refreshTokenExpiry = LocalDateTime.ofInstant(
                    Date(System.currentTimeMillis() + jwtRefreshExpirationMs).toInstant(),
                    ZoneId.systemDefault()
                )
                userEntity.refreshTokenExpiryDate = refreshTokenExpiry

                var partnerNickname: String? = null
                userEntity.partnerUserUid?.let { pUid ->
                    if (pUid.isNotBlank()) {
                        partnerNickname = userRepository.findById(pUid)
                            .map { it.nickname }
                            .orElse(null)
                        if (partnerNickname == null) {
                            logger.warn("Partner user not found with UID: {} for user UID: {}", pUid, userEntity.uid)
                        }
                    }
                }

                userRepository.save(userEntity)

                logger.info(
                    "Access & Refresh Tokens issued for user UID: {}. Provider: {}. Is new user: {}. AppPasswordIsSet: {}. Stored ProviderId (hashed): {}, PartnerUID: {}, PartnerNickname: {}",
                    userEntity.uid,
                    userEntity.loginProvider,
                    isNewUser,
                    userEntity.appPasswordIsSet,
                    userEntity.providerId.take(10) + "...",
                    userEntity.partnerUserUid ?: "N/A",
                    partnerNickname ?: "N/A"
                )

                val authResponse = AuthResponseDto(
                    accessToken = appAccessToken,
                    refreshToken = appRefreshToken,
                    isNew = isNewUser,
                    uid = userEntity.uid,
                    nickname = userEntity.nickname,
                    loginProvider = userEntity.loginProvider.value, // .name -> .value 로 변경
                    createdAt = userEntity.createdAt.format(dateTimeFormatter),
                    partnerUid = userEntity.partnerUserUid,
                    partnerNickname = partnerNickname,
                    appPasswordSet = userEntity.appPasswordIsSet
                )

                logger.debug("Login success AuthResponseDto for user UID {}: {}", userEntity.uid, authResponse)

                Mono.just(authResponse)
            }
            .doOnError { e ->
                if (e !is ResponseStatusException) {
                    logger.error(
                        "Unexpected error during social login for provider {}, clientSentOriginalId (from request.id) {}",
                        request.platform, request.id, e
                    )
                }
            }
    }

    private fun verifySocialToken(request: SocialLoginRequestDto): Mono<VerifiedSocialUser> {
        logger.info("Verifying social token for provider: {}, ClientSentOriginalID (from request.id): {}, AccessToken (first 10 chars): {}...",
            request.platform, request.id, request.socialAccessToken.take(10))
        return when (request.platform) {
            LoginProvider.NAVER -> fetchNaverUserProfile(request.socialAccessToken)
            LoginProvider.KAKAO -> fetchKakaoUserProfile(request.socialAccessToken)
            else -> {
                logger.warn("Unsupported login platform: {}", request.platform)
                Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 플랫폼입니다: ${request.platform}"))
            }
        }
    }

    private fun fetchNaverUserProfile(accessToken: String): Mono<VerifiedSocialUser> {
        return naverWebClient.get()
            .uri("/v1/nid/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .onStatus({ httpStatus -> httpStatus.isError }, { clientResponse ->
                clientResponse.bodyToMono(String::class.java)
                    .flatMap { errorBody ->
                        logger.error(
                            "Naver API call failed. Status: ${clientResponse.statusCode()}, AccessToken: ${accessToken.take(10)}..., ResponseBody: $errorBody"
                        )
                        Mono.error(
                            ResponseStatusException(
                                clientResponse.statusCode(),
                                "네이버 인증 실패: ${clientResponse.statusCode()}"
                            )
                        )
                    }
            })
            .bodyToMono(NaverUserResponse::class.java)
            .flatMap { naverResponse ->
                if (naverResponse.resultCode == "00" && naverResponse.response != null) {
                    val profile = naverResponse.response
                    logger.info("Successfully fetched Naver profile. Original Provider ID (from Naver): {}", profile.id.take(10) + "...")
                    Mono.just(VerifiedSocialUser(profile.id, profile.nickname))
                } else {
                    logger.error(
                        "Naver user info fetch logic error. ResultCode: ${naverResponse.resultCode}, Message: ${naverResponse.message}"
                    )
                    Mono.error(
                        ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "네이버 사용자 정보 조회 실패: ${naverResponse.message}"
                        )
                    )
                }
            }
    }

    private fun fetchKakaoUserProfile(accessToken: String): Mono<VerifiedSocialUser> {
        return kakaoWebClient.get()
            .uri("/v2/user/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .onStatus({ httpStatus -> httpStatus.isError }, { clientResponse ->
                clientResponse.bodyToMono(String::class.java)
                    .flatMap { errorBody ->
                        logger.error(
                            "Kakao API call failed. Status: ${clientResponse.statusCode()}, AccessToken: ${accessToken.take(10)}..., ResponseBody: $errorBody"
                        )
                        Mono.error(
                            ResponseStatusException(
                                clientResponse.statusCode(),
                                "카카오 인증 실패: ${clientResponse.statusCode()}"
                            )
                        )
                    }
            })
            .bodyToMono(KakaoUserResponse::class.java)
            .map { kakaoResponse ->
                val nickname = kakaoResponse.properties?.nickname
                    ?: kakaoResponse.kakaoAccount?.profile?.nickname
                logger.info("Successfully fetched Kakao profile. Original Provider ID (from Kakao): {}", kakaoResponse.id.toString().take(10) + "...")
                VerifiedSocialUser(kakaoResponse.id.toString(), nickname)
            }
    }
}