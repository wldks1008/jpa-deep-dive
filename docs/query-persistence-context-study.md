# 쿼리와 영속성 컨텍스트 실험실

출발 질문은 두 가지다.

1. JPQL이 DB에서 실행된다면 Spring Data JPA 조회는 영속성 컨텍스트를 거치지 않는가?
2. `findById()`도 `findBy...`인데 왜 일반적인 메서드명 기반 조회와 다른가?

**DB에서 조건을 평가하는 일과, 결과 엔티티를 영속성 컨텍스트에서 관리하는 일은 별개다.** 각 테스트에서 SQL 순서, DB 값, 객체 참조, 관리 상태를 따로 확인한다.

## 실행과 읽는 순서

```bash
# 새 학습 테스트만 실행. MySQL/Docker 없이 H2에서 동작한다.
./gradlew test --tests '*QueryPersistenceContextStudyTest'

# 특정 실험만 실행
./gradlew test --tests '*QueryPersistenceContextStudyTest.13*'

# 전체 테스트
./gradlew test

# 이미 실행한 테스트를 재실행하며 표준 출력도 확인
./gradlew test --tests '*QueryPersistenceContextStudyTest' --rerun-tasks --info
```

Gradle HTML 보고서 `build/reports/tests/test/index.html`에서 테스트 클래스의 **Standard output**을 열면 실험 이름과 SQL 목록을 볼 수 있다. 보고서는 가장 최근 test 실행 결과다. IDE에서는 테스트 메서드 옆 실행 버튼을 사용해도 된다.

아래 파일을 순서대로 읽는다.

1. [QueryStudyMember.kt](../src/main/kotlin/com/example/jpadeepdive/learning/domain/QueryStudyMember.kt): `@Id memberId`와 일반 속성 `id`가 공존하는 모델
2. [QueryStudyMemberRepository.kt](../src/main/kotlin/com/example/jpadeepdive/learning/domain/QueryStudyMemberRepository.kt): 기본 메서드, 파생 쿼리, 명시적 JPQL, 벌크 DML
3. [QueryPersistenceContextStudyTest.kt](../src/test/kotlin/com/example/jpadeepdive/learning/QueryPersistenceContextStudyTest.kt): 01부터 16까지 독립적인 실험
4. [DataSourceProxyTestConfiguration.kt](../src/test/kotlin/com/example/jpadeepdive/support/DataSourceProxyTestConfiguration.kt): datasource-proxy 라이브러리를 테스트 DataSource에 연결하는 설정

각 테스트의 assertion을 먼저 가리고 **SQL 순서 → 반환 결과 → 같은 객체인지**를 예상한 후 실행해 보자. 번호는 학습 순서일 뿐 실행 순서에 의존하지 않는다.

## 실험 조건

- 프로젝트 버전: Spring Boot 3.5.0, Spring Data JPA 3.5.0, Hibernate ORM 6.6.15.Final, Jakarta Persistence 3.1.
- `@DataJpaTest`가 테스트마다 트랜잭션을 시작하고 종료 시 롤백한다. 각 테스트 내부 호출들은 같은 영속성 컨텍스트를 사용한다.
- H2에서 실행한다. 학습용 엔티티와 Repository는 `src/main`의 기본 스캔 범위에 있으므로 별도 `@EntityScan`, `@EnableJpaRepositories`가 필요 없다. 이 테스트는 Flyway를 비활성화하고 Hibernate로 스키마를 생성한다. 일반 애플리케이션과 기존 테스트의 `ddl-auto: validate`를 위해 `V3__create_query_study_tables.sql`도 제공한다.
- 준비 단계에서 `memberId=1, id=101, name="kim"` 한 행을 저장하고 `flush → clear`한다. 준비 SQL은 기록에서 제거한다.
- 2차 캐시와 쿼리 캐시를 끄고, 연관관계·페이징·잠금 없이 비교한다. 정확한 SQL 횟수는 이 조건에서의 관찰값이다.
- 직접 할당한 식별자와 `em.persist()`를 사용한다. `IDENTITY`는 식별자를 얻으려고 INSERT를 먼저 실행할 수 있어 쓰기 지연 실험에 적합하지 않다. 직접 할당 ID를 `repository.save()`에 전달할 때 생길 수 있는 신규 판정/merge 문제도 실험에서 제외한다.
- `datasource-proxy:1.11.0`이 DataSource를 감싸 실제 JDBC 실행을 관찰한다. Hibernate SQL과 `JdbcTemplate`의 DB 확인용 SELECT를 모두 포함한다. 준비 이후의 횟수는 라이브러리의 `QueryCountHolder`, 순서는 Mockito에 전달된 실행 완료 이벤트로 검증한다.

## SQL 로그와 집계 방법

`DataSourceProxyTestConfiguration`은 엔티티를 등록하는 설정이 아니라 테스트에서 라이브러리를 연결하는 설정이다. `@Import`한 테스트에만 적용되며, 직접 구현한 `StatementInspector`나 SQL 기록기는 사용하지 않는다.

- `.logQueryToSysOut()`: SQL, 바인딩 파라미터, 성공 여부를 실행 순서대로 콘솔에 출력한다. 중복 출력을 피하려고 이 테스트의 Hibernate SQL 로깅은 끈다.
- `.countQuery()`: `QueryCountHolder.getGrandTotal()`에서 전체 및 SELECT/INSERT/UPDATE/DELETE 횟수를 조회한다.
- `.listener(queryListener)`: datasource-proxy의 `QueryExecutionListener`를 Mockito mock으로 등록한다. Mockito가 보관한 `afterQuery` 호출을 읽어 SQL 순서를 검증한다. 실제 DataSource나 Repository는 mock이 아니다.

테스트마다 준비 SQL 이후 집계와 Mockito 호출 기록을 초기화하고, 테스트 끝에 누적 횟수와 번호가 붙은 SQL 목록을 출력한다. 라이브러리의 실시간 로그에는 스키마 생성과 준비 INSERT도 보이지만, `[실험]` 아래 요약에는 준비 이후의 SQL만 포함된다. 이 테스트는 순차 실행하고, 기본적으로 스레드 단위인 `QueryCountHolder`도 종료 시 비운다.

`assertSql("select", "update", "select")`는 순서뿐 아니라 라이브러리 집계가 전체 3회, SELECT 2회, UPDATE 1회인지도 검증한다. 이 실험에서는 JDBC 배치를 사용하지 않는다.

## 실험별 예상 결과

아래 SQL은 준비 단계를 제외한 **테스트 전체의 누적 순서**다. S=JPA SELECT, I=INSERT, U=UPDATE, J=`JdbcTemplate`의 확인용 SELECT. 라이브러리 집계에서는 S와 J 모두 SELECT로 센다.

| 번호 | 확인할 질문 | 예상 SQL | 핵심 관찰 |
| --- | --- | --- | --- |
| 01 | `findById` 두 번은? | S | 같은 객체, 두 번째 조회 SQL 없음 |
| 02 | `findByName` 두 번은? | S → S | SQL은 두 번, 객체는 동일 |
| 03 | PK 속성을 파생 쿼리로 조회하면? | S → S → S | `findByMemberId`도 쿼리를 실행 |
| 04 | `findById`와 `findMemberById`의 Id는 같은가? | S → S → S → S | 각각 `memberId`와 일반 `id`를 조회 |
| 05 | `@Query`로 같은 PK를 조회하면? | S → S | 기존 관리 객체 반환 |
| 06 | `findAll`과 Specification은? | S → S → S | 조건 평가 후 기존 객체 반환 |
| 07 | 기본 메서드는 모두 1차 캐시만 보는가? | S → S → S | `existsById`, 단순 PK `findAllById`도 SQL 실행 |
| 08 | INSERT 전 `findById`로 찾을 수 있는가? | J | JPA SQL은 없음. 관리 객체는 있지만 DB 행은 없음 |
| 09 | AUTO에서 새 객체를 이름으로 찾으면? | I → S → J | INSERT가 SELECT보다 먼저 실행 |
| 10 | 이름을 바꾼 뒤 변경된 이름으로 찾으면? | S → J → U → S → J | 변경 감지 후 조회, save 재호출 불필요 |
| 11 | 관련 없는 테이블을 조회해도 flush하는가? | S → J → I → S | notes 조회 때 미루고 members 조회 전에 INSERT |
| 12 | COMMIT에서 새 객체를 이름으로 찾으면? | S → I → S | 처음에는 못 찾고 명시적 flush 후 찾음 |
| 13 | 조건이 kim인데 반환 객체 이름은 changed일 수 있는가? | S → S → S → J | DB 조건 평가와 객체 상태의 차이 |
| 14 | `clear` 전후 객체는 같은가? | S → S | 같은 PK, 다른 객체, 이전 객체는 detached |
| 15 | `clear`가 변경을 저장해 주는가? | S → S | 미반영 변경이 영속성 컨텍스트에서 사라짐 |
| 16 | 벌크 UPDATE 후 SELECT가 refresh를 대신하는가? | S → U → J → S → S → S | 엔티티는 기존 값, 스칼라는 DB 값, refresh 후 동기화 |

## 메서드명과 실제 실행 경로

| 메서드 | 의미 | 이 프로젝트에서의 실행 경로 |
| --- | --- | --- |
| 상속받은 `findById(1)` | 엔티티의 식별자 `memberId=1` | `SimpleJpaRepository.findById` → `EntityManager.find` |
| `findByMemberId(1)` | 이름으로 지정한 속성 `memberId=1` | 메서드명 파싱 → Criteria 쿼리 |
| `findMemberById(101)` | 일반 속성 `id=101` | 메서드명 파싱 → Criteria 쿼리 |
| `findByName("kim")` | 속성 `name="kim"` | 메서드명 파싱 → Criteria 쿼리 |
| `findUsingJpql(1)` | `@Query`에 지정한 조건 | 선언한 JPQL 실행 |

Spring Data JPA의 파생 쿼리는 일반적으로 **JPQL 문자열을 조립하는 방식이 아니라 Criteria API를 사용**한다. 학습 설명에서 대응하는 JPQL을 보여 주는 것은 쿼리의 의미를 설명하는 것이다. IDE에서 `PartTreeJpaQuery`, `JpaQueryCreator`, `SimpleJpaRepository.findById`를 찾아 실행 경로를 비교할 수 있다.

기본 구현을 그대로 사용하는 경우를 실험한다. 커스텀 repository 구현이나 별도 쿼리 지정까지 포함해 메서드의 문자열만으로 실행 경로를 단정하지 말자.

## SQL이 나가도 같은 객체인 이유

01과 02의 차이는 쿼리 결과 캐시의 유무가 아니다. `findById`는 엔티티 타입과 식별자로 관리 객체를 바로 찾을 수 있다. 파생 쿼리는 DB에서 조건을 평가한 후, 조회한 식별자에 해당하는 기존 관리 객체가 있으면 그 객체를 반환한다.

13에서는 이 차이가 더 뚜렷하다. DB의 이름은 `kim`, 관리 객체의 이름은 `changed`다. Hibernate COMMIT 모드에서 `findByName("kim")`은 DB 행을 찾고 기존 관리 객체를 반환하므로 결과의 이름은 `changed`다. 반대로 `findByName("changed")`은 DB에 해당 값이 없어 결과가 없다.

동일성 보장은 **같은 영속성 컨텍스트에서 같은 엔티티 식별자**를 다룬다는 전제가 필요하다. 14처럼 clear하거나 영속성 컨텍스트가 달라지면 같은 PK여도 다른 객체일 수 있다. Kotlin에서 `==`는 equals 비교이고, 이 테스트는 참조 동일성을 검증하는 `assertSame`을 사용한다.

## flush를 해석할 때 주의할 점

JPA AUTO의 요구는 활성 트랜잭션에 참여한 영속성 컨텍스트의 변경 중 쿼리 결과에 영향을 줄 수 있는 변경을 쿼리가 볼 수 있게 하는 것이다. 구현체는 flush 또는 다른 방법으로 이를 보장할 수 있다. 따라서 **모든 JPQL 전에 무조건 물리적인 flush를 호출해야 한다는 뜻은 아니다.** 11의 테이블 겹침 판단은 Hibernate의 구체적인 최적화다.

COMMIT 모드에서 미반영 변경이 쿼리에 미치는 영향은 명세상 미지정이다. 12와 13의 결과는 Hibernate 6.6 실험 결과이며 다른 구현체까지 보장하지 않는다. COMMIT을 쿼리 전 flush가 절대 불가능한 모드로 해석해서도 안 된다.

`flush`는 DB에 변경 SQL을 보내는 것이고, `clear`는 관리 객체들을 분리하는 것이며, `refresh`는 DB 상태로 관리 객체를 갱신하는 것이다. `flush`만으로 트랜잭션이 커밋되지는 않는다. 이 테스트에서 JDBC가 변경을 읽는 이유는 **같은 트랜잭션의 연결**을 사용하기 때문이며, 다른 트랜잭션의 가시성을 검증한 것은 아니다.

16의 벌크 UPDATE는 일반 엔티티 SELECT와 구분해야 한다. 벌크 DML은 DB를 변경하고 기존 관리 객체를 자동 동기화하지 않는다. `@Modifying`만으로 clear되는 것도 아니다. 이 실험은 기본 설정에서의 차이를 확인하고, 명시적 `refresh`로 한 객체를 동기화한다. 미반영 변경이 있다면 refresh나 clear로 변경을 잃을 수 있으므로 호출 순서도 고려해야 한다.

## 직접 바꿔 볼 과제

- 03에서 `findByMemberId`를 `findById`로 바꾸면 SELECT가 몇 번 줄어드는가?
- 10에서 이름 변경을 없애면 UPDATE가 사라지는가?
- 13에서 COMMIT을 AUTO로 바꾸면 UPDATE 시점과 두 조회 결과는 어떻게 달라지는가?
- 15에서 `clear()` 바로 앞에 `flush()`를 추가하면 다시 조회한 이름은 무엇인가?
- 16에서 `refresh`를 `clear → findById`로 바꾸면 값과 객체 참조는 각각 어떻게 달라지는가?

## 공식 자료와 구현 확인

- [datasource-proxy 공식 가이드](https://jdbc-observations.github.io/datasource-proxy/docs/current/user-guide/): SQL/파라미터 로깅, QueryCountHolder 집계, QueryExecutionListener 실행 이벤트.
- [Spring Data: Reserved Method Names](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html#repositories.query-methods.query-creation.reserved-methods): 식별자 속성과 일반 id 속성 구분.
- [SimpleJpaRepository 3.5.0 소스](https://github.com/spring-projects/spring-data-jpa/blob/3.5.0/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java): findById, existsById, findAllById의 서로 다른 기본 구현.
- [PartTreeJpaQuery 3.5.0 소스](https://github.com/spring-projects/spring-data-jpa/blob/3.5.0/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/query/PartTreeJpaQuery.java): 파생 쿼리 생성 경로.
- [Hibernate ORM 6.6: Flushing](https://docs.hibernate.org/orm/6.6/userguide/html_single/#flushing): AUTO의 테이블 겹침과 IDENTITY 전략의 INSERT 시점.
- [Jakarta Persistence 3.1 명세](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1): §3.1 영속성 컨텍스트, §3.10.8 쿼리와 flush, §4.10 벌크 UPDATE/DELETE. 최신 초안 대신 프로젝트의 API 버전을 기준으로 읽는다.
