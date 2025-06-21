package com.company.rest.api.event

import java.time.LocalDateTime

data class PartnerRelationEstablishedEvent(
    val issuerUserId: String,
    val accepterUserId: String,
    val accepterNickname: String?,
    val partnerSince: LocalDateTime
)