package com.company.rest.api.event

data class UserAccountDeletedEvent(
    val userId: String,
    val fcmToken: String?,
    val isOnline: Boolean
)