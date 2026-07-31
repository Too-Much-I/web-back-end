# Phase 08: 최종 통합 검증 및 운영 문서

- 상태: EXECUTED

## 목표

Phase 01부터 Phase 07까지 구현된 블로그 MVP의 API, MongoDB 상태 전이, Redis Lua, 뉴스레터 Scheduler, 내부 API 보안과 개인정보 보호를 실제 실행 환경에 가까운 조건에서 최종 검증한다. 현재 Mockito, MockMvc와 BSON/IndexDefinition 계약 테스트로 확인한 동작 중 MongoDB와 Redis 서버의 실제 원자성, unique 충돌, TTL 및 동시성은 격리된 통합 테스트로 보강한다.

AWS SES, DKIM, RFC 8058의 이메일 클라이언트 표시, CloudFront 또는 정적 아바타 제공, ALB·Security Group·방화벽, Sentry outbound event와 reverse proxy access log처럼 저장소 밖에 있는 항목은 Codex가 운영 자원에 접근하거나 실제 이메일을 보내지 않는다. 대신 운영자가 비운영 환경에서 실행할 절차, 기대 결과, 증적과 출시 차단 기준을 문서화한다.

최종 공개·내부 API 계약과 기존 exams 계약을 고정하고, 제공하지 않기로 한 게시글 쓰기, 댓글 수정·삭제, newsletter verify와 Subscriber PENDING이 생기지 않았는지 검증한다. API, 운영, 배포, 보안, 테스트 문서를 완성하되 새로운 제품 기능이나 승인되지 않은 API 변경은 추가하지 않는다.

## 작업 시작 조건

- 계획 수립 브랜치는 `feat/blog-mvp`다.
- 계획 수립 직전 `git status --short` 출력은 없어 작업 트리가 깨끗하다.
- `scripts/codex-preflight.sh`는 `Current branch: feat/blog-mvp`와 `Preflight: PASS`를 출력했다.
- Phase 01부터 Phase 07까지 모두 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-01-infrastructure.md`부터 `PHASE-07-internal-operations-security.md`까지 모두 `EXECUTED`다.
- Current phase는 Phase 08이며 계획 수립 전 상태는 `TODO`다.
- 계획 수립에서는 이 계획서와 `docs/blog-mvp/IMPLEMENTATION_STATUS.md`만 변경한다.
- 사용자 승인 전 Java, test, `build.gradle`, application 설정, CI, Dockerfile과 API·운영 문서를 수정하거나 생성하지 않는다.

## 현재 시스템 요약

### 실제 package와 구현 경계

- 게시글은 `web.tosunsaeng.domain.blog`, 댓글은 `web.tosunsaeng.domain.comment`, 뉴스레터는 `web.tosunsaeng.domain.newsletter`, 기존 시험 기능은 `web.tosunsaeng.domain.exams`에 있다.
- main과 test에 `web.tosunsaeng.domain.blog.comment` 또는 `web.tosunsaeng.domain.blog.newsletter` package는 없다.
- Java toolchain, GitHub Actions와 실제 `Dockerfile`은 Java 21을 사용한다.
- Spring Boot는 3.4.2, MongoDB와 Redis는 Spring Data, SES와 S3는 AWS SDK v2 `2.29.52`를 사용한다.
- `compose.local.yml`은 loopback에 고정한 MongoDB 7.0과 Redis 7.2 standalone, named volume과 health check를 제공한다. backend service는 포함하지 않는다.

### 구현 완료 API

블로그 MVP 공개 API는 실제 Controller 기준으로 다음과 같다.

```http
GET  /api/posts
GET  /api/posts/{slug}
GET  /api/posts/search

GET  /api/posts/{slug}/comments
POST /api/posts/{slug}/comments
POST /api/comments/nickname/regenerate

POST /api/newsletter/subscribe
POST /api/newsletter/unsubscribe
POST /api/newsletter/one-click-unsubscribe/{token}
```

내부 API는 다음 여섯 개이며 두 Controller 모두 `@Hidden`이다.

```http
GET   /internal/comments
PATCH /internal/comments/{commentId}/hide
PATCH /internal/comments/{commentId}/restore

POST  /internal/newsletter/posts/{postId}/test
POST  /internal/newsletter/posts/{postId}/cancel
POST  /internal/newsletter/posts/{postId}/retry
```

- one-click endpoint는 `application/x-www-form-urlencoded`와 `multipart/form-data` POST만 받고 정상 시 redirect 없는 204와 `Cache-Control: no-store`를 반환한다.
- 기존 exams Controller에는 요구된 `GET /api/v1/exams/{examId}/summary`와 `GET /api/v1/exams/{examId}/questions` 외에도 기존 세션·업로드·callback endpoint가 있다. Phase 08은 이들을 삭제하거나 변경하지 않는다.
- exams `questions`는 path의 examId, 필수 query `questionNumber`, 기본 0인 선택 query `retryCount`를 사용하는 단건 조회다.

### MongoDB 구현

- `blog_posts`는 slug unique, status + publishedAt, newsletter reconciliation index를 programmatic initializer로 생성한다.
- 공개 게시글 query는 PUBLISHED, publishedAt 존재·non-null·현재 이하 조건과 publishedAt, createdAt, `_id` 내림차순 안정 정렬을 사용한다. 제목 검색은 `Pattern.quote`로 literal regex를 만든다.
- `anonymous_visitors`는 tokenHash unique, `blog_comments`는 postId + status + createdAt, anonymousVisitorId, status + createdAt index를 가진다.
- 댓글 숨김은 VISIBLE/PENDING에서 HIDDEN, 복원은 HIDDEN에서 VISIBLE로 `findAndModify`한다. 운영 기간은 `createdAt >= from`, `createdAt < to`다.
- `newsletter_subscribers`는 email unique와 status index를 가진다. 재구독과 해지는 status 및 tokenVersion 조건의 `findAndModify`다.
- `newsletter_campaigns`는 postId unique와 schedule, claim, updated index를 가진다. `newsletter_deliveries`는 postId + subscriberId unique와 campaign, retry, claim index를 가진다.
- Campaign/Delivery claim, stale recovery, 취소와 재시도에는 실제 `findAndModify` 또는 조건부 update와 claimToken fencing이 적용돼 있다.
- 모든 index initializer는 `@Profile("!test")`라 현재 test profile에서는 실제 MongoDB index 생성이 실행되지 않으며, 기존 테스트는 mock `IndexDefinition` 검증이다.

### Redis 구현

- 댓글은 여섯 key를 한 Lua admission script에서 검사한 뒤 mutation한다. visitor short/medium/daily, IP medium/daily counter와 duplicate reservation을 함께 처리한다.
- duplicate cleanup은 owner token이 일치할 때만 별도 Lua로 key를 삭제한다.
- 뉴스레터 구독은 IP medium/daily 두 key를 하나의 Lua에서 검사하고 원자 증가한다.
- 새 key의 count가 1일 때만 TTL을 설정하고 기존 key에서는 TTL을 연장하지 않는다.
- Redis 연결 및 명확한 timeout은 application service에서 fail-open하지만 Lua syntax, 잘못된 결과, HMAC과 application 오류는 fail-open하지 않는다.
- 설정과 script는 standalone 또는 single-primary를 전제로 한다. Redis Cluster용 hash tag나 CROSSSLOT 회피 설계는 없다.
- 현재 Redis 테스트는 `RedisTemplate.execute` mock과 Lua source 문자열 계약 테스트이며 실제 Redis 실행은 없다.

### Newsletter Scheduler와 SES

- Campaign reconciliation, Campaign claim/Delivery 생성, Delivery PENDING/retry, stale recovery와 완료 집계는 세 Scheduler adapter로 나뉘고 모두 `fixedDelay`를 사용한다.
- `newsletterTaskExecutor`는 기본 worker 2, max 2, queue 100, `AbortPolicy`, `newsletter-` prefix와 graceful shutdown을 가진 bounded executor다.
- 실제 claim은 worker task 안에서 수행하므로 queue 거절 전에 Document가 SENDING으로 바뀌지 않는다.
- `NEWSLETTER_SENDING_ENABLED` 기본값은 false다. false에서도 reconciliation은 SCHEDULED Campaign을 만들 수 있지만 claim, retry, provider와 test send는 진행하지 않는다.
- SES adapter는 Simple Message의 HTML/plain body와 `List-Unsubscribe`, `List-Unsubscribe-Post` custom header만 허용한다. Raw MIME fallback은 없다.
- SES SDK retry는 0이며 application retry가 총 provider 호출 4회와 5분, 30분, 2시간 backoff를 관리한다.
- provider 결과가 불명확한 stale Delivery는 `PROVIDER_RESULT_UNKNOWN`, retryable=false로 종료한다.
- 자동화 테스트는 현재 SES request mapping까지만 검증하고 실제 SES, DKIM과 이메일 클라이언트는 검증하지 않는다.

### 내부 API와 개인정보

- `/internal/**`는 `@Order(1)` 전용 stateless `SecurityFilterChain`, 나머지는 기존 JWT/public `@Order(2)` chain이다.
- internal disabled는 404, enabled 상태의 key 누락·오류는 동일 401, 정상 key는 fixed principal, null credentials, `ROLE_INTERNAL`을 사용한다.
- configured/request key는 SHA-256 fixed-length digest로 변환해 `MessageDigest.isEqual`로 비교한다.
- 기존 Sentry callback은 newsletter POST, one-click과 `/internal/**`의 data, query, cookies, user PII 및 key/auth/proxy/IP header를 제거한다. one-click event URL은 `{token}`으로 치환하고 해당 transaction은 drop한다.
- 실제 Sentry outbound payload, reverse proxy/CDN/ALB access log는 검증되지 않았다.

### 테스트, CI와 Docker 현황

- Phase 07 최종 Gradle XML 기준 기존 테스트는 421개, failures/errors/skipped 0이다. Java source의 단순 `@Test` annotation은 402개이고 parameterized 실행을 포함한 실제 실행 건수는 421개다.
- 실제 MongoDB/Redis Testcontainers, `@DataMongoTest` 기반 서버 통합 또는 별도 integration source set은 없다.
- `.github/workflows/deploy.yml`은 main push에서 checkout, Java 21, `clean test`, `bootJar`, Docker build/push, EC2 SSH 배포 순서다. 통합 테스트용 MongoDB/Redis 환경은 없다.
- CI는 실제 `Dockerfile`을 명시한다. `Dokerfile`은 어디에서도 참조되지 않고 Java 17을 적은 오래된 오탈자 파일이다.
- 계획 수립 환경에는 Docker client 29.6.1과 Compose 5.2.0이 있고 `docker compose -f compose.local.yml config --quiet`는 성공했다. 현재 Codex sandbox는 Docker socket 접근이 거절돼 daemon 및 image build 가능 여부를 확인하지 못했다.

### 계획 수립 중 발견한 정적 위험

- `GlobalExceptionAdvice`가 `printStackTrace()`를 호출하고 예상하지 못한 exception 및 JSON parsing exception의 원문 message를 log와 BaseResponse result에 넣는다. 개인정보·credential·driver 오류가 포함될 수 있어 출시 차단 후보로 분류한다.
- `application.yml`의 JWT secret에는 고정된 fallback 문자열이 있다. 실제 secret 누락 시 공용 key로 기동할 수 있어 hardcoded secret 검사에서 차단 대상으로 분류한다.
- Sentry DSN이 main 설정에 직접 있고 `send-default-pii: true`다. DSN은 AWS credential과 같은 비밀은 아니지만 환경 분리 및 최소 PII 원칙에 맞게 외부 주입과 false 기본값을 권고한다.
- `Dokerfile`은 Java 17 정보를 남기지만 실제 workflow는 Java 21 `Dockerfile`만 사용한다.
- newsletter executor는 bounded지만 기존 exams에 JVM common pool을 쓰는 `CompletableFuture.runAsync`가 두 곳 있다. Phase 08의 관련 없는 exams 리팩터링 금지에 따라 임의 수정하지 않고 운영 위험으로 문서화한다.
- generic `PENDING` 검색은 comment, Delivery와 exams의 합법적인 상태도 찾는다. 금지 검사는 `NewsletterSubscriberStatus`에 한정해야 한다.
- repository 안에는 avatar image asset, frontend 정적 asset 또는 CloudFront/S3 IaC가 없다. backend는 외부 `BLOG_ANONYMOUS_AVATAR_BASE_URL`과 외부 avatar-options만 검증하므로 실제 제공 방식을 코드만으로 확정할 수 없다.

## 구현 완료 범위

1. Java 21, Gradle test/bootJar, Mongo Repository scan, Scheduling과 local MongoDB/Redis compose가 준비됐다.
2. 공개 게시글 목록·상세·제목 검색과 programmatic index가 구현됐다.
3. 익명 댓글 작성·조회, 고정 rule 1~10, 익명 cookie/profile과 avatar URL 생성이 구현됐다.
4. 댓글 Redis rate limit, duplicate reservation cleanup과 Controller 없는 moderation Service가 구현됐다.
5. 즉시 ACTIVE newsletter subscribe, JSON POST unsubscribe, stateless token rotation과 구독 Redis rate limit이 구현됐다.
6. BlogPost opt-in, Campaign/Delivery, bounded Scheduler, logging/SES sender, retry, kill switch와 RFC 8058 endpoint가 구현됐다.
7. 댓글 및 newsletter 운영 API와 `/internal/**` API Key SecurityFilterChain이 구현됐다.
8. 위 범위의 mock, BSON, index definition, MockMvc와 Security contract 테스트 421개가 성공했다.

## 남은 검증 범위

1. MongoDB 서버에서 실제 index 생성과 unique 충돌
2. MongoDB `findAndModify`, claim fencing과 두 worker 동시성
3. Subscriber insert/reactivation/tokenVersion 경쟁
4. Redis에서 실제 Lua syntax, counter, TTL, reservation owner와 동시 요청
5. Scheduler service를 실제 MongoDB, 주입 Clock과 fake sender로 연결한 end-to-end 상태 전이
6. public/internal/exams API mapping과 금지 API 부재의 전체 application contract
7. Sentry redaction과 일반 예외 처리의 민감정보 비노출
8. Docker image와 compose 문법, CI의 통합 테스트 gate
9. 실제 SES/DKIM/RFC 8058, avatar origin, internal network boundary와 access log의 수동 운영 검증
10. API, 운영, 배포, 보안, 테스트 문서와 출시 승인 checklist

## 구현 범위

1. Testcontainers 기반 별도 `integrationTest` source set과 Gradle task를 추가한다.
2. MongoDB 7.0과 Redis 7.2-alpine container를 pin하고 integration task에서만 사용한다.
3. 실제 MongoDB index, query, unique, 원자 상태 전이, claim fencing과 동시성 테스트를 추가한다.
4. 실제 Redis에서 두 Lua admission, duplicate release, TTL, fixed-window와 동시성 테스트를 추가한다.
5. Newsletter service를 실제 MongoDB, deterministic mutable Clock과 fake `NewsletterEmailSender`로 검증한다.
6. 전체 application의 API mapping, SecurityFilterChain, OpenAPI 숨김과 금지 endpoint 부재 contract를 보강한다.
7. 계획 단계에서 확인한 `GlobalExceptionAdvice`, JWT fallback과 Sentry PII 기본값을 승인된 최소 보안 수정 범위로 교정하고 회귀 테스트를 추가한다.
8. CI가 unit test 다음 integration test, bootJar와 Docker build/push 순서로 실패를 전파하도록 조정한다.
9. API, 운영, 배포, 보안, 테스트 문서 다섯 개를 코드와 실제 설정 기준으로 작성한다.
10. SES, RFC 8058, avatar, internal network, Sentry/access log를 운영자가 검증할 수 있는 절차와 증적 양식을 작성한다.
11. 정적 검수를 수행하고 발견 결과를 차단, Phase 08 최소 수정, 문서화된 후속 위험으로 분류한다.

## 제외 범위

- 새로운 사용자 기능과 API 경로
- 기존 API method, request, response와 DTO 계약 변경
- 게시글 생성·수정·삭제 API와 관리자 UI
- 댓글 수정·삭제와 삭제 상태
- 이메일 인증, verify API와 NewsletterSubscriber PENDING
- 추천, segmentation, A/B test와 사용자별 newsletter
- OAuth 관리자 로그인, RBAC와 다중 API key rotation
- SES bounce 및 complaint webhook
- Raw MIME fallback과 SES Contact List subscription management
- Redis Cluster 지원과 multi-key Lua 재설계
- microservice 분리와 관련 없는 exams 비즈니스 리팩터링
- 운영 MongoDB, Redis, AWS, Sentry, ALB, EC2와 실제 사용자 데이터 접근
- 실제 운영 이메일 또는 구독자 대상 test send
- AWS resource, DNS, CloudFront, S3 bucket, Security Group와 firewall 변경
- Docker image push, registry login, 배포와 SSH 실행

## 예상 생성·수정 파일

### 이번 DRAFT 계획 수립

- 생성: `docs/blog-mvp/plans/PHASE-08-final-verification-documentation.md`
- 수정: `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

### 승인 후 예상 수정 파일

- `build.gradle`
- `.github/workflows/deploy.yml`
- `.env.example`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `src/main/java/web/tosunsaeng/global/exception/GlobalExceptionAdvice.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/config/NewsletterTelemetryPrivacyConfig.java`
- `src/test/java/web/tosunsaeng/domain/newsletter/config/NewsletterTelemetryPrivacyConfigTest.java`
- `src/test/java/web/tosunsaeng/global/config/InternalApiSecurityContractTest.java`
- 기존 Mongo query/index test는 새 integration test와 중복되지 않는 contract assertion 보강이 필요한 경우에만 최소 수정한다.
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`
- 이 계획서는 구현 차이와 검증 결과를 기록하기 위해 수정한다.

### 승인 후 예상 생성 integration test 파일

구체 class 이름은 구현 시 실제 책임에 맞춰 아래 범위에서 확정한다.

- `src/integrationTest/java/web/tosunsaeng/integration/support/IntegrationContainers.java`
- `src/integrationTest/java/web/tosunsaeng/integration/mongo/BlogPostMongoIntegrationTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/mongo/BlogCommentMongoIntegrationTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/mongo/NewsletterSubscriberMongoIntegrationTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/mongo/NewsletterCampaignDeliveryMongoIntegrationTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/mongo/NewsletterSchedulerMongoIntegrationTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/redis/BlogCommentRedisIntegrationTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/redis/NewsletterRedisIntegrationTest.java`
- `src/integrationTest/resources/application-integration.yml` 또는 `DynamicPropertySource`만으로 충분하면 이 파일은 만들지 않는다.

### 승인 후 예상 생성 unit/contract test 파일

- `src/test/java/web/tosunsaeng/global/contract/BlogMvpApiContractTest.java`
- `src/test/java/web/tosunsaeng/global/exception/GlobalExceptionAdvicePrivacyTest.java`
- 필요하면 `src/test/java/web/tosunsaeng/global/config/RuntimeConfigurationSecurityTest.java`

### 승인 후 생성 문서

- `docs/blog-mvp/API.md`
- `docs/blog-mvp/OPERATIONS.md`
- `docs/blog-mvp/DEPLOYMENT_CHECKLIST.md`
- `docs/blog-mvp/SECURITY.md`
- `docs/blog-mvp/TESTING.md`

### 수정하지 않는 파일

- blog/comment/newsletter/exams의 공개 Controller와 DTO
- Campaign, Delivery, Subscriber, BlogPost와 Comment Document field 및 enum
- Mongo index 정의 자체. 실제 서버 검증에서 결함이 재현되면 수정 전 사용자에게 계획 차이를 보고한다.
- newsletter Scheduler 주기, retry 정책, kill switch와 EmailSender 계약
- `Dockerfile`
- 운영 AWS와 인프라 설정

### 별도 승인 대상 파일

- `Dokerfile`: 삭제를 권고하지만 계획 전체 승인과 별개로 삭제가 명시적으로 승인된 경우에만 제거한다. 승인되지 않으면 그대로 두고 문서에 미사용 파일임을 기록한다.

## API 변경

없음. Phase 08은 새 mapping, redirect, compatibility endpoint와 관리자 endpoint를 추가하지 않는다.

전체 application의 `RequestMappingHandlerMapping`과 MockMvc/OpenAPI를 이용해 다음을 검증한다.

- 공개 blog/comment/newsletter API의 method, path, consumes와 주요 DTO 계약
- internal 여섯 endpoint만 `/internal/**`에 존재하고 Swagger에서 숨겨짐
- one-click은 POST form/multipart만 상태를 바꾸고 GET은 handler가 없거나 405이며 Service를 호출하지 않음
- 게시글 쓰기 네 method, public 댓글 PATCH/DELETE, internal 댓글 DELETE, newsletter verify와 GET unsubscribe 부재
- exams summary와 questions의 기존 path 및 query parameter 계약
- 기존 exams의 다른 endpoint는 Phase 08 exact-set assertion에서 삭제 대상으로 취급하지 않고 회귀 실행만 한다.

API 부재 검증은 HTTP status 하나만 믿지 않는다. Security 또는 fallback handler가 반환한 404/405와 실제 mapping 부재를 구분하기 위해 handler mapping introspection과 Service 미호출을 함께 확인한다.

## DB 변경

제품 Document, collection, field, 상태와 운영 index 정의 변경은 계획하지 않는다.

- Testcontainers MongoDB 안에 격리된 test database와 현재 initializer가 정의한 index만 생성한다.
- 각 test는 고유 database 또는 collection cleanup으로 서로 격리하며 로컬 compose와 운영 DB에 연결하지 않는다.
- unique 충돌 데이터와 concurrency fixture는 container 종료와 함께 폐기한다.
- 실제 검증에서 index key 순서, 이름 또는 query 조건의 결함이 발견되면 Phase 08을 완료하지 않고 재현 증거, 영향과 필요한 최소 source 변경을 사용자에게 보고한다.
- 운영 index 생성·drop, 데이터 backfill, migration과 collection 삭제는 Codex가 실행하지 않는다.

## 상태 전이

새 상태와 전이는 추가하지 않는다. 아래 기존 전이를 실제 MongoDB에서 검증한다.

| 대상 | 허용 전이 | 검증 핵심 |
|---|---|---|
| 댓글 | VISIBLE/PENDING -> HIDDEN | 동시 요청 winner 한 건, reason/time 기록 |
| 댓글 | HIDDEN -> VISIBLE | metadata unset, 동시 winner 한 건 |
| Subscriber | UNSUBSCRIBED -> ACTIVE | tokenVersion 정확히 1 증가 |
| Subscriber | ACTIVE/BOUNCED -> UNSUBSCRIBED | id + tokenVersion fencing, 멱등 해지 |
| Campaign | SCHEDULED -> SENDING | due 정렬, 원자 claim, SecureRandom token |
| Campaign | SCHEDULED -> CANCELED | 조건부 winner 한 건 |
| Campaign | SENDING -> SENT/FAILED | 모든 Delivery 최종 상태와 count 집계 |
| Delivery | PENDING/eligible FAILED -> SENDING | 원자 claim과 old-token fencing |
| Delivery | SENDING -> SENT/FAILED/SKIPPED | 현재 claimToken만 완료 가능 |
| stale Delivery | provider 시작 전 SENDING -> PENDING | claim 제거, attempt 증가 없음 |
| stale Delivery | provider 시작 후 SENDING -> FAILED | PROVIDER_RESULT_UNKNOWN, retry 불가 |

SENT Delivery, PROVIDER_RESULT_UNKNOWN, CANCELED/SENT Campaign과 같은 terminal 상태의 재발송 경로가 없는지도 부정 테스트한다.

## MongoDB 통합 전략

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
|---|---|---|---|
| Testcontainers MongoDB·Redis | 개발자와 CI가 같은 image/version을 사용하고 test별 격리·자동 수명주기를 얻는다. 실제 unique, Lua와 동시성을 재현할 수 있다. | test dependency와 image pull이 추가되고 Docker daemon이 필수이며 CI 시간이 늘어난다. | 권고 |
| `compose.local.yml`의 서비스 직접 사용 | 새 container test dependency가 없고 현재 compose를 재사용한다. | named volume 오염, 고정 port 충돌, 수동 기동·정리, 병렬 실행과 CI 재현성이 약하다. | 보조 수동 smoke 용도 |
| CI service container만 사용 | CI wiring은 단순하고 Testcontainers lifecycle code가 없다. | 로컬과 CI 경로가 달라지고 test별 database 격리와 image lifecycle을 별도로 관리해야 한다. | 비권고 |

### 권고 구성

- `org.testcontainers:testcontainers-bom`을 test scope에만 적용하고 JUnit Jupiter와 MongoDB module을 추가한다. Redis는 `GenericContainer`를 사용한다.
- image는 `mongo:7.0`, `redis:7.2-alpine`으로 현재 compose와 맞춘다.
- `src/integrationTest` source set과 `integrationTest` task를 만들고 unit `test`와 분리한다.
- integration task는 parallel fork를 1로 제한하고 suite 내부의 명시적 concurrency test만 executor/barrier로 병렬화한다.
- container unavailable을 성공적인 skip으로 처리하지 않는다. release/CI integration task에서 Docker를 사용할 수 없으면 실패 또는 BLOCKED다.
- application `test` 안전 설정과 `DynamicPropertySource`로 container URI/host/port를 주입하며 실제 AWS/Sentry/SES는 비활성화한다.
- index initializer는 test profile에서 자동 실행되지 않으므로 integration test가 실제 initializer를 명시적으로 두 번 실행하고 `getIndexInfo`로 이름, key 순서, unique와 idempotence를 확인한다.

### 게시글 검증

- slug unique index에 같은 slug 두 건을 넣어 두 번째가 실제 `DuplicateKeyException`인지 확인한다.
- status + publishedAt 및 newsletter reconciliation index의 실제 key와 이름을 확인한다.
- PUBLISHED/current 및 과거 글만 공개되고 미래, null, DRAFT와 ARCHIVED가 제외되는지 확인한다.
- 같은 publishedAt/createdAt fixture에서 `_id`까지 안정 정렬되는지 확인한다.
- `.*`, `[`, `+` 같은 regex meta 문자가 literal title 검색으로만 동작하는지 확인한다.
- related slug batch query가 공개 조건을 유지하고 query 횟수를 늘리지 않는지 확인한다.

### 댓글 검증

- anonymous visitor tokenHash unique와 세 comment index를 실제 생성한다.
- 공개 목록이 VISIBLE만 반환하고 createdAt, `_id` 정렬을 지키는지 확인한다.
- 운영 `[from,to)` 경계를 실제 저장 데이터로 확인한다.
- 두 thread가 같은 comment를 hide 또는 restore할 때 조건부 update 하나만 성공하는지 확인한다.
- moderation Service 결과의 distinct postId batch slug 보강은 실제 결과와 기존 mock의 단일 `findAllById` 호출 계약을 함께 사용해 N+1 부재를 확인한다.

### Subscriber 검증

- 정규화 email unique index와 status index를 실제 생성한다.
- 같은 신규 email의 동시 subscribe가 Document 하나와 두 성공 결과로 수렴하는지 확인한다.
- `DuplicateKeyException` loser의 상태 재조회 경로를 실제 server에서 확인한다.
- 같은 UNSUBSCRIBED email 동시 재구독이 ACTIVE 하나, tokenVersion +1 한 번으로 끝나는지 확인한다.
- 재구독과 오래된 token 해지 경쟁에서 과거 token이 새 ACTIVE를 해지하지 못하는지 확인한다.
- ACTIVE, BOUNCED, UNSUBSCRIBED 해지와 멱등성을 실제 조건부 update로 확인한다.

### Campaign과 Delivery 검증

- Campaign postId unique와 Delivery postId + subscriberId unique의 실제 충돌을 확인한다.
- 두 worker가 같은 due Campaign 또는 Delivery를 동시에 claim하면 한 Optional만 성공하는지 barrier로 검증한다.
- 이전 claimToken의 heartbeat, provider 시작, 완료와 stale update가 모두 실패하는지 확인한다.
- provider 전/후 stale recovery와 manual/automatic retry query를 실제 저장 상태로 검증한다.
- `countByCampaign`이 retryable FAILED를 terminal로 조기 계산하지 않고 retry 불가 또는 최대 attempt FAILED만 집계하는지 확인한다.
- 205개 이상의 fixture로 subscriber `_id` cursor batch와 manual retry 100 + hasMore를 검증한다.

## Redis 통합 전략

### 댓글 Lua

- visitor 1회/10초, 5회/10분, 20회/하루 및 IP 10회/10분, 50회/하루 기본 경계를 검증한다.
- integration 전용 짧은 window property를 사용해 fixed-window 경계와 expiry 후 신규 counter를 검증하되 제품 기본값은 바꾸지 않는다.
- blocker가 하나라도 있으면 나머지 counter와 duplicate reservation이 mutation되지 않는지 raw Redis 값을 확인한다.
- 새 key TTL만 설정되고 반복 허용 요청이 기존 TTL을 연장하지 않는지 허용 오차를 둔 PTTL로 확인한다.
- 같은 duplicate key의 경쟁에서 한 owner만 reservation을 얻고 다른 요청은 거절되는지 확인한다.
- 다른 owner cleanup은 0, 현재 owner cleanup은 1이고 key가 삭제되는지 확인한다.
- Mongo save 실패를 service 수준에서 유도해 현재 owner reservation만 cleanup되고 rate counter는 유지되는지 확인한다.
- 동시 요청을 barrier로 시작해 허용량을 초과한 ALLOWED 결과가 없는지 확인한다.

### newsletter Lua

- IP 10분 10회와 하루 30회, 11번째/31번째 차단을 실제 Lua로 검증한다.
- 두 counter가 함께 증가하고 blocker가 있으면 어느 counter도 증가하지 않는지 확인한다.
- 여러 blocker의 최대 TTL이 `Retry-After` 계산에 사용되는지 repository 결과와 service exception을 연결해 확인한다.
- 새 key TTL과 기존 TTL 비연장, expiry 후 새 window를 확인한다.
- 동시 요청에서도 실제 허용 수가 limit을 넘지 않는지 확인한다.
- 별도 일회용 Redis container를 중단하거나 연결 불가 endpoint를 유한 timeout으로 사용해 연결 장애 fail-open을 확인한다. Lua/codec 오류는 fail-open하지 않는 기존 unit contract를 유지한다.

### topology 제한

- 검증 환경은 standalone/single-primary만 사용한다.
- `SECURITY.md`, `OPERATIONS.md`와 배포 checklist에 Redis Cluster 미지원과 multi-key Lua의 CROSSSLOT 위험을 명시한다.
- Cluster 지원을 위한 hash tag, script 분할 또는 부분 원자 fallback을 Phase 08에 추가하지 않는다.

## Scheduler 통합 전략

- `@Scheduled` background thread는 integration test에서 비활성화하고 Application Service method를 test가 직접 호출해 결정성을 확보한다.
- 실제 Mongo Repository, 주입 가능한 mutable UTC Clock과 deterministic fake `NewsletterEmailSender`를 사용한다.
- Scheduler adapter의 fixedDelay, bounded executor와 rejection delegation은 기존 unit test를 유지하고 실제 상태 전이는 service integration test가 담당한다.
- PUBLISHED, publishedAt 존재, newsletterEnabled=true만 Campaign을 만들고 null/false, DRAFT, ARCHIVED를 제외한다.
- 정확한 15분 예약, 지난 글의 now 예약과 postId unique를 확인한다.
- 두 reconciliation/claim worker 동시 실행에서 Campaign/Delivery가 중복되지 않는지 확인한다.
- 205 ACTIVE Subscriber를 batch 100으로 처리하고 같은 campaign 재실행에도 Delivery가 하나씩만 있는지 확인한다.
- kill switch false에서 reconciliation 이외 claim/provider/retry가 없고 SENDING 상태를 강제 변경하지 않는지 확인한다.
- 같은 properties를 true로 전환한 뒤 SCHEDULED/PENDING backlog가 처리되는지 확인한다.
- provider 전 stale는 PENDING, provider 후 stale는 retry 불가 PROVIDER_RESULT_UNKNOWN인지 확인한다.
- fake sender가 순서대로 transient/success 또는 unknown 결과를 반환하도록 해 최대 4회와 5분, 30분, 2시간을 Clock으로 이동해 검증한다.
- 일부 final failure와 skipped-only Campaign의 최종 FAILED/SENT 및 count/completedAt을 검증한다.
- 자동화 test는 `SesNewsletterEmailSender` network 호출을 만들지 않는다.

## Security 통합 전략

- 기존 `InternalApiSecurityContractTest`의 실제 `FilterChainProxy`와 MockMvc 검증을 유지한다.
- disabled + key 존재도 404, enabled + key 누락/오류 401, 정상 key 성공을 모든 internal method의 representative request로 확인한다.
- JWT만으로 internal 접근 불가, API key만으로 fixed `ROLE_INTERNAL`, 공개 API는 internal key 없이 접근 가능함을 확인한다.
- internal chain이 public/JWT chain보다 먼저이고 matcher가 `/internal/**`에만 일치하는지 확인한다.
- public blog/comment/newsletter/one-click/exams 및 Swagger 접근과 기존 CORS/CSRF 설정을 회귀 검증한다.
- OpenAPI JSON에 internal Controller가 없고 public API가 남는지 확인한다.
- production-equivalent smoke 절차에는 ALB/SG/firewall의 source 제한, 401/404 metric과 audit log 확인을 넣는다.
- 단일 static key의 재시작 rotation, dual-key grace period 부재와 operator 식별 불가 한계를 문서화한다.

## SES 수동 검증

Codex는 AWS에 접속하거나 실제 이메일을 보내지 않는다. 운영자는 승인된 비운영 recipient와 계정으로 다음을 수행한다.

1. 배포 region과 `NEWSLETTER_FROM_EMAIL`, SES domain identity가 같은 region에 있는지 확인한다.
2. Easy DKIM 또는 BYODKIM 상태와 필요한 DNS record의 verified 상태를 캡처한다.
3. sandbox 여부, production access 승인, 24시간 quota와 초당 rate를 확인한다.
4. EC2 instance role 또는 표준 credential chain에 최소 `ses:SendEmail` 권한이 있고 static key를 새로 넣지 않았는지 확인한다.
5. `NEWSLETTER_EMAIL_PROVIDER=ses`, master/test switch와 test allowlist를 검토하고 허용된 비운영 주소만 사용한다.
6. SDK contract test로 SES Simple Message, HTML/plain, custom header와 SDK retry 0을 먼저 확인한다.
7. 운영자가 한 건의 통제된 test mail을 발송한 뒤 SES console/event에서 acceptance와 message ID를 확인한다. Codex는 이 호출을 하지 않는다.
8. 수신 원문에서 From, subject, UTF-8, HTML/plain, UTM URL, 수동 해지 URL과 custom header를 확인한다.
9. DKIM `Authentication-Results`와 `DKIM-Signature h=`에 두 List-Unsubscribe header가 보호되는지 확인한다.
10. 결과, 시각, region, identity, message ID의 마스킹된 증적과 실패 원인을 deployment checklist에 기록한다.

실제 campaign Delivery의 providerMessageId는 MongoDB에 저장되지만 test send는 Campaign/Delivery를 만들지 않아 저장하지 않는다. 이 차이를 운영 문서에 명시한다.

## RFC 8058 검증

### 자동 검증

- 정상 active/previous key token의 form POST가 204, no-store, redirect 없음과 UNSUBSCRIBED 전이를 만든다.
- form field가 정확히 `List-Unsubscribe=One-Click`일 때만 상태가 바뀐다.
- GET, JSON, 잘못된 field/token은 상태를 변경하지 않는다.
- cookie, session, login과 JWT 없이 처리된다.
- tokenVersion이 바뀐 과거 token은 현재 ACTIVE를 해지하지 않는다.
- response에 email, subscriberId와 tokenVersion이 없다.
- header URL은 `NEWSLETTER_API_BASE_URL`, 본문 link는 `NEWSLETTER_PUBLIC_BASE_URL`을 사용한다.

### 수동 운영 검증

- 실제 수신 원문에서 두 header와 HTTPS URL을 확인한다.
- `curl` 또는 승인된 client로 redirect를 따르지 않고 form POST해 204와 상태 전이를 확인한다.
- Gmail 등 대상 client에서 unsubscribe UI가 노출되는지 확인하되 UI 노출은 provider/client 정책상 보장되지 않음을 기록한다.
- automatic scanner와 manual frontend link가 의도치 않은 GET 상태 변경을 만들지 않는지 확인한다.
- one-click path token이 ALB, proxy, CDN, application access log와 Sentry transaction name에 남지 않는지 sentinel token으로 검사한다.

token을 POST body로 옮기는 방식은 RFC client가 고정 form field 외 application-specific token을 보내지 못할 수 있어 자동 변경하지 않는다. path token 노출을 안전하게 해소하지 못하면 별도 API 계약 변경안으로만 제시하고 사용자 승인 전 현재 endpoint를 바꾸지 않는다.

## 아바타 이미지 검증

현재 저장소만으로 실제 제공 방식을 확정할 수 없다.

- backend에는 asset이 없고 `BLOG_ANONYMOUS_AVATAR_BASE_URL` 및 외부 `avatar-options`만 있다.
- `.env.example`은 CloudFront 주소를 배포 환경에서 주입하라고 안내하지만 CloudFront/S3 IaC나 실제 domain은 저장소에 없다.
- test profile의 `https://cdn.example.test`와 example image key는 운영 방식의 증거가 아니다.

운영자는 먼저 frontend 정적 파일, CloudFront + private S3, public S3 중 실제 방식을 선언하고 다음 증적을 제공한다.

- 배포 avatar-options의 모든 imageKey manifest
- 각 HTTPS URL의 200, MIME type, cache-control과 필요한 CORS
- 누락 key의 동작과 fallback 정책
- 기존 key의 불변성, version suffix와 삭제·덮어쓰기 금지 정책
- base URL trailing slash와 path segment encoding 결과
- `..`, absolute URL, query/fragment 같은 path traversal 입력 거절

CloudFront + private S3라면 public access block, OAC, 최소 bucket policy, custom domain/ACM certificate와 cache invalidation 정책을 추가 확인한다. Codex는 해당 AWS 자원을 변경하지 않는다.

## 개인정보 및 로그 검증

### 자동 contract

- 고정 sentinel API key, Authorization, Cookie, anon_session, token, email, comment content와 IP를 test request에 넣는다.
- Sentry event/transaction callback 결과의 header, env, data, query, cookie, URL과 user context에 sentinel이 없는지 확인한다.
- one-click event URL은 `{token}`이고 transaction은 drop되는지 확인한다.
- internal 및 public error BaseResponse, captured application log와 exception message에 sentinel이 없는지 확인한다.
- Mongo duplicate, Redis error와 provider exception의 원문이 response/log에 노출되지 않는지 확인한다.

### 계획된 최소 보안 수정

- `GlobalExceptionAdvice`의 `printStackTrace()`를 제거한다.
- 예상하지 못한 exception과 JSON parse exception의 raw `getMessage()`를 log 및 BaseResponse result에 넣지 않고 정제된 code/message를 사용한다.
- Sentry capture는 callback redaction을 거치게 유지하되 중복 capture와 raw request body가 없는지 test한다.
- JWT fixed fallback을 제거하고 main에서 외부 `JWT_SECRET_KEY` 누락/짧은 값이 안전하게 실패하도록 하며 test profile에는 명확한 dummy key를 둔다.
- Sentry DSN은 환경변수로 외부화하고 PII 전송 기본값을 false로 바꾸며 test/integration에서는 outbound 전송을 비활성화한다.

이 변경은 보안상 권고하지만 기존 generic 오류 result와 기동 설정에 영향을 준다. Phase 08 계획 승인 시 이 최소 수정까지 명시적으로 승인받는다. 승인되지 않으면 release blocker로 남기고 Phase 08을 완료 처리하지 않는다.

### 수동 운영 검증

- 실제 secret 대신 고유한 비운영 sentinel을 사용한다.
- application log, container log, reverse proxy/ALB/CDN access log와 비운영 Sentry project를 검색한다.
- `X-Internal-Api-Key`, Authorization, Cookie, anon_session, unsubscribe/one-click token, email 전체, comment 전체, IP 원문, HMAC/AWS credential와 provider payload가 한 건도 없어야 한다.
- frontend unsubscribe query token에 `Referrer-Policy: no-referrer`, third-party resource 제한, query redaction과 사용자 확인 전 POST 금지를 확인한다.
- log retention, access 권한과 삭제 절차를 deployment/security 문서에 기록한다.

## CI/CD 검증

- Java toolchain, workflow setup과 `Dockerfile`의 Java 21 일치를 유지한다.
- `build.gradle`에 test-only Testcontainers dependency와 별도 `integrationTest` task를 추가한다.
- workflow를 `clean test`, `integrationTest`, `bootJar` 순으로 실행하고 어느 단계든 실패하면 Docker build/push와 SSH 배포에 진입하지 않게 한다.
- integration profile은 dummy secret, logging/fake sender, sending=false, Sentry disabled를 사용하고 실제 AWS secret을 요구하거나 전송하지 않는다.
- GitHub hosted runner의 Docker로 Testcontainers image를 pull하되 image version을 pin한다.
- main push 자동 배포 정책은 변경하지 않지만 branch protection, environment approval와 rollback artifact 보존 여부를 운영 위험으로 기록한다.
- `docker compose -f compose.local.yml config`를 정적 검증하고 Mongo/Redis health check를 수동 smoke checklist에 포함한다.
- `bootJar` 뒤 실제 `Dockerfile`로 local image build를 수행하되 push/login은 하지 않는다.
- 현재 sandbox는 Docker daemon 접근이 불가하므로 승인된 구현 세션에서 필요한 권한이 제공되지 않으면 CI의 성공 build 증적 또는 사용자의 로컬 실행 결과가 필요하다. 증적 없이 Docker 검증을 성공으로 기록하지 않는다.

### `Dokerfile` 제안

- 실제 CI 참조는 `Dockerfile` 하나이며 Java 21이다.
- Java 17 `Dokerfile`은 사용되지 않고 오사용 위험만 남긴다.
- 삭제를 권고한다. 다만 사용자 명시적 삭제 승인 전에는 보존하고 `DEPLOYMENT_CHECKLIST.md`에 사용 금지를 적는다.

## API 문서 계획

`docs/blog-mvp/API.md`에는 실제 Controller, DTO, `SuccessStatus`와 `ErrorStatus`를 근거로 다음을 기록한다.

- 공개 blog/comment/newsletter endpoint의 method, path, query, request와 성공 result
- internal 여섯 endpoint, API key header, disabled/401/403/404/409/502 계약
- BaseResponse 공통 구조와 HTTP/code/message/result 예시
- 게시글 pagination, public 판정, 검색 literal 처리와 제공하지 않는 write API
- 댓글 rule 1~10, rule 3 trim, 오름차순 violation, cookie/profile과 429 Retry-After
- subscribe 즉시 ACTIVE, BOUNCED 정책, JSON POST unsubscribe와 제공하지 않는 verify/PENDING
- RFC 8058 form POST, 204, GET 무상태와 no redirect
- Campaign/Delivery 상태를 public response로 노출하지 않는 원칙
- exams summary/questions의 기존 계약과 다른 exams API는 본 Phase에서 변경하지 않는다는 범위
- 명시적으로 제공하지 않는 endpoint 목록

문서 예시는 실제 email, token, key와 운영 ID가 아닌 고정 placeholder만 사용한다.

## 운영 문서 계획

### `OPERATIONS.md`

- MongoDB로 BlogPost를 등록할 때 필수 field, slug와 PUBLISHED/publishedAt/newsletterEnabled 절차
- Campaign/Delivery 상태와 count 조회, backlog 및 claim 확인
- kill switch false 기본, 변경 시 restart/배포와 in-flight 한계
- 내부 API를 이용한 test send, cancel, retry batch/hasMore
- 댓글 목록, hide와 restore
- API key 일시 중단을 포함한 단일 key rotation 순서
- SES identity, DKIM, sandbox, quota와 IAM 확인
- PROVIDER_RESULT_UNKNOWN, Redis 장애, Mongo duplicate/index 실패와 backlog 대응
- 정확히 한 번 발송을 보장하지 않는다는 명시

### `DEPLOYMENT_CHECKLIST.md`

- 모든 환경변수와 secret separation
- JWT/Sentry/AWS credential 및 default false 확인
- Mongo index와 duplicate 사전 검사
- Redis standalone topology, health, eviction와 persistence
- SES/DKIM/identity/quota/allowlist
- avatar origin과 asset manifest
- internal API disabled/enabled smoke와 network boundary
- Sentry/access log redaction sentinel 검사
- public/internal/exams smoke, bootJar, Docker/compose
- rollback artifact, kill switch와 no-go 조건

### `SECURITY.md`

- API key constant-time digest, single key와 rotation 한계
- anonymous/comment/newsletter HMAC secret 분리와 token key rotation
- stateless bearer token, path/query log 위험과 response/cache 정책
- email/comment/IP 개인정보와 Sentry/log 처리
- Redis fail-open 및 fixed-window burst
- Redis Cluster 비지원과 CROSSSLOT
- SES result unknown과 exactly-once 미보장
- bounce/complaint webhook 부재
- 기존 S3 static credential 구성과 exams common-pool 위험을 알려진 범위 밖 위험으로 기록

### `TESTING.md`

- 421개 기존 unit/contract baseline
- `test`와 `integrationTest` 책임, Docker requirement와 image version
- Mongo/Redis/Scheduler/Security/Sentry/API absence 범주
- deterministic Clock/fake sender와 실제 SES 금지
- 실행 명령, test profile dummy environment와 cleanup 범위
- 수동 SES/RFC/avatar/internal/log checklist와 아직 미검증인 항목 표기

## 전체 테스트 계획

1. 기존 421개 이상 unit/contract 회귀를 failures/errors/skipped 0으로 유지한다.
2. MongoDB actual index와 unique 테스트를 실행한다.
3. blog public/search/related actual query를 실행한다.
4. comment query와 원자 moderation을 실행한다.
5. Subscriber unique, duplicate recovery, tokenVersion과 해지 동시성을 실행한다.
6. Campaign/Delivery unique, claim fencing, stale, retry와 count를 실행한다.
7. Redis comment Lua, TTL, duplicate owner와 concurrency를 실행한다.
8. Redis newsletter Lua, TTL, atomic increment, Retry-After와 fail-open을 실행한다.
9. Scheduler service concurrency, kill switch와 최대 4회 retry를 실행한다.
10. 두 SecurityFilterChain, internal authentication와 공개 API를 실행한다.
11. one-click active/previous token, form, no redirect와 GET 무상태를 실행한다.
12. index initializer 반복 실행과 오류 전파를 실행한다.
13. Sentry 및 일반 exception redaction contract를 실행한다.
14. API mapping exact/absence와 OpenAPI hidden을 실행한다.
15. 환경변수 누락 fail-fast, sending/internal 기본 false를 실행한다.
16. exams summary/questions 및 기존 전체 exams 회귀를 실행한다.
17. `bootJar`를 생성하고 artifact 존재를 확인한다.
18. Compose config와 health definition을 검증한다.
19. Docker daemon이 허용된 환경에서 image를 build하고 Java 21 startup을 확인한다.
20. 다섯 Markdown 문서를 실제 코드와 상호 대조하고 placeholder에 secret/PII가 없는지 검수한다.

### 필수 명령

승인 구현의 최종 검증 후보는 다음 순서다.

```bash
git diff --check
bash ./gradlew clean test
bash ./gradlew integrationTest
bash ./gradlew bootJar
docker compose -f compose.local.yml config
docker build -t to-teacher-backend:phase08 .
```

한 번의 Gradle build graph 검증도 별도로 실행한다.

```bash
bash ./gradlew clean test integrationTest bootJar
```

- Docker build는 image push, login과 deployment를 하지 않는다.
- Docker socket을 사용할 수 없으면 권한 오류를 기록하고 CI 또는 사용자 환경의 같은 command 성공 증적을 받는다.
- integration test가 skip되거나 container가 시작되지 않으면 성공으로 간주하지 않는다.
- `git diff --check`, Gradle, integration, bootJar, compose 또는 Docker 검증이 실패하면 Phase 08을 DONE으로 바꾸지 않는다. Docker만 외부 권한 때문에 실행할 수 없는 경우에도 대체 CI 증적 없이는 출시 승인하지 않는다.

## 최종 정적 검수

다음 분류 기준으로 `rg`, handler mapping, Gradle compile과 diff를 함께 사용한다.

| 검색 항목 | 처리 기준 |
|---|---|
| TODO/FIXME | 제품 source의 미해결 항목은 owner와 release 영향이 없으면 문서화, 핵심 경로면 차단 |
| `System.out`/`printStackTrace` | main source에서는 release blocker, 제거 후 test |
| hardcoded secret/AWS key | 실제 또는 fallback credential이면 blocker, 명확한 test dummy만 허용 |
| email/token/body log | blocker, sentinel contract와 source 수정 |
| 금지 API/Subscriber PENDING | blocker, 승인 없이 compatibility endpoint 추가 금지 |
| 미사용 Controller/dead code | mapping/참조를 확인해 안전한 경우만 제거 제안, 임의 삭제 금지 |
| 중복 Security matcher | internal/public chain 충돌이면 blocker |
| 무제한 executor/queue | newsletter에서 blocker, 기존 exams 발견은 범위 밖 위험으로 기록 |
| sending default true | blocker, false로 유지 |
| index 이름/key 충돌 | 실제 Mongo 재현 시 blocker |
| 구 package import | blocker |
| `Dokerfile` | 사용자 삭제 승인 전 문서화된 stale file |

검색 결과는 단순 문자열 일치만으로 판정하지 않는다. comment/Delivery/exams의 합법적 PENDING, test dummy email/token, 문서의 금지 예시는 context를 확인한다.

## 위험 요소

- Testcontainers는 Docker daemon과 image pull이 필요해 로컬 및 CI 시간이 늘고 네트워크 장애에 영향을 받는다.
- 현재 Codex sandbox에서 Docker socket이 차단돼 계획 수립 시 실제 container/image를 확인하지 못했다.
- 실제 MongoDB concurrency test는 재현성을 높이지만 모든 production latency, replica failover와 network partition을 증명하지 않는다.
- Redis TTL assertion은 wall clock 경계에 민감하므로 PTTL 허용 범위와 bounded polling이 필요하다.
- Redis Cluster로 전환하면 multi-key Lua가 CROSSSLOT로 실패할 수 있다.
- SES 수락은 최종 inbox delivery를 뜻하지 않고 bounce/complaint 처리가 없다.
- RFC 8058 UI 노출은 Gmail 등 client 정책, sender reputation, DKIM와 발송량에 좌우된다.
- bearer token이 path/query에 있어 application 밖 log와 browser history에서 유출될 수 있다.
- 실제 avatar host와 asset manifest가 저장소 밖이라 운영자 증적 없이는 완전 검증할 수 없다.
- single internal API key는 operator별 권한/audit가 없고 무중단 rotation을 지원하지 않는다.
- `GlobalExceptionAdvice` 보안 수정은 일부 기존 generic error result를 바꿀 수 있어 API regression test와 명시적 승인이 필요하다.
- JWT fallback 제거는 환경변수를 누락한 기존 배포를 fail-fast시키므로 deployment checklist와 배포 전 secret 준비가 필수다.
- main push가 곧 Docker push와 SSH 배포로 이어져 integration test의 시간 증가와 external registry/host 가용성이 배포에 영향을 준다.
- 기존 exams common-pool 비동기 실행과 `Dokerfile`은 Phase 08 기본 수정 범위 밖의 알려진 위험이다.

## 롤백 방법

- 구현 전에는 이 계획서와 상태 문서의 Phase 08 변경만 이전 Session Log를 보존하는 수동 역패치로 되돌린다.
- Testcontainers 문제가 있으면 새 integration source set, test-only dependency, Gradle task와 CI integration step을 후속 수동 patch로 제거하고 기존 421개 unit test 경로를 보존한다.
- 보안 설정 수정 rollback은 이전 fallback secret을 복원하지 않는다. 필요한 환경변수를 먼저 주입하고 검증된 artifact로 되돌리는 운영 절차를 사용한다.
- 문서 오류는 해당 Markdown만 후속 patch로 정정하고 code/API를 문서에 맞춰 임의 변경하지 않는다.
- `Dokerfile`을 명시적으로 삭제 승인받은 뒤 되돌릴 필요가 생기면 사용자 보유 이력의 동일 내용을 새 patch로 복원한다.
- Testcontainers cleanup은 전용 ephemeral container와 test database에만 한정하며 local named volume과 운영 data를 삭제하지 않는다.
- Docker image, Mongo index, Redis key, AWS resource와 실제 이메일을 Codex가 운영 환경에서 rollback하지 않는다.
- `git reset`, `git restore`, `git checkout`, `git clean`, `git stash`와 사용자 변경을 되돌리는 명령을 사용하지 않는다.

## 완료 조건

- 사용자가 이 DRAFT 계획과 결정 항목을 명시적으로 승인한 뒤에만 `APPROVED` 및 Phase 08 `IN_PROGRESS`로 전환한다.
- 승인된 예상 파일 범위와 최소 검증 수정만 구현한다.
- 기존 421개 이상 test와 새 integration/contract test가 failures/errors/skipped 0이다.
- MongoDB 실제 index, unique, query, atomic claim/fencing과 concurrency가 성공한다.
- Redis 실제 Lua, TTL, duplicate owner, atomicity와 concurrency가 성공한다.
- Scheduler의 opt-in, delay, batch, kill switch, stale, retry와 completion이 실제 Mongo + fake sender로 성공한다.
- 공개/internal/exams 계약과 금지 API 부재가 handler mapping과 MockMvc에서 성공한다.
- API key, Sentry redaction, one-click과 개인정보 contract가 성공한다.
- hardcoded JWT fallback, `printStackTrace`, raw exception response와 PII true 기본값이 승인된 방식으로 제거된다.
- API, OPERATIONS, DEPLOYMENT_CHECKLIST, SECURITY와 TESTING 문서가 완성된다.
- `git diff --check`, Gradle unit/integration/bootJar, compose config와 Docker build가 성공하거나 Docker build의 동등한 CI 증적이 있다.
- 실제 SES/email client/avatar/internal network/Sentry/access log는 Codex가 검증했다고 가장하지 않고 운영 checklist와 미검증 상태가 명확하다.
- 실패나 승인 계획 차이가 하나라도 있으면 계획을 EXECUTED 또는 Phase 08을 DONE으로 변경하지 않는다.

## 출시 승인 기준

Phase 08 code/document 완료와 실제 출시 GO를 구분한다.

### 자동화 GO 조건

- unit, integration, bootJar, compose와 Docker image build 전부 성공
- critical/high static finding 미해결 0건
- API contract 및 금지 기능 위반 0건
- sending/internal safe default와 secret fail-fast 확인
- 문서와 실제 source/config 불일치 0건

### 운영자 수동 GO 조건

- Mongo index 및 duplicate 사전 점검 승인
- Redis standalone topology와 health/eviction 확인
- SES identity, DKIM, sandbox 해제, quota, IAM과 controlled test evidence
- RFC 8058 header/DKIM/POST와 대상 email client evidence
- avatar origin, 모든 image key와 CDN/S3 보안 evidence
- internal API network boundary, disabled/enabled smoke와 key rotation 준비
- Sentry, application, proxy/CDN/ALB log sentinel redaction evidence
- rollback artifact, kill switch, 담당자와 장애 대응 연락 체계

운영자 증적이 없으면 Phase 08 문서 작성은 완료할 수 있어도 출시는 `NO-GO`로 표시한다. Codex는 실제 AWS 변경, test email, registry push와 배포를 수행하지 않는다.

## 승인 시 확정된 결정

1. MongoDB/Redis Testcontainers와 별도 `integrationTest` task 도입을 승인받았다.
2. CI main 배포 전에 `integrationTest`를 필수 gate로 추가했다.
3. `GlobalExceptionAdvice` raw message/stack trace 제거, JWT fallback 제거, Sentry DSN 외부화와 PII false 기본값을 적용했다.
4. 저장소 참조가 없는 Java 17 `Dokerfile` 삭제를 승인받아 제거했다.
5. MVP avatar는 `https://to-teacher.com/character-image/{filename}`의 frontend 정적 파일로 확정했다. 실제 filename manifest와 배포는 frontend 담당자 과제다.
6. SES/DKIM/Gmail은 메일 운영자, Sentry는 보안/관측 담당자, ALB/firewall은 인프라 담당자가 수동 증적을 만든다.
7. 로컬 Docker daemon을 이용한 integrationTest, image build와 Java 21 확인이 성공했다. 실제 CI run URL은 아직 운영 checklist 항목이다.

## 실제 구현 중 발생한 차이

- 별도 `src/integrationTest` source set과 task, MongoDB 5개 suite, Redis 2개 suite를 계획대로 만들었다. 별도 integration resource 파일은 필요하지 않아 `DynamicPropertySource`와 직접 container endpoint를 사용했다.
- Spring Boot 3.4.2가 관리하는 Testcontainers 1.20.4는 Docker Engine 29에서 기본 API 1.32 요청이 HTTP 400으로 거절됐다. Docker API 1.32는 400, 1.44는 200임을 확인한 뒤, API 1.44를 기본 사용하는 호환 가능한 1.x 최신 안정판 1.21.4로 BOM과 `testcontainers.version`만 최소 상향했다. Spring, AWS SDK와 다른 의존성은 변경하지 않았다.
- 실제 MongoDB Scheduler test가 동일 millisecond heartbeat에서 update query는 일치하지만 값이 같아 `modifiedCount=0`이 되는 결함을 재현했다. claim 소유 여부는 `matchedCount==1`로 판정하도록 `NewsletterCampaignQueryRepositoryImpl`과 해당 단위 test를 최소 수정했다.
- `GlobalExceptionAdvice`, JWT secret, Sentry callback/config는 승인된 최소 보안 범위만 수정했고 공개·내부 API와 DTO 계약은 바꾸지 않았다.
- 저장소 전체 참조 검색 결과 사용되지 않는 Java 17 `Dokerfile`을 승인 조건에 따라 삭제했다.
- 계획 수립 당시 접근하지 못했던 Docker daemon은 구현 검증 시 사용할 수 있어 로컬 image build와 image 내부 Java 21까지 확인했다.
- 검증 도중 저장소 루트에 생긴 이름만 있는 0바이트 미추적 파일 7개는 재개 직후에는 없었고 source/test 참조도 없는 우발 산출물임을 확인한 뒤 `apply_patch`로 제거했다.

## 검증 결과

- `git diff --check`: 성공
- `bash ./gradlew clean test`: 성공, unit/contract 433개, skipped/failures/errors 0
- `bash ./gradlew integrationTest`: 성공, integration 32개, skipped/failures/errors 0
- `bash ./gradlew bootJar`: 성공, 약 60 MiB 실행 JAR 생성
- `bash ./gradlew clean test integrationTest bootJar`: 단일 clean graph 성공
- `docker compose -f compose.local.yml config`: 성공, MongoDB 7.0과 Redis 7.2-alpine standalone 정의 확인
- `docker build -t to-teacher-backend:phase08 .`: 성공
- `docker run --rm --entrypoint java to-teacher-backend:phase08 -version`: Temurin OpenJDK 21.0.11 확인
- Testcontainers 1.21.4가 `mongo:7.0`, `redis:7.2-alpine` container를 실제 시작했고 skip 없이 전체 suite가 실행됐다.
- 최종 API mapping/Security/Sentry/config contract, one-click POST/GET 무상태, 금지 API와 NewsletterSubscriber PENDING 부재 test가 성공했다.
- main source의 TODO/FIXME, `System.out`, `printStackTrace`, AWS key/private key pattern, hardcoded JWT fallback, 구 package import와 실제 발송 true 기본값이 없음을 정적 확인했다.
- 첫 integration 실행은 Testcontainers 1.20.4와 Docker Engine 29 API 비호환으로 7개 suite 초기화가 실패했다. 1.21.4 적용 후 32개 중 Scheduler 1건이 실제 heartbeat 결함을 발견했고, 최소 수정 뒤 전체 명령을 처음부터 재실행해 최종 성공했다.
- 실제 SES, DKIM, Gmail UI, 운영 Sentry outbound, ALB/proxy/CDN access log, Security Group, 실제 avatar 파일과 운영 이메일은 접근하거나 검증하지 않았다. `DEPLOYMENT_CHECKLIST.md`에서 미완료 상태로 유지한다.
- 자동화 코드 Phase 완료 조건은 충족했지만 운영 수동 GO 조건은 미완료이므로 운영 출시 승인을 의미하지 않는다.
