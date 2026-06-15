@echo off

echo [1/4] .env 파일 확인 중...
if not exist .env (
    echo .env 파일이 없습니다. .env.example 을 복사하여 .env 를 생성하세요.
    pause
    exit /b 1
)

echo [2/4] config\application.properties 파일 확인 중...
if not exist config\application.properties (
    echo config\application.properties 파일이 없습니다. config\application.properties.example 을 참고하여 생성하세요.
    pause
    exit /b 1
)

echo [3/4] 기존 컨테이너 정리 중...
docker compose down --remove-orphans

echo [4/4] 빌드 및 배포 시작...
docker compose up --build -d

if %ERRORLEVEL% neq 0 (
    echo 배포 실패. 로그를 확인하세요: docker compose logs -f
    pause
    exit /b 1
)

echo.
echo ============================================
echo  배포 완료! http://localhost:8080
echo  Swagger UI: http://localhost:8080/swagger-ui/index.html
echo  로그 확인: docker compose logs -f app
echo ============================================
pause
