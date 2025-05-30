package com.company.rest.api.service

import com.company.rest.api.dto.UserAccountUpdateRequestDto
import com.company.rest.api.entity.User
import com.company.rest.api.repository.ChatMessageRepository
import com.company.rest.api.repository.EventRepository
import com.company.rest.api.repository.PartnerInvitationRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val chatMessageRepository: ChatMessageRepository,
    private val eventRepository: EventRepository,
    private val partnerInvitationRepository: PartnerInvitationRepository
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    @Transactional(readOnly = true)
    fun verifyAppPassword(userUid: String, plainPasswordToCheck: String): Boolean {
        logger.info("Attempting to verify app password for user UID: {}", userUid)
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for app password verification.", userUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
            }

        // appPasswordIsSet 플래그를 사용하여 비밀번호 설정 여부 확인
        if (!user.appPasswordIsSet || user.appPassword == null) { // appPasswordIsSet이 false거나, appPassword가 실제로 null이면 (이중 체크)
            logger.warn("User UID: {} has not set an app password (appPasswordIsSet: {}).", userUid, user.appPasswordIsSet)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "앱 비밀번호가 설정되어 있지 않습니다. 먼저 비밀번호를 설정해주세요.")
        }

        val matches = passwordEncoder.matches(plainPasswordToCheck, user.appPassword)
        if (matches) {
            logger.info("App password verification successful for user UID: {}", userUid)
        } else {
            logger.warn("App password verification failed for user UID: {}", userUid)
        }
        return matches
    }

    @Transactional
    fun updateUserAccount(userUid: String, requestDto: UserAccountUpdateRequestDto): User {
        logger.info("Attempting to update account for user UID: {}", userUid)
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for account update.", userUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "계정 정보를 업데이트할 사용자를 찾을 수 없습니다.")
            }

        var updated = false

        requestDto.nickname?.let { newNickname ->
            if (user.nickname != newNickname) {
                logger.info("Updating nickname for user UID: {} from '{}' to '{}'", userUid, user.nickname, newNickname)
                user.nickname = newNickname
                updated = true
            }
        }

        if (requestDto.newAppPassword != null) {
            val newPassword = requestDto.newAppPassword
            if (newPassword.isNotBlank()) {
                // 기존 비밀번호가 설정되어 있는 경우 (비밀번호 변경) -> user.appPasswordIsSet 플래그 사용
                if (user.appPasswordIsSet) {
                    if (requestDto.currentAppPassword == null || requestDto.currentAppPassword.isBlank()) {
                        logger.warn("Current app password is required to change existing app password for user UID: {}", userUid)
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "기존 앱 비밀번호 변경 시 현재 앱 비밀번호를 입력해야 합니다.")
                    }
                    // user.appPassword가 null일 수도 있으므로 (플래그는 true인데 비밀번호가 없는 비정상적 상황 방지)
                    if (user.appPassword == null || !passwordEncoder.matches(requestDto.currentAppPassword, user.appPassword)) {
                        logger.warn("Current app password verification failed for user UID: {} during password change.", userUid)
                        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 앱 비밀번호가 올바르지 않습니다.")
                    }
                } else { // 기존 비밀번호가 없는 경우 (최초 설정)
                    if (requestDto.currentAppPassword != null && requestDto.currentAppPassword.isNotBlank()) {
                        logger.info("User UID: {} is setting app password for the first time (appPasswordIsSet: false). CurrentAppPassword field was provided but will be ignored.", userUid)
                    }
                }
                logger.info("Updating app password for user UID: {}", userUid)
                user.appPassword = passwordEncoder.encode(newPassword)
                user.appPasswordIsSet = true // 비밀번호가 성공적으로 설정/변경되었으므로 플래그를 true로 설정
                updated = true
            } else {
                logger.info("New app password field was present but blank. App password for user UID: {} will not be changed.", userUid)
            }
        }

        if (updated) {
            val savedUser = userRepository.save(user)
            logger.info("User account updated successfully for UID: {}. AppPasswordIsSet: {}", userUid, savedUser.appPasswordIsSet)
            return savedUser
        } else {
            logger.info("No changes detected for user account UID: {}", userUid)
            return user
        }
    }

    @Transactional
    fun removeAppPassword(userUid: String, currentPlainPassword: String) {
        logger.info("Attempting to remove app password for user UID: {}", userUid)
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for app password removal.", userUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
            }

        // appPasswordIsSet 플래그와 appPassword 값 모두 확인
        if (!user.appPasswordIsSet || user.appPassword == null) {
            logger.info("User UID: {} does not have an app password set (appPasswordIsSet: {}). No action needed.", userUid, user.appPasswordIsSet)
            return
        }

        if (!passwordEncoder.matches(currentPlainPassword, user.appPassword)) {
            logger.warn("Current app password verification failed for user UID: {} during password removal.", userUid)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 앱 비밀번호가 올바르지 않습니다. 비밀번호를 해제할 수 없습니다.")
        }

        user.appPassword = null
        user.appPasswordIsSet = false // 비밀번호가 성공적으로 해제되었으므로 플래그를 false로 설정
        userRepository.save(user)
        logger.info("App password removed successfully for user UID: {}. AppPasswordIsSet: false", userUid)
    }

    @Transactional
    fun clearPartnerAndChatHistory(currentUserUid: String) {
        // ... (기존 로직과 동일) ...
        logger.info("Attempting to clear partner data and chat history for user UID: {}", currentUserUid)
        val currentUser = userRepository.findById(currentUserUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for clearing partner data.", currentUserUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
            }

        val partnerUid = currentUser.partnerUserUid
        if (partnerUid == null) {
            logger.info("User UID: {} does not have a partner. No action needed.", currentUserUid)
            return
        }

        currentUser.partnerUserUid = null
        currentUser.partnerSince = null
        userRepository.save(currentUser)
        logger.info("Partner information cleared for user UID: {}", currentUserUid)

        val partnerUser = userRepository.findById(partnerUid).orElse(null)

        if (partnerUser != null) {
            partnerUser.partnerUserUid = null
            partnerUser.partnerSince = null
            userRepository.save(partnerUser)
            logger.info("Partner information cleared for former partner UID: {}", partnerUid)

            val deletedMessagesCount = chatMessageRepository.deleteAllMessagesBetweenUsers(currentUser, partnerUser)
            logger.info("Chat history between user UID: {} and former partner UID: {} has been deleted. Count: {}", currentUserUid, partnerUid, deletedMessagesCount)
        } else {
            logger.warn("Former partner with UID: {} not found. Only cleared partner info for current user UID: {}. Chat history specific to this pair might not be fully cleared if partner entity is missing.", partnerUid, currentUserUid)
        }
    }


    @Transactional
    fun updateFcmToken(userUid: String, fcmToken: String) {
        logger.info("Attempting to update FCM token for user UID: {}", userUid)
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} when trying to update FCM token.", userUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
            }

        if (user.fcmToken != fcmToken) {
            user.fcmToken = fcmToken
            userRepository.save(user)
            logger.info("FCM token updated successfully for user UID: {}", userUid)
        } else {
            logger.info("FCM token is the same as the existing one for user UID: {}. No update performed.", userUid)
        }
    }

    @Transactional
    fun removeFcmToken(fcmToken: String) {
        if (fcmToken.isBlank()) {
            logger.warn("Attempted to remove a blank FCM token. Skipping.")
            return
        }
        logger.info("Attempting to remove FCM token (first 10 chars): {}...", fcmToken.take(10))
        val userOptional = userRepository.findByFcmToken(fcmToken)

        if (userOptional.isPresent) {
            val user = userOptional.get()
            if (user.fcmToken == fcmToken) {
                user.fcmToken = null
                userRepository.save(user)
                logger.info("Successfully removed FCM token for user UID: {}. Token (first 10 chars): {}...", user.uid, fcmToken.take(10))
            } else {
                logger.warn("FCM token for user UID: {} in DB does not precisely match the token to be removed, though it was found by it. DB token (first 10): {}, To remove (first 10): {}. No action taken.",
                    user.uid, user.fcmToken?.take(10), fcmToken.take(10))
            }
        } else {
            logger.info("No user found with FCM token (first 10 chars): {}... No action taken.", fcmToken.take(10))
        }
    }

    @Transactional
    fun deleteUserAccount(userUid: String) {
        logger.info("Attempting to delete account for user UID: {}", userUid)
        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} for account deletion.", userUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "삭제할 사용자를 찾을 수 없습니다.")
            }

        if (user.partnerUserUid != null) {
            clearPartnerAndChatHistory(user.uid)
        }

        val deletedInvitationsCount = partnerInvitationRepository.deleteAllByIssuerUser(user)
        logger.info("Deleted {} partner invitations issued by user UID: {}", deletedInvitationsCount, user.uid)

        val deletedEventsCount = eventRepository.deleteAllByUser(user)
        logger.info("Deleted {} events for user UID: {}", deletedEventsCount, user.uid)

        val deletedAllMessagesCount = chatMessageRepository.deleteAllBySenderOrReceiver(user)
        logger.info("Deleted {} total chat messages involving user UID: {}", deletedAllMessagesCount, user.uid)

        userRepository.delete(user)
        logger.info("User account UID: {} and all associated data deleted successfully.", user.uid)
    }
}