# Code Convention

## 패키지 구조
```
com.hanol
├── domain                 # 기능(도메인)별 패키지
│   ├── auth               # 회원가입, 이메일 인증, 로그인/로그아웃, 토큰 재발급
│   ├── user               # 회원 정보, 마이 탭
│   ├── mood               # 마음 고르기(감정 카드), 사연, 기록 캘린더
│   ├── cheer              # 한 올 보내기/받기, 신고
│   ├── blanket            # 담요(30칸), 지난 담요
│   ├── interaction        # 온기 지도 인원, 함께 쉬는 방
│   └── alert              # 도움 요청(위기 안내)
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

### 규칙
- 공통 인프라는 `global` 패키지에 모은다
- 도메인별로 `controller` / `service` / `repository` / `entity` / `dto` / `exception` 하위 패키지를 구성한다
- 도메인 간 의존은 허용하되, `global` → 도메인 방향 의존은 금지한다

## DTO
- Request / Response 분리, `record` 사용
- 변환: Response 내부 `from()` 정적 팩토리 메서드
- 입력값 검증(`@NotBlank`, `@NotNull`, `@Pattern`, `@Email`, `@Size` 등)은 Request DTO에서 수행, 컨트롤러에서 `@Valid`

## Entity
- 모든 엔티티는 `BaseTimeEntity`를 상속해 `created_at`, `updated_at`을 자동 기록한다
- `@Table(name = "...")` 항상 명시, **복수형** snake_case (`users`, `cheers`)
- 기본 생성자(JPA용): `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- 빌더: `@Builder`(생성자) + 의미 있는 메서드로 상태를 바꾼다. `@Setter` 금지
- UniqueConstraint 이름: `uk_{테이블명}_{컬럼명들}`
- 각 필드마다 `@Column()` 명시, camelCase 필드는 `name = "..."` 추가
- enum 컬럼은 `@Enumerated(EnumType.STRING)`
- 연관관계는 기본 `FetchType.LAZY`. 복잡한 조회는 QueryDSL(`XxxQueryRepository`)로 작성한다

## Transaction
- 서비스 클래스에 `@Transactional(readOnly = true)`를 선언하고, 쓰기 메소드에만 `@Transactional`을 개별로 붙여 덮어쓴다
- `org.springframework.transaction.annotation.Transactional`을 사용한다
- Redis, Mail 등 JPA 외 작업만 수행하는 메소드에는 JPA 트랜잭션을 붙이지 않는다

## Exception
- 에러코드는 인터페이스 + 도메인별 enum으로 분리한다
```
global/exception/
├── ErrorCode.java            # 인터페이스 (getStatus, getMessage, name)
├── GlobalErrorCode.java      # 공통 에러코드 enum
├── BusinessException.java    # ErrorCode를 담는 런타임 예외
└── GlobalExceptionHandler.java

{domain}/exception/
└── {Domain}ErrorCode.java    # 도메인 전용 에러코드 enum
```
- 코드 형식: `{HTTPstatus}_{Name}` 예) `401_UNAUTHORIZED`, `500_INTERNAL_SERVER_ERROR`
- 메시지는 사용자에게 그대로 보여줄 수 있는 한국어 문장으로 쓴다

## Logging
### 별도의 MDC 로그가 있다.
- 로그 패턴: `%5p [%X{requestId:-}] %X{method:-} %X{uri:-}` (`MdcFilter`가 요청마다 MDC에 적재)
- `requestId`, `method`, `uri`는 MDC에서 자동 출력되므로 로그 메시지에 포함하지 않는다
- 상세 규칙은 [logging.md](logging.md) 참조

### 서비스 로그
- 각 서비스 클래스에 `@Slf4j`를 선언한다
- 핵심 동작마다 데이터 확인용 로그를 1줄 남긴다
- 로그 메시지에는 **동작 설명 + 해당 동작의 핵심 식별값**만 남긴다
- `BusinessException`을 던지기 직전에 `log.warn`을 찍지 않는다. `GlobalExceptionHandler`에서 일괄 로깅한다.
- 로그 레벨 기준:
    - `info`: 정상 흐름 (생성, 수정, 삭제 등 주요 동작)
    - `warn`: 비즈니스 예외 (잘못된 요청, 인증 실패 등) — `GlobalExceptionHandler`에서 일괄 로깅한다.
    - `error`: 시스템 오류 (외부 API 실패, 예상치 못한 예외)

## API
- URL: `/api/v1/{복수형 리소스}` 예) `POST /api/v1/cheers`, `GET /api/v1/moods?date=2026-11-04`
- 응답은 항상 `ApiResponse`로 감싼다
  - 성공: `ApiResponse.ok(data)` / 데이터 없음: `ApiResponse.ok()`
  - 실패: 직접 만들지 말고 `throw new BusinessException(XxxErrorCode.YYY)` → `GlobalExceptionHandler`가 처리
- 로그인 사용자 id: 컨트롤러 파라미터에 `@AuthenticationPrincipal Long userId`
- 로그인 없이 열어야 하는 API는 `SecurityConfig.PUBLIC_URLS`에 추가한다
- 요청 DTO는 `record` + Bean Validation, 컨트롤러에서 `@Valid`
- API를 추가·변경하면 `src/main/resources/static/docs/openapi.yaml`과 맞춘다. 명세의 원본은 이 YAML이다.
- 성공 응답만 명시한다. 에러 응답(`4xx`, `5xx`)은 문서화하지 않는다

## 코드 스타일
- Google Java Format: 스페이스 2칸 들여쓰기, 한 줄 최대 100자, import 자동 정렬
- 커밋할 때 pre-commit 훅이 `spotlessCheck`로 검사한다. 포맷 오류 시 `./gradlew spotlessApply` 실행
- 와일드카드 import(`*`) 금지