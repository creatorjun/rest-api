package com.company.rest.api.dto

import com.company.rest.api.entity.User
import java.time.format.DateTimeFormatter

data class UserAccountUpdateResponseDto(
    val uid: String,
    val nickname: String?,
    val loginProvider: String,
    val createdAt: String,
    val partnerUid: String?,
    val appPasswordSet: Boolean
) {
    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        fun fromUser(user: User): UserAccountUpdateResponseDto {
            return UserAccountUpdateResponseDto(
                uid = user.uid,
                nickname = user.nickname,
                loginProvider = user.loginProvider.name,
                createdAt = user.createdAt.format(dateTimeFormatter),
                partnerUid = user.partnerUserUid,
                appPasswordSet = user.appPassword != null
            )
        }
    }
}