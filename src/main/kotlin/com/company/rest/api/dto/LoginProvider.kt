// src/main/kotlin/com/company/rest/api/dto/LoginProvider.kt
package com.company.rest.api.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class LoginProvider(@get:JsonValue val value: String) {
    NAVER("naver"),
    KAKAO("kakao"),
    NONE("none"); // 'none' 추가 (이메일/비밀번호 등 자체 인증용)

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): LoginProvider? {
            return entries.find { it.value == value.lowercase() }
        }
    }
}