package com.company.rest.api.entity

import com.company.rest.api.dto.MessageType
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "chat_messages")
data class ChatMessage(
    @Id
    @Column(name = "message_id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    var type: MessageType = MessageType.CHAT,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "sender_uid",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_chatmessage_sender_uid")
    )
    val sender: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "receiver_uid",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_chatmessage_receiver_uid")
    )
    val receiver: User,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "read_at", nullable = true)
    var readAt: LocalDateTime? = null,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Column(name = "deleted_at", nullable = true)
    var deletedAt: LocalDateTime? = null
)