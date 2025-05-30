package com.company.rest.api.service

import com.company.rest.api.dto.EventCreateRequestDto // DTO 임포트 변경
import com.company.rest.api.dto.EventResponseDto // DTO 임포트 변경
import com.company.rest.api.dto.EventUpdateRequestDto // DTO 임포트 변경
import com.company.rest.api.entity.Event // 엔티티 임포트 변경
import com.company.rest.api.repository.EventRepository // 리포지토리 임포트 변경
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
// import java.time.LocalDateTime // @PreUpdate로 자동 관리되므로 명시적 updatedAt 설정 불필요
import java.util.stream.Collectors

@Service
class EventService( // 클래스 이름 변경: MemoService -> EventService
    private val eventRepository: EventRepository, // 리포지토리 타입 및 이름 변경
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(EventService::class.java)

    @Transactional
    fun createEvent(requestDto: EventCreateRequestDto, userUid: String): EventResponseDto { // 메소드 이름 및 DTO 타입 변경
        logger.info("Attempting to create event for user with UID: $userUid") // 로그 메시지 변경

        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found for UID: $userUid while creating event.") // 로그 메시지 변경
                ResponseStatusException(HttpStatus.NOT_FOUND, "이벤트를 생성할 사용자를 찾을 수 없습니다.") // 예외 메시지 변경
            }

        val event = Event( // 엔티티 생성 변경: Memo -> Event
            user = user,
            text = requestDto.text,
            startTime = requestDto.getStartTimeAsLocalTime(),
            endTime = requestDto.getEndTimeAsLocalTime(),
            eventDate = requestDto.getEventDateAsLocalDate() // DTO 필드 및 메소드 호출 변경
        )

        val savedEvent = eventRepository.save(event) // 변수 이름 변경
        logger.info("Event created successfully with ID: ${savedEvent.id} for user UID: ${user.uid}") // 로그 메시지 및 변수 변경
        return EventResponseDto.fromEntity(savedEvent) // DTO 변환 변경
    }

    @Transactional(readOnly = true)
    fun getEventsForUser(userUid: String): List<EventResponseDto> { // 메소드 이름 및 반환 타입 변경
        logger.info("Fetching events for user UID: $userUid") // 로그 메시지 변경
        if (!userRepository.existsById(userUid)) {
            logger.warn("Attempted to fetch events for non-existent user UID: $userUid") // 로그 메시지 변경
            return emptyList()
        }

        val events = eventRepository.findByUserUidOrderByCreatedAtDesc(userUid) // 변수 이름 및 리포지토리 호출 변경
        return events.stream()
            .map(EventResponseDto::fromEntity) // DTO 변환 변경
            .collect(Collectors.toList())
    }

    @Transactional
    fun updateEvent(userUid: String, requestDto: EventUpdateRequestDto): EventResponseDto { // 메소드 이름 및 DTO 타입 변경
        logger.info("Attempting to update event with ID: ${requestDto.eventId} for user UID: $userUid") // 로그 메시지 및 DTO 필드 변경

        val event = eventRepository.findById(requestDto.eventId) // 변수 이름, 리포지토리 호출, DTO 필드 변경
            .orElseThrow {
                logger.warn("Event not found with ID: ${requestDto.eventId} for update attempt by user UID: $userUid") // 로그 메시지 및 DTO 필드 변경
                ResponseStatusException(HttpStatus.NOT_FOUND, "수정할 이벤트를 찾을 수 없습니다.") // 예외 메시지 변경
            }

        // 이벤트 소유자 확인
        if (event.user.uid != userUid) {
            logger.warn("User UID: $userUid attempted to update event ID: ${requestDto.eventId} owned by another user UID: ${event.user.uid}") // 로그 메시지 및 DTO 필드 변경
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 이벤트를 수정할 권한이 없습니다.") // 예외 메시지 변경
        }

        // 변경 요청된 필드만 업데이트
        var updated = false
        requestDto.text?.let {
            if (event.text != it) {
                event.text = it
                updated = true
            }
        }

        val newStartTime = requestDto.getStartTimeAsLocalTime()
        if (requestDto.startTime != null) {
            if (event.startTime != newStartTime) {
                event.startTime = newStartTime
                updated = true
            }
        }

        val newEndTime = requestDto.getEndTimeAsLocalTime()
        if (requestDto.endTime != null) {
            if (event.endTime != newEndTime) {
                event.endTime = newEndTime
                updated = true
            }
        }

        val newEventDate = requestDto.getEventDateAsLocalDate() // DTO 필드 및 메소드 호출 변경
        if (requestDto.eventDate != null) { // DTO 필드 변경
            if (event.eventDate != newEventDate) { // 엔티티 필드 변경
                event.eventDate = newEventDate // 엔티티 필드 변경
                updated = true
            }
        }

        if (updated) {
            // Event 엔티티에 @PreUpdate 어노테이션이 있다면 updatedAt은 자동으로 갱신됨
            val updatedEvent = eventRepository.save(event) // 변수 이름 변경
            logger.info("Event ID: ${updatedEvent.id} updated successfully by user UID: $userUid") // 로그 메시지 변경
            return EventResponseDto.fromEntity(updatedEvent) // DTO 변환 변경
        } else {
            logger.info("Event ID: ${event.id} had no fields to update for user UID: $userUid") // 로그 메시지 변경
            return EventResponseDto.fromEntity(event) // 변경 사항이 없으면 기존 이벤트 정보 반환
        }
    }

    @Transactional
    fun deleteEvent(userUid: String, eventId: String) { // 메소드 이름 및 파라미터 이름 변경
        logger.info("Attempting to delete event with ID: $eventId by user UID: $userUid") // 로그 메시지 및 파라미터 이름 변경

        val event = eventRepository.findById(eventId) // 변수 이름, 리포지토리 호출, 파라미터 이름 변경
            .orElseThrow {
                logger.warn("Event not found with ID: $eventId for delete attempt by user UID: $userUid") // 로그 메시지 및 파라미터 이름 변경
                ResponseStatusException(HttpStatus.NOT_FOUND, "삭제할 이벤트를 찾을 수 없습니다.") // 예외 메시지 변경
            }

        // 이벤트 소유자 확인
        if (event.user.uid != userUid) {
            logger.warn("User UID: $userUid attempted to delete event ID: $eventId owned by another user UID: ${event.user.uid}") // 로그 메시지 및 파라미터 이름 변경
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "해당 이벤트를 삭제할 권한이 없습니다.") // 예외 메시지 변경
        }

        eventRepository.delete(event) // 리포지토리 호출 변경
        logger.info("Event ID: $eventId deleted successfully by user UID: $userUid") // 로그 메시지 및 파라미터 이름 변경
    }
}