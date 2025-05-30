package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "partner_invitations")
data class PartnerInvitation(
    @Id
    @Column(name = "invitation_id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(), // 고유한 초대 ID (예: UUID)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_user_uid", nullable = false, updatable = false, foreignKey = ForeignKey(name = "fk_invitation_issuer_uid"))
    val issuerUser: User, // 초대를 생성한 사용자

    @Column(name = "expires_at", nullable = false, updatable = false)
    var expiresAt: LocalDateTime, // 초대 만료 시간 (예: 생성 시간 + 24시간)

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(), // 초대 생성 시간

    @Column(name = "is_used", nullable = false)
    var isUsed: Boolean = false, // 초대 사용 여부 (파트너 연결 시 true로 변경)

    @Column(name = "accepted_by_user_uid", nullable = true) // 초대를 수락한 사용자 uid (선택 사항)
    var acceptedByUserUid: String? = null
)