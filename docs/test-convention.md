# Test Convention

## 기본 원칙

- 전체 커버리지 목표는 없다. **락/동시성, 돈(보내기 개수 등), 보안처럼 조용히 틀어지면 피해가 큰 로직에만** 테스트를 둔다.
- 단순 getter, 빌더, 매핑처럼 코드를 보면 바로 맞는지 알 수 있는 곳에는 테스트를 만들지 않는다.

## 네이밍

- 클래스: `{대상클래스}Test` (`Tests` 아님)
- 메서드: 영어 camelCase로 뭘 확인하는지만 짧게. 한글 설명은 `@DisplayName`에 쓴다.

```java
@Test
@DisplayName("만료 시간이 지난 토큰은 EXPIRED_TOKEN")
void rejectExpiredToken() { ... }
```

## 구조

- Given/When/Then 같은 주석은 달지 않는다. 빈 줄 하나로 준비 → 실행+검증을 나눈다.
- 단언은 AssertJ(`assertThat`, `assertThatThrownBy`)만 쓴다(`spring-boot-starter-test`에 포함되어 있음).

## 서비스 레이어 (여러 리포지토리·외부 클라이언트에 의존하는 클래스)

- 리포지토리·외부 클라이언트는 Mockito로 전부 mock한다. `@SpringBootTest`/H2는 쓰지 않는다 — 빠르고, 리포지토리 구현이 바뀌어도 테스트가 안 흔들린다.
- 생성자 주입(`@RequiredArgsConstructor`) 그대로 각 의존성을 `@Mock`으로 선언하고, `new XxxService(mock1, mock2, ...)`로 직접 만든다.
- `TransactionTemplate`을 쓰는 서비스는 `execute()`가 넘겨준 콜백을 실제로 실행하도록 스텁해야 로직이 돈다.

```java
when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
  TransactionCallback<?> callback = invocation.getArgument(0);
  return callback.doInTransaction(null);
});
```

## 시간이 들어가는 로직

- `Clock`을 주입받는 클래스는 `Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneId.of("Asia/Seoul"))`처럼 고정해서 테스트한다. 시스템 시계를 테스트에서 그대로 쓰지 않는다.

## 에러 검증

- `BusinessException`을 던지는 코드는 어떤 에러코드인지까지 확인한다.

```java
assertThatThrownBy(() -> service.doSomething())
    .isInstanceOf(BusinessException.class)
    .extracting("errorCode")
    .isEqualTo(XxxErrorCode.YYY);
```

## 동시성 테스트 (예외적으로 통합 테스트 허용)

- 락·중복 방지처럼 "동시에 와도 한 번만 반영된다"를 확인해야 하는 로직은 Mockito mock 레벨에서 못 잡는다. 스레드 두 개를 직접 띄워 동시에 호출하고, 최종 결과(예: `daily_seq` 중복 없이 생성됨)로 검증한다.
- 이런 경우에만 `@SpringBootTest` + `@ActiveProfiles("test")` + H2(`application-test.yml`)를 쓴다.

## 통합/컨텍스트 테스트

- `@SpringBootTest`는 앱 전체가 뜨는지 확인하는 용도(`HanolApplicationTests`)와 위 동시성 테스트 용도로만 쓴다. 평범한 서비스 로직 테스트에는 쓰지 않는다.
- 테스트 DB는 H2(MySQL 호환 모드)를 쓰고 Flyway는 꺼져 있다(`ddl-auto: create-drop`). 즉 **마이그레이션 SQL 자체는 테스트로 검증되지 않는다** — 엔티티와 Flyway SQL이 어긋나면 로컬/배포에서만 터진다는 점을 알고 있어야 한다(`docs/flyway.md` 참고).
