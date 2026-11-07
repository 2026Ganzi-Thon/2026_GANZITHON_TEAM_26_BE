# 구성

| 파일 | 역할 |
| --- | --- |
| `global/filter/MdcFilter.java` | 요청 단위 컨텍스트를 MDC에 적재 |
| `src/main/resources/application.yml` | MDC 값을 로그 패턴에 주입 |
| `global/exception/GlobalExceptionHandler.java` | 예외 로그 형식 |

---

## 예외 로그

### 로그 레벨

- **TRACE**: 가장 상세한 정보로, 디버그 단계보다 더 세부적인 추적용
- **DEBUG**: 개발 중 디버깅을 목적으로 변수 값이나 흐름을 확인할 때 사용
- **INFO**: 애플리케이션의 일반적인 운영 정보 및 상태 변경 메시지
- **WARN**: 처리는 되었으나 향후 시스템 에러의 원인이 될 수 있는 잠재적 경고 상황(4xx)
- **ERROR**: 요청을 처리하는 중 발생한 심각한 오류나 예외 상황(5xx)

```
[<상태코드>] <에러코드명> - <메시지>
```

---

### MDC

`MdcFilter`가 요청마다 아래 값을 넣고, `finally`에서 `MDC.clear()`로 정리한다.

| 키 | 값 |
| --- | --- |
| `requestId` | UUID 앞 8자. 요청 1건을 식별한다 |
| `method` | HTTP 메서드 |
| `uri` | 요청 경로 |

`@Order(Ordered.HIGHEST_PRECEDENCE)`로 등록해 다른 필터보다 먼저 실행된다.

```yaml
logging:
  pattern:
    level: "%5p [%X{requestId:-}] %X{method:-} %X{uri:-}"
```

Spring Boot 기본 패턴을 유지한 채 level 필드만 확장하는 방식이다.

---

## 규칙

- `method`, `uri`, `requestId`는 MDC가 붙이므로 **로그 메시지에 다시 넣지 않는다.** 예외 핸들러가 `HttpServletRequest`를 파라미터로 받을 필요도 없다.
- 예외 상세(`e.getMessage()`)는 **로그에만** 남기고 클라이언트 응답에는 `ErrorCode`의 일반 메시지를 내보낸다.
- 핸들러를 추가할 때도 위 형식을 그대로 따른다.

---

## 주의

- **비동기에서는 MDC가 전파되지 않는다.** `@Async`나 별도 스레드로 넘어가면 값이 비어버린다. 도입 시 `TaskDecorator`로 복사해야 한다.
