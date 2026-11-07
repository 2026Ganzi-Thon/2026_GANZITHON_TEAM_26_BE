# CLAUDE.md

이 파일은 Claude Code 등 AI 도구와 팀원이 이 레포에서 작업할 때 따르는 규칙이다.
새 기능을 만들 때는 이 문서의 구조와 컨벤션을 그대로 따른다.

다음 문서를 반드시 참조한다.

코드 컨벤션: @docs/code-convention.md
커밋 규칙: @docs/commit.md
브랜치 규칙: @docs/branch.md
로깅/MDC 규칙: @docs/logging.md
DB 마이그레이션 규칙: @docs/flyway.md

PR 템플릿: @.github/pull_request_template.md
이슈 템플릿: @.github/ISSUE_TEMPLATE/bug.yml, @.github/ISSUE_TEMPLATE/feature.yml

## 프로젝트 개요

- 서비스: **한 올** — 20~30대 대학생·취준생·사회초년생이 감정 카드를 고르고, 같은 마음인 사람 수를 확인하고(온기 지도), 익명 응원(한 올)을 주고받으며 받은 응원이 담요로 쌓이는 정신 건강 케어 앱
- 대회: 2026 간지톤 26팀 / 이 레포는 **백엔드**
- 클라이언트: 안드로이드 앱 (서버와 JSON API 로 통신)
- 핵심 원칙: **완전 익명.** 다른 사용자에게 닉네임·번호·보낸 사람 정보를 절대 노출하지 않는다. 보낸 사람 정보는 신고 제재용으로만 쓴다.

## 기술 스택

| 구분 | 사용 |
| --- | --- |
| 언어/프레임워크 | Java 21, Spring Boot 4.1 (Spring Framework 7, Jackson 3) |
| 빌드 | Gradle (Groovy DSL) |
| DB | MySQL 8, Spring Data JPA, QueryDSL(OpenFeign 포크), Flyway |
| 캐시/임시 데이터 | Redis |
| 인증 | Spring Security + JWT (jjwt), Access/Refresh 토큰 |
| 문서 | springdoc-openapi (Swagger UI) + 설계용 `openapi.yaml` |
| 코드 스타일 | Google Java Format (Checkstyle + Spotless) |

> Spring Boot 4 / Jackson 3 주의: Jackson 패키지는 `tools.jackson.*` 이다. (`com.fasterxml.jackson.databind` 아님)
> 단, 어노테이션(`@JsonInclude` 등)은 기존처럼 `com.fasterxml.jackson.annotation.*` 이다.

## 시간

- 서비스 기준 시간은 **한국 시간(Asia/Seoul)**, '하루'는 **자정(00시)** 에 바뀐다.
- 현재 시각이 필요하면 `Clock` 을 주입받아 `LocalDateTime.now(clock)` / `LocalDate.now(clock)` 를 쓴다. (테스트에서 시간 고정 가능)

## Redis 사용처 (만료 시간이 필요한 임시 데이터)

- 이메일 인증번호 (예: 5분 만료)
- 로그아웃한 Access 토큰 블랙리스트 (`JwtProvider.getRemainingValidity` 만큼 만료), Refresh 토큰
- 한 올 보내기 쿨타임, 함께 쉬는 방 현재 인원 등

키 이름 형식: `{도메인}:{용도}:{id}` 예) `auth:email-code:user@example.com`, `cheer:cooldown:12`

## 비밀값

- 비밀번호, API 키, JWT 키는 **코드와 yml 에 직접 쓰지 않는다.** `${환경변수}` 로 받고 로컬은 `.env` 에 적는다.
- 새 환경변수를 추가하면 `.env.example` 에도 (값 없이) 추가하고 PR 에 적는다.

## 자주 쓰는 명령어

```bash
docker compose -f docker-compose.local.yml up -d   # 로컬 MySQL, Redis 실행
./gradlew bootRun                                   # 서버 실행 (local 프로필)
./gradlew test                                      # 테스트
./gradlew spotlessApply                             # 코드 포맷 자동 정리
./gradlew checkstyleMain                            # 코드 스타일 검사
./gradlew build                                     # 전체 빌드 (포맷·스타일 검사·테스트 포함)
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health