# 🧶 패치 (PATCH) — Backend

> "같은 마음인 사람들이 건넨 작은 응원 한 조각이 모여, 나를 덮어주는 담요가 됩니다."

**2026 간지톤 26팀 백엔드 레포지토리**입니다.

### 기능 흐름

> 작성 예정

<br>

## 팀원 소개 (Backend)

| 이름   | 소속       | 역할                       |
| ------ | ---------- | -------------------------- |
| 이현정 | 한국외대   | BE총괄, 개발보조, 서버세팅 |
| 이재현 | 한국항공대 | 개발총괄, AI API           |

<br>

## 기술 스택 (Tech Stack)

| 영역       | 기술                                       | 비고                                          |
| ---------- | ------------------------------------------ | --------------------------------------------- |
| Framework  | Spring Boot 4.1 (Java 21)                  | Spring Framework 7, Jackson 3                 |
| Data       | MySQL 8 · Spring Data JPA · QueryDSL       | 스키마는 Flyway 마이그레이션으로만 관리       |
| Cache      | Redis                                      | 이메일 인증번호, 토큰 블랙리스트·Refresh 토큰 |
| Auth       | Spring Security + JWT                      | Access/Refresh 토큰, 기기별 세션              |
| AI         | LLM API                                    | 패치·사연의 부적절/위기 표현 검수             |
| API Docs   | springdoc-openapi + `openapi.yaml`         | 설계 원본은 YAML                              |
| CI/CD      | GitHub Actions → GHCR → AWS EC2            | `main` 머지 시 Docker 이미지 빌드·배포        |
| Code Style | Google Java Format (Spotless + Checkstyle) | Git Hooks로 커밋·푸시 전 검사                 |

<br>

## 아키텍처 (System Architecture)

> 작성 예정

<br>

## 핵심 기능 및 구현 상태

✅ 완료 · 🚧 진행 중 · ⏳ 예정

| 도메인    | 기능 ID                     | 기능명                                   | 우선순위 | 상태 |
| --------- | --------------------------- | ---------------------------------------- | -------- | ---- |
| 회원      | USR-AUTH-001 ~ 003          | 회원가입 · 이메일 인증 · 로그인          | P0       | ✅   |
| 회원      | USR-AUTH-004                | 로그인 잠금                              | P0       | ⏳   |
| 회원      | USR-AUTH-006 ~ 007          | 토큰 재발급 · 로그아웃                   | P0       | ✅   |
| 마음 기록 | CRD-WRITE-002 ~ 003         | 사연 작성 · 마음 기록 저장               | P0       | ✅   |
| 마음 기록 | CRD-VIEW-001 ~ 003          | 기록 캘린더 · 그날 기록 목록 · 기록 상세 | P0       | ✅   |
| 마음 기록 | CRD-DEL-001                 | 기록 지우기                              | P0       | ✅   |
| 온기 지도 | ITR-MAP-001                 | 온기 지도 인원 표시                      | P0       | ⏳   |
| 패치      | BLK-SEND-001 ~ 002          | 보내기 가능 확인 · 패치 검사·발송        | P0       | 🚧   |
| 패치      | BLK-RCV-001 ~ 002           | 패치 받기 · 기본 위로 받기               | P0       | ⏳   |
| 패치      | BLK-RPT-001                 | 패치 신고                                | P0       | ⏳   |
| 담요      | BLK-STACK-001               | 담요 적립·완성                           | P0       | ⏳   |
| 담요      | BLK-VIEW-001 ~ 002          | 담요 조회 · 패치 카드 조회               | P0       | ⏳   |
| 위기 대응 | ALRT-DETECT-001             | 위기 표현 감지                           | P0       | 🚧   |
| 시스템    | SYS-STAT-001                | 하루 집계                                | P0       | 🚧   |
| 패치      | BLK-RPT-002                 | 보내기 제한                              | P1       | ⏳   |
| 온기 지도 | ITR-REST-001                | 함께 쉬는 방                             | P1       | ⏳   |
| 회원      | USR-AUTH-005 · USR-USER-001 | 온보딩 · 회원 탈퇴                       | P2       | ⏳   |
| 시스템    | SYS-DATA-001 ~ 002          | 지도 원본 집계 전환 · 신고 데이터 파기   | P2       | ⏳   |

> 서버 처리가 없는 프론트 전용 화면(감정 선택, 위기 안내, 고객센터 등)은 표에서 제외했습니다.
> 전체 기획 기능의 트리거·처리 로직·예외 케이스는 👉 **[기능 명세서 (docs/spec)](docs/spec)** 에서 확인할 수 있습니다.

<br>

## API 명세서 (API Documentation)

API 설계의 원본은 [`openapi.yaml`](src/main/resources/static/docs/openapi.yaml) 이며, 도메인별 경로는 [`static/docs/paths`](src/main/resources/static/docs/paths) 에 나뉘어 있습니다. 모든 응답은 공통 `ApiResponse` 형식(`code`, `message`, `data`)으로 내려갑니다.

- **Swagger UI (개발 서버):** https://nyam17.site/swagger-ui/index.html

<br>

## Git 브랜치 전략 (Git Branching Strategy)

- `main`: 배포 브랜치. 머지되면 GitHub Actions가 EC2에 자동 배포합니다.
- `feat/#{이슈번호}-{설명}`: 새 기능 (예: `feat/#14-mood`)
- `fix/#{이슈번호}-{설명}`: 버그 수정
- `refactor/` · `docs/` · `chore/` · `test/`: 리팩터링 · 문서 · 설정 · 테스트

커밋 메시지는 `<타입>(<스코프>): <제목>` 형식이며, `commit-msg` 훅이 검사합니다. 사용할 수 있는 타입은 `feat` `fix` `refactor` `style` `docs` `test` `chore` `ci` 8개입니다.

```
이슈 생성 -> dev에서 브랜치 생성 -> 커밋
-> CI check -> PR (base: main) -> 리뷰·머지
-> 자동 배포
```

> 자세한 규칙은 [브랜치 규칙](docs/branch.md) · [커밋 규칙](docs/commit.md) 을 참고하세요.

<br>

## 시작하기 (Getting Started)

JDK 21과 Docker Desktop이 필요합니다.

```bash
docker compose -f docker-compose.local.yml up -d   # 로컬 MySQL, Redis 실행
cp .env.example .env                               # JWT_SECRET, CHEER_SENDER_HMAC_SECRET 값 채우기(필수)
./gradlew bootRun                                  # 서버 실행 (기본 local 프로필)
```

- `JWT_SECRET`, `CHEER_SENDER_HMAC_SECRET`은 기본값이 없어 값을 안 채우면 기동이 실패합니다(`openssl rand -base64 32`로 생성). 메일·AI API 값은 그 기능을 테스트할 때만 필요합니다.
- 헬스체크: http://localhost:8080/actuator/health → `{"status":"UP"}`
- 처음 `./gradlew build` 를 실행하면 `.githooks` 가 Git Hooks로 자동 등록됩니다. 포맷 오류는 `./gradlew spotlessApply` 로 정리하세요.

<br>

## 문서 모음

| 문서                                                                                  | 내용                                                               |
| ------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| [CLAUDE.md](CLAUDE.md)                                                                | 프로젝트 개요, 패키지 구조, 코딩·API·DB 규칙 (AI 도구 & 팀원 공용) |
| [기능 명세서](docs/spec)                                                              | 기능 ID별 트리거, 처리 로직, 예외 케이스                           |
| [API 명세서](src/main/resources/static/docs/openapi.yaml)                             | OpenAPI 설계 원본                                                  |
| [코드 컨벤션](docs/code-convention.md)                                                | Google Java Format 기반 코드 스타일                                |
| [커밋 규칙](docs/commit.md)                                                           | 커밋 메시지 형식과 타입                                            |
| [브랜치 규칙](docs/branch.md)                                                         | 브랜치 이름과 작업 순서                                            |
| [로깅 규칙](docs/logging.md)                                                          | 로그 레벨, MDC 사용법                                              |
| [DB 마이그레이션 규칙](docs/flyway.md)                                                | Flyway 파일 작성·버전 관리                                         |
| [PR 템플릿](.github/pull_request_template.md) · [이슈 템플릿](.github/ISSUE_TEMPLATE) | 협업 양식                                                          |
| [환경변수 예시](.env.example)                                                         | 로컬·서버 실행에 필요한 환경변수 목록                              |
