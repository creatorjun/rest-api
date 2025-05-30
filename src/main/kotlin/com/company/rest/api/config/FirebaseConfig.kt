package com.company.rest.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.IOException

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)
    @Value("\${google.fcm.key}")
    private lateinit var firebaseSdkPath: String
    @PostConstruct
    fun initializeFirebase() {
        try {
            // FirebaseApp이 이미 초기화되지 않았을 경우에만 초기화 진행
            if (FirebaseApp.getApps().isEmpty()) {
                val serviceAccount = ClassPathResource(firebaseSdkPath).inputStream

                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    // 만약 Firebase Realtime Database 또는 다른 Firebase 서비스를 사용한다면,
                    // .setDatabaseUrl("https://<YOUR_PROJECT_ID>.firebaseio.com") 등을 추가할 수 있습니다.
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK initialized successfully.")
            } else {
                logger.info("Firebase Admin SDK already initialized.")
            }
        } catch (e: IOException) {
            logger.error("Error initializing Firebase Admin SDK: ${e.message}", e)
            // SDK 초기화 실패는 심각한 문제일 수 있으므로, 필요에 따라 애플리케이션 실행을 중단시킬 수도 있습니다.
            throw RuntimeException("Failed to initialize Firebase Admin SDK. Please check the service account key file and path.", e)
        }
    }
}