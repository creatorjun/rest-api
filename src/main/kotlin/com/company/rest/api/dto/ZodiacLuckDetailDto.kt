package com.company.rest.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ZodiacLuckDetailDto( // 클래스 이름 변경
    @JsonProperty("띠")
    val zodiacName: String? = null,

    @JsonProperty("해당년도")
    val applicableYears: List<String>? = null,

    @JsonProperty("오늘의운세")
    val overallLuck: String? = null,

    @JsonProperty("금전운")
    val financialLuck: String? = null,

    @JsonProperty("애정운")
    val loveLuck: String? = null,

    @JsonProperty("건강운")
    val healthLuck: String? = null,

    @JsonProperty("행운의숫자")
    val luckyNumber: Int? = null,

    @JsonProperty("행운의색상")
    val luckyColor: String? = null,

    @JsonProperty("오늘의조언")
    val advice: String? = null
)