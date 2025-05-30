package com.company.rest.api.dto

import java.time.format.DateTimeFormatter

/**
 * 파트너 초대가 수락되었음을 알리는 WebSocket 알림 DTO 입니다.
 * 초대자(issuer)에게 전송됩니다.
 */
data class PartnerInvitationAcceptedNotificationDto(
    val message: String,                // 알림 메시지 (예: "회원님의 파트너 초대를 수락했습니다!")
    val invitationId: String,           // 수락된 초대의 ID
    val accepterUserUid: String,        // 초대를 수락한 사용자의 UID
    val accepterNickname: String?,      // 초대를 수락한 사용자의 닉네임
    val partnerSince: String            // 파트너 관계가 시작된 시간 (ISO 8601 형식)
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun create(
            invitationId: String,
            accepterUserUid: String,
            accepterNickname: String?,
            partnerSinceTime: java.time.LocalDateTime // LocalDateTime을 직접 받음
        ): PartnerInvitationAcceptedNotificationDto {
            return PartnerInvitationAcceptedNotificationDto(
                message = "${accepterNickname ?: "상대방"}님이 회원님의 파트너 초대를 수락했습니다!",
                invitationId = invitationId,
                accepterUserUid = accepterUserUid,
                accepterNickname = accepterNickname,
                partnerSince = partnerSinceTime.format(formatter)
            )
        }
    }
}