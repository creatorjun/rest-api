package com.company.rest.api.exception

/**
 * ErrorCode를 포함하는 커스텀 예외 클래스입니다.
 * 서비스 로직에서 특정 예외 상황이 발생했을 때 이 클래스를 사용합니다.
 */
class CustomException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)