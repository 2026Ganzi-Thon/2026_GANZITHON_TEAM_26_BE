# CLAUDE.md

이 파일은 Claude Code 등 AI 도구와 팀원이 이 레포에서 작업할 때 따르는 규칙이다.
새 기능을 만들 때는 이 문서의 구조와 컨벤션을 그대로 따른다.

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

## 패키지 구조

```
com.hanol
├── domain                 # 기능(도메인)별 패키지 - 기능 ID 대분류 기준
│   ├── auth               # USR  회원가입, 이메일 인증, 로그인/로그아웃, 토큰 재발급
│   ├── user               # USR  회원 정보, 마이 탭
│   ├── mood               # CRD  마음 고르기(감정 카드), 사연, 기록 캘린더
│   ├── cheer              # BLK  한 올 보내기(AI 검수, 대기열), 받기, 신고
│   ├── blanket            # BLK  담요(30칸), 지난 담요
│   ├── interaction        # ITR  온기 지도 인원, 함께 쉬는 방
│   └── alert              # ALRT 도움 요청(위기 안내)
├── global                 # 여러 도메인이 함께 쓰는 공통 코드
│   ├── common             # BaseTimeEntity 등
│   ├── config             # 설정(JPA, QueryDSL, Swagger, Clock, CORS)
│   ├── exception          # ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── response           # ApiResponse (공통 응답)
│   └── security           # Security 설정, JWT
└── infra                  # 외부 서비스 연동 (AI API, 메일 등)
```

각 도메인 패키지 안은 아래처럼 나눈다.

```
domain/cheer
├── controller   # CheerController
├── service      # CheerService
├── repository   # CheerRepository (+ CheerQueryRepository: QueryDSL)
├── entity       # Cheer, CheerStatus(enum)
├── dto
│   ├── request  # CheerSendRequest
│   └── response # CheerReceiveResponse
└── exception    # CheerErrorCode
```

## 코딩 규칙

### API
- URL: `/api/v1/{복수형 리소스}` 예) `POST /api/v1/cheers`, `GET /api/v1/moods?date=2026-11-04`
- 응답은 항상 `ApiResponse` 로 감싼다.
  - 성공: `ApiResponse.ok(data)` / 데이터 없음: `ApiResponse.ok()`
  - 실패: 직접 만들지 말고 `throw new BusinessException(XxxErrorCode.YYY)` → `GlobalExceptionHandler` 가 처리
- 로그인 사용자 id: 컨트롤러 파라미터에 `@AuthenticationPrincipal Long userId`
- 로그인 없이 열어야 하는 API 는 `SecurityConfig.PUBLIC_URLS` 에 추가한다.
- 요청 DTO 는 `record` + Bean Validation(`@NotBlank`, `@Size(max = 50)` 등), 컨트롤러에서 `@Valid`
- API 를 추가/변경하면 `src/main/resources/static/docs/openapi.yaml` 명세와 맞춘다. 명세의 원본은 이 YAML 이다.

### 에러 코드
- 도메인마다 `XxxErrorCode` enum 이 `ErrorCode` 를 구현한다. (예시: `global/security/AuthErrorCode`)
- 코드 형식: `{대분류}{HTTP상태}_{번호}` 예) `BLK400_1`, `USR404_1`
- 메시지는 사용자에게 그대로 보여줄 수 있는 한국어 문장으로 쓴다.

### 엔티티 / DB
- 테이블은 **Flyway SQL 로만** 만든다. (`ddl-auto: validate`)
  - 위치: `src/main/resources/db/migration`
  - 파일명: `V{번호}__{설명}.sql` 예) `V1__create_users.sql`, `V2__create_cheers.sql`
  - **이미 머지된 SQL 파일은 절대 수정하지 않는다.** 바꿀 게 있으면 새 버전 파일을 추가한다.
- 테이블명은 복수형 snake_case(`users`, `cheers`), 컬럼은 snake_case
- 엔티티는 `BaseTimeEntity` 를 상속해 `created_at`, `updated_at` 을 자동 기록한다.
- 엔티티에는 `@Setter` 를 쓰지 않는다. `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder`(생성자) + 의미 있는 메서드로 상태를 바꾼다.
- 연관관계는 기본 `FetchType.LAZY`. 복잡한 조회는 QueryDSL(`XxxQueryRepository`)로 작성한다.
- enum 컬럼은 `@Enumerated(EnumType.STRING)`

### 시간
- 서비스 기준 시간은 **한국 시간(Asia/Seoul)**, '하루'는 **자정(00시)** 에 바뀐다.
- 현재 시각이 필요하면 `Clock` 을 주입받아 `LocalDateTime.now(clock)` / `LocalDate.now(clock)` 를 쓴다. (테스트에서 시간 고정 가능)

### Redis 사용처 (만료 시간이 필요한 임시 데이터)
- 이메일 인증번호 (예: 5분 만료)
- 로그아웃한 Access 토큰 블랙리스트 (`JwtProvider.getRemainingValidity` 만큼 만료), Refresh 토큰
- 한 올 보내기 쿨타임, 함께 쉬는 방 현재 인원 등

키 이름 형식: `{도메인}:{용도}:{id}` 예) `auth:email-code:user@example.com`, `cheer:cooldown:12`

### 비밀값
- 비밀번호, API 키, JWT 키는 **코드와 yml 에 직접 쓰지 않는다.** `${환경변수}` 로 받고 로컬은 `.env` 에 적는다.
- 새 환경변수를 추가하면 `.env.example` 에도 (값 없이) 추가하고 PR 에 적는다.

### 코드 스타일
- Google Java Format: **스페이스 2칸 들여쓰기**, 한 줄 최대 100자, import 자동 정렬
- 커밋할 때 pre-commit 훅이 `spotlessCheck` 로 검사한다. 포맷 오류 시 `./gradlew spotlessApply` 실행
- 와일드카드 import(`*`) 금지

## Git 규칙

### 브랜치
- `main`: 배포 브랜치. 직접 푸시 금지(훅이 막지 않으므로 주의), `dev` 에서 PR 로만 머지
- `dev`: 개발 통합 브랜치. 기능 브랜치는 여기서 따고, 여기로 PR
- 기능 브랜치: `{타입}/#{이슈번호}-{간단한-설명}` 예) `feat/#12-cheer-send`, `fix/#20-map-count`

### 커밋 메시지 (commit-msg 훅이 검사)
```
<타입>(<범위>): <제목>
```
- 범위(scope)는 선택. 예) `feat(auth): 로그인 API 추가`, `fix: 온기 지도 인원 중복 집계 수정`
- 이슈 번호는 제목 끝에 붙여도 된다. 예) `feat(cheer): 한 올 보내기 API 구현 (#12)`
- 중요한 변경은 `feat!:` 또는 `feat(auth)!:` 처럼 `!` 로 표시
- Merge / Revert 커밋은 검사하지 않는다

| 타입 | 의미 |
| --- | --- |
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 코드 개선 |
| style | 포맷, 세미콜론 등 (로직 변화 없음) |
| docs | 문서 |
| test | 테스트 |
| chore | 빌드 설정, 패키지 등 기타 |
| ci | CI·CD |

위 8개 외의 타입(build, perf, rename, remove, hotfix 등)은 훅에서 막힌다.

### 작업 순서
1. 이슈 생성 (템플릿: Feature / Bug / Config)
2. `dev` 에서 브랜치 생성 → 작업 → 커밋
3. PR 생성 (base: `dev`) → 리뷰 → 머지

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
