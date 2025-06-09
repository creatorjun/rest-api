package com.company.rest.api.service

import com.company.rest.api.dto.EventCreateRequestDto
import com.company.rest.api.dto.EventResponseDto
import com.company.rest.api.dto.EventUpdateRequestDto
import com.company.rest.api.entity.Event
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.EventRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(EventService::class.java)

    @Transactional
    fun createEvent(requestDto: EventCreateRequestDto, userUid: String): EventResponseDto {
        logger.info("Attempting to create event for user with UID: $userUid")

        val user = userRepository.findById(userUid)
            .orElseThrow {
                logger.warn("User not found for UID: $userUid while creating event.")
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        val event = Event(
            user = user,
            text = requestDto.text,
            startTime = requestDto.getStartTimeAsLocalTime(),
            endTime = requestDto.getEndTimeAsLocalTime(),
            eventDate = requestDto.getEventDateAsLocalDate()
        )

        val savedEvent = eventRepository.save(event)
        logger.info("Event created successfully with ID: ${savedEvent.id} for user UID: ${user.uid}")
        return EventResponseDto.fromEntity(savedEvent)
    }

    @Transactional(readOnly = true)
    fun getEventsForUser(userUid: String): List<EventResponseDto> {
        logger.info("Fetching events for user UID: $userUid")
        if (!userRepository.existsById(userUid)) {
            logger.warn("Attempted to fetch events for non-existent user UID: $userUid")
            return emptyList()
        }

        val events = eventRepository.findByUserUidOrderByCreatedAtDesc(userUid)
        return events.stream()
            .map(EventResponseDto::fromEntity)
            .collect(Collectors.toList())
    }

    @Transactional
    fun updateEvent(userUid: String, requestDto: EventUpdateRequestDto): EventResponseDto {
        logger.info("Attempting to update event with ID: ${requestDto.eventId} for user UID: $userUid")

        val event = eventRepository.findById(requestDto.eventId)
            .orElseThrow {
                logger.warn("Event not found with ID: ${requestDto.eventId} for update attempt by user UID: $userUid")
                throw CustomException(ErrorCode.EVENT_NOT_FOUND)
            }

        // 이벤트 소유자 확인
        if (event.user.uid != userUid) {
            logger.warn("User UID: $userUid attempted to update event ID: ${requestDto.eventId} owned by another user UID: ${event.user.uid}")
            throw CustomException(ErrorCode.FORBIDDEN_EVENT_ACCESS)
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

        val newEventDate = requestDto.getEventDateAsLocalDate()
        if (requestDto.eventDate != null) {
            if (event.eventDate != newEventDate) {
                event.eventDate = newEventDate
                updated = true
            }
        }

        if (updated) {
            val updatedEvent = eventRepository.save(event)
            logger.info("Event ID: ${updatedEvent.id} updated successfully by user UID: $userUid")
            return EventResponseDto.fromEntity(updatedEvent)
        } else {
            logger.info("Event ID: ${event.id} had no fields to update for user UID: $userUid")
            return EventResponseDto.fromEntity(event)
        }
    }

    @Transactional
    fun deleteEvent(userUid: String, eventId: String) {
        logger.info("Attempting to delete event with ID: $eventId by user UID: $userUid")

        val event = eventRepository.findById(eventId)
            .orElseThrow {
                logger.warn("Event not found with ID: $eventId for delete attempt by user UID: $userUid")
                throw CustomException(ErrorCode.EVENT_NOT_FOUND)
            }

        // 이벤트 소유자 확인
        if (event.user.uid != userUid) {
            logger.warn("User UID: $userUid attempted to delete event ID: $eventId owned by another user UID: ${event.user.uid}")
            throw CustomException(ErrorCode.FORBIDDEN_EVENT_ACCESS)
        }

        eventRepository.delete(event)
        logger.info("Event ID: $eventId deleted successfully by user UID: $userUid")
    }
}