package com.company.rest.api.service

import com.company.rest.api.dto.AnniversaryCreateRequestDto
import com.company.rest.api.dto.AnniversaryResponseDto
import com.company.rest.api.dto.AnniversaryUpdateRequestDto
import com.company.rest.api.entity.Anniversary
import com.company.rest.api.entity.User
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.AnniversaryRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class AnniversaryService(
    private val anniversaryRepository: AnniversaryRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(AnniversaryService::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Transactional
    fun createAnniversary(userUid: String, requestDto: AnniversaryCreateRequestDto): AnniversaryResponseDto {
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for anniversary creation.", userUid)
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        val anniversaryDate = try {
            LocalDate.parse(requestDto.date, dateFormatter)
        } catch (e: DateTimeParseException) {
            logger.warn("Invalid date format for anniversary creation: {}", requestDto.date)
            throw CustomException(ErrorCode.INVALID_INPUT_VALUE)
        }

        val anniversary = Anniversary(
            user = user,
            title = requestDto.title,
            date = anniversaryDate
        )

        val savedAnniversary = anniversaryRepository.save(anniversary)
        logger.info("Anniversary created with ID: {} for user UID: {}", savedAnniversary.id, userUid)
        return AnniversaryResponseDto.fromEntity(savedAnniversary)
    }

    @Transactional(readOnly = true)
    fun getAnniversariesForUser(userUid: String): List<AnniversaryResponseDto> {
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for getting anniversaries.", userUid)
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        val uidsToFetch = mutableListOf(user.uid)
        user.partnerUserUid?.let { uidsToFetch.add(it) }

        val anniversaries = anniversaryRepository.findByUserUidInOrderByDateAsc(uidsToFetch)
        return anniversaries.map { AnniversaryResponseDto.fromEntity(it) }
    }

    @Transactional
    fun updateAnniversary(
        userUid: String,
        anniversaryId: String,
        requestDto: AnniversaryUpdateRequestDto
    ): AnniversaryResponseDto {
        val currentUser = userRepository.findById(userUid)
            .orElseThrow { throw CustomException(ErrorCode.USER_NOT_FOUND) }

        val anniversary = anniversaryRepository.findById(anniversaryId)
            .orElseThrow { throw CustomException(ErrorCode.ANNIVERSARY_NOT_FOUND) }

        checkAccessPermission(currentUser, anniversary)

        var updated = false
        requestDto.title?.let {
            if (anniversary.title != it) {
                anniversary.title = it
                updated = true
            }
        }
        requestDto.date?.let {
            val newDate = try {
                LocalDate.parse(it, dateFormatter)
            } catch (e: DateTimeParseException) {
                throw CustomException(ErrorCode.INVALID_INPUT_VALUE)
            }
            if (anniversary.date != newDate) {
                anniversary.date = newDate
                updated = true
            }
        }

        if (updated) {
            val updatedAnniversary = anniversaryRepository.save(anniversary)
            logger.info("Anniversary ID: {} updated by user UID: {}", anniversaryId, userUid)
            return AnniversaryResponseDto.fromEntity(updatedAnniversary)
        }

        return AnniversaryResponseDto.fromEntity(anniversary)
    }

    @Transactional
    fun deleteAnniversary(userUid: String, anniversaryId: String) {
        val currentUser = userRepository.findById(userUid)
            .orElseThrow { throw CustomException(ErrorCode.USER_NOT_FOUND) }

        val anniversary = anniversaryRepository.findById(anniversaryId)
            .orElseThrow { throw CustomException(ErrorCode.ANNIVERSARY_NOT_FOUND) }

        checkAccessPermission(currentUser, anniversary)

        anniversaryRepository.delete(anniversary)
        logger.info("Anniversary ID: {} deleted by user UID: {}", anniversaryId, userUid)
    }

    private fun checkAccessPermission(currentUser: User, anniversary: Anniversary) {
        val owner = anniversary.user
        val isOwner = owner.uid == currentUser.uid
        val isPartnerOfOwner = owner.partnerUserUid == currentUser.uid

        if (!isOwner && !isPartnerOfOwner) {
            logger.warn(
                "User UID: {} attempted to access anniversary ID: {} owned by UID: {}",
                currentUser.uid, anniversary.id, owner.uid
            )
            throw CustomException(ErrorCode.FORBIDDEN_ANNIVERSARY_ACCESS)
        }
    }
}