package com.company.rest.api.repository

import com.company.rest.api.entity.Anniversary
import com.company.rest.api.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AnniversaryRepository : JpaRepository<Anniversary, String> {

    fun findByUserUidInOrderByDateAsc(userUids: List<String>): List<Anniversary>

    @Modifying
    @Query("DELETE FROM Anniversary a WHERE a.user = :user")
    fun deleteAllByUser(@Param("user") user: User): Int
}