package com.company.rest.api.dto

import com.company.rest.api.entity.User // User 엔티티 임포트
import java.time.format.DateTimeFormatter

// 사용자 정보를 간략하게 담을 내부 DTO
data class PartnerUserInfoDto(
    val userUid: String, // "userId"에서 "userUid"로 변경
    val nickname: String?
)

data class PartnerRelationResponseDto(
    val message: String,
    val currentUser: PartnerUserInfoDto,
    val partnerUser: PartnerUserInfoDto,
    val partnerSince: String // ISO 8601 형식 (예: "2025-05-20T14:30:00")
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME //

        fun fromUsers(
            currentUser: User, // 현재 요청을 보낸 사용자 (초대를 수락한 사용자)
            partnerUser: User  // 새로 파트너가 된 사용자 (초대를 생성했던 사용자)
        ): PartnerRelationResponseDto {
            return PartnerRelationResponseDto(
                message = "파트너 관계가 성공적으로 맺어졌습니다.",
                currentUser = PartnerUserInfoDto(
                    userUid = currentUser.uid, // "userId"에서 "userUid"로 변경, currentUser.uid 사용
                    nickname = currentUser.nickname
                ),
                partnerUser = PartnerUserInfoDto(
                    userUid = partnerUser.uid, // "userId"에서 "userUid"로 변경, partnerUser.uid 사용
                    nickname = partnerUser.nickname
                ),
                // currentUser의 partnerSince 또는 partnerUser의 partnerSince 값을 사용
                // (양쪽 다 동일한 시간으로 설정되어야 함)
                partnerSince = currentUser.partnerSince?.format(formatter)
                    ?: partnerUser.partnerSince?.format(formatter)
                    ?: "" // 혹시 모를 null 상황 방지
            )
        }
    }
}