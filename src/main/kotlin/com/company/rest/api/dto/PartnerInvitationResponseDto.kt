package com.company.rest.api.dto

import com.company.rest.api.entity.PartnerInvitation
import java.time.format.DateTimeFormatter

data class PartnerInvitationResponseDto(
    val invitationId: String,
    val expiresAt: String // ISO 8601 형식 (예: "2025-05-20T14:30:00")
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun fromEntity(partnerInvitation: PartnerInvitation): PartnerInvitationResponseDto {
            return PartnerInvitationResponseDto(
                invitationId = partnerInvitation.id,
                expiresAt = partnerInvitation.expiresAt.format(formatter)
            )
        }
    }
}