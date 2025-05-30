package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

data class PartnerInvitationAcceptRequestDto(
    @field:NotBlank(message = "초대 ID는 비워둘 수 없습니다.")
    val invitationId: String
)