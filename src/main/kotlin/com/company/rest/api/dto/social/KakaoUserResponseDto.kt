package com.company.rest.api.dto.social

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserResponse(
    val id: Long, // 고유 식별자 (숫자 타입)
    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount?,
    val properties: KakaoProperties?
)

data class KakaoAccount(
    @JsonProperty("profile_nickname_needs_agreement")
    val profileNicknameNeedsAgreement: Boolean?,
    @JsonProperty("profile_image_needs_agreement")
    val profileImageNeedsAgreement: Boolean?,
    val profile: KakaoProfile?
    // 필요한 다른 정보들
)

data class KakaoProfile(
    val nickname: String?,
    @JsonProperty("thumbnail_image_url")
    val thumbnailImageUrl: String?,
    @JsonProperty("profile_image_url")
    val profileImageUrl: String?,
    @JsonProperty("is_default_image")
    val isDefaultImage: Boolean?
)

data class KakaoProperties(
    val nickname: String?,
    @JsonProperty("profile_image")
    val profileImage: String?,
    @JsonProperty("thumbnail_image")
    val thumbnailImage: String?
)