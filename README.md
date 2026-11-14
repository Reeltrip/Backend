# Reeltrip / Wayvi Backend

Java 21 · Spring Boot 3 · MySQL 8이에요. 작업 규칙은 [AGENTS.md](AGENTS.md), 최신 결정은 [docs/decisions.md](docs/decisions.md)를 따라요.

## 로컬

1. Java 21과 MySQL을 준비하고 빈 개발 DB에 [schema.sql](docs/schema.sql)을 수동 적용해요.
2. application-local.example.yml을 application-local.yml로 복사하고 DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET을 셸 환경 변수로 설정해요. 비밀 파일은 커밋하지 않아요.
3. `./gradlew bootRun`을 실행해요. Windows는 `gradlew.bat`를 사용해요. `.env`는 Spring이 자동으로 읽지 않으며 컨테이너의 `--env-file` 용도로 사용할 수 있어요.
4. `./gradlew test verifyMockIsolation`로 검증해요. 기본 테스트는 H2·고정 응답이며 실제 MySQL 선택 테스트는 각 문서의 일회용 DB 변수를 설정할 때만 실행해요.

local의 Gemini 키가 없으면 기존 FakeLlmClient가 선택될 수 있어요. prod는 가짜 추출·개발 로그인을 사용하지 않아요. FE 전용 가짜 API는 `./gradlew bootMockRun`으로 별도 실행하며 운영 JAR에 포함하지 않아요.

## 컨테이너

`docker build -t reeltrip-backend:local .`로 빌드해요. 빌드 단계에서 테스트와 mock 격리를 확인하고 런타임에는 운영 JAR만 복사해요. 비밀 값·로컬 설정·인증 파일은 .dockerignore로 제외해요. 런타임은 Java 21, prod, UID 10001이에요.

`docker run --rm -p 8080:8080 --env-file .env reeltrip-backend:local`로 실행하고 `/actuator/health`를 확인해요. .env에는 외부에서 접속 가능한 개발 DB와 prod 필수 환경 값을 준비해야 해요. PORT로 서버 포트를 바꿀 수 있어요.

Docker Hub CDN에 접속하지 못하는 환경은 `--build-arg BUILD_IMAGE=mirror.gcr.io/library/eclipse-temurin:21-jdk-jammy --build-arg RUNTIME_IMAGE=mirror.gcr.io/library/eclipse-temurin:21-jre-jammy`로 Google의 공개 이미지 캐시를 사용할 수 있어요.

PR CI는 Java 21·H2 테스트·운영 JAR·Docker 빌드를 실행해요. 외부 API 키·GCP 인증은 필요하지 않아요. 실제 배포는 별도 설정·승인 후 진행해요.
