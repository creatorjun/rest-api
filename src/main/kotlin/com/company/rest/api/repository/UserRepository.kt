package com.company.rest.api.repository

import com.company.rest.api.dto.LoginProvider
import com.company.rest.api.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, String> { // User의 PK는 String (uid)
    fun findByProviderIdAndLoginProvider(providerId: String, loginProvider: LoginProvider): Optional<User> //
    fun findByUidAndRefreshToken(uid: String, refreshToken: String): Optional<User> //

    // providerId로 사용자를 찾는 메소드 추가
    fun findByProviderId(providerId: String): Optional<User>

    // FCM 토큰으로 사용자를 찾는 메소드 추가 (새로 추가된 부분)
    // FCM 토큰은 유니크하다고 가정하지만, 만약 여러 사용자가 같은 토큰을 가질 수 있는 극단적인 상황을 고려한다면 List<User> 반환도 가능
    // 그러나 일반적으로 FCM 토큰은 특정 디바이스의 특정 앱 인스턴스에 해당하므로, User 엔티티에 저장 시 유니크하게 관리하는 것이 좋음
    fun findByFcmToken(fcmToken: String): Optional<User>
}