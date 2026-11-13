# USR-AUTH 기능 명세

## USR-AUTH-001 회원가입

**트리거**
'가입' 버튼 클릭

**사전조건**
비로그인 상태, 해당 이메일로 인증 완료 상태([[USR-AUTH-002]] 선행 필요)

**입력**

- `email` (string): 이메일 형식, 필수
- `password` (string): 8~20자, 필수
- `agreements` (array\<string\>): `TERMS_OF_SERVICE` / `PRIVACY_POLICY` / `USAGE_PRINCIPLES` 세 값 모두 포함, 필수

**처리 로직**

1. 이메일 형식 서버 재검증
2. 이메일 중복 여부 확인
3. Redis에서 이메일 인증 완료 상태 확인 (`auth:email-verified:{email}`)
4. `agreements` 배열에 필수 3개 값이 모두 포함됐는지 확인, 동의 시각·약관 버전 기록
5. 비밀번호 bcrypt 해시 후 저장, 가입일·온보딩 완료 여부(`false`) 저장. 이메일에 유니크 제약을 걸어, 2번에서 중복이 아니라고 판단된 후에도 동시 요청으로 같은 이메일이 거의 동시에 가입을 시도하면 하나만 저장에 성공하고 나머지는 저장 시점에 제약 위반으로 실패하게 한다.
6. Redis에서 인증 완료 상태 삭제

**출력**
HTTP 201, 응답 본문 없음 ([[USR-AUTH-003]] 로그인 화면 이동 안내)

**예외 케이스**

| Status Code | Status Name           | Description                                                                                  | 참고        |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------- | ----------- |
| 400         | BAD_REQUEST           | 이메일 형식 불일치                                                                           | -           |
| 400         | BAD_REQUEST           | 비밀번호 8자 미만 또는 20자 초과                                                             | -           |
| 400         | BAD_REQUEST           | 필수 동의 3개 중 미포함 항목 존재                                                            | 도메인 전용 |
| 400         | BAD_REQUEST           | 이메일 인증 미완료 (Redis 키 없음)                                                           | 도메인 전용 |
| 409         | CONFLICT              | 이미 가입된 이메일(중복 확인 시점 또는 동시 요청으로 저장 시점에 뒤늦게 걸린 경우 모두 포함) | 도메인 전용 |
| 500         | INTERNAL_SERVER_ERROR | 계정 저장 중 서버 오류                                                                       | -           |

## USR-AUTH-002 이메일 인증

**트리거**
[[USR-AUTH-001]] 회원가입 화면에서 '인증 요청' 클릭 (인증번호 발송) / 인증번호 입력 후 '확인' 클릭 (인증 확인)

**사전조건**
비로그인 상태

**입력**

- 인증번호 발송: `email` (string): 이메일 형식, 필수
- 인증번호 확인: `email` (string), `code` (string): 6자리 숫자, 필수

**처리 로직**
_인증번호 발송_

1. 이메일 형식 서버 재검증
2. 이미 가입된 이메일인지 확인
3. 6자리 숫자 인증번호 생성
4. Redis에 `auth:email-code:{email}` → `{code}` 로 5분 만료로 저장
5. 해당 이메일로 인증 메일 발송

_인증번호 확인_

1. 인증번호 형식 검증 (6자리 숫자)
2. Redis에서 `auth:email-code:{email}` 조회
3. 저장된 코드와 입력값 비교
4. 일치하면 인증 완료 처리: `auth:email-verified:{email}` 를 Redis에 10분 만료로 저장, 기존 코드 키 삭제

**출력**

- 발송: HTTP 200
- 확인: HTTP 200

**예외 케이스**

| Status Code | Status Name           | Description                                      | 참고        |
| ----------- | --------------------- | ------------------------------------------------ | ----------- |
| 400         | BAD_REQUEST           | 이메일 형식 불일치 (발송 요청)                   | -           |
| 409         | CONFLICT              | 이미 가입된 이메일로 인증 요청                   | 도메인 전용 |
| 429         | TOO_MANY_REQUESTS     | 5분 이내 재발송 시도 (Redis 키가 아직 살아 있음) | 도메인 전용 |
| 400         | BAD_REQUEST           | 인증번호 형식 불일치 (6자리 숫자가 아님)         | -           |
| 404         | NOT_FOUND             | 발급 이력 없이 (또는 만료 후) 인증번호 확인 요청 | 도메인 전용 |
| 400         | BAD_REQUEST           | 인증번호 불일치                                  | 도메인 전용 |
| 500         | INTERNAL_SERVER_ERROR | 메일 발송 실패                                   | -           |

## USR-AUTH-003 로그인

**트리거**
'로그인' 버튼 클릭

**사전조건**
비로그인 상태, 가입된 계정, 로그인 잠금 아님([[USR-AUTH-004]] 참고)

**입력**

- `email` (string): 필수
- `password` (string): 필수

**처리 로직**

1. 이메일로 계정 조회
2. 로그인 잠금 상태 확인 (연속 실패 횟수·해제 시각)
3. 비밀번호 일치 여부 확인
4. 실패 시 연속 실패 횟수 +1(동시에 여러 번 틀린 요청이 들어와도 최종 횟수가 어긋나지 않도록 원자적으로 증가), 5회째면 5분 잠금 처리 ([[USR-AUTH-004]])
5. 성공 시 연속 실패 횟수 0으로 초기화
6. Access Token(1시간 만료) · Refresh Token(7일 만료) 발급
7. Refresh Token을 Redis `auth:refresh:{userId}` 에 저장 (7일 만료)
8. 온보딩 완료 여부로 이동 화면 분기 ([[USR-AUTH-005]] 온보딩 / [[CRD-WRITE-001]] 마음 고르기 화면)

**출력**

```
{
  "accessToken":  string,
  "refreshToken": string,
  "isOnboardingCompleted": boolean
}
```

**예외 케이스**

| Status Code | Status Name           | Description                                               | 참고        |
| ----------- | --------------------- | --------------------------------------------------------- | ----------- |
| 401         | UNAUTHORIZED          | 존재하지 않는 이메일 또는 비밀번호 불일치 (구분하지 않음) | 도메인 전용 |
| 423         | LOCKED                | 로그인 잠금 중                                            | 도메인 전용 |
| 500         | INTERNAL_SERVER_ERROR | 토큰 발급 실패                                            | -           |

## USR-AUTH-004 로그인 잠금

**트리거**
같은 계정으로 로그인 5회째 연속 실패

**사전조건**
같은 계정으로 로그인 실패 4회 누적

**입력**
없음 ([[USR-AUTH-003]] 로그인 처리 내부에서 판단)

**처리 로직**

1. 연속 실패 횟수가 5회에 도달했는지 확인
2. 도달 시 현재 시각 기준 5분 뒤 해제 시각 저장, 계정 잠금 처리
3. 로그인 재시도 시 현재 시각과 해제 시각 비교
4. 해제 시각 이후면 잠금 해제, 연속 실패 횟수 초기화

**출력**
잠금 여부, 해제 시각

**예외 케이스**

| Status Code | Status Name | Description                  | 참고        |
| ----------- | ----------- | ---------------------------- | ----------- |
| 423         | LOCKED      | 해제 시각 이전 재로그인 시도 | 도메인 전용 |

## USR-AUTH-005 온보딩

**추후 구현 - P2**

**트리거**
첫 로그인 후 온보딩 화면 진입

**사전조건**
로그인 상태, 온보딩 미완료

**입력**

- `action` (string): `next` | `skip` | `complete`, 필수

**처리 로직**

1. 온보딩 3장 중 '다음' 또는 '건너뛰기' 선택 처리
2. 완료(3장 끝 또는 건너뛰기) 시 온보딩 완료 여부를 `true`로 저장

**출력**
온보딩 완료 여부, [[CRD-WRITE-001]] 마음 고르기 화면으로 이동 안내

**예외 케이스**

| Status Code | Status Name  | Description                                    |
| ----------- | ------------ | ---------------------------------------------- |
| 401         | UNAUTHORIZED | 로그인하지 않은 상태로 접근                    |
| 400         | BAD_REQUEST  | `action` 값이 `next`/`skip`/`complete` 외의 값 |
| 409         | CONFLICT     | 이미 온보딩 완료된 계정의 재요청               |

## USR-AUTH-006 토큰 재발급

**트리거**
클라이언트가 API 호출 중 Access Token 만료를 감지하고 재발급 요청

**사전조건**
유효한 Refresh Token 보유

**입력**

- `refreshToken` (string): 필수 (Authorization 헤더 또는 요청 본문)

**처리 로직**

1. Refresh Token 서명·만료 검증
2. Redis `auth:refresh:{userId}` 에 저장된 값과 일치 여부 확인 (탈취·로그아웃 여부 판별)
3. 새 Access Token(1시간 만료) 발급 (Refresh Token은 교체하지 않고 그대로 유지, Rotation 미적용)

**출력**

```
{
  "accessToken": string
}
```

**예외 케이스**

| Status Code | Status Name  | Description                                    |
| ----------- | ------------ | ---------------------------------------------- |
| 401         | UNAUTHORIZED | Refresh Token 없음                             |
| 401         | UNAUTHORIZED | Refresh Token 서명 불일치 또는 만료            |
| 401         | UNAUTHORIZED | Redis에 저장된 토큰과 불일치 (로그아웃된 토큰) |

## USR-AUTH-007 로그아웃

**트리거**
마이 탭에서 '로그아웃' 버튼 클릭 후 확인 창에서 '로그아웃' 선택

**사전조건**
로그인 상태 (유효한 Access Token 보유)

**입력**
없음 (Access Token은 Authorization 헤더로 전달)

**처리 로직**

1. Access Token의 남은 유효 시간만큼 Redis 블랙리스트에 등록 (`auth:blacklist:{accessToken}`)
2. Redis `auth:refresh:{userId}` 삭제

**출력**
로그아웃 처리 결과, [[USR-AUTH-003]] 로그인 화면으로 이동 안내

**예외 케이스**

| Status Code | Status Name  | Description                 |
| ----------- | ------------ | --------------------------- |
| 401         | UNAUTHORIZED | 로그인하지 않은 상태로 접근 |
