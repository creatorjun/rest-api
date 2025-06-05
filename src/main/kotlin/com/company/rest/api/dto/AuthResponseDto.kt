package com.company.rest.api.dto

import com.company.rest.api.entity.User
import java.time.format.DateTimeFormatter

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String?,
    val isNew: Boolean = false,
    val uid: String,
    val nickname: String?,
    val loginProvider: String,
    val createdAt: String,
    val partnerUid: String?,
    val partnerNickname: String?,
    val appPasswordSet: Boolean,
    val fcmToken: String? // FCM 토큰 필드 추가
) {
    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun fromUserForService(
            user: User,
            newAccessToken: String,
            newRefreshToken: String?,
            isNewUser: Boolean,
            partnerNickname: String?,
            fcmToken: String?
        ): AuthResponseDto {
            return AuthResponseDto(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                isNew = isNewUser,
                uid = user.uid,
                nickname = user.nickname,
                loginProvider = user.loginProvider.value, // .value 사용
                createdAt = user.createdAt.format(dateTimeFormatter),
                partnerUid = user.partnerUserUid,
                partnerNickname = partnerNickname,
                appPasswordSet = user.appPasswordIsSet,
                fcmToken = fcmToken // FCM 토큰 매핑
            )
        }
    }
}