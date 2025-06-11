package com.company.rest.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource // ClassPathResource를 사용하도록 변경
import java.io.IOException

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    // application.properties를 통해 주입될 키 파일의 '클래스패스 상의 경로'
    @Value("\${google.fcm.key}") // 프로퍼티 이름을 좀 더 명확하게 변경 (예: key-path)
    private lateinit var firebaseSdkKeyPath: String

    @PostConstruct
    fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                // ClassPathResource를 사용하여 resources 폴더 밑의 파일을 찾습니다.
                val serviceAccountResource = ClassPathResource(firebaseSdkKeyPath)

                if (!serviceAccountResource.exists()) {
                    throw IOException("Firebase key file not found at classpath: $firebaseSdkKeyPath")
                }

                // 리소스의 InputStream을 얻어옵니다.
                val serviceAccount = serviceAccountResource.inputStream

                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK initialized successfully with key from classpath: {}", firebaseSdkKeyPath)
            } else {
                logger.info("Firebase Admin SDK already initialized.")
            }
        } catch (e: IOException) {
            logger.error(
                "Error initializing Firebase Admin SDK with key from classpath '{}': {}",
                firebaseSdkKeyPath,
                e.message,
                e
            )
            // 에러 메시지도 클래스패스 기준으로 수정
            throw RuntimeException(
                "Failed to initialize Firebase Admin SDK. Please check the service account key file path in your application properties: '$firebaseSdkKeyPath'",
                e
            )
        }
    }
}