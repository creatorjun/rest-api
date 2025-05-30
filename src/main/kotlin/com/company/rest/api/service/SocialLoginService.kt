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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

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

    @Transactional
    fun processLogin(request: SocialLoginRequestDto): Mono<AuthResponseDto> {
        return verifySocialToken(request)
            .flatMap { verifiedUser ->
                logger.info(
                    "Social login attempt. Provider: ${request.platform}, " +
                            "ClientSentHashedID: ${request.id}, " +
                            "ProviderOriginalID (for reference/logging only): ${verifiedUser.originalId}"
                )

                var isNewUser = false
                val userOptional = userRepository.findByProviderIdAndLoginProvider(request.id, request.platform)

                val userEntity = userOptional.orElseGet {
                    isNewUser = true
                    logger.info("Creating new user. Provider: ${request.platform}, ProviderIdForDB (hashed by client): ${request.id}")
                    val finalNickname = request.nickname ?: verifiedUser.nickname ?: "User_${request.id.take(6)}"
                    val newUser = User(
                        nickname = finalNickname,
                        loginProvider = request.platform,
                        providerId = request.id
                        // appPassword는 소셜 로그인 시에는 null (사용자가 추후 설정)
                        // appPasswordIsSet은 User 엔티티 기본값 false로 초기화됨
                    )
                    userRepository.save(newUser)
                }

                if (!isNewUser && request.nickname != null && request.nickname != userEntity.nickname) {
                    userEntity.nickname = request.nickname
                    userEntity.updatedAt = LocalDateTime.now() // User 엔티티에 @PreUpdate 있으므로 명시적 호출은 선택적
                }

                val appAccessToken = jwtTokenProvider.generateAccessToken(
                    userUid = userEntity.uid,
                    userSocialId = userEntity.providerId,
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
                userRepository.save(userEntity) // isNewUser이거나, 닉네임 변경, 토큰 정보 업데이트 시 저장

                logger.info("Access & Refresh Tokens issued for user UID: ${userEntity.uid}, Provider: ${userEntity.loginProvider}. Is new user: $isNewUser. AppPasswordIsSet: {}", userEntity.appPasswordIsSet)

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
                        appPasswordSet = userEntity.appPasswordIsSet // 변경된 부분: userEntity.appPassword != null -> userEntity.appPasswordIsSet
                    )
                )
            }
            .doOnError { e ->
                if (e !is ResponseStatusException) {
                    logger.error(
                        "Unexpected error during social login for provider ${request.platform}, " +
                                "clientSentId ${request.id}", e
                    )
                }
            }
    }

    private fun verifySocialToken(request: SocialLoginRequestDto): Mono<VerifiedSocialUser> {
        logger.info("Verifying social token for provider: ${request.platform}, AccessToken (first 10 chars): ${request.socialAccessToken.take(10)}...")
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
                    logger.info("Successfully fetched Naver profile. Original Provider ID: ${profile.id}")
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
                logger.info("Successfully fetched Kakao profile. Original Provider ID: ${kakaoResponse.id}")
                VerifiedSocialUser(kakaoResponse.id.toString(), nickname)
            }
    }
}