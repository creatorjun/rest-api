package com.company.rest.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class EventCreateRequestDto(

    @field:NotBlank(message = "이벤트 내용은 비워둘 수 없습니다.")
    @field:Size(max = 1000, message = "이벤트 내용은 최대 1000자까지 입력 가능합니다.")
    val text: String,

    val startTime: String?, // "HH:mm" 형식
    val endTime: String?,   // "HH:mm" 형식
    val eventDate: String?,  // "YYYY-MM-DD" 형식

    @field:NotNull(message = "displayOrder는 필수 값입니다.")
    @field:Min(value = 0, message = "displayOrder는 0 이상이어야 합니다.")
    val displayOrder: Int

) {
    fun getStartTimeAsLocalTime(): LocalTime? {
        return startTime?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME) }
    }

    fun getEndTimeAsLocalTime(): LocalTime? {
        return endTime?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME) }
    }

    fun getEventDateAsLocalDate(): LocalDate? {
        return eventDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
    }
}