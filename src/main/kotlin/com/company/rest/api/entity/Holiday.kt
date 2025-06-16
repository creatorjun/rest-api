package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.util.*

@Entity
@Table(
    name = "holidays",
    indexes = [Index(name = "idx_holiday_date", columnList = "holiday_date")]
)
data class Holiday(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "holiday_date", nullable = false, unique = true)
    val date: LocalDate,

    @Column(name = "name", nullable = false)
    val name: String
)