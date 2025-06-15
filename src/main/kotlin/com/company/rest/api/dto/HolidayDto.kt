package com.company.rest.api.dto

import com.company.rest.api.entity.Holiday
import java.time.format.DateTimeFormatter

data class HolidayDto(
    val date: String,
    val name: String
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "YYYY-MM-DD"

        fun fromEntity(holiday: Holiday): HolidayDto {
            return HolidayDto(
                date = holiday.date.format(dateFormatter),
                name = holiday.name
            )
        }
    }
}