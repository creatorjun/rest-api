package com.company.rest.api.dto

import com.company.rest.api.entity.User // User 엔티티 임포트 필요
import java.time.format.DateTimeFormatter

data class AuthResponseDto(
    // JWT 토큰 정보
    val accessToken: String,
    val refreshToken: String?,
    val isNew: Boolean = false,

    // 사용자 상세 정보
    val uid: String,
    val nickname: String?,
    val loginProvider: String,
    val createdAt: String,
    val partnerUid: String?,
    val appPasswordSet: Boolean // 앱 비밀번호 설정 여부 필드
) {
    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        // SocialLoginService와 AuthService에서 직접 생성자를 호출하므로,
        // 이 fromUser 메소드는 사용되지 않거나, 필요하다면 아래와 같이 수정할 수 있습니다.
        // 현재는 서비스에서 직접 생성자를 사용하고 있으므로 이 메소드는 참고용입니다.
        fun fromUserForService(user: User, newAccessToken: String, newRefreshToken: String?, isNewUser: Boolean): AuthResponseDto {
            return AuthResponseDto(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                isNew = isNewUser,
                uid = user.uid,
                nickname = user.nickname,
                loginProvider = user.loginProvider.name,
                createdAt = user.createdAt.format(dateTimeFormatter),
                partnerUid = user.partnerUserUid,
                appPasswordSet = user.appPasswordIsSet // User 엔티티의 appPasswordIsSet 필드 사용하도록 변경 (만약 이 메소드가 사용된다면)
            )
        }
    }
}