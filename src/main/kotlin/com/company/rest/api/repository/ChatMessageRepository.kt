package com.company.rest.api.repository

import com.company.rest.api.entity.ChatMessage
import com.company.rest.api.entity.User
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, String> {

    @Query("""
        SELECT cm FROM ChatMessage cm 
        JOIN FETCH cm.sender s
        JOIN FETCH cm.receiver r
        WHERE (s = :user1 AND r = :user2) 
           OR (s = :user2 AND r = :user1) 
        ORDER BY cm.createdAt ASC
    """)
    fun findConversationMessages(
        @Param("user1") user1: User,
        @Param("user2") user2: User
    ): List<ChatMessage>


    fun findByReceiverAndSenderAndIsReadFalse(receiver: User, sender: User): List<ChatMessage>

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readTime WHERE cm.id IN :messageIds")
    fun markMessagesAsReadByIds(
        @Param("messageIds") messageIds: List<String>,
        @Param("readTime") readTime: LocalDateTime
    ): Int

    fun countByReceiverAndIsReadFalse(receiver: User): Long

    @Query("""
        SELECT cm.createdAt 
        FROM ChatMessage cm 
        WHERE ((cm.sender = :user1 AND cm.receiver = :user2) OR (cm.sender = :user2 AND cm.receiver = :user1))
          AND cm.createdAt < :beforeTime
        ORDER BY cm.createdAt DESC
    """)
    fun findLatestMessageTimeBetweenUsersBefore(
        @Param("user1") user1: User,
        @Param("user2") user2: User,
        @Param("beforeTime") beforeTime: LocalDateTime,
        pageable: Pageable
    ): Slice<LocalDateTime>

    @Query("""
        SELECT cm FROM ChatMessage cm
        JOIN FETCH cm.sender s
        JOIN FETCH cm.receiver r
        WHERE (s = :user1 AND r = :user2) 
           OR (s = :user2 AND r = :user1)
        ORDER BY cm.createdAt DESC
    """)
    fun findLatestMessagesBetweenUsers(
        @Param("user1") user1: User,
        @Param("user2") user2: User,
        pageable: Pageable
    ): Slice<ChatMessage>

    @Query("""
        SELECT cm FROM ChatMessage cm
        JOIN FETCH cm.sender s
        JOIN FETCH cm.receiver r
        WHERE ((s = :user1 AND r = :user2) OR (s = :user2 AND r = :user1))
          AND cm.createdAt < :cursorCreatedAt
        ORDER BY cm.createdAt DESC
    """)
    fun findMessagesBetweenUsersBefore(
        @Param("user1") user1: User,
        @Param("user2") user2: User,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime,
        pageable: Pageable
    ): Slice<ChatMessage>

    @Query("""
        SELECT cm FROM ChatMessage cm
        JOIN FETCH cm.sender s
        JOIN FETCH cm.receiver r
        WHERE ((s = :user1 AND r = :user2) OR (s = :user2 AND r = :user1))
          AND cm.isDeleted = false
          AND LOWER(cm.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY cm.createdAt DESC
    """)
    fun searchMessagesBetweenUsers(
        @Param("user1") user1: User,
        @Param("user2") user2: User,
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Slice<ChatMessage>

    @Modifying
    @Query("""
        DELETE FROM ChatMessage cm
        WHERE (cm.sender = :user1 AND cm.receiver = :user2)
           OR (cm.sender = :user2 AND cm.receiver = :user1)
    """)
    // 위 쿼리에서 오타를 수정했습니다. (r -> cm.receiver)
    fun deleteAllMessagesBetweenUsers(@Param("user1") user1: User, @Param("user2") user2: User)

    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.sender = :user OR cm.receiver = :user")
    fun deleteAllBySenderOrReceiver(@Param("user") user: User): Int
}