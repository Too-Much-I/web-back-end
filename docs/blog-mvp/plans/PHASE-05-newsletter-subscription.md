# Phase 05: 뉴스레터 구독 및 구독 해지

- 상태: DRAFT

## 목표

이메일 소유권 인증 없이도 유효한 이메일과 명시적 수신 동의를 제출하면 즉시 `ACTIVE`가 되는 뉴스레터 구독 기능을 설계한다. 정규화된 이메일의 unique 제약과 조건부 MongoDB 갱신으로 신규 구독, 이미 활성 상태인 중복 요청, 해지 후 재구독을 동시 요청에서도 멱등하게 처리한다.

Phase 06에서 실제 이메일을 만들 때 원본 token을 DB에 저장하지 않고도 안전한 구독 해지 링크를 발급할 수 있도록 stateless HMAC 서명 token을 준비한다. 구독 요청에는 newsletter 전용 Redis IP rate limit을 적용하고, 연결 장애와 timeout만 fail-open으로 처리한다.

이번 Phase의 공개 API는 `POST /api/newsletter/subscribe`와 JSON body를 받는 `POST /api/newsletter/unsubscribe` 두 개뿐이다. `GET /api/newsletter/unsubscribe`는 제공하지 않고 GET 요청으로 Subscriber 상태를 변경하지 않는다. 이메일 인증, verify API, `PENDING` 상태, 실제 이메일 Sender, Scheduler, 내부 운영 API는 만들지 않는다.

## 작업 시작 조건

- 계획 수립 시작 시 실제 브랜치는 `feat/blog-mvp`다.
- `git status --short` 출력은 없어 작업 트리가 깨끗하다.
- `scripts/codex-preflight.sh`는 `Preflight: PASS`를 출력했다.
- Phase 00~04는 모두 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-04-comment-abuse-moderation.md`의 상태는 `EXECUTED`다.
- Current phase는 Phase 05이고 계획 수립 전 상태는 `TODO`다.
- 이 계획 수립에서는 이 계획서와 `docs/blog-mvp/IMPLEMENTATION_STATUS.md`만 변경한다.
- 이 계획은 `DRAFT`이며 사용자가 명시적으로 승인하기 전에는 Java, test, Gradle, application 설정과 API를 수정하지 않는다.

## 현재 코드 분석

### newsletter 구현 현황

- `src/main/java/web/tosunsaeng/domain/blog/**`에는 게시글과 댓글 package만 있고 Newsletter Controller, Service, Document, Repository, Sender와 Scheduler는 없다.
- 공개 newsletter mapping, verify mapping, `NewsletterSubscriber`, `NewsletterDelivery`, 이메일 발송 client와 `@Scheduled` newsletter 작업도 없다.
- Phase 05는 기존 package와 결합하지 않는 `web.tosunsaeng.domain.blog.newsletter` 하위 package로 추가할 수 있다.
- `TosunsaengApplication`의 Mongo Repository scan 범위는 `web.tosunsaeng.domain` 전체이므로 새 newsletter Repository를 위해 scan 설정을 수정할 필요가 없다.
- UTC `Clock` Bean이 이미 있어 consent, subscribe, unsubscribe, create, update 시각에 재사용할 수 있다. `Instant.now()`를 직접 호출하지 않는다.

### 공통 응답과 예외

- `BaseResponse<T>`는 `isSuccess`, `code`, `message`, nullable `result`를 제공하고 실패 응답에도 구조화한 result를 넣을 수 있다.
- 현재 `SuccessStatus`의 blog 계열 code는 `BLOG_200`, `COMMENT_200` 형식이고 `ErrorStatus`의 세부 오류는 `COMMENT_4001`, `COMMENT_4290` 형식이다. newsletter도 같은 형식으로 `NEWSLETTER_200`, `NEWSLETTER_201`, `NEWSLETTER_4xxx`를 append한다.
- `GlobalExceptionAdvice`는 exams를 포함해 전역 적용되고 일부 일반 예외의 원문 message를 result로 반환하며 Sentry에도 예외를 보낸다. 이메일, unsubscribe token, Mongo duplicate message가 들어갈 가능성이 있는 newsletter 예외를 이 경로에 그대로 보내면 민감정보가 노출될 수 있다.
- newsletter Controller에만 적용하는 우선순위 높은 `NewsletterExceptionAdvice`에서 고정된 newsletter domain 오류, request body 오류와 429를 처리하는 방식을 권고한다. 기존 `GlobalExceptionAdvice`, exams 응답과 comment 전용 advice는 수정하지 않는다.
- handler mapping 전에 발생하는 잘못된 HTTP method의 405 응답은 Controller 한정 advice로 완전히 격리하기 어렵다. Phase 05에서는 기존 Spring 405 동작과 상태를 유지하고 service 미호출을 검증한다. 모든 endpoint의 405를 BaseResponse로 통일하는 전역 변경은 별도 승인 없이는 하지 않는다.

### 이메일 validation 기반

- `build.gradle`에 `spring-boot-starter-validation`이 이미 있어 Jakarta Bean Validation의 표준 `@Email` 검증을 사용할 수 있다. 새 dependency는 필요하지 않다.
- raw request DTO에 곧바로 `@Email`을 붙이면 앞뒤 공백을 제거하기 전에 거절될 수 있다. 요청 순서를 지키기 위해 null·strip·lowercase·길이 검사를 먼저 수행한 뒤, 정규화한 값을 전용 value object 또는 `jakarta.validation.Validator`로 검증한다.
- 저장소에는 이메일 정규화 선례가 없다. `Locale.ROOT` lowercase와 최대 254자 정책을 newsletter policy 한 곳에서 관리해야 한다.

### Mongo Document, Repository와 인덱스

- BlogPost, AnonymousVisitor, BlogComment는 `@Document`, String `@Id`, `Instant`, builder, protected no-args constructor 패턴을 사용한다.
- 복합 query와 원자적 상태 전이는 custom Repository + `MongoTemplate` + `findAndModify` 패턴이 이미 있다.
- index initializer는 `ApplicationRunner`, 명시적 index 이름, `ensureIndex`, `@Profile("!test")`를 사용한다. 오류를 흡수하지 않고 test profile에서 실제 Mongo 연결을 강제하지 않는다.
- Phase 05는 정규화 email unique와 status index를 같은 방식의 별도 newsletter initializer로 정의한다. tokenVersion은 `_id`와 함께 조건부 상태 전이에만 쓰므로 별도 index는 필요하지 않다.
- 단순 `findByEmail` 후 `insert`만 사용하면 동시 신규 요청에서 unique 충돌이 500으로 노출될 수 있다. 조건부 `findAndModify`, unique insert와 `DuplicateKeyException` 재조회가 필요하다.

### 기존 Redis 구성과 재사용 경계

- Redis는 단일 host와 port, `RedisTemplate<String, Object>`, String serializer를 사용한다. Cluster, Sentinel, 다중 primary 설정은 없다.
- exams는 `exam:status:*`, comment는 `blog:comment:*` namespace를 사용한다. newsletter는 `newsletter:subscribe:*` namespace로 분리해야 한다.
- Phase 04는 Lua fixed window, key 생성 시에만 TTL 설정, 연결·timeout만 fail-open, `request.getRemoteAddr()` 사용 패턴을 구현했다.
- comment의 rate limit 클래스는 comment properties, scope와 duplicate reservation에 결합되어 있어 newsletter가 그 package를 직접 의존하지 않는다. `RedisTemplate`과 Lua 실행 방식, 실패 분류 정책을 설계 선례로 재사용하되 newsletter 전용 properties, key factory, hasher, adapter를 둔다.
- 현재 standalone 전제에서는 newsletter의 2-key Lua가 가능하다. Redis Cluster로 전환하면 여러 key가 서로 다른 slot에 위치해 `CROSSSLOT`이 발생할 수 있으므로 Phase 08 또는 topology 전환 작업에서 key hash tag와 원자성 전략을 재설계해야 한다.

### client IP와 SecurityConfig

- Phase 04의 comment 요청은 `HttpServletRequest.getRemoteAddr()`만 사용하고 `X-Forwarded-For`를 직접 읽지 않는다.
- Phase 05도 같은 정책을 유지한다. 임의 proxy header를 파싱하거나 첫 X-Forwarded-For 값을 신뢰하지 않는다.
- CloudFront·ALB 환경의 실제 client IP는 신뢰 proxy와 forward-header 인프라 정책이 확정된 뒤 별도 운영 작업으로 다룬다.
- `SecurityConfig`는 현재 모든 요청을 허용한다. 공개 subscribe/unsubscribe API 때문에 변경할 필요가 없으며 이번 Phase에서 수정하지 않는다.

### unsubscribe body token, frontend URL과 Sentry

- 백엔드 `POST /api/newsletter/unsubscribe`는 token을 JSON request body로만 받는다. backend query parameter에는 token을 받지 않는다.
- `application.yml`은 Sentry `send-default-pii: true`와 transaction 수집을 사용하므로 request body를 수집하는 설정이나 예외 경로에서 bearer token이 event data에 들어갈 위험이 있다.
- Sentry 7.14.0의 event·transaction 전송 직전 callback으로 정확히 unsubscribe POST endpoint의 request data와 body token field를 제거하는 newsletter privacy config를 둔다.
- expected token 오류는 newsletter 전용 advice에서 원문 없는 고정 응답으로 처리하고 exception message, MDC와 application log에 raw token을 넣지 않는다.
- Phase 06 이메일은 backend API가 아니라 frontend 확인 page URL을 사용한다. frontend URL query의 token은 browser history, Referer, frontend hosting/CDN access log에 노출될 수 있으므로 실제 redaction과 흐름 검증을 Phase 08 운영 과제로 남긴다.

### 테스트 기반

- 기존 테스트는 JUnit 5, Mockito, AssertJ, standalone MockMvc, Mongo Query BSON과 IndexDefinition 검증을 사용한다.
- Redis는 mock `RedisTemplate`과 Lua resource 계약 테스트를 사용하며 embedded Redis와 Testcontainers는 없다.
- Phase 05도 새 dependency 없이 policy, token, Service, custom Repository BSON, index, Redis script 계약과 Controller를 단위·mock 테스트한다.
- 실제 Mongo unique/upsert 경쟁, Redis TTL·Lua 원자성, Sentry outbound payload와 web server access log는 Phase 08에서 실제 구성으로 검증한다.

## 구현 범위

사용자 승인 후 다음 범위만 구현한다.

1. NewsletterSubscriber Document와 `ACTIVE`, `UNSUBSCRIBED`, `BOUNCED` 상태를 추가한다.
2. null, strip, `Locale.ROOT` lowercase, 최대 길이와 Jakarta email 형식을 적용하는 정규화 policy를 추가한다.
3. 신규 구독, ACTIVE 멱등 성공, UNSUBSCRIBED 재활성화, BOUNCED 자동 재활성화 거부를 구현한다.
4. email unique 경쟁을 조건부 `findAndModify`, insert, duplicate 재조회로 처리한다.
5. stateless HMAC-SHA256 unsubscribe token 발급·검증과 subscriber tokenVersion을 구현한다.
6. `POST /api/newsletter/subscribe`와 JSON body 기반 `POST /api/newsletter/unsubscribe`만 노출한다.
7. unsubscribe token을 backend query parameter로 받지 않고 응답, 로그, 예외와 Sentry event/transaction request data에서 제거한다.
8. newsletter 전용 IP HMAC과 10분·하루 Redis fixed-window rate limit을 추가한다.
9. Redis 연결·timeout만 fail-open하고 script·decode·key 생성 오류는 숨기지 않는다.
10. email unique와 status Mongo index를 programmatic initializer로 추가한다.
11. 관련 unit, mock Repository, Lua 계약, MockMvc와 범위 부정 테스트를 추가한다.

## 제외 범위

- 이메일 인증과 소유권 확인
- 인증 이메일, 인증 링크와 인증 재전송
- `GET /api/newsletter/verify`, `POST /api/newsletter/verify`와 다른 verify endpoint
- `PENDING`, `UNVERIFIED`, `VERIFIED` 상태
- verification token, hash, expiry와 verifiedAt
- 실제 newsletter 이메일 발송
- AWS SES, SMTP와 다른 email Sender
- NewsletterDelivery, NewsletterCampaign
- 자동 발송 Scheduler, 15분 예약, 테스트 발송, 예약 취소와 실패 재시도
- `/internal/newsletter`와 다른 내부 newsletter API
- 내부 API Key 인증과 `SecurityConfig` 변경
- frontend 구독·해지 UI와 redirect page
- Gmail dot 제거, plus addressing 제거와 provider별 재작성
- DNS MX 조회와 국제 도메인의 자체 변환
- email 원문과 정규화값의 중복 저장
- unsubscribe raw token 또는 token hash 저장
- IP 또는 IP hash의 MongoDB 저장
- raw IP 또는 email의 Redis key 저장
- 기존 comment rate limit key, secret, Lua, 동작 수정
- 실제 운영 MongoDB·Redis·Sentry·email 접근
- embedded Redis, Testcontainers와 새 test dependency
- Redis Cluster 지원
- 실제 web server, CloudFront·ALB access log 설정 변경
- 관련 없는 blog, comment, exams 리팩터링

## 예상 변경 파일

### 예상 생성·수정 파일

예상 파일은 다음과 같다.

이번 DRAFT 계획 수립에서 생성·수정하는 파일:

- `docs/blog-mvp/plans/PHASE-05-newsletter-subscription.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

승인 후 예상 생성 main 파일:

- `src/main/java/web/tosunsaeng/domain/blog/newsletter/api/NewsletterRestController.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/api/support/NewsletterClientIpResolver.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/application/NewsletterService.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/application/NewsletterServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/application/NewsletterRateLimitService.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/application/NewsletterRateLimitServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterConfig.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterRateLimitProperties.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterUnsubscribeTokenProperties.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterMongoIndexInitializer.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterTelemetryPrivacyConfig.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/converter/NewsletterConverter.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/entity/NewsletterSubscriber.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/enums/NewsletterSubscriberStatus.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/enums/NewsletterRateLimitScope.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterEmailNormalizer.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterRateLimitHasher.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterRateLimitKeyFactory.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterRedisFailureClassifier.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterUnsubscribeTokenManager.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/NewsletterSubscriberRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/NewsletterSubscriberQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/NewsletterSubscriberQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/NewsletterRateLimitRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/RedisNewsletterRateLimitRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/dto/NewsletterRequestDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/dto/NewsletterResponseDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/exception/NewsletterException.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/exception/NewsletterRateLimitException.java`
- `src/main/java/web/tosunsaeng/domain/blog/newsletter/exception/NewsletterExceptionAdvice.java`
- `src/main/resources/redis/newsletter-subscribe-rate-limit.lua`

승인 후 예상 수정 main·설정·문서 파일:

- `.env.example`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `docs/blog-mvp/plans/PHASE-05-newsletter-subscription.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

승인 후 예상 생성 test 파일:

- `src/test/java/web/tosunsaeng/domain/blog/newsletter/api/NewsletterRestControllerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/api/support/NewsletterClientIpResolverTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/application/NewsletterServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/application/NewsletterRateLimitServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterMongoIndexInitializerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterPropertiesTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/config/NewsletterTelemetryPrivacyConfigTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/entity/NewsletterSubscriberStateTransitionTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterEmailNormalizerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterRateLimitHasherTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterRateLimitKeyFactoryTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterRedisFailureClassifierTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/policy/NewsletterUnsubscribeTokenManagerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/NewsletterSubscriberQueryRepositoryImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/newsletter/domain/repository/RedisNewsletterRateLimitRepositoryTest.java`

구현 상세를 단일 class로 안전하게 합칠 수 있으면 같은 package 안에서 파일 수를 줄일 수 있다. 반대로 예상 파일 밖 소스 변경이 필요하면 구현을 중단하고 사용자에게 차이와 이유를 보고한다.

보호하며 수정하지 않을 파일:

- `build.gradle`
- `src/main/java/web/tosunsaeng/global/common/response/BaseResponse.java`
- `src/main/java/web/tosunsaeng/global/exception/GlobalExceptionAdvice.java`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- 기존 RedisConfig, comment rate limit와 Lua 전체
- 기존 BlogPost·BlogComment 공개 API와 비즈니스 로직
- `src/main/java/web/tosunsaeng/domain/exams/**`
- 기존 blog, comment와 exams test

## API 계약

### POST /api/newsletter/subscribe

요청:

```json
{
  "email": "user@example.com",
  "consent": true
}
```

- 로그인과 이메일 인증 없이 처리한다.
- raw email을 null 검사한 뒤 strip, lowercase, 길이, 형식을 검증한다.
- `consent`는 Boolean으로 받고 `true`만 허용한다. null과 false는 동일한 수신 동의 필요 오류다.
- JSON 역직렬화에 성공한 모든 구독 요청은 email·consent validation보다 먼저 IP rate limit에 집계한다. malformed JSON처럼 역직렬화할 수 없는 요청은 application Service에 도달하지 않으므로 집계하지 않는다.
- 신규, 기존 ACTIVE와 UNSUBSCRIBED 재구독은 외부에서 구분하지 않고 HTTP 200과 같은 성공 payload를 반환한다.
- 응답에 email, 신규 여부, subscriberId, unsubscribe token을 넣지 않는다.

성공 의미:

```json
{
  "isSuccess": true,
  "code": "NEWSLETTER_200",
  "message": "뉴스레터 구독 요청이 처리되었습니다.",
  "result": {
    "status": "ACTIVE"
  }
}
```

### POST /api/newsletter/unsubscribe

요청:

```http
Content-Type: application/json
```

```json
{
  "token": "{signed-token}"
}
```

- 로그인과 이메일 재입력 없이 JSON body의 bearer token만 검증한다.
- token이 null, blank, oversized, malformed, 위조됐거나 지원하지 않는 format/key/version 또는 존재하지 않는 subscriber를 가리키면 모두 같은 외부 오류로 처리한다.
- query parameter의 token은 읽거나 fallback으로 사용하지 않는다. body 없이 `POST /api/newsletter/unsubscribe?token={token}`만 호출하면 거절하고 Subscriber를 조회·변경하지 않는다.
- ACTIVE와 BOUNCED는 `UNSUBSCRIBED`로 전환하고 `unsubscribedAt`을 기록한다.
- 이미 UNSUBSCRIBED이고 tokenVersion이 일치하면 멱등 성공한다.
- tokenVersion이 오래됐거나 subscriber가 없으면 일반적인 잘못된 해지 token 오류를 반환한다.
- redirect나 HTML을 만들지 않고 BaseResponse JSON을 반환한다.
- 응답에는 `Cache-Control: no-store`를 적용하고 token을 포함하지 않는다.

성공 의미:

```json
{
  "isSuccess": true,
  "code": "NEWSLETTER_201",
  "message": "뉴스레터 구독이 해지되었습니다.",
  "result": {
    "status": "UNSUBSCRIBED"
  }
}
```

### 공개하지 않는 API

- `GET /api/newsletter/unsubscribe`
- `GET /api/newsletter/verify`
- `POST /api/newsletter/verify`
- newsletter token 발급 공개 API
- `/internal/newsletter/**`
- email send, test send, schedule, cancel과 retry API

### frontend 해지 확인 page

Phase 06에서 이메일 본문의 사용자용 link는 backend API가 아니라 frontend 확인 page를 가리킨다.

```text
https://to-teacher.com/newsletter/unsubscribe?token={signed-token}
```

- 위 URL은 경로 의미의 예시이며 backend code에 production origin을 하드코딩하지 않는다.
- frontend는 page를 표시하는 GET만으로 해지를 실행하지 않는다.
- 사용자가 명시적으로 해지를 확인한 뒤 JSON body로 `POST /api/newsletter/unsubscribe`를 호출한다.
- email scanner, link preview와 prefetch가 frontend GET을 열어도 Subscriber 상태는 바뀌지 않아야 한다.
- 이번 Phase에서는 frontend page, confirmation UI와 frontend API client를 구현하지 않는다.
- frontend URL query에 token이 남는 browser history, Referer와 frontend/CDN access log 위험은 Phase 08에서 검증한다.

## API 변경

- 새 공개 endpoint는 subscribe POST와 unsubscribe POST 두 개다.
- 기존 게시글·댓글·exams endpoint의 URI, HTTP method, request와 response 구조는 변경하지 않는다.
- `GET /api/newsletter/unsubscribe` mapping은 만들지 않는다. 기존 지원하지 않는 HTTP method 처리 방식에 따라 405로 처리할 수 있지만 newsletter Service를 호출하거나 Subscriber 상태를 변경해서는 안 된다.
- `GET /api/newsletter/subscribe`와 다른 잘못된 method도 기존 Spring 처리 방식을 유지하고 service를 호출하지 않는다.
- unsubscribe token은 POST JSON body로만 받고 query parameter-only 요청은 유효한 해지 요청으로 인정하지 않는다.
- 잘못된 method의 body를 BaseResponse로 통일하기 위한 전역 advice 변경은 이번 범위에서 하지 않는다.

## NewsletterSubscriber 모델

collection 이름은 기존 snake_case 복수형 관례에 맞춰 `newsletter_subscribers`를 사용한다.

필드:

| 필드 | 타입/정책 |
|---|---|
| id | Mongo String `@Id` |
| email | strip·lowercase한 정규화값 하나만 저장, unique |
| status | `ACTIVE`, `UNSUBSCRIBED`, `BOUNCED` |
| tokenVersion | 양의 정수, 신규 1, 재구독 때 원자적 증가 |
| consentAt | 최초 또는 가장 최근 재구독의 명시적 동의 시각 |
| subscribedAt | 최초 활성화 또는 가장 최근 재활성화 시각 |
| unsubscribedAt | 해지 시각, ACTIVE에서는 null |
| createdAt | 최초 Document 생성 시각, 불변 |
| updatedAt | 실제 상태·동의 변경 시각; ACTIVE 멱등 요청에는 변경하지 않음 |

- stateless 방식을 사용하므로 `unsubscribeTokenHash`, raw unsubscribe token과 암호화 token을 저장하지 않는다.
- `verificationToken`, `verificationTokenHash`, `verificationExpiresAt`, `verifiedAt`을 만들지 않는다.
- email 원문과 normalizedEmail을 함께 두지 않고 정규화한 `email` 한 필드만 저장한다.
- entity method는 허용 상태 전이와 timestamp 불변식을 방어하고 Repository conditional update와 같은 규칙을 사용한다.

## DB 변경

- `newsletter_subscribers` collection의 Document와 Repository를 추가한다.
- email ASC unique index와 status ASC index를 추가한다.
- schema migration이나 기존 blog/comment Document 변경은 없다.
- tokenVersion query는 `_id` equality가 선행되므로 별도 tokenVersion index를 추가하지 않는다.
- 실제 운영 index 생성과 기존 중복 데이터 여부는 Phase 08에서 검증한다.

## 이메일 정규화

처리 순서는 고정한다.

1. raw email이 null인지 확인한다.
2. Java `String.strip()`으로 앞뒤 Unicode whitespace를 제거한다.
3. 빈 문자열이면 email 누락 오류로 처리한다.
4. `toLowerCase(Locale.ROOT)`로 lowercase한다.
5. 정규화값이 254자를 초과하면 최대 길이 오류로 처리한다.
6. 정규화값을 Jakarta `@Email`이 붙은 전용 value object로 검증한다.
7. 통과한 정규화값만 lookup, 저장과 응답 내부 처리에 사용한다.

- DTO raw 값에 먼저 `@Email`을 적용하지 않아 앞뒤 공백 제거 요구를 보장한다.
- length는 Java UTF-16 unit 혼동을 피하도록 Unicode code point 기준 254로 정의한다. SMTP octet·IDN의 더 세밀한 처리는 MVP 범위 밖이다.
- Gmail dot 제거, plus suffix 제거, provider별 alias 통합, DNS/MX 조회를 하지 않는다.
- Unicode domain을 자체 punycode 변환하지 않고 Jakarta validator가 허용하는 표준 범위만 사용한다.
- policy와 exception은 email 원문을 message에 포함하지 않는다.

## 상태 전이

| 현재 상태 | 요청 | 결과 | timestamp/token 정책 |
|---|---|---|---|
| 없음 | 구독 | ACTIVE 생성 | consentAt=subscribedAt=createdAt=updatedAt=now, tokenVersion=1 |
| ACTIVE | 구독 | ACTIVE 멱등 성공 | 모든 timestamp와 tokenVersion 유지 |
| UNSUBSCRIBED | 구독 | ACTIVE 재활성화 | consentAt/subscribedAt/updatedAt=now, unsubscribedAt 제거, tokenVersion +1 |
| BOUNCED | 구독 | 전이 없음 | generic 409, 내부 BOUNCED 명칭과 email 비노출 |
| ACTIVE | 유효 token 해지 | UNSUBSCRIBED | unsubscribedAt/updatedAt=now, tokenVersion 유지 |
| BOUNCED | 유효 token 해지 | UNSUBSCRIBED | 명시적 opt-out을 우선해 같은 방식으로 전환 |
| UNSUBSCRIBED | 같은 유효 token 해지 | UNSUBSCRIBED 멱등 성공 | timestamp와 tokenVersion 유지 |

- `PENDING`, `UNVERIFIED`, `VERIFIED`는 정의하지 않는다.
- BOUNCED의 자동 ACTIVE 전환은 발송 문제가 해결되지 않은 주소를 재활성화할 위험이 있어 금지한다.
- BOUNCED 재구독은 성공으로 가장하지 않고 `NEWSLETTER_SUBSCRIPTION_UNAVAILABLE` 의미의 generic 409를 권고한다. 이 선택은 이메일 존재 여부 추론 위험과 응답 정확성의 trade-off이므로 사용자 승인을 받는다.
- BOUNCED 해지는 현재 발송 대상이 아니더라도 사용자의 명시적 opt-out을 영구 상태로 반영하기 위해 UNSUBSCRIBED로 전환한다.

## 동시성 및 멱등성

구독 Service는 다음 순서와 재시도 경계를 사용한다.

1. JSON request가 역직렬화되면 newsletter IP rate limit을 먼저 통과한다.
2. email과 consent를 검증하고 정규화한다.
3. email + UNSUBSCRIBED 조건의 `findAndModify`로 재활성화를 먼저 시도한다. 성공한 한 요청만 timestamp 갱신과 tokenVersion 증가를 수행한다.
4. 재활성화 결과가 없으면 email로 현재 Document를 조회한다.
5. ACTIVE면 변경 없이 성공하고 BOUNCED면 generic conflict를 반환한다.
6. Document가 없으면 ACTIVE 신규 Document를 insert한다.
7. insert가 `DuplicateKeyException`이면 email 원문이나 driver message를 외부로 보내지 않고 다시 조회해 3~5의 상태별 처리를 수행한다.
8. 제한된 재조회 뒤에도 상태를 안정적으로 판정할 수 없으면 고정된 newsletter internal error를 반환한다.

이 구조로 동시 신규 요청은 unique index 때문에 Document 하나만 만들고 loser는 ACTIVE 멱등 성공으로 수렴한다. 동시 UNSUBSCRIBED 재구독은 조건부 update 한 건만 tokenVersion과 시각을 변경하고 나머지는 새 ACTIVE 상태를 그대로 성공 처리한다.

구독 해지는 subscriberId + tokenVersion + 허용 현재 상태를 조건으로 `findAndModify`한다. update가 없으면 `_id`로 재조회해 같은 version의 UNSUBSCRIBED만 멱등 성공하고, 다른 version·상태·미존재는 같은 invalid token 응답으로 처리한다. 재구독과 과거 해지 link가 경쟁해도 tokenVersion 조건 때문에 재활성화 뒤 오래된 link가 새 ACTIVE 상태를 해지하지 못한다.

MongoDB와 Redis 사이의 transaction은 만들지 않는다. rate counter가 승인된 뒤 Mongo save가 실패해도 counter를 되돌리지 않는다. Phase 04와 같이 rollback이 다른 동시 요청의 count를 손상시킬 수 있고 실패 요청도 서버 자원을 사용했기 때문이다.

## 구독 해지 token 설계 비교와 추천

| 선택지 | 장점 | 단점 | 판단 |
|---|---|---|---|
| A. stateless 서명 token | DB에 원문 저장 없이 Phase 06이 언제든 link 생성 가능, HMAC 검증과 tokenVersion으로 위변조·과거 link 제어 | bearer token 유출 방지와 signing key rotation 필요, payload는 암호화되지 않음 | 권고 |
| B. 암호화 token 저장 | DB 값을 복호화해 같은 token 재사용 가능 | 암호화·복호화와 nonce/key 관리가 복잡하고 DB와 key 동시 유출 위험이 큼 | MVP에서 제외 |
| raw random token hash만 저장 | 검증은 단순 | Phase 06이 원본을 복구해 link를 만들 수 없음 | 금지 |

선택지 A를 사용한다.

- compact token은 `base64url(canonical payload).base64url(HMAC-SHA256 signature)` 두 segment이며 padding을 쓰지 않는다.
- payload에는 `formatVersion`, `keyId`, `subscriberId`, `subscriberTokenVersion`, `issuedAtEpochSeconds`만 넣는다. email, 상태와 consent 시각을 넣지 않는다.
- payload는 서명되지만 암호화되지 않으므로 민감정보를 추가하지 않는다.
- `NEWSLETTER_UNSUBSCRIBE_TOKEN_SECRET`의 UTF-8 32 byte 이상 secret으로 서명한다.
- signature는 `MessageDigest.isEqual`로 constant-time 비교한다.
- token 전체 길이와 decoded payload 크기에 상한을 두고 segment 수, Base64URL, 필드 타입, formatVersion, keyId, issuedAt 미래 편차를 순서대로 검증한다.
- MVP token은 명시적 만료를 두지 않는다. 오래된 newsletter에서도 해지가 가능해야 하며, 재구독 시 증가하는 subscriber tokenVersion으로 과거 link를 무효화한다.
- Phase 06은 발송 content를 만들 때 현재 subscriberId와 tokenVersion으로 새 signed token을 발급하고 frontend 확인 page link의 query에 넣는다. 원본 token을 DB에서 복원할 필요가 없으며 backend API URL을 이메일 link로 사용하지 않는다.
- token 원문, signature, decoded payload 전체는 log, exception, BaseResponse와 MongoDB에 넣지 않는다.

## token version 및 rotation

두 version을 구분한다.

- `formatVersion`: token wire format과 parser 호환성을 나타낸다. Phase 05는 1만 발급·허용하고 지원하지 않는 값은 generic invalid token으로 처리한다.
- `subscriberTokenVersion`: 특정 subscriber의 과거 link를 무효화한다. 신규는 1, UNSUBSCRIBED 재구독 때만 원자적으로 증가하며 ACTIVE 중복과 해지에는 유지한다.

secret rotation을 위해 payload에 non-secret `keyId`를 처음부터 포함한다. 권고 구현은 active signing key 한 개와 이전 verification key들을 읽을 수 있는 key ring이다.

- 새 token은 active keyId와 `NEWSLETTER_UNSUBSCRIBE_TOKEN_SECRET`으로만 서명한다.
- active keyId는 non-secret `NEWSLETTER_UNSUBSCRIBE_TOKEN_KEY_ID`로 주입하고 blank를 허용하지 않는다.
- 이전 key는 `newsletter.unsubscribe-token.previous-keys`의 외부 secret map으로만 주입한다. map의 key는 과거 keyId, value는 해당 verification secret이며 main repository 설정에 실제 값을 넣지 않는다.
- previous key map은 검증에만 사용하고 새 서명에는 사용하지 않는다.
- 회전 시 새 active key를 먼저 배포하고 구 active key는 기존 이메일 link를 검증할 기간 동안 유지한 뒤 제거한다.
- 모든 configured secret은 32 byte 이상이어야 하고 keyId 중복, blank와 active key 누락은 시작 실패다.
- unsubscribe secret은 BLOG_ANONYMOUS_TOKEN_SECRET, BLOG_COMMENT_RATE_LIMIT_SECRET과 아래에서 권고하는 newsletter rate limit secret 어느 것과도 같을 수 없다. 비교는 `MessageDigest.isEqual`을 사용하고 오류에 실제 값을 포함하지 않는다.
- `.env.example`에는 active keyId와 active secret 이름만 안내한다. previous map은 배포 환경의 외부 YAML/config tree 또는 secret manager가 제공하며 저장소의 application.yml에 예시 secret을 하드코딩하지 않는다.
- test profile에는 active와 previous dummy key를 제공해 두 세대 token 검증과 active-key-only 발급을 결정적으로 검증한다.

## 설정

승인 후 `ConfigurationProperties`를 newsletter unsubscribe token과 subscribe rate limit 두 묶음으로 분리한다.

필수·권고 환경변수:

- `NEWSLETTER_UNSUBSCRIBE_TOKEN_SECRET`: active HMAC secret, 32 byte 이상
- `NEWSLETTER_UNSUBSCRIBE_TOKEN_KEY_ID`: active non-secret key ID
- `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_SECRET`: newsletter IP HMAC 전용 secret, 32 byte 이상
- `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_ENABLED`: 운영 기본 true, test에서 제어

안전한 기본값:

- subscribe rate limit enabled: true
- medium: 10회 / 600초
- daily: 30회 / 86400초
- token formatVersion: 1
- token 만료: 없음

운영 또는 다른 non-test 실행에서 active unsubscribe secret·keyId가 없거나 잘못되면 시작을 실패시킨다. rate limit이 enabled인데 rate secret이 없거나 짧아도 시작을 실패시킨다. 모든 limit/window는 양수이고 medium이 daily보다 작아야 한다. test application 설정에는 실제 secret이 아닌 명확한 dummy 값을 사용한다. `application.yml`은 properties binding에 필요한 최소 항목만 추가하고 기존 Mongo, Redis, Sentry, blog와 exams 설정을 바꾸지 않는다.

## MongoDB 인덱스

`NewsletterMongoIndexInitializer`가 다음 named index를 `ensureIndex`한다.

1. `email ASC`, unique: 정규화 이메일당 Document 하나를 보장한다.
2. `status ASC`: Phase 06의 ACTIVE subscriber 조회를 준비한다.

- initializer는 idempotent하고 `@Profile("!test")`로 실제 test Mongo 연결을 강제하지 않는다.
- unique index 충돌과 다른 생성 오류를 catch해 무시하지 않아 시작 실패로 드러낸다.
- tokenVersion은 `_id` lookup과 조건부 update에 쓰므로 별도 index를 만들지 않는다.
- mock IndexDefinition test로 key, unique, 이름, 반복 실행과 오류 전파를 검증한다.
- 실제 Mongo unique index, 기존 중복 데이터, 동시 upsert는 Phase 08 통합 과제로 남긴다.

## rate limit

### secret과 HMAC

comment secret을 newsletter가 직접 재사용하면 설정 활성화와 장애 범위가 결합된다. 별도 `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_SECRET`을 추가하는 방식을 권고한다.

- UTF-8 기준 32 byte 이상이어야 한다.
- unsubscribe, anonymous token, comment rate limit secret과 같으면 시작 실패한다.
- IP는 `HMAC-SHA256(secret, "newsletter-subscribe-ip\0" + remoteAddr)`의 Base64URL digest로 만든다.
- raw IP, email, token과 nickname을 key에 넣지 않는다.
- test profile은 안전한 dummy secret과 제어 가능한 enabled 값을 사용한다.
- `.env.example`에는 변수 이름과 생성·분리 설명만 넣고 실제 값을 넣지 않는다.

### key와 fixed window

namespace:

```text
newsletter:subscribe:rate:ip:medium:{ipHash}
newsletter:subscribe:rate:ip:daily:{ipHash}
```

정책:

| scope | limit | TTL |
|---|---:|---:|
| IP_MEDIUM | 10회 | 600초 |
| IP_DAILY | 30회 | 86400초 |

- calendar window가 아니라 key 최초 생성 시점부터 시작하는 TTL fixed window다.
- 새 counter가 1이 될 때만 TTL을 설정하고 기존 key increment에서는 TTL을 연장하지 않는다.
- TTL이 없거나 1초 미만, count type 오류와 script 결과 오류는 구현·운영 오류로 처리하며 fail-open하지 않는다.
- fixed-window 경계에서 순간적으로 허용량이 늘어나는 burst는 MVP의 알려진 제한이다.

### Lua 원자성

두 counter를 한 번의 Lua script에서 처리한다.

1. limit/window argument와 두 기존 counter의 type, 값, TTL을 mutation 전에 검증한다.
2. 이번 요청을 더했을 때 medium 또는 daily가 초과되는지 모두 확인한다.
3. blocker가 하나라도 있으면 어느 counter도 증가시키지 않는다.
4. 허용되면 두 counter를 한 번에 증가시킨다.
5. 새 key에만 해당 TTL을 설정한다.
6. 차단 시 모든 blocker와 남은 TTL을 반환한다.
7. application은 실제로 두 제한이 모두 풀리는 시각을 안내하도록 최대 TTL을 선택한다.
8. 반환 Retry-After는 최소 1초의 정수다.

현재 standalone Redis에서는 원자적이지만 Cluster 전환 시 두 key의 cross-slot 문제가 있다. 이번 Phase에 Cluster 지원을 가장한 hash tag나 부분 원자성 fallback을 추가하지 않고 Phase 08 topology 검증에 남긴다.

### 적용 순서와 장애

1. request body 역직렬화
2. `request.getRemoteAddr()` 획득
3. IP HMAC과 newsletter key 생성
4. Redis Lua admission
5. 제한 시 429 반환
6. 연결·timeout이면 sanitized warning 뒤 fail-open
7. email 정규화·형식과 consent 검증
8. Subscriber 상태 조회·전이
9. 성공 BaseResponse

- 역직렬화 가능한 요청은 email 누락·형식 오류, 길이 초과, consent=false/null, ACTIVE 멱등과 BOUNCED를 포함해 모두 rate counter 대상이다.
- malformed JSON 또는 DTO로 역직렬화할 수 없는 요청은 Service에 도달하지 않으므로 counter 대상이 아니다.
- invalid email과 consent=false 요청은 rate counter는 증가시키되 Mongo 조회·저장은 하지 않는다.
- unsubscribe API에는 이 subscribe rate limit을 적용하지 않는다.
- `RedisConnectionFailureException`, Lettuce connection/command timeout과 명확한 socket timeout만 fail-open한다.
- NullPointerException, IllegalStateException, key·HMAC 오류, Lua syntax/result decode와 serializer 오류는 generic server error로 전파한다.
- warning/error log에는 raw IP, email, digest, key와 secret을 넣지 않고 event name, operation, exception type만 남긴다.

### 429 계약

HTTP 429와 `Retry-After` header를 반환한다.

```json
{
  "isSuccess": false,
  "code": "NEWSLETTER_4290",
  "message": "뉴스레터 구독 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
  "result": {
    "retryAfterSeconds": 600
  }
}
```

- comment의 `COMMENT_4290`과 `CommentLimitScope`를 사용하지 않는다.
- internal Redis scope, key와 hash는 응답에 포함하지 않는다.

## 개인정보 및 로깅

- 정규화 email은 서비스 제공에 필요한 개인정보로 MongoDB에 저장하지만 일반 application log와 BaseResponse에는 넣지 않는다.
- 예상 domain 예외와 duplicate 복구 exception은 email을 message나 cause text로 외부에 전달하지 않는다.
- raw unsubscribe token, signature, decoded claims, HMAC secret과 Redis digest/key를 로그에 남기지 않는다.
- backend unsubscribe API는 token을 JSON body로만 받고 query parameter를 읽지 않는다.
- Sentry event와 transaction callback은 정확히 unsubscribe POST endpoint의 request data와 body token field를 제거한다. callback 자체가 token을 log하지 않는다.
- malformed token 예외는 raw input을 포함하지 않는 고정 reason만 사용한다.
- 응답 body에는 email, subscriberId, tokenVersion, token과 신규/중복 여부를 넣지 않는다.
- `request.getRemoteAddr()` 원문과 hash를 MongoDB에 저장하지 않는다.
- application.yml의 전역 `send-default-pii`를 이번 Phase에서 임의로 변경하지 않고 newsletter endpoint를 좁게 sanitize한다.
- frontend 확인 page URL에는 token query가 있으므로 browser history, Referer, frontend hosting/CDN access log의 원문 노출 위험이 남는다. backend API query log 문제와 구분해 Phase 08에서 frontend·인프라 redaction을 확인한다.
- frontend GET은 상태를 바꾸지 않고 사용자 확인 뒤 POST body 요청만 상태를 바꾼다. email scanner와 link preview의 실제 동작은 Phase 08에서 확인한다.

## 예외 및 응답

권고 status는 다음과 같다. enum 이름과 code는 구현 승인 시 이 의미로 append하고 기존 값을 변경하지 않는다.

| 의미 | HTTP | code | 외부 정책 |
|---|---:|---|---|
| email null/blank | 400 | `NEWSLETTER_4001` | email 값 비노출 |
| email 형식 오류 | 400 | `NEWSLETTER_4002` | validator 상세·원문 비노출 |
| email 254자 초과 | 400 | `NEWSLETTER_4003` | 최대 길이만 안내 |
| consent null/false | 400 | `NEWSLETTER_4004` | 수신 동의 필요 안내 |
| invalid/missing/forged/old unsubscribe token | 400 | `NEWSLETTER_4005` | 원인과 subscriber 존재 여부 통합 |
| BOUNCED 재구독 | 409 | `NEWSLETTER_4091` | BOUNCED 용어 없이 처리 불가 안내 |
| subscribe rate limit | 429 | `NEWSLETTER_4290` | Retry-After와 retryAfterSeconds |
| newsletter 구현 오류 | 500 | 기존 generic internal code | driver/Lua/secret/token 원문 비노출 |
| 잘못된 HTTP method | 405 | 기존 Spring 계약 | service 미호출, 전역 응답 변경 없음 |

- NewsletterExceptionAdvice는 newsletter Controller의 구체 domain exception, rate limit와 request body 오류만 처리한다.
- unsubscribe body 누락, 빈 JSON, token null·blank와 query parameter-only POST는 같은 invalid token 응답으로 처리하고 Service 상태 전이를 호출하지 않는다.
- signature mismatch, unknown keyId, unsupported formatVersion, subscriber 미존재와 old tokenVersion은 외부에서 모두 `NEWSLETTER_4005`다.
- ACTIVE 신규·중복·재구독 성공 payload는 완전히 같다.
- BOUNCED를 409로 처리하는 권고는 상태를 직접 말하지 않지만 해당 email 처리 불가 여부는 관찰 가능하다. 200으로 숨기는 대안은 실제로 ACTIVE가 아닌데 ACTIVE를 응답하는 문제가 있어 권고하지 않는다.

## 테스트 계획

### 이메일 정규화

1. 앞뒤 공백을 제거한 email을 저장·조회에 사용한다.
2. `Locale.ROOT` lowercase를 적용한다.
3. 유효한 표준 email을 허용한다.
4. null email을 `NEWSLETTER_4001`로 거절한다.
5. strip 후 빈 문자열을 `NEWSLETTER_4001`로 거절한다.
6. 잘못된 형식을 `NEWSLETTER_4002`로 거절한다.
7. Unicode code point 기준 최대 길이 254 경계를 허용한다.
8. 최대 길이 255를 `NEWSLETTER_4003`으로 거절한다.
9. Gmail dot, plus addressing과 provider별 값을 임의 변환하지 않는다.

### 신규 구독

10. 신규 email은 ACTIVE Document 하나를 생성한다.
11. 신규 구독은 consentAt을 Clock의 now로 기록한다.
12. 신규 구독은 subscribedAt을 같은 now로 기록한다.
13. 신규 ACTIVE의 unsubscribedAt은 null이다.
14. 성공 응답에 email, subscriberId와 신규 여부가 없다.
15. 구독 처리 중 인증 token, verify 호출과 email 발송이 없다.

### 중복 및 상태 전이

16. 기존 ACTIVE 구독 요청은 같은 성공 payload로 멱등 처리한다.
17. ACTIVE 중복 요청은 Document를 추가 생성하지 않는다.
18. ACTIVE 중복은 subscribedAt, consentAt, updatedAt과 tokenVersion을 변경하지 않는다.
19. UNSUBSCRIBED 구독 요청은 조건부 재활성화한다.
20. 재구독 결과는 ACTIVE다.
21. 재구독은 consentAt을 now로 갱신한다.
22. 재구독은 subscribedAt을 now로 갱신한다.
23. 재구독은 unsubscribedAt을 제거하고 tokenVersion을 한 번 증가시킨다.
24. BOUNCED 구독 요청은 자동 ACTIVE 전환 없이 generic conflict다.
25. 동시 신규 insert의 duplicate key를 재조회해 Document 하나와 멱등 성공으로 수렴시킨다.
26. 동시 재구독에서는 조건부 update 한 건만 timestamp와 tokenVersion을 갱신한다.

### 구독 해지 token

27. 현재 subscriberId·tokenVersion으로 정상 token을 생성한다.
28. payload와 signature가 padding 없는 URL-safe Base64 형식이다.
29. 정상 HMAC signature를 검증한다.
30. payload 한 byte 위변조를 차단한다.
31. subscriberId 위변조를 차단한다.
32. formatVersion, keyId와 subscriberTokenVersion을 검증한다.
33. 다른 secret 또는 허용되지 않은 keyId로 검증에 실패한다.
34. raw token과 token hash를 NewsletterSubscriber에 저장하지 않는다.
35. raw token, signature와 claims가 log·exception에 포함되지 않는다.
36. Phase 06 email 생성 시 현재 subscriber snapshot만으로 frontend 확인 page link용 새 token을 만들 수 있다.

### 구독 해지

37. 정상 token은 ACTIVE를 UNSUBSCRIBED로 원자적 전환한다.
38. 해지 시 unsubscribedAt과 updatedAt을 now로 기록한다.
39. 같은 version의 이미 UNSUBSCRIBED token은 timestamp 변경 없이 멱등 성공한다.
40. malformed, oversized, 위조와 unsupported token은 같은 400 응답이다.
41. 존재하지 않는 subscriberId token은 같은 400 응답이고 id 존재 여부를 노출하지 않는다.
42. 해지에 email 재입력이나 로그인은 필요하지 않다.
43. 응답에 subscriberId, email, tokenVersion과 token이 없고 `Cache-Control: no-store`가 있다.

### Rate limit

44. IP당 10분 10개까지 허용한다.
45. 10분 창의 11번째 요청을 제한한다.
46. 하루 30개까지 허용하고 31번째를 제한한다.
47. 제한은 newsletter 전용 429 BaseResponse code를 사용한다.
48. 여러 blocker의 최대 TTL을 Retry-After와 retryAfterSeconds에 반환한다.
49. raw IP를 Redis key와 log에 사용하지 않고 newsletter HMAC digest만 사용한다.
50. email과 unsubscribe token이 Redis key/ARGV에 없다.
51. Redis connection failure와 timeout은 fail-open해 정상 구독 흐름을 계속한다.
52. Lua syntax/result, HMAC/key 생성과 application 오류는 fail-open하지 않는다.
53. 임의 X-Forwarded-For를 무시하고 getRemoteAddr만 hash한다.

### Mongo 인덱스 및 Repository

54. email ASC named index가 unique다.
55. status ASC named index가 존재한다.
56. UNSUBSCRIBED 재구독과 해지가 status·tokenVersion 조건의 findAndModify다.
57. 신규 insert duplicate key를 원문 없는 재조회 흐름으로 복구한다.
58. index initializer가 오류를 숨기지 않고 test profile에서 비활성화된다.

### Controller

59. 구독 성공은 HTTP 200과 승인된 BaseResponse다.
60. 잘못된 email 오류가 newsletter code이고 email을 되돌려주지 않는다.
61. consent=false 또는 null은 고정된 consent 오류다.
62. JSON body의 유효한 token으로 POST 해지하면 HTTP 200과 UNSUBSCRIBED BaseResponse다.
63. POST body의 잘못된 token 원인이 통합된 newsletter 오류이고 token을 되돌려주지 않는다.
64. rate limit은 HTTP 429, Retry-After와 retryAfterSeconds를 포함한다.
65. GET·POST verify mapping이 없고 404이며 service interaction이 없다.
66. NewsletterSubscriberStatus에 PENDING, UNVERIFIED, VERIFIED가 없다.

### 회귀 및 범위

67. 기존 공개 게시글 목록·상세·검색 API와 테스트가 그대로 통과한다.
68. 기존 댓글 조회·작성·profile regenerate·rate limit·moderation Service 테스트가 통과한다.
69. 기존 exams 테스트와 전체 application context가 통과한다.
70. email Sender, AWS SES/SMTP adapter가 source와 bean에 없다.
71. newsletter Scheduler와 `@Scheduled` newsletter 작업이 없다.
72. 실제 email 발송 호출이 없고 token 발급은 내부 policy일 뿐 공개 API가 아니다.
73. `/internal/newsletter/**` mapping이 없다.
74. `SecurityConfig`, GlobalExceptionAdvice와 기존 blog/comment/exams 계약이 변경되지 않는다.

### 해지 method·token 전달 부정 테스트

75. `GET /api/newsletter/unsubscribe` handler가 존재하지 않고 기존 unsupported-method 응답을 사용한다.
76. GET 해지 요청은 Newsletter Service와 Repository를 호출하지 않고 Subscriber 상태를 변경하지 않는다.
77. `POST /api/newsletter/unsubscribe`는 JSON body의 유효한 token으로만 해지할 수 있다.
78. body 없이 query parameter token만 보낸 POST 요청은 거절하고 Subscriber 상태를 변경하지 않는다.
79. body token 원문이 BaseResponse, 일반 application log, 예외 message와 Sentry request data에 노출되지 않는다.

추가 보안·계약 테스트:

- active와 previous key 설정, 32-byte minimum, blank·duplicate keyId와 secret 동일성 검증
- expired가 아니라 version으로 무효화하는 정책과 issuedAt 미래 편차 검증
- 재구독 뒤 과거 token이 현재 ACTIVE를 해지하지 못하는 경쟁 조건
- BOUNCED → UNSUBSCRIBED와 BOUNCED 재구독 conflict
- rate limit key namespace가 `exam:status:*`, `blog:comment:*`와 충돌하지 않음
- Lua가 차단 전에 mutation하지 않고 새 key에만 TTL을 설정하며 기존 TTL을 연장하지 않음
- 역직렬화 가능한 invalid email·consent=false도 Redis counter를 증가시키지만 Mongo는 미호출이고, malformed JSON은 Redis·Mongo 모두 미호출
- Sentry event와 transaction의 정확한 unsubscribe body token 제거, 다른 endpoint request data는 불필요하게 변경하지 않음
- malformed token과 Mongo duplicate exception의 외부 응답·log에 email/token/driver message 없음
- 잘못된 HTTP method 405와 service 미호출

실행 예정 검증:

- `git diff --check`
- `./gradlew test --tests 'web.tosunsaeng.domain.blog.newsletter.*'`
- `./gradlew clean test bootJar`
- mapping, verify/PENDING/Sender/Scheduler/internal API, secret·email·token log와 보호 파일을 `rg`·`git diff`로 정적 확인

실제 MongoDB unique/upsert 동시성, 실제 Redis Lua syntax·TTL·원자성, Cluster topology, Sentry outbound event, frontend URL query log와 email client scanner 동작은 Testcontainers를 임의로 추가하지 않고 Phase 08 과제로 기록한다.

## 기존 blog·comment·exams 영향

- 새 코드는 newsletter package와 newsletter 전용 status/config/resource에 격리한다.
- 기존 게시글·댓글 Controller, Service, DTO, Document, Repository와 endpoint를 변경하지 않는다.
- comment의 RedisTemplate bean은 공유하되 comment key, Lua, properties, secret과 fail-open 흐름을 수정하지 않는다.
- `SuccessStatus`와 `ErrorStatus`에는 newsletter 상수만 append하고 기존 code/message/status를 변경하지 않는다.
- GlobalExceptionAdvice와 SecurityConfig는 보호 파일로 둔다.
- Sentry privacy callback은 정확한 unsubscribe POST request body token만 sanitize하고 기존 endpoint event를 바꾸지 않는 테스트를 둔다.
- 전체 test와 bootJar로 blog, comment와 exams 회귀를 확인한다.

## 위험 요소

- email 인증이 없으므로 제3자가 타인의 email을 등록할 수 있다. IP rate limit은 자동화를 줄일 뿐 소유권을 증명하지 않는다.
- BOUNCED 재구독을 generic 409로 반환하면 구체 상태명은 숨기지만 해당 email이 처리 불가라는 정보는 추론될 수 있다. 200으로 숨기면 실제 ACTIVE가 아닌 상태를 성공으로 오인하게 된다.
- frontend 확인 page의 GET은 상태를 변경하지 않아 scanner의 즉시 해지 위험을 줄이지만 frontend가 page load 시 POST를 자동 호출하지 않도록 별도 구현 검증이 필요하다.
- stateless token은 bearer credential이며 frontend URL query, browser history, Referer와 frontend/CDN access log 유출 시 제3자가 POST 해지를 호출할 수 있다.
- backend POST body도 일반 log나 Sentry request capture에 남으면 token이 유출될 수 있어 endpoint 범위의 redaction이 필요하다.
- 단일 active secret만 교체하면 과거 link가 모두 무효화된다. previous verification key 유지 형식을 승인 전에 확정해야 한다.
- stateless payload는 암호화되지 않으므로 subscriberId와 version이 보인다. email 등 직접 개인정보를 넣지 않아야 한다.
- Mongo unique index가 생성되기 전에 운영 데이터에 정규화 중복 email이 있으면 시작이 실패한다.
- application-level duplicate recovery는 실제 Mongo 다중 instance 경쟁을 mock만으로 완전히 증명하지 못한다.
- Redis fail-open 동안 subscribe 남용 방지가 일시적으로 사라진다.
- fixed-window 경계에서는 설정량보다 순간 요청이 늘 수 있다.
- standalone 다중 key Lua는 Redis Cluster에서 `CROSSSLOT`이 발생할 수 있다.
- `getRemoteAddr()`는 신뢰 proxy 설정 전 CloudFront/ALB 환경에서 proxy IP로 집계될 수 있다.
- Sentry callback은 CDN, reverse proxy와 web server access log까지 지우지 못한다.
- email 최대 254 code point 정책은 SMTP octet과 완전히 같지 않아 국제 email의 엄밀한 전달 가능성을 보장하지 않는다.

## 롤백 방법

- 구현 전에는 이 계획서와 상태 문서에 대한 수동 역패치만 수행하며 사용자 변경과 이전 Session Log를 보존한다.
- 구현 후 문제가 생기면 Phase 05에서 추가한 newsletter Controller/Service/config/Document/Repository/resource를 후속 patch로 제거하고 status enum의 newsletter 상수와 application/env binding을 수동으로 되돌린다.
- 이미 생성된 subscriber Document를 자동 삭제하거나 email을 export하지 않는다. index 제거도 데이터 영향 검토와 사용자 승인 없이 수행하지 않는다.
- 배포된 version rollback은 사용자가 이전 검증 artifact로 수행하며 Codex는 배포, 운영 DB와 Redis에 접근하지 않는다.
- `git reset`, `git restore`, `git checkout`, `git clean`과 사용자 변경을 되돌리는 명령은 사용하지 않는다.

## 완료 조건

- 사용자가 이 DRAFT 계획과 아래 결정 항목을 명시적으로 승인한다.
- 계획 상태를 `APPROVED`, Phase 05를 `IN_PROGRESS`로 변경한 뒤에만 구현한다.
- 공개 endpoint는 subscribe POST와 JSON body 기반 unsubscribe POST 두 개뿐이다.
- `GET /api/newsletter/unsubscribe` mapping이 없고 GET 요청은 Subscriber 상태를 절대 변경하지 않는다.
- Phase 06 email link는 frontend 확인 page를 가리키며 사용자의 확인 뒤에만 backend POST를 호출한다.
- 유효 email과 consent=true는 즉시 ACTIVE이며 verify/PENDING/email 발송이 없다.
- 정규화 email unique, ACTIVE 멱등, UNSUBSCRIBED 원자적 재구독과 BOUNCED 정책이 구현된다.
- stateless HMAC token을 DB에 저장하지 않고 Phase 06에서 link를 생성할 수 있다.
- raw email, IP, token과 secret이 응답, Redis key와 log, exception 또는 Sentry backend request data에 노출되지 않는다.
- newsletter 2-window rate limit, 최대 TTL Retry-After와 연결 장애 fail-open이 구현된다.
- 관련 테스트 1~79와 추가 보안 테스트가 모두 성공한다.
- `git diff --check`와 `./gradlew clean test bootJar`가 성공한다.
- verify API, Sender, Scheduler, `/internal/newsletter`, SecurityConfig 변경과 관련 없는 리팩터링이 없음을 확인한다.
- 실제 Mongo, Redis, Sentry와 frontend URL query/access log, email scanner 통합 검증을 Phase 08 과제로 기록한다.
- 검증 실패가 하나라도 있으면 계획을 `EXECUTED` 또는 Phase 05를 `DONE`으로 변경하지 않는다.

## 사용자 승인 필요 결정

이번 보정 요청으로 아래 구현 기준은 확정됐으며 선택지를 다시 결정할 필요는 없다. 구현 전에는 이 기준을 포함한 DRAFT 계획 전체에 대한 사용자의 명시적 승인만 남는다.

1. unsubscribe는 JSON body 기반 POST만 제공하고 GET mapping과 query parameter fallback은 만들지 않는다.
2. email link는 frontend 확인 page를 가리키며 frontend GET은 상태를 변경하지 않고 사용자 확인 뒤 POST한다.
3. BOUNCED 재구독은 자동 활성화하지 않고 상태명을 숨긴 HTTP 409로 처리하며, 유효한 BOUNCED 해지는 UNSUBSCRIBED로 전환한다.
4. subscribe·unsubscribe 성공은 HTTP 200과 `NEWSLETTER_200`, `NEWSLETTER_201`을 사용한다.
5. token은 만료 없는 HMAC-SHA256 stateless token이고 재구독 때 subscriber tokenVersion을 증가시킨다.
6. secret rotation은 `NEWSLETTER_UNSUBSCRIBE_TOKEN_KEY_ID`/active secret과 `newsletter.unsubscribe-token.previous-keys` 외부 verification map을 사용한다.
7. comment rate secret을 재사용하지 않고 별도 `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_SECRET`을 추가한다.
8. 역직렬화 가능한 모든 subscribe 요청을 validation보다 먼저 IP rate limit에 집계한다.
9. 지원하지 않는 HTTP method는 기존 Spring 처리 방식을 유지하고 전역 BaseResponse 통일은 범위 밖으로 둔다.
10. 이메일 인증, verify API, PENDING, 실제 발송, Scheduler와 frontend 화면은 구현하지 않는다.

## 실제 구현 중 발생한 차이

없음. 현재는 계획 수립만 수행했으며 Java, test, Gradle, application 설정과 API를 구현하지 않았다.

## 검증 결과

계획 수립 시작 시 branch, clean working tree, preflight, Phase 04 `DONE`과 계획 `EXECUTED`를 확인했다. 최초 DRAFT 작성 후 필수 문서를 규정 순서와 EOF까지 재독했고 필수 heading, 테스트 1~74, DRAFT·PLANNING 상태, 변경 파일 두 개, trailing whitespace와 `git diff --check`를 확인했다. 이후 사용자 보정에 따라 unsubscribe를 POST JSON body로 변경하고 GET 비노출, frontend 확인 page와 부정 테스트 75~79를 추가했다. 보정 후 상태·범위·old GET 성공 계약 부재, 테스트 번호 연속성과 문서 정적 검증을 다시 수행해 성공했다. 구현 test와 build는 계획 승인 전이므로 실행하지 않았다.
