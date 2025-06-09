package com.company.rest.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream // FileInputStream을 사용하도록 변경
import java.io.IOException

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    // application-prod.properties를 통해 주입될 키 파일의 '절대 경로'
    @Value("\${google.fcm.key}")
    private lateinit var firebaseSdkPath: String // 변수명을 다시 path로 변경

    @PostConstruct
    fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                // ClassPathResource 대신 FileInputStream을 사용하여 외부 경로의 파일을 직접 읽습니다.
                val serviceAccount = FileInputStream(firebaseSdkPath)

                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK initialized successfully with key from path: {}", firebaseSdkPath)
            } else {
                logger.info("Firebase Admin SDK already initialized.")
            }
        } catch (e: IOException) {
            logger.error(
                "Error initializing Firebase Admin SDK with key from path '{}': {}",
                firebaseSdkPath,
                e.message,
                e
            )
            // 에러 메시지도 외부 경로를 확인하도록 수정
            throw RuntimeException(
                "Failed to initialize Firebase Admin SDK. Please check the service account key file path specified in your environment configuration: '$firebaseSdkPath'",
                e
            )
        }
    }
}