package com.company.rest.api.dto

enum class SystemNotificationType {
    FORCED_LOGOUT,
    ACCOUNT_DELETED,
    PARTNER_RELATION_TERMINATED,
    PARTNER_RELATION_ESTABLISHED
}

data class SystemNotificationDto(
    val type: SystemNotificationType,
    val message: String,
    val data: Map<String, Any?>? = null
)