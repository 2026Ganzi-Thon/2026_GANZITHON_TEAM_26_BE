# 구성

| 파일 | 역할 |
| --- | --- |
| `src/main/resources/db/migration/` | 마이그레이션 SQL 스크립트 보관 |
| `src/main/resources/application.yml` | Flyway 활성화 및 스크립트 경로 지정 |
| `flyway_schema_history` (DB 테이블) | 적용된 버전과 체크섬 기록. Flyway가 자동 생성 |

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## 동작 순서

애플리케이션 기동 시 **Hibernate보다 먼저** 실행된다.

1. DataSource 준비
2. `classpath:db/migration` 스캔 → 파일명에서 버전 추출
3. `flyway_schema_history`와 대조 → 미적용 버전만 버전 순으로 실행
4. 실행 결과를 체크섬과 함께 `flyway_schema_history`에 기록
5. Hibernate 기동 → `ddl-auto: validate`로 엔티티와 실제 스키마 대조

Flyway가 스키마를 만들고 Hibernate가 검증하는 구조다. 이미 적용된 버전은 재기동해도 다시 실행되지 않는다.

---

## 파일 규칙

```
V{버전}__{설명}.sql
```

예시:

```
V1__init.sql
V2__add_member_table.sql
V3__alter_performance_add_venue.sql
```

- 언더스코어는 **두 개**다. 하나면 Flyway가 인식하지 못한다.
- 버전은 정수로 1씩 올린다.
- 설명은 snake_case로 무엇을 하는지 드러나게 쓴다.
- 파일 하나에 하나의 논리적 변경만 담는다.

---

## 스크립트 작성

Flyway는 SQL을 생성해주지 않는다. 직접 작성한다.

손으로 쓴 DDL이 Hibernate가 기대하는 형태와 어긋나면 `validate`에서 기동이 실패하므로, Hibernate에게 DDL을 뽑게 한 뒤 옮기는 방식을 권장한다.

로컬에 아래 설정을 임시로 추가하고 한 번 기동한다.

```yaml
spring:
  jpa:
    properties:
      jakarta:
        persistence:
          schema-generation:
            scripts:
              action: create
              create-target: build/schema.sql
```

`build/schema.sql`에 생성된 DDL에서 필요한 부분만 잘라 마이그레이션 파일로 옮긴다. **작업이 끝나면 설정을 반드시 제거한다.** 

---

## 규칙

- **이미 배포되어 실행된 파일은 수정하지 않는다.**
- 엔티티를 추가하거나 컬럼을 변경하면 **같은 PR에 마이그레이션 파일을 포함한다.** 엔티티만 바뀌면 배포 시 `validate`가 실패한다.
---

