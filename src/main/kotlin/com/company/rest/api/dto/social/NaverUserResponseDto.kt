package com.company.rest.api.dto.social

import com.fasterxml.jackson.annotation.JsonProperty

data class NaverUserResponse(
    @JsonProperty("resultcode")
    val resultCode: String,
    val message: String,
    val response: NaverProfile?
)

data class NaverProfile(
    val id: String, // 고유 식별자
    val nickname: String?,
    @JsonProperty("profile_image")
    val profileImage: String?
    // 필요한 다른 정보들
)