# Phase 01: 프로젝트 기반 인프라 정비

- 상태: EXECUTED

## 목표

블로그와 뉴스레터 도메인을 구현하기 전에 Mongo Repository 탐색, Java 21 기반 CI, Spring Scheduling, 로컬 MongoDB/Redis, 테스트 환경변수 정책을 정리한다. 기존 exams 기능의 Bean 등록과 애플리케이션 기동을 보존하며, 이번 단계에서는 블로그·댓글·뉴스레터 제품 기능을 구현하지 않는다.

## 현재 코드 분석

### 빌드와 Java

- `build.gradle`은 Spring Boot 3.4.2와 Java toolchain 21을 사용한다.
- MongoDB와 Redis starter가 이미 있으므로 로컬 인프라 또는 Repository 탐색을 위해 의존성을 추가할 필요가 없다.
- Scheduling 활성화에 필요한 Spring context 기능도 기존 starter에 포함되어 있으므로 별도 Scheduling 의존성이 필요하지 않다.
- Gradle wrapper는 Git index에서 실행 가능 모드 `100755`이며 wrapper 설정은 Gradle 9.5.1을 가리킨다.

### Mongo Repository

- `TosunsaengApplication`의 `@EnableMongoRepositories`는 `web.tosunsaeng.domain.exams.domain.repository`만 탐색한다.
- 현재 이 패키지의 `AzureResultRepository`, `MockExamRepository`, `SpeechAceResultRepository`, `ExamResultRepository`, `QuestionRepository`는 `MongoRepository`를 확장하며 정상 후보가 된다.
- 같은 패키지의 `ExamRepository`는 Spring Data Repository를 확장하지 않는 빈 인터페이스이므로 Repository Bean 대상이 아니다.
- 현 설정을 유지한 채 blog 또는 newsletter Repository를 다른 도메인 패키지에 추가하면 Spring Data가 해당 인터페이스를 탐색하지 않아 Repository Bean 주입 시 애플리케이션 컨텍스트가 실패한다.

선택지 비교:

1. `@EnableMongoRepositories(basePackages = "web.tosunsaeng.domain")`로 확장
   - 기존 명시적 Mongo 설정을 유지하면서 모든 도메인 하위 Mongo Repository를 포함한다.
   - 변경이 패키지 문자열 하나로 제한되고 기존 exams Repository가 기존 탐색 범위의 부분집합으로 그대로 남는다.
   - 향후 도메인 밖에 Repository를 둘 경우 자동으로 포함되지 않으므로 패키지 규칙을 지켜야 한다.
2. `@EnableMongoRepositories`를 제거하고 Spring Boot 기본 탐색 사용
   - `@SpringBootApplication`의 기준 패키지인 `web.tosunsaeng` 아래를 기본 탐색하므로 현재 exams와 향후 blog/newsletter Repository가 등록된다.
   - 관례에 가깝고 설정은 줄지만 탐색 범위가 애플리케이션 전체로 넓어지며, 기존의 명시적 설정을 제거하는 의미 변화가 있다.

권고안은 1번이다. 기존 exams 동작을 보존하는 최소 변경이고, 블로그 MVP 도메인 패키지를 `web.tosunsaeng.domain` 아래에 둔다는 현재 구조를 명시적으로 강제한다. 구현 전에 사용자가 이 선택을 승인해야 한다.

### CI/CD

- `.github/workflows/deploy.yml`은 `main` push를 배포 트리거로 사용한다.
- 현재 첫 `Build and test` 단계가 checkout과 Java 설정보다 앞서 있어 runner에 저장소와 `gradlew`가 없는 상태에서 실패한다.
- `actions/setup-java`의 표시 이름과 주석은 JDK 17이지만 실제 `java-version`은 21이다. `build.gradle`과 `Dockerfile`도 Java 21이므로 표시와 주석을 Java 21로 맞춰야 한다.
- `chmod +x ./gradlew`는 checkout과 Java 설정 뒤의 두 번째 빌드 단계에 있어 첫 Gradle 실행을 보호하지 못한다. 실행 권한 단계는 checkout 직후, 첫 Gradle 명령 전에 둔다.
- 현재 유효한 테스트는 잘못 배치된 첫 단계에만 있고, 두 번째 단계는 `clean bootJar`만 실행한다. 테스트 성공 후에만 `bootJar`와 배포 단계가 진행되도록 순서를 분리한다.
- `main` push, Docker 이미지 build/push, 기존 SSH 배포라는 배포 구조는 변경하지 않는다. Codex는 workflow를 실제로 트리거하거나 배포·push·SSH를 실행하지 않는다.

### 테스트 환경변수와 외부 호출

- `application.yml`은 `MONGODB_URI`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_S3_BUCKET_NAME`을 placeholder로 요구하고 Redis는 기본적으로 `localhost:6379`를 사용한다.
- 현재 유일한 테스트인 `TosunsaengApplicationTests.contextLoads()`는 전체 Spring context를 생성한다.
- `S3Config`는 정적 자격증명으로 `S3Presigner`를 생성한다. Bean 생성과 presigned URL 계산은 로컬 연산이며 AWS API 호출을 수행하지 않는다.
- `ExamServiceImpl`의 외부 S3/AI HTTP 호출은 서비스 메서드를 실제 호출할 때만 발생한다. 현재 context test와 계획된 Repository Bean 등록 테스트에서는 해당 메서드를 호출하지 않는다.
- MongoDB와 Redis의 실제 Repository/Template 연산도 context test나 Bean 존재 확인만으로는 수행하지 않는다. 테스트 URI는 외부 주소가 아니라 localhost를 사용한다.
- 따라서 CI의 형식상 유효한 더미 AWS 값으로 context 생성은 충분하며 실제 AWS 키는 필요하지 않다. 다만 CI workflow에만 더미 값을 두면 로컬 실행 정책이 달라지므로 테스트 전용 profile을 단일 기준으로 두는 편이 안전하다.

승인안은 `src/test/resources/application-test.yml`에 테스트 context 생성에 필요한 localhost Mongo URI와 명백한 AWS 더미 자격증명·bucket만 두고 테스트에 `@ActiveProfiles("test")`를 적용하는 것이다. 운영 `application.yml`은 수정하지 않으며 `S3Presigner` mock Bean은 현재 테스트 범위에서 불필요하다. 향후 exams 서비스 메서드를 직접 호출하는 테스트는 Mongo/Redis/S3/AI 경계를 별도로 mock 또는 통합 테스트 인프라로 격리해야 한다.

### Scheduling

- 현재 `@EnableScheduling`, `@Scheduled`, `TaskScheduler` 사용은 없다.
- `TosunsaengApplication`에 기능을 추가하기보다 `web.tosunsaeng.global.config.SchedulingConfig`를 별도로 두고 `@Configuration`, `@EnableScheduling`만 선언하는 방식을 권고한다.
- 별도 설정은 기반 기능의 위치를 명확히 하고, 이후 newsletter scheduler의 조건부 활성화 정책을 독립적으로 검토하기 쉽다.
- 이번 단계에서는 `@Scheduled` 메서드, newsletter scheduler, 발송 로직, 예약 데이터 또는 재시도 작업을 만들지 않는다.

### 로컬 MongoDB와 Redis

- 저장소에는 Docker Compose 파일이 없다. CI가 원격 EC2에서 `docker compose`를 실행하지만 그 원격 compose 파일은 저장소에 없고 운영 접근 금지 범위이므로 확인하거나 변경하지 않는다.
- `compose.local.yml`을 새로 두어 애플리케이션이 아닌 MongoDB와 Redis만 로컬에서 실행하도록 하는 것이 필요하다.
- 서비스는 지원되는 고정 버전의 공식 MongoDB/Redis 이미지를 사용하고, 포트는 `127.0.0.1`에만 바인딩하며, healthcheck와 이름 있는 volume을 둔다.
- 로컬 compose는 운영 배포 compose를 대체하지 않으며 Docker image build/push 또는 registry login을 포함하지 않는다.
- 로컬 애플리케이션용 `.env`는 추적하지 않고 `.env.example`에는 localhost 연결값과 `test-*` 형식의 안전한 AWS dummy 예시만 기록한다.

### Dockerfile과 Dokerfile

- CI는 `file: ./Dockerfile`로 Java 21 기반 `Dockerfile`을 명시적으로 참조한다.
- 별도 `Dokerfile`은 Java 17 기반이며 repository, workflow, 문서의 실행 경로 어디에서도 참조되지 않는다.
- `Dokerfile`은 오탈자로 생긴 미사용 파일로 보이지만 이 Phase에서는 수정하거나 삭제하지 않는다. 삭제가 필요하면 사용자 확인을 거친 별도 정리 범위로 다룬다.

### `.gitignore`

- 현재 IDE와 Gradle 산출물만 제외하며 `.env` 계열 로컬 비밀 파일을 제외하지 않는다.
- 실제 AWS 키나 운영 연결값의 우발적 추적을 막기 위해 로컬 `.env` 파일을 ignore하고, 값이 비어 있거나 로컬 더미 값만 있는 `.env.example`은 추적 가능하게 유지할 필요가 있다.

## 구현 범위

사용자 승인 후 다음 순서로만 구현한다.

1. `@EnableMongoRepositories`의 base package를 `web.tosunsaeng.domain`으로 확장한다.
2. 별도 `SchedulingConfig`를 추가해 Spring Scheduling 기반만 활성화한다.
3. 테스트 전용 profile과 `@ActiveProfiles("test")`를 추가하여 실제 비밀값과 외부 AWS 호출 없이 context test를 안정화한다.
4. 기존 exams Mongo Repository 다섯 개가 모두 Bean으로 등록되는 회귀 테스트를 추가한다. 빈 `ExamRepository`는 Bean 기대 대상에서 제외한다.
5. CI 단계를 checkout → Java 21 설정 → gradlew 실행 권한 확인 → test → bootJar → 기존 배포 순서로 재배치한다.
6. MongoDB와 Redis만 포함하는 `compose.local.yml`과 비밀값 없는 `.env.example`을 추가하고 `.gitignore`에 로컬 `.env` 보호 규칙을 추가한다.
7. 관련 테스트, compose 정적 검증, 전체 Gradle 검증을 실행하고 승인 계획과 실제 변경을 대조한다.

## 제외 범위

- BlogPost Document
- 게시글 API
- 댓글 및 댓글 validation
- 뉴스레터 구독과 구독 해지
- 이메일 발송
- 실제 Scheduler 작업 또는 `@Scheduled` 메서드
- 내부 API 인증
- 기존 exams 구조 또는 비즈니스 로직 리팩터링
- 게시글이나 댓글 관련 의존성 추가
- 실제 AWS 키, 운영 DB URI, Docker registry 자격증명 저장
- 운영 application.yml 동작 변경
- `Dockerfile` 또는 `Dokerfile` 수정·삭제
- `main` push, Docker image push, SSH 배포 실행

## 예상 변경 파일

승인 후 애플리케이션/인프라 변경 예정:

- `src/main/java/web/tosunsaeng/TosunsaengApplication.java` — Mongo Repository 탐색 범위 확장
- `src/main/java/web/tosunsaeng/global/config/SchedulingConfig.java` — Scheduling 기반 설정 신규 생성
- `.github/workflows/deploy.yml` — checkout, Java 21, test, bootJar 실행 순서 정정
- `compose.local.yml` — 로컬 MongoDB/Redis 실행 환경 신규 생성
- `.env.example` — 로컬 연결값과 빈 비밀값 placeholder 예시 신규 생성
- `.gitignore` — 실제 로컬 `.env` 파일 제외 규칙 추가
- `src/test/resources/application-test.yml` — 외부 호출 없는 테스트 profile 신규 생성
- `src/test/java/web/tosunsaeng/TosunsaengApplicationTests.java` — test profile 활성화
- `src/test/java/web/tosunsaeng/domain/exams/domain/repository/ExamsRepositoryScanTest.java` — 기존 exams Repository Bean 회귀 테스트 신규 생성

단계 관리 중 변경 예정:

- `docs/blog-mvp/plans/PHASE-01-infrastructure.md` — 승인 상태, 실제 차이, 검증 결과 기록
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md` — 단계 상태, 명령, 테스트 결과, Session Log 기록

변경하지 않을 확인 대상:

- `build.gradle`
- `src/main/resources/application.yml`
- `Dockerfile`
- `Dokerfile`
- 기존 exams 애플리케이션 코드

예상 변경 파일 밖의 소스 또는 설정을 변경해야 하면 즉시 중단하고 사용자에게 차이와 선택지를 보고한다.

## 설정 변경

- Mongo: 명시적 탐색 시작점을 exams Repository 패키지에서 전체 domain 패키지로 한 단계 확장한다.
- Scheduling: 별도 설정 class에서 프레임워크 기능만 활성화한다. 실행 작업은 추가하지 않는다.
- Test: `test` profile에 context 생성에 필요한 localhost Mongo URI, 명백한 AWS 더미 값과 더미 bucket만 둔다. 운영 profile과 실제 비밀값은 건드리지 않는다.
- CI: `main` push trigger와 배포 단계는 보존하고 빌드 선행 순서만 바로잡는다. 테스트가 실패하면 bootJar와 배포 단계로 진행하지 않게 한다.
- Local: compose service를 loopback에만 노출하고 volume 삭제를 기본 종료 절차에 포함하지 않는다.
- Secret policy: 실제 키는 저장소나 workflow 평문에 기록하지 않는다. `.env.example`은 명백한 `test-*` dummy만 사용하고 실제 `.env` 계열은 Git에서 제외한다.

## 기존 exams 영향

- 권고한 Mongo 변경은 현재 exams Repository 패키지를 더 넓은 탐색 범위 안에 그대로 포함하므로 기존 Bean 이름과 interface를 변경하지 않는다.
- exams entity, DTO, controller, service, converter, repository interface의 코드는 수정하지 않는다.
- `SchedulingConfig`는 현재 예약 작업이 없어 exams 실행 흐름에 직접 영향을 주지 않는다.
- test profile은 테스트 classpath에서만 활성화하며 운영 exams 환경변수를 바꾸지 않는다.
- CI는 이전과 같은 `bootJar`를 만들되 tests가 먼저 성공해야 하므로 exams context wiring 오류가 배포 전에 차단된다.
- 현재 exams 기능의 단위/통합 테스트가 사실상 없으므로 이 Phase가 보장하는 회귀 범위는 컴파일, 전체 context 기동, 기존 Mongo Repository Bean 등록까지다. 실제 Mongo/Redis/S3/AI 동작 회귀는 보장하지 않으며 기존 기능 리팩터링으로 범위를 넓히지 않는다.

## API 변경

없음. 공개 API와 내부 API endpoint, request/response 형식, 인증 정책을 변경하지 않는다.

## DB 변경

없음. Document, collection, index, migration 또는 운영 데이터를 변경하지 않는다. `compose.local.yml`의 이름 있는 volume은 로컬 개발 데이터만 보관한다.

## 상태 전이

- 이번 계획 수립 작업: Phase 01 `TODO` → `PLANNING`, 계획 `DRAFT`
- 계획 수립 종료 시 사용자 지시에 따라 Phase 01은 `PLANNING`, 계획은 `DRAFT`로 유지했다.
- `2026-07-29` 사용자 조건부 승인으로 계획을 `APPROVED`, Phase 01을 `IN_PROGRESS`로 변경한다. 실제 직전 문서 상태는 `PLANNING`이었으므로 이 차이를 Session Log에 보존한다.
- 구현 및 필수 검증 완료 후 Phase 01을 `VERIFYING`으로 거쳐 `DONE`, 계획을 `EXECUTED`로 변경했다.

## 테스트 계획

### 정적 검증

1. `bash -n`이 필요한 shell script 변경은 없음을 확인한다.
2. `docker compose -f compose.local.yml config`로 compose 문법과 최종 port/volume 구성을 확인한다.
3. `.github/workflows/deploy.yml`에서 Gradle 실행 전에 checkout, Java 21 설정, 실행 권한 단계가 위치하는지 정적으로 확인한다.
4. workflow의 trigger가 계속 `push.branches: [main]`이고 기존 Docker build/push 및 SSH 배포 단계가 유지되는지 diff로 확인한다.
5. 저장소 전체에서 실제 AWS 키 형식이나 새 비밀값이 추가되지 않았는지 변경 파일을 검토한다.

### 자동 테스트

1. `TosunsaengApplicationTests.contextLoads()`를 `test` profile로 실행하여 외부 AWS 자격증명 없이 context가 기동하는지 확인한다.
2. `ExamsRepositoryScanTest`에서 현재 다섯 Mongo Repository Bean이 모두 존재하는지 확인한다.
3. 테스트는 Repository 메서드, Redis 연산, S3/AI HTTP 호출을 실행하지 않아 외부 시스템에 접근하지 않게 한다.
4. `./gradlew clean test bootJar`를 실행하고 모든 task가 성공하는지 확인한다.
5. `git diff --check`를 실행한다.

### 로컬 인프라 검증

1. `docker compose -f compose.local.yml config`로 문법, MongoDB/Redis service, healthcheck, localhost port, local volume 구성을 확인한다.
2. container를 시작하거나 MongoDB/Redis에 실제 연결하지 않는다. Phase 01의 Repository 테스트도 Bean 등록 여부만 검증한다.

CI의 `main` push 배포는 Codex가 실행하지 않는다. 따라서 workflow 실제 배포 성공 여부는 사용자 또는 승인된 CI 실행 결과로만 최종 확인할 수 있으며, Phase 01에서는 정적 순서 검토와 로컬 Gradle 검증을 수행한다.

## 위험 요소

- Mongo 탐색 범위 확장 후 `web.tosunsaeng.domain` 아래에 다른 저장소 기술의 잘못 분류된 interface가 추가되면 후보 탐색 경고나 충돌이 생길 수 있다. 도메인 패키지 규칙과 Repository Bean 회귀 테스트로 감시한다.
- test profile의 localhost Mongo/Redis는 Bean 생성에는 충분하지만 실제 Repository/Redis 연산을 검증하지 않는다. 이를 완전한 exams 통합 테스트로 오해하면 안 된다.
- `S3Presigner` 자체는 네트워크를 사용하지 않지만 exams service의 특정 메서드는 presigned URL, Redis, AI server를 실제 사용한다. Phase 01 테스트에서 해당 메서드를 호출하지 않는다.
- Scheduling을 전역 활성화하면 이후 추가되는 모든 `@Scheduled` Bean이 실행 대상이 된다. Phase 06에서 newsletter kill switch 기본값 `false`와 조건부 실행을 반드시 별도로 검증해야 한다.
- 로컬 기본 포트 27017 또는 6379가 이미 사용 중이면 compose가 시작되지 않는다. 포트 변경은 application 환경변수와 함께 조정해야 한다.
- 이름 있는 Docker volume 때문에 이전 로컬 데이터가 남을 수 있다. 자동 volume 삭제는 하지 않으며 정리가 필요하면 대상과 복구 가능성을 확인해 별도로 수행한다.
- `Dokerfile`은 Java 17이라는 오래된 정보를 남겨 개발자가 잘못 사용할 위험이 있으나 이번 단계에서는 삭제할 수 없다. 별도 정리 여부를 사용자와 결정해야 한다.
- 기존 exams에는 기능 수준 회귀 테스트가 없어 CI 순서 수정 후에도 Mongo/Redis/S3/AI 연동 동작 전체는 검증되지 않는다.
- workflow의 실제 성공은 GitHub runner와 저장된 secrets에 의존하지만 Codex는 main push, image push, SSH 배포를 실행할 수 없다.

## 롤백 방법

- 롤백은 사용자 변경을 먼저 확인한 뒤 Phase 01에서 추가한 줄만 수동 역패치한다. 금지된 `git reset`, `git restore`, `git checkout`, `git clean`은 사용하지 않는다.
- Mongo 문제가 생기면 `@EnableMongoRepositories`의 base package를 기존 exams Repository 패키지로 수동 복구하고 context 및 Repository Bean 테스트를 다시 실행한다.
- Scheduling 문제가 생기면 Phase 01에서 추가한 `SchedulingConfig`만 사용자 변경 여부를 확인한 뒤 수동 제거한다.
- test profile 또는 CI 문제가 생기면 추가된 test 설정과 workflow 단계만 이전 diff를 기준으로 수동 복원하며 운영 `application.yml`은 계속 보존한다.
- 로컬 compose는 `docker compose -f compose.local.yml down`으로 중지하고 volume은 삭제하지 않는다. compose와 env 예시 파일 제거가 필요하면 사용자 변경이 없는지 확인한 뒤 수동 역패치한다.
- `Dokerfile`과 기존 exams 파일은 애초에 변경하지 않으므로 롤백 대상이 아니다.

## 완료 조건

- 사용자가 이 계획을 명시적으로 승인했고 계획 상태가 `APPROVED`다.
- 승인된 예상 변경 파일과 범위 안에서만 구현했다.
- 기존 exams 다섯 Mongo Repository Bean이 등록되고 context test가 성공한다.
- 실제 AWS 키 없이 test profile의 더미 값만으로 테스트가 성공하며 테스트 중 AWS/S3/AI 외부 호출이 없다.
- 운영 `application.yml`, `Dockerfile`, `Dokerfile`, exams 비즈니스 코드를 변경하지 않았다.
- CI가 checkout → Java 21 → gradlew 권한 → test → bootJar → 기존 배포 순서를 가지며 `main` push 배포 구조를 유지한다.
- 로컬 MongoDB와 Redis compose 구성이 정적 검증을 통과하고 두 service healthcheck가 성공한다.
- `./gradlew clean test bootJar`가 성공한다.
- `git diff --check`가 성공한다.
- 고정 제품 요구사항과 제외 범위, 승인 계획, 실제 diff를 대조해 차이가 없다.
- 실행 명령, 결과, 변경 파일, 남은 위험을 계획서와 `IMPLEMENTATION_STATUS.md`에 기록했다.

## 실제 구현 중 발생한 차이

- 원래 DRAFT의 테스트 profile 제안 중 Redis override와 Sentry 비활성화는 조건부 승인에서 요구한 최소 구성에 맞춰 제외했다.
- 원래 DRAFT의 로컬 container 기동 검증은 조건부 승인 범위에 맞춰 `docker compose config` 문법 검증으로 축소했다. container와 volume은 생성하지 않았다.
- `.env.example`은 조건부 승인에서 허용한 안전한 예시에 맞춰 빈 placeholder 대신 `test-*` dummy 값을 사용했다.
- 위 항목은 사용자의 조건부 승인으로 확정된 범위 반영이며, 승인된 구현 범위와 실제 변경 사이의 미승인 차이는 없다.
- 첫 `./gradlew clean test bootJar`는 샌드박스가 `~/.gradle` lock 파일 접근을 막아 애플리케이션 task 실행 전에 실패했다. 같은 명령을 승인된 외부 실행으로 재시도해 성공했으며 코드 변경으로 우회하지 않았다.

## 검증 결과

- 계획 수립 전 `git branch --show-current`: `feat/blog-mvp`
- 계획 수립 전 `git status --short`: 깨끗함
- 계획 수립 전 `scripts/codex-preflight.sh`: 성공
- 지정 파일과 관련 Repository/S3/Scheduling 참조를 읽기 전용으로 분석했다.
- 사용자 조건부 승인으로 계획을 `APPROVED`, Phase 01을 `IN_PROGRESS`로 변경한 뒤 승인 파일만 구현했다.
- `git diff --check`: 성공
- `./gradlew clean test bootJar`: 샌드박스 내부 첫 실행은 Gradle cache lock 접근 제한으로 task 실행 전 실패, 같은 명령의 승인된 재실행은 `BUILD SUCCESSFUL`
- `TosunsaengApplicationTests.contextLoads()`: 1건 성공, 실패·오류·skip 없음
- `ExamsRepositoryScanTest.registersExistingExamsMongoRepositories()`: 1건 성공, 실패·오류·skip 없음
- Spring Data 로그에서 Mongo Repository interface 5개 탐색을 확인했다.
- `docker compose -f compose.local.yml config`: 성공. MongoDB, Redis, healthcheck, loopback port, local volume 구성을 확인했다.
- GitHub Actions YAML parse: 성공. checkout → Java 21 → gradlew 권한 → `clean test` → `bootJar` → 기존 Docker/SSH 배포 순서를 확인했다.
- Java 21 정합성: `build.gradle`, workflow, `Dockerfile`에서 21 확인
- 실제 AWS access key ID/private key 패턴 검색: 없음. test profile과 `.env.example`은 `test-*` dummy만 사용한다.
- 테스트 소스에 S3, RestTemplate, RedisTemplate, MongoTemplate 또는 Repository 메서드 호출이 없음을 확인했다. AWS Bean mock은 추가하지 않았다.
- `build.gradle`, 운영 `application.yml`, `SecurityConfig`, `Dockerfile`, `Dokerfile`, exams 비즈니스 코드는 변경되지 않았다.
- `Dokerfile`은 docs 밖에서 참조되지 않으며 Java 17 기반 파일을 그대로 보존했다.
- 최종 Codex review와 관리 문서 반영 후 `git diff --check` 및 신규 파일 trailing whitespace 검사가 성공했다.
- Phase 01을 `DONE`, 현재 단계를 Phase 02 `TODO`로 변경했으며 Phase 02 계획이나 구현은 시작하지 않았다.
