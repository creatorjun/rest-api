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

    // SHA-256 해싱 함수
    private fun sha256(input: String): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    @Transactional
    fun processLogin(request: SocialLoginRequestDto): Mono<AuthResponseDto> {
        // 클라이언트는 request.id에 원본 소셜 ID를 보내야 함.
        // verifySocialToken은 socialAccessToken을 검증하고 원본 소셜 ID(originalId)와 닉네임을 가져옴.
        return verifySocialToken(request)
            .flatMap { verifiedUser ->
                // 클라이언트가 보낸 request.id (원본 소셜 ID여야 함)와
                // 토큰 검증으로 얻은 verifiedUser.originalId가 일치하는지 추가 검증 가능 (선택 사항)
                if (request.id != verifiedUser.originalId) {
                    logger.warn(
                        "Mismatch between client-sent ID ({}) and token-derived original ID ({}). Provider: {}",
                        request.id, verifiedUser.originalId, request.platform
                    )
                    // 이 경우 에러 처리 또는 로깅 후 진행 결정 필요. 여기서는 일단 진행하나, 보안상 확인하는 것이 좋음.
                    // Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "제공된 ID와 토큰의 사용자 정보가 일치하지 않습니다."))
                }

                // DB에 저장 및 조회 시에는 verifiedUser.originalId를 해싱하여 사용
                val hashedOriginalId = sha256(verifiedUser.originalId)
                logger.info(
                    "Social login attempt. Provider: {}, ClientSentOriginalID: {}, HashedOriginalIDForDB: {}, VerifiedOriginalID: {}",
                    request.platform,
                    request.id, // 클라이언트가 보낸 원본 ID (로깅용)
                    hashedOriginalId.take(10) + "...", // DB에 사용될 해시된 ID (일부만 로깅)
                    verifiedUser.originalId.take(10) + "..." // 소셜 플랫폼에서 가져온 원본 ID (일부만 로깅)
                )

                var isNewUser = false
                // DB에서 해시된 ID로 사용자 조회
                val userOptional = userRepository.findByProviderIdAndLoginProvider(hashedOriginalId, request.platform)

                val userEntity = userOptional.orElseGet {
                    isNewUser = true
                    logger.info(
                        "Creating new user. Provider: {}, OriginalSocialID: {}, HashedProviderIdForDB: {}",
                        request.platform,
                        verifiedUser.originalId.take(10) + "...", // 원본 ID
                        hashedOriginalId.take(10) + "..."       // 해시된 ID
                    )
                    val finalNickname = request.nickname ?: verifiedUser.nickname ?: "User_${verifiedUser.originalId.take(6)}"
                    val newUser = User(
                        nickname = finalNickname,
                        loginProvider = request.platform,
                        providerId = hashedOriginalId // DB에는 백엔드에서 해싱한 ID를 저장
                    )
                    userRepository.save(newUser)
                }

                if (!isNewUser && request.nickname != null && request.nickname != userEntity.nickname) {
                    userEntity.nickname = request.nickname
                    // userEntity.updatedAt = LocalDateTime.now() // @PreUpdate로 자동 관리
                }

                val appAccessToken = jwtTokenProvider.generateAccessToken(
                    userUid = userEntity.uid,
                    userSocialId = userEntity.providerId, // DB에 저장된 해시된 ID
                    provider = userEntity.loginProvider.name
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
                userRepository.save(userEntity)

                logger.info(
                    "Access & Refresh Tokens issued for user UID: {}. Provider: {}. Is new user: {}. AppPasswordIsSet: {}. Stored ProviderId (hashed): {}",
                    userEntity.uid,
                    userEntity.loginProvider,
                    isNewUser,
                    userEntity.appPasswordIsSet,
                    userEntity.providerId.take(10) + "..."
                )

                Mono.just(
                    AuthResponseDto(
                        accessToken = appAccessToken,
                        refreshToken = appRefreshToken,
                        isNew = isNewUser,
                        uid = userEntity.uid,
                        nickname = userEntity.nickname,
                        loginProvider = userEntity.loginProvider.name,
                        createdAt = userEntity.createdAt.format(dateTimeFormatter),
                        partnerUid = userEntity.partnerUserUid,
                        appPasswordSet = userEntity.appPasswordIsSet
                    )
                )
            }
            .doOnError { e ->
                if (e !is ResponseStatusException) {
                    logger.error(
                        "Unexpected error during social login for provider {}, clientSentOriginalId (from request.id) {}",
                        request.platform, request.id, e
                    )
                }
                // ResponseStatusException은 그대로 전파되거나 @ControllerAdvice에서 처리
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
                    Mono.just(VerifiedSocialUser(profile.id, profile.nickname)) // 원본 ID 반환
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
                VerifiedSocialUser(kakaoResponse.id.toString(), nickname) // 원본 ID 반환
            }
    }
}