package com.company.rest.api.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class WebSocketActivityService {
    private val logger = LoggerFactory.getLogger(WebSocketActivityService::class.java)

    // Key: 사용자 UID, Value: 현재 대화 중인 파트너의 UID
    private val activeChatPartners = ConcurrentHashMap<String, String>()

    /**
     * 사용자가 특정 대화창에 들어왔음을 기록합니다.
     */
    fun userEnteredChat(userUid: String, partnerUid: String) {
        logger.info("User {} entered chat with partner {}", userUid, partnerUid)
        activeChatPartners[userUid] = partnerUid
    }

    /**
     * 사용자가 대화창에서 나갔음을 기록합니다.
     * partnerUid를 받는 이유는, 혹시 나중에 다른 대화창으로 바로 이동했을 경우를 대비하기 위함입니다.
     */
    fun userLeftChat(userUid: String, partnerUid: String) {
        // 현재 사용자가 해당 파트너와 대화 중이었는지 확인하고 제거
        if (activeChatPartners[userUid] == partnerUid) {
            logger.info("User {} left chat with partner {}", userUid, partnerUid)
            activeChatPartners.remove(userUid)
        }
    }

    /**
     * 사용자가 로그아웃 등으로 연결이 완전히 끊겼을 때 호출됩니다.
     */
    fun userDisconnected(userUid: String) {
        if (activeChatPartners.containsKey(userUid)) {
            logger.info("User {} disconnected, removing from active chat tracking.", userUid)
            activeChatPartners.remove(userUid)
        }
    }

    /**
     * 특정 사용자가 현재 특정 파트너와의 대화창을 보고 있는지 확인합니다.
     * @param userUid 확인 대상 사용자
     * @param partnerUid 대화 상대
     * @return 활성 상태이면 true
     */
    fun isUserActiveInChat(userUid: String, partnerUid: String): Boolean {
        val isActive = activeChatPartners[userUid] == partnerUid
        if (isActive) {
            logger.debug("Activity check: User {} IS active in chat with {}", userUid, partnerUid)
        }
        return isActive
    }
}