package com.company.rest.api.dto

import com.company.rest.api.entity.Anniversary
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.format.DateTimeFormatter

data class AnniversaryCreateRequestDto(
    @field:NotBlank(message = "기념일 이름은 비워둘 수 없습니다.")
    @field:Size(max = 100, message = "기념일 이름은 최대 100자까지 입력 가능합니다.")
    val title: String,

    @field:NotBlank(message = "기념일 날짜는 비워둘 수 없습니다.")
    val date: String // "YYYY-MM-DD" 형식
)

data class AnniversaryUpdateRequestDto(
    @field:Size(max = 100, message = "기념일 이름은 최대 100자까지 입력 가능합니다.")
    val title: String?,

    val date: String? // "YYYY-MM-DD" 형식
)

data class AnniversaryResponseDto(
    val id: String,
    val title: String,
    val date: String // "YYYY-MM-DD" 형식
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun fromEntity(anniversary: Anniversary): AnniversaryResponseDto {
            return AnniversaryResponseDto(
                id = anniversary.id,
                title = anniversary.title,
                date = anniversary.date.format(dateFormatter)
            )
        }
    }
}