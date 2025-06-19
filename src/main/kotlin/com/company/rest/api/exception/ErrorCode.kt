package com.company.rest.api.exception

import org.springframework.http.HttpStatus

/**
 * 애플리케이션에서 사용할 에러 코드를 정의하는 열거형 클래스입니다.
 * 각 에러 코드는 HTTP 상태 코드와 클라이언트에게 보여줄 메시지를 가집니다.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_NOT_MATCH(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 일치하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요."),
    UNSUPPORTED_LOGIN_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 플랫폼입니다."),
    SOCIAL_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다."),


    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    NICKNAME_UPDATE_FAILED(HttpStatus.BAD_REQUEST, "닉네임 변경에 실패했습니다."),
    APP_PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "앱 비밀번호가 설정되어 있지 않습니다."),
    APP_PASSWORD_INVALID(HttpStatus.UNAUTHORIZED, "앱 비밀번호가 일치하지 않습니다."),
    CURRENT_APP_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "기존 비밀번호 변경 시 현재 앱 비밀번호를 입력해야 합니다."),

    PARTNER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 파트너가 존재합니다."),
    CANNOT_INVITE_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 초대할 수 없습니다."),
    INVITATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 초대 코드입니다."),
    INVITATION_ISSUER_HAS_PARTNER(HttpStatus.CONFLICT, "초대자가 이미 다른 파트너와 연결되었습니다."),
    FORBIDDEN_INVITATION_ACCESS(HttpStatus.FORBIDDEN, "해당 초대 코드를 삭제할 권한이 없습니다."),
    INVITATION_ALREADY_USED(HttpStatus.BAD_REQUEST, "이미 사용된 초대 코드는 삭제할 수 없습니다."),

    // Event
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    FORBIDDEN_EVENT_ACCESS(HttpStatus.FORBIDDEN, "해당 이벤트에 접근할 권한이 없습니다."),

    // Anniversary
    ANNIVERSARY_NOT_FOUND(HttpStatus.NOT_FOUND, "기념일을 찾을 수 없습니다."),
    FORBIDDEN_ANNIVERSARY_ACCESS(HttpStatus.FORBIDDEN, "해당 기념일에 접근할 권한이 없습니다."),

    // Chat
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    FORBIDDEN_MESSAGE_ACCESS(HttpStatus.FORBIDDEN, "해당 메시지를 삭제할 권한이 없습니다."),

    // Weather
    WEATHER_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "날씨 정보를 찾을 수 없습니다."),

    // Luck
    LUCK_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "운세 정보를 찾을 수 없습니다.")
}