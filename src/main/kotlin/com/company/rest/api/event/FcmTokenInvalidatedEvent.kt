package com.company.rest.api.event

data class FcmTokenInvalidatedEvent(
    val fcmToken: String
)