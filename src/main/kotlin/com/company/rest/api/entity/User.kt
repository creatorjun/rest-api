package com.company.rest.api.entity

import com.company.rest.api.dto.LoginProvider
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "app_users", uniqueConstraints = [
        UniqueConstraint(name = "uk_user_provider_id_login_provider", columnNames = ["provider_id", "loginProvider"])
    ]
)
data class User(

    @Id
    @Column(name = "uid", unique = true, nullable = false, updatable = false)
    val uid: String = UUID.randomUUID().toString(),

    @Column
    var nickname: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val loginProvider: LoginProvider,

    @Column(name = "provider_id", nullable = false)
    val providerId: String,

    @Column(name = "app_password", length = 255, nullable = true)
    var appPassword: String? = null,

    @Column(name = "app_password_is_set", nullable = false) // 새로운 필드 추가
    var appPasswordIsSet: Boolean = false,                 // 기본값 false

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "refresh_token", length = 512)
    var refreshToken: String? = null,

    @Column(name = "refresh_token_expiry_date")
    var refreshTokenExpiryDate: LocalDateTime? = null,

    @Column(name = "partner_user_uid", unique = true, nullable = true)
    var partnerUserUid: String? = null,

    @Column(name = "partner_since", nullable = true)
    var partnerSince: LocalDateTime? = null,

    @Column(name = "fcm_token", length = 512, nullable = true)
    var fcmToken: String? = null

) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    fun setPartner(partner: User) {
        this.partnerUserUid = partner.uid
        this.partnerSince = LocalDateTime.now()
    }

    fun clearPartner() {
        this.partnerUserUid = null
        this.partnerSince = null
    }
}