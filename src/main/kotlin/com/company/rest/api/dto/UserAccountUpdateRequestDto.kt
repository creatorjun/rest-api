package com.company.rest.api.dto

import jakarta.validation.constraints.Size

data class UserAccountUpdateRequestDto(
    @field:Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하로 입력해주세요.")
    val nickname: String?,

    val currentAppPassword: String?, // Nullable로 선언되어 있음

    @field:Size(min = 4, message = "새 앱 비밀번호는 4자 이상이어야 합니다.")
    val newAppPassword: String?,
    )