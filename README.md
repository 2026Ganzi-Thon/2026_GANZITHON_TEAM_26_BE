# 2026_GANZITHON_TEAM_26_BE

> **한 올** — 양이 털을 조금씩 내어 온기를 나누듯, 서로 건넨 작은 응원이 모여 하나의 담요가 되는 서비스

2026 간지톤 26팀 백엔드 레포지토리입니다.

## 기술 스택

Java 21 · Spring Boot 4.1 · Spring Data JPA · QueryDSL · MySQL 8 · Flyway · Redis · Spring Security · JWT · Swagger(springdoc) · Docker

## 처음 실행하기

### 1. 준비물
- JDK 21 (IntelliJ: `File > Project Structure > SDK` 에서 21 선택)
- Docker Desktop

### 2. 로컬 DB(MySQL)·Redis 실행
```bash
docker compose -f docker-compose.local.yml up -d
```
> 내 컴퓨터에 MySQL 이 이미 3306 포트로 켜져 있으면 충돌합니다. 기존 MySQL 을 끄거나 `.env` 에서 포트를 바꿔주세요.

### 3. (선택) 환경변수 파일 만들기
```bash
cp .env.example .env
```
로컬은 기본값이 있어서 `.env` 를 수정하지 않아도 실행됩니다. 메일·AI API 를 테스트할 때만 해당 값을 채워주세요.

### 4. 서버 실행
```bash
./gradlew bootRun
```
또는 IntelliJ 에서 `HanolApplication` 실행 (프로필을 지정하지 않으면 `local` 로 실행됩니다)

### 5. 확인
- 헬스체크: http://localhost:8080/actuator/health → `{"status":"UP"}`
- Swagger UI: http://localhost:8080/swagger-ui.html

## 프로필

| 프로필 | 용도 | 설정 파일 |
| --- | --- | --- |
| `local` (기본) | 내 컴퓨터 | `application-local.yml` + `.env` |
| `dev` | 개발 서버 | `application-dev.yml` + 서버 환경변수 |
| `test` | 테스트 (H2 인메모리 DB) | `src/test/resources/application-test.yml` |

## 환경변수

전체 목록은 [`.env.example`](.env.example) 을 참고하세요. 서버(dev)에는 아래 값이 반드시 필요합니다.

| 이름 | 설명 |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 정보 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 접속 정보 |
| `JWT_SECRET` | JWT 서명 키 (`openssl rand -base64 32`) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | 인증 메일 발송 계정 (Gmail 은 앱 비밀번호) |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` | 한 올 메시지 검수용 AI API |
| `CORS_ALLOWED_ORIGINS` | 브라우저 접근 허용 주소 (기본 `*`) |

## Docker

```bash
docker build -t hanol-server .
docker run -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=dev hanol-server
```
이미지 안에서 빌드까지 하므로 서버에 JDK 가 없어도 됩니다. 시간대는 `Asia/Seoul` 로 고정되어 있습니다.

## 협업 규칙

브랜치 전략, 커밋 메시지 규칙, 패키지 구조, 코딩 컨벤션은 [`CLAUDE.md`](CLAUDE.md) 에 정리되어 있습니다.

- 브랜치: `main`(배포) ← `dev`(개발) ← `feat/#이슈번호-설명`
- 커밋: `feat(cheer): 한 올 보내기 API 구현 (#12)`
- 코드 스타일: [Google Java Format](https://github.com/google/google-java-format) (Spotless + Checkstyle)

### Git Hooks
처음 한 번 `./gradlew build` (또는 IntelliJ 에서 실행)하면 `.githooks` 폴더가 자동으로 Git Hooks 로 등록됩니다.

| Hook | 하는 일 |
| --- | --- |
| `pre-commit` | 코드 포맷 검사 (`spotlessCheck`). 실패하면 `./gradlew spotlessApply` 실행 후 다시 커밋 |
| `commit-msg` | 커밋 메시지 형식 검사 |
| `pre-push` | 코드 스타일 검사 (`checkstyleMain`, `checkstyleTest`) |

수동 등록: `git config core.hooksPath .githooks`

> 훅은 검사만 하고 코드를 고치지 않습니다. 포맷 오류가 나면 `./gradlew spotlessApply` 를 실행해서 자동 정리한 뒤 다시 커밋해 주세요.
> `main` 직접 푸시는 훅이 막지 않으니 주의해 주세요.
