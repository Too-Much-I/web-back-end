# Phase 07: 내부 운영 API 및 인증

- 상태: EXECUTED

## 조건부 승인 확정 사항

- `/internal/**`는 기존 JWT chain에 Filter를 삽입하지 않고 `@Order(1)` 내부 전용 `SecurityFilterChain`으로 분리한다. 기존 애플리케이션 chain은 `@Order(2)`로 유지한다.
- `INTERNAL_API_ENABLED=false`가 local/test 포함 기본값이며, 내부 API 활성화가 필요한 테스트만 32바이트 이상 dummy key로 명시적으로 활성화한다.
- 테스트 수신 email이 allowlist 밖이면 인증된 운영자의 허용 범위 위반으로 정제된 403을 반환한다.
- post 단위 수동 재시도는 한 요청에서 최대 100개 Delivery만 처리하고 `postId`, `retriedCount`, `skippedCount`, `hasMore`를 반환한다.
- 수동 재시도 HTTP 요청은 provider를 호출하지 않고 기존 Delivery만 PENDING 또는 SKIPPED로 조건부 전이한다.
- 아래 DRAFT 분석·권고 중 이 절과 충돌하는 JWT Filter 뒤 삽입, allowlist 400, 전체 cursor 순회 및 응답 명칭은 이 조건부 승인 사항으로 대체한다.

## 목표

Phase 04가 Controller 없이 준비한 댓글 운영 Service와 Phase 06이 Controller 없이 준비한 뉴스레터 운영 Service를 `/internal/**` HTTP API로 노출한다. 운영자는 댓글을 조건에 따라 조회하고 `VISIBLE` 또는 `PENDING` 댓글을 숨기며 `HIDDEN` 댓글을 복원할 수 있다. 뉴스레터는 특정 게시글의 테스트 발송, `SCHEDULED` Campaign 취소, 재시도 가능한 기존 `FAILED` Delivery의 수동 재등록만 제공한다.

모든 내부 API는 `X-Internal-Api-Key`를 반드시 검증한다. key가 없거나 틀리면 동일한 401 BaseResponse를 반환하고, 올바른 key만 고정된 내부 권한을 얻는다. JWT만으로 내부 API를 통과할 수 없고, 공개 blog/comment/newsletter와 one-click unsubscribe, exams 및 Swagger의 기존 접근 정책은 유지한다.

내부 인증·오류 응답, 일반 로그 및 Sentry에서 API key, 테스트 수신 email 전체, 댓글 내용 전체, anon_session, unsubscribe token, provider payload, 원문 IP와 proxy header를 제거한다. 인증된 댓글 목록의 content는 운영 요구사항에 따라 response에만 제공한다. 관리자 UI, 댓글 삭제·수정, 게시글 쓰기, 이메일 인증과 API Key rotation은 구현하지 않는다.

## 작업 시작 조건

- 계획 수립 브랜치는 `feat/blog-mvp`다.
- 계획 수립 전 `git status --short` 출력은 없어 작업 트리가 깨끗하다.
- `scripts/codex-preflight.sh`는 `Current branch: feat/blog-mvp`와 `Preflight: PASS`를 출력했다.
- Phase 00부터 Phase 06까지 모두 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-06-newsletter-delivery.md`는 `EXECUTED`다.
- Current phase는 Phase 07이고 계획 수립 전 상태는 `TODO`다.
- 이번 계획 수립에서는 이 계획서와 `docs/blog-mvp/IMPLEMENTATION_STATUS.md`만 변경한다.
- 사용자 명시적 승인 전 Java, test, `build.gradle`, application 설정, `SecurityConfig`, Controller와 Filter를 수정하거나 생성하지 않는다.

## 현재 코드 분석

### 실제 package와 공개 계약

- 게시글은 `web.tosunsaeng.domain.blog`, 댓글은 `web.tosunsaeng.domain.comment`, 뉴스레터는 `web.tosunsaeng.domain.newsletter`에 있다.
- main과 test에 `web.tosunsaeng.domain.blog.comment` 또는 `web.tosunsaeng.domain.blog.newsletter` package는 없다. Phase 07도 root comment/newsletter package만 사용한다.
- 공개 게시글 Controller는 `GET /api/posts`, `GET /api/posts/search`, `GET /api/posts/{slug}`만 제공한다.
- 공개 댓글 Controller는 `GET /api/posts/{slug}/comments`, `POST /api/posts/{slug}/comments`, `POST /api/comments/nickname/regenerate`만 제공한다.
- 공개 뉴스레터 Controller는 JSON `POST /api/newsletter/subscribe`, JSON `POST /api/newsletter/unsubscribe`를 제공하고, 별도 Controller가 form 기반 `POST /api/newsletter/one-click-unsubscribe/{token}`을 제공한다.
- one-click GET, newsletter verify, 게시글 쓰기, 댓글 수정·삭제와 내부 Controller는 없다.

### 댓글 운영 Service

- `BlogCommentModerationService`의 실제 메서드는 `getComments(CommentFilter)`, `hide(commentId, HiddenReason)`, `restore(commentId)`다.
- `CommentFilter`의 실제 필드는 `status`, `postId`, `slug`, `createdAtFrom`, `createdAtTo`, `page`, `size`다.
- `BlogCommentModerationServiceImpl`은 page 0 이상, size 1부터 100, `postId`와 `slug` 동시 사용 금지, `createdAtFrom < createdAtTo`를 검증한다.
- slug는 공개 상태와 무관하게 `BlogPostRepository.findBySlug`로 postId를 찾는다. 없는 slug는 404가 아니라 빈 page로 처리한다. postId 필터는 댓글 query에 그대로 적용한다.
- `BlogCommentQueryRepositoryImpl.findCommentsForModeration`은 status와 postId를 선택 적용하고 기간을 `createdAt >= from`, `createdAt < to`로 구현한다. 정렬은 `createdAt DESC`, `_id DESC`다.
- `hideComment`는 Mongo `findAndModify`로 `VISIBLE` 또는 `PENDING`을 `HIDDEN`으로 바꾸고 `hiddenAt`, `hiddenReason`, `updatedAt`을 기록한다.
- `restoreComment`는 Mongo `findAndModify`로 `HIDDEN`만 `VISIBLE`로 바꾸고 `hiddenAt`, `hiddenReason`을 제거한다.
- 조건부 update 실패 후 `findById`로 리소스 없음과 상태 충돌을 구분하며 기존 ErrorStatus는 404 `_COMMENT_NOT_FOUND`, 409 `_COMMENT_STATE_CONFLICT`를 이미 제공한다.
- `BlogComment` 실제 필드는 id, postId, anonymousVisitorId, nickname, avatarSeed, avatarImageKey, content, status, createdAt, updatedAt, hiddenAt, hiddenReason이다. status는 `VISIBLE`, `PENDING`, `HIDDEN`, HiddenReason은 `SPAM`, `ABUSE`, `ADVERTISEMENT`, `PERSONAL_INFORMATION`, `OTHER`다.
- 기존 `BlogCommentModerationDTO.ModeratedCommentResult`는 `avatarSeed`를 포함하고 `postSlug`는 포함하지 않는다. 이를 그대로 HTTP response로 사용하지 않는다.
- comment page는 여러 post의 댓글을 포함할 수 있으므로 page 안의 distinct postId를 `BlogPostRepository.findAllById`로 한 번에 조회해 postSlug map을 만든다. 댓글마다 post를 읽는 N+1 query는 만들지 않는다. 삭제되었거나 찾을 수 없는 post의 postSlug는 null로 둘 수 있다.
- 기존 moderation index `status ASC, createdAt DESC`와 공개 조회 index `postId ASC, status ASC, createdAt DESC`가 있어 Phase 07은 새 Mongo index를 추가하지 않는다.

### 뉴스레터 운영 Service

- `NewsletterOperationsService`의 실제 메서드는 `sendTest(postId, recipientEmail)`, `cancelScheduledCampaign(campaignId)`, `retryFailedDelivery(deliveryId)`다.
- 테스트 발송은 `NEWSLETTER_SENDING_ENABLED`와 `NEWSLETTER_TEST_SENDING_ENABLED`가 모두 true인지 먼저 확인한다. 이후 email을 정규화하고 allowlist 정확 일치를 검사하며 `BlogPostRepository.findById`로 글을 읽는다.
- 테스트 template은 `[테스트]` 제목과 게시글 link를 사용하며 실제 Subscriber, unsubscribe token, 수동 해지 link, RFC 8058 header, Campaign과 Delivery를 사용하지 않는다. 성공 log는 postId와 provider만 포함한다.
- 테스트 발송은 게시글의 공개 상태, `publishedAt`, `newsletterEnabled` 또는 Campaign 존재를 요구하지 않는다. 이는 실제 구독자 발송 전 draft 또는 opt-in 전 content를 검사할 수 있는 기존 Phase 06 계약으로 유지한다.
- 현재 allowlist 거절과 post 없음은 `IllegalArgumentException`이며 HTTP 계약이 없다. Phase 07에서는 raw message에 의존하지 않고 domain ErrorStatus로 변환해야 한다.
- Campaign은 `NewsletterCampaignRepository.findByPostId`로 조회할 수 있고 postId가 unique다. 기존 취소 전이는 `NewsletterCampaignQueryRepository.cancelScheduled(campaignId, now)`의 `findAndModify`로 `SCHEDULED -> CANCELED`만 허용한다.
- Phase 07 cancel URI는 postId를 받으므로 postId로 unique Campaign을 찾은 뒤 기존 campaignId 조건부 취소를 호출한다. 상태 변경은 read/save가 아니라 기존 `findAndModify`가 담당한다. Campaign이 없으면 404, 조건부 취소가 실패하면 409로 구분한다.
- 현재 수동 재시도는 deliveryId 한 건을 읽어 `FAILED`, `retryable=true`, `attemptCount < 4`, `lastErrorCode != PROVIDER_RESULT_UNKNOWN`을 확인한다. Subscriber가 ACTIVE면 같은 Delivery를 PENDING으로 조건부 requeue하고, 비활성이면 기존 Delivery를 SKIPPED로 바꾼다.
- `requeueFailedForManualRetry`는 같은 Document를 `findAndModify`하고 새 Delivery나 Campaign을 만들지 않는다. 성공 후 기존 FAILED Campaign을 SENDING으로 되돌려 completion 집계가 다시 끝낼 수 있게 한다.
- Phase 07 retry URI는 postId를 받으므로 Campaign을 postId로 찾고, 기존 `campaignId + status` index를 활용해 eligibility 조건과 `_id ASC`로 최대 101개의 수동 retry 후보 Delivery id만 읽는 query가 필요하다. 앞의 100개만 처리하고 101번째로 hasMore를 계산하며 실제 전이는 기존 단건 조건부 update를 재사용한다.
- 동시 retry 요청에서는 각 Delivery 조건부 update winner만 재시도 건수에 포함한다. ACTIVE가 아닌 Subscriber는 기존 Phase 06 정책대로 SKIPPED가 될 수 있지만 requeued count에는 포함하지 않는다. requeued count가 0이면 409다.
- master kill switch가 false여도 수동 재시도 등록 자체는 기존 Service처럼 허용한다. provider claim과 호출은 Phase 06 Scheduler가 계속 차단하므로 true로 복구되기 전 실제 발송은 일어나지 않는다. 테스트 발송만 두 switch가 모두 true여야 한다.

### SecurityConfig와 JWT

- `SecurityConfig`는 CORS, CSRF disable, stateless session을 설정하고 현재 `anyRequest().permitAll()`을 사용한다.
- `JwtAuthenticationFilter`는 Authorization Bearer token이 있을 때만 검증하고 유효하면 SecurityContext에 `ROLE_USER` Authentication을 넣는다. JWT가 없거나 잘못돼도 filter chain은 계속 진행한다.
- JWT Filter는 `UsernamePasswordAuthenticationFilter` 앞에 직접 생성해 추가된다. 별도 내부 인증 Filter나 Security test는 없다.
- 내부 matcher를 단순 `.authenticated()`로 만들면 유효한 JWT만으로 내부 API를 통과할 수 있으므로 `ROLE_INTERNAL` 전용 matcher가 필요하다.
- `/internal/**`는 `@Order(1)` 전용 SecurityFilterChain이 먼저 처리하므로 기존 JWT Filter가 실행되는 `@Order(2)` 공개 chain에 도달하지 않는다. key가 없거나 잘못되면 JWT 유무와 관계없이 401에서 chain을 중단한다.
- internal Authentication principal은 고정 문자열과 `ROLE_INTERNAL`만 가지며 credentials는 null이다. API key 원문이나 digest를 principal, authorities, exception과 SecurityContext에 넣지 않는다.
- 공개 경로에서는 내부 Filter가 `shouldNotFilter`로 완전히 빠지므로 내부 key header가 없어도 기존처럼 접근할 수 있다.

### 공통 응답과 예외

- `BaseResponse`는 `isSuccess`, `code`, `message`, non-null일 때만 `result`를 직렬화한다.
- `ErrorStatus`에는 공통 400, 401, 403과 댓글 moderation 400/404/409가 있으나 내부 API 전용 인증, disabled, newsletter post/Campaign 없음, test disabled, allowlist, provider 실패 코드는 없다.
- `SuccessStatus`에는 내부 댓글 및 뉴스레터 operation 성공 code가 없다.
- `GlobalExceptionAdvice`는 예상하지 못한 exception에서 `printStackTrace`, Sentry capture와 exception message result를 사용한다. 내부 API의 email, content 또는 key가 exception chain에 포함될 위험을 줄이기 위해 내부 Controller 전용 highest-precedence advice를 둔다.
- 내부 advice는 알려진 `BlogCommentException`, `NewsletterException`, 입력 binding/type 오류를 400/404/409로 매핑하고, 예상하지 못한 오류는 원문 message 없이 generic 500 BaseResponse를 반환한다.
- Filter 단계의 인증 실패는 Controller advice에 도달하지 않으므로 Filter가 동일한 ObjectMapper 기반 BaseResponse를 직접 작성한다.

### Sentry와 일반 로그

- `NewsletterTelemetryPrivacyConfig`가 현재 newsletter subscribe/unsubscribe와 one-click 요청의 data, query, cookie, Authorization와 일부 forwarding/IP header, user email/IP를 제거한다.
- one-click path token event URL은 `{token}`으로 치환하고 SDK 제약 때문에 해당 transaction은 drop한다.
- `X-Internal-Api-Key`와 `/internal/**`는 현재 sanitizing 대상이 아니다.
- 같은 BeforeSend callback Bean을 별도로 추가하면 callback 선택 충돌 위험이 있으므로 기존 privacy config를 확장해 `/internal/**` 전체를 민감 요청으로 취급한다.
- 내부 request data와 query, cookie, user PII를 제거하고 `X-Internal-Api-Key`, Authorization, Cookie 및 proxy/IP 관련 header와 env를 case-insensitive하게 제거한다. internal resource ID가 있는 path는 유지할 수 있지만 request body, test email과 댓글 content는 전송하지 않는다.
- Filter와 Controller는 key, request URI, email, content 또는 원문 IP를 log하지 않는다. 인증 실패 log가 필요하면 `event=internal_api.authentication_failed` 같은 고정 event만 사용하고 이유, URI, header와 IP를 넣지 않는다.

### 설정과 테스트 기반

- main application 설정에는 internal API property가 없다. test profile에도 내부 key가 없다.
- 저장소에는 신뢰할 수 있는 `production` profile 규약이 없고 application의 Sentry environment만 active profile 기본값을 local로 표시한다.
- 기존 Controller test는 standalone MockMvc가 대부분이라 실제 SecurityFilterChain을 적용하지 않는다.
- `TosunsaengApplicationTests`와 exams Repository scan test는 test profile 전체 context를 기동한다.
- 현재 dependency로 Spring Security, Spring Boot Test, MockMvc와 Mockito를 사용할 수 있다. Phase 07 test를 위해 `build.gradle` 의존성을 추가할 필요가 없다.

## 구현 범위

1. `GET /internal/comments`와 댓글 숨김·복원 PATCH Controller를 추가한다.
2. 기존 댓글 moderation query와 원자적 상태 전이를 재사용하고 HTTP response 전용 안전한 DTO를 만든다.
3. 댓글 목록에 postSlug를 batch로 보강하고 금지된 visitor/token/rate-limit 내부 필드를 제외한다.
4. 뉴스레터 테스트 발송·예약 취소·post 단위 실패 재시도 Controller를 추가한다.
5. Phase 06 운영 Service를 postId 기반 HTTP orchestration과 안전한 domain 오류 계약으로 확장한다.
6. 수동 retry 후보를 campaignId와 eligibility 조건으로 `_id ASC` 최대 101개 조회하고 앞의 100개에 기존 Delivery 조건부 update를 재사용한다.
7. `INTERNAL_API_ENABLED`와 `INTERNAL_API_KEY` 설정, UTF-8 32바이트 이상 검증과 고정 길이 digest 비교를 추가한다.
8. `/internal/**` 전용 OncePerRequestFilter, `@Order(1)` SecurityFilterChain과 고정 `ROLE_INTERNAL` Authentication을 추가한다.
9. 기존 SecurityConfig에는 `@Order(2)`만 추가하고 공개 경로 `permitAll`, JWT, CORS와 CSRF 정책을 유지한다.
10. 내부 인증·입력·리소스·상태 충돌·provider 실패에 대한 BaseResponse status code를 추가한다.
11. 기존 Sentry privacy callback을 내부 요청까지 확장한다.
12. mock, repository BSON, Controller MockMvc와 실제 SecurityFilterChain contract test를 추가하고 전체 회귀를 실행한다.

## 제외 범위

- 관리자 웹 UI
- 댓글 삭제와 삭제 상태
- 댓글 수정
- 게시글 생성·수정·삭제 API
- 이메일 인증, verify API와 NewsletterSubscriber PENDING 상태
- SES bounce 및 complaint webhook
- API Key rotation, 복수 active key와 key 관리 UI
- 운영자 계정, RBAC와 OAuth 관리자 login
- IP allowlist, AWS WAF, API Gateway와 새 Redis rate limit
- 브라우저 frontend에 internal key 전달
- CORS와 CSRF의 광범위한 재설계
- 기존 JWT 발급·payload·유효성 정책 변경
- SES Contact List, 실제 테스트 email과 실제 provider 호출
- Testcontainers와 실제 MongoDB 상태 전이 통합 test
- 실제 ALB, reverse proxy, Sentry와 운영 인프라 접근
- 기존 public API response 또는 URI 변경
- 관련 없는 blog, comment, newsletter와 exams 리팩터링
- `web.tosunsaeng.domain.blog.comment` 또는 `web.tosunsaeng.domain.blog.newsletter` 재생성

## 예상 변경 파일

### 계획 수립에서 생성 및 수정

- 생성: `docs/blog-mvp/plans/PHASE-07-internal-operations-security.md`
- 수정: `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

### 승인 후 예상 수정 main 및 설정 파일

- `.env.example`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `src/main/java/web/tosunsaeng/domain/comment/application/BlogCommentModerationService.java`
- `src/main/java/web/tosunsaeng/domain/comment/application/BlogCommentModerationServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/comment/converter/BlogCommentModerationConverter.java`
- `src/main/java/web/tosunsaeng/domain/comment/dto/BlogCommentModerationDTO.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterOperationsService.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterOperationsServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterDeliveryQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterDeliveryQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/config/NewsletterTelemetryPrivacyConfig.java`

`build.gradle`, 기존 public Controller, `NewsletterService`, `NewsletterSubscriber`, Campaign/Delivery Document, Mongo index initializer, JWT Filter/Provider, CORS 설정과 exams source는 수정하지 않는다.

### 승인 후 예상 생성 main 파일

- `src/main/java/web/tosunsaeng/global/config/InternalApiSecurityConfig.java`
- `src/main/java/web/tosunsaeng/global/config/security/InternalApiProperties.java`
- `src/main/java/web/tosunsaeng/global/config/security/InternalApiKeyVerifier.java`
- `src/main/java/web/tosunsaeng/global/config/security/InternalApiKeyAuthenticationFilter.java`
- `src/main/java/web/tosunsaeng/global/exception/InternalOperationsExceptionAdvice.java`
- `src/main/java/web/tosunsaeng/domain/comment/api/InternalCommentController.java`
- `src/main/java/web/tosunsaeng/domain/comment/dto/InternalCommentRequestDTO.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/api/InternalNewsletterController.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/dto/InternalNewsletterRequestDTO.java`
- `src/main/java/web/tosunsaeng/domain/newsletter/dto/InternalNewsletterResponseDTO.java`

Filter는 일반 servlet Filter Bean으로 자동 등록하지 않고 내부 전용 SecurityFilterChain 안에 한 번만 추가한다. 별도 `FilterRegistrationBean` 없이 `InternalApiSecurityConfig`에서 명시적으로 생성·배치한다.

### 승인 후 예상 test 파일

- 생성: `src/test/java/web/tosunsaeng/global/config/security/InternalApiPropertiesTest.java`
- 생성: `src/test/java/web/tosunsaeng/global/config/security/InternalApiKeyVerifierTest.java`
- 생성: `src/test/java/web/tosunsaeng/global/config/InternalApiSecurityContractTest.java`
- 생성: `src/test/java/web/tosunsaeng/domain/comment/api/InternalCommentControllerTest.java`
- 생성: `src/test/java/web/tosunsaeng/domain/newsletter/api/InternalNewsletterControllerTest.java`
- 수정: `src/test/java/web/tosunsaeng/domain/comment/application/BlogCommentModerationServiceImplTest.java`
- 수정: `src/test/java/web/tosunsaeng/domain/newsletter/application/NewsletterOperationsServiceImplTest.java`
- 수정: `src/test/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterDeliveryQueryRepositoryImplTest.java`
- 수정: `src/test/java/web/tosunsaeng/domain/newsletter/config/NewsletterTelemetryPrivacyConfigTest.java`

실제 구현 중 이 목록 밖 source 수정이 필요하면 즉시 중단하고 차이와 이유를 사용자에게 보고한다.

## API 변경

### 내부 API 계약

모든 JSON API는 기존 `BaseResponse`를 사용한다. 성공 result만 아래 필드를 가지며 인증 실패와 오류 result는 null이라 JSON에서 생략한다. 내부 Controller는 공개 Swagger에 key 입력 UI가 생기지 않도록 `@Hidden` 처리한다.

### 댓글 목록

~~~http
GET /internal/comments?status=HIDDEN&postId=...&createdAtFrom=...&createdAtTo=...&page=0&size=20
X-Internal-Api-Key: ...
~~~

- query parameter는 status, postId, slug, createdAtFrom, createdAtTo, page, size다.
- page 기본값은 0, size 기본값은 20, 최대 size는 100이다.
- status는 `VISIBLE`, `PENDING`, `HIDDEN` 중 하나다.
- from은 포함하고 to는 제외한다. from과 to가 모두 있으면 from이 반드시 to보다 앞서야 한다.
- postId와 slug를 동시에 보내면 기존 Service 계약대로 400이다.
- 없는 slug는 기존 Service 의미를 유지해 200 빈 page이며, 임의 postId도 일치 댓글이 없으면 빈 page다.
- response item은 commentId, postId, nullable postSlug, nickname, avatarImageUrl, content, status, hiddenReason, createdAt, hiddenAt만 제공한다.
- anonymousVisitorId, tokenHash, anon_session, avatarSeed, avatarImageKey, updatedAt, IP/IP hash, Redis key, content hash, HMAC와 rate-limit owner token은 제공하지 않는다.

### 댓글 숨김

~~~http
PATCH /internal/comments/{commentId}/hide
Content-Type: application/json

{"reason":"SPAM"}
~~~

- reason은 실제 `HiddenReason` 다섯 값만 허용하며 null, unknown과 unreadable JSON은 400이다.
- `VISIBLE -> HIDDEN`, `PENDING -> HIDDEN`만 기존 원자적 findAndModify로 처리한다.
- 성공 result는 id, status=`HIDDEN`, hiddenReason, hiddenAt만 가진다.
- 댓글이 없으면 404, 이미 HIDDEN 등 허용되지 않은 상태면 409다.

### 댓글 복원

~~~http
PATCH /internal/comments/{commentId}/restore
~~~

- request body는 없다.
- `HIDDEN -> VISIBLE`만 원자적으로 처리한다.
- 성공 result는 id, status=`VISIBLE`, hiddenReason=null, hiddenAt=null 의미를 가진다. null field 직렬화 여부와 무관하게 Service result에서 값이 제거됐음을 test한다.
- 댓글이 없으면 404, VISIBLE 또는 PENDING이면 409다.

### 뉴스레터 테스트 발송

~~~http
POST /internal/newsletter/posts/{postId}/test
Content-Type: application/json

{"email":"operator-test@example.com"}
~~~

- master/test switch와 allowlist 검사는 기존 Phase 06 Service에서 수행한다.
- 성공 result는 postId와 status=`SENT`만 반환하며 email 전체, providerMessageId와 provider response를 반환하지 않는다.
- invalid email은 400, allowlist 밖 email은 403, 두 switch 중 하나라도 false이면 409, 게시글이 없으면 404다.
- provider 호출이 안전하게 실패하면 원문 없이 generic 502로 변환한다. 예상하지 못한 code 오류는 generic 500이다.
- Campaign, Delivery, Subscriber와 실제 unsubscribe token은 생성·조회·변경하지 않는다.

### 뉴스레터 예약 취소

~~~http
POST /internal/newsletter/posts/{postId}/cancel
~~~

- unique postId Campaign을 찾고 기존 `cancelScheduledCampaign(campaignId)`를 호출한다.
- 성공 result는 postId와 status=`CANCELED`만 반환한다.
- Campaign이 없으면 404다.
- SENDING, SENT, FAILED, 이미 CANCELED 또는 동시 취소 loser는 동일한 generic 409다. 현재 상태를 상세 message로 노출하지 않는다.

### 뉴스레터 실패 재시도

~~~http
POST /internal/newsletter/posts/{postId}/retry
~~~

- postId의 Campaign이 없으면 404다.
- candidate는 기존 Delivery 중 `FAILED`, `retryable=true`, `attemptCount < 4`, `lastErrorCode != PROVIDER_RESULT_UNKNOWN`인 항목이다.
- campaignId와 eligibility 조건으로 `_id ASC` 후보 id만 최대 101개 읽고 앞의 100개를 처리한다. 101번째 존재 여부로 `hasMore`를 계산한다.
- 각 id는 기존 단건 전이 정책을 재사용해 Delivery 및 현재 Subscriber 상태를 다시 확인하고 조건부 requeue 또는 SKIPPED 처리한다.
- ACTIVE만 같은 Delivery를 PENDING으로 만들고, inactive Subscriber는 기존 Phase 06 정책대로 조건부 SKIPPED가 될 수 있다.
- SENT, SKIPPED, retryable=false, 최대 시도, PROVIDER_RESULT_UNKNOWN은 선택·변경하지 않는다.
- 성공 result는 postId, retriedCount, skippedCount와 hasMore를 제공한다. Subscriber, Delivery id, email, 오류 code 목록은 반환하지 않는다.
- 실제 PENDING 또는 SKIPPED 전이가 하나도 없으면 409다. 새 Delivery나 새 Campaign은 만들지 않는다.

### 금지 endpoint

- `DELETE /internal/comments/{commentId}` 없음
- `DELETE /api/comments/{commentId}` 없음
- `PATCH /api/comments/{commentId}` 없음
- 게시글 생성·수정·삭제 API 없음
- newsletter verify API 없음
- `/internal/**` 밖 운영 기능 없음

## DB 변경

- 새 Mongo Document, collection, field와 index는 추가하지 않는다.
- 기존 `blog_comments`, `newsletter_campaigns`, `newsletter_deliveries` Document 구조를 유지한다.
- 댓글 hide/restore, Campaign cancel과 Delivery retry/SKIPPED는 기존 field를 조건부 update한다.
- newsletter retry 후보를 campaignId, status, retryable, attemptCount, lastErrorCode와 `_id ASC`, limit으로 조회하는 custom Repository method만 추가한다.
- 기존 `campaignId + status` index를 활용하고 Phase 07 전용 index를 임의로 만들지 않는다. 실제 explain과 대량 성능은 Phase 08에서 검증한다.
- migration, backfill, 운영 DB query, 데이터 삭제와 index 변경을 실행하지 않는다.

## API Key 인증 설계

### 설정 정책 비교와 결론

| 정책 | 장점 | 위험 | 판단 |
|---|---|---|---|
| key 하나만 두고 누락 시 항상 기동 실패 | 운영 오설정을 즉시 발견한다. | local 기본 실행도 secret을 강제하며 저장소에는 신뢰할 수 있는 production profile 규약이 없다. | 비권고 |
| profile 이름으로 production만 fail-fast | local 편의와 운영 fail-fast를 함께 얻는다. | 실제 배포가 어떤 profile 이름을 쓰는지 코드로 확인되지 않아 잘못된 안전 가정이 된다. | 현재 적용 불가 |
| enabled 기본 false, enabled=true일 때 key fail-fast | 환경 누락 시 endpoint가 닫히며 명시적으로 켤 때는 모든 profile에서 강하게 검증한다. | 운영자가 enabled를 빼먹으면 API가 404로 닫혀 operation이 불가능하다. | 권고 |

권고 설정은 다음과 같다.

~~~text
INTERNAL_API_ENABLED=false
INTERNAL_API_KEY=
~~~

- enabled=false이면 `/internal/**`는 generic 404로 끝나며 인증 없이 공개되는 fallback이 없다.
- enabled=true이면 key가 없거나 UTF-8 기준 32바이트 미만, CR/LF 포함이면 application context 기동을 실패시킨다.
- main과 test profile 기본값은 disabled와 빈 key다. Security contract test만 enabled=true와 32바이트 이상 dummy key를 명시한다.
- local에서 내부 API가 필요할 때도 repository에 실제 key를 넣지 않고 환경변수로 32바이트 이상 개발용 값을 명시한다.
- 실제 production profile 이름을 임의 도입하지 않는다. 배포에서 enabled=true와 secret manager의 key를 함께 주입하는 운영 checklist를 둔다.

### constant-time 비교

1. 설정 key를 UTF-8 byte로 변환해 최소 32바이트를 검증한다.
2. verifier 초기화 시 설정 key의 SHA-256 digest만 비교용 값으로 만든다.
3. 요청 header도 UTF-8 byte SHA-256 digest로 바꾼다.
4. 항상 같은 길이인 두 digest를 `MessageDigest.isEqual`로 비교한다.
5. header 없음, 빈 값, 복수 header와 잘못된 값은 같은 401 body로 처리한다.

SHA-256은 key를 암호학적으로 저장하기 위한 DB hash가 아니라 길이 차이의 조기 return 없이 고정 길이 byte를 비교하기 위한 process-local 값이다. 원본 key와 digest를 log, exception, Authentication, response와 Sentry에 넣지 않는다.

## Filter 및 SecurityConfig 적용

- `InternalApiKeyAuthenticationFilter`는 `OncePerRequestFilter`이고 `/internal/**`에만 적용한다.
- enabled=false이면 generic 404 BaseResponse를 쓰고 chain을 호출하지 않는다.
- enabled=true에서 header 없음 또는 불일치는 동일한 401 `_INTERNAL_API_UNAUTHORIZED` BaseResponse를 쓰고 chain을 호출하지 않는다.
- key가 맞으면 fixed principal `internal-api`, credentials null, authority `ROLE_INTERNAL`인 Authentication을 SecurityContext에 넣고 chain을 진행한다.
- Filter는 request URI, key, Authorization, Cookie, email과 IP를 log하지 않는다.
- `InternalApiSecurityConfig`가 `securityMatcher("/internal/**")`인 `@Order(1)` chain을 제공하고 기존 SecurityConfig는 `@Order(2)` 공개 chain으로 유지한다.
- 내부 chain의 authorization rule은 모든 matching request에 `ROLE_INTERNAL`을 요구한다. JWT가 있어도 내부 chain에는 JWT Filter가 없으므로 API Key 없이는 통과하지 못한다.
- 공개 blog/comment/newsletter/one-click/exams 및 Swagger matcher는 기존 `permitAll` 의미를 유지한다.
- JwtAuthenticationFilter와 JwtTokenProvider 구현은 수정하지 않는다.
- CSRF disable, stateless session, 기존 CORS origin/method/header와 exposed header는 변경하지 않는다.
- Filter instance는 SecurityFilterChain 안에만 등록해 servlet container와 Security chain에서 두 번 실행되지 않게 한다.

## key 설정 검증

- 바이트 길이는 Java `String.length()`가 아니라 UTF-8 encoded byte 수로 판정한다.
- key가 enabled 상태에서 null, blank, 32바이트 미만 또는 CR/LF를 포함하면 secret 내용을 포함하지 않는 `IllegalStateException`으로 기동 실패한다.
- disabled 상태에서는 key가 없어도 context는 기동하지만 내부 요청은 항상 404다.
- test dummy key는 application-test에만 두고 unsubscribe/comment HMAC secret과 다른 값으로 둔다.
- key rotation은 하나의 active key를 교체하고 instance를 재시작하는 수동 절차밖에 없다. 무중단 dual-key rotation은 Phase 08 또는 후속 Phase 범위다.

## 댓글 운영 API

- Controller는 기존 `BlogCommentModerationService`만 호출하고 Repository에 직접 접근하지 않는다.
- HTTP query를 `CommentFilter`로 조립하며 page/size 기본값만 Controller가 제공하고 범위와 기간 검증은 기존 Service가 담당한다.
- Service는 page의 distinct postId를 batch로 조회해 `postSlug`를 보강한다.
- moderation response DTO에서 avatarSeed와 updatedAt을 제거하고 application/HTTP 경계 모두 민감 필드를 최소화한다.
- hide/restore Controller는 기존 Service의 404/409 구분을 그대로 BaseResponse로 변환한다.
- public `BlogCommentRestController`, public response DTO, cookie, rate limit과 고정 rule 1부터 10은 수정하지 않는다.
- DELETE mapping, soft-delete field와 삭제 상태는 만들지 않는다.

## 뉴스레터 운영 API

- Controller는 `NewsletterOperationsService`만 호출한다.
- test endpoint는 email을 request body로만 받고 response, exception과 log에 되돌리지 않는다.
- allowlist 밖 수신자는 인증된 운영자의 테스트 발송 허용 범위 위반으로 정제된 403을 반환한다. allowlist 내용과 email은 응답하지 않는다.
- test disabled는 server configuration과 현재 operation의 충돌이므로 409를 권고한다.
- cancel은 postId lookup 후 기존 campaignId 기반 원자 `findAndModify`를 재사용한다.
- retry는 Campaign 존재 확인 후 후보 id를 최대 101개 조회하고 앞의 100개에 단건 조건부 전이를 적용해 `REQUEUED`와 `SKIPPED_INACTIVE`를 각각 count한다.
- `PROVIDER_RESULT_UNKNOWN`은 candidate query, 단건 application guard와 repository 조건 모두에서 제외해 방어를 중첩한다.
- inactive Subscriber의 failed Delivery를 SKIPPED로 바꾸는 repository 조건에도 retryable, attemptCount와 unknown-result 제외 조건을 보강해 stale read가 더 넓은 상태를 변경하지 못하게 한다.
- Campaign을 새로 만들지 않고 기존 FAILED Campaign의 reopen과 completion 집계를 재사용한다.
- internal response에는 email, subscriberId, deliveryId, providerMessageId, lastErrorCode와 unsubscribe token을 포함하지 않는다.

## 상태 전이

### 허용 전이

| 기능 | 허용 전이 | 원자성 |
|---|---|---|
| 댓글 숨김 | VISIBLE 또는 PENDING -> HIDDEN | 기존 comment findAndModify |
| 댓글 복원 | HIDDEN -> VISIBLE | 기존 comment findAndModify |
| Campaign 취소 | SCHEDULED -> CANCELED | 기존 Campaign findAndModify |
| Delivery 수동 retry | eligible FAILED -> PENDING | 기존 Delivery findAndModify |
| inactive retry 후보 | eligible FAILED -> SKIPPED | 조건을 보강한 기존 update |
| Campaign reopen | FAILED -> SENDING | 기존 조건부 update |

SENT Delivery 재발송, PROVIDER_RESULT_UNKNOWN 재시도, 새 post/subscriber Delivery 생성과 새 Campaign 생성은 없다.

### 상태 전이 및 HTTP 예외

| 상황 | HTTP | 노출 정책 |
|---|---:|---|
| internal key 없음 또는 불일치 | 401 | 둘 다 동일 code/message, result 없음 |
| internal API disabled | 404 | endpoint 존재를 상세 설명하지 않음 |
| 댓글, 게시글 또는 Campaign 없음 | 404 | 내부 상태나 email 정보 없음 |
| 잘못된 enum, 기간, page/size 또는 email | 400 | 안전한 고정 message |
| allowlist 밖 테스트 email | 403 | allowlist와 email을 노출하지 않음 |
| 허용되지 않은 댓글/Campaign 상태, retry 0건, test switch disabled | 409 | 현재 상세 상태 목록을 응답하지 않음 |
| test provider 실패 | 502 | provider message와 request/response 없음 |
| 예상하지 못한 오류 | 500 | exception message와 stack trace를 response에 넣지 않음 |

## 개인정보 및 로깅

- API key, key digest, Authorization, Cookie와 anon_session을 log하거나 response에 넣지 않는다.
- 테스트 email 전체, Subscriber email, unsubscribe token/payload/signature와 provider request/response를 log하거나 response에 넣지 않는다.
- 댓글 content는 인증된 목록 response에는 요구사항에 따라 제공하지만 application log, 인증 log, exception message와 Sentry request data에는 넣지 않는다.
- IP 원문, forwarding/proxy header와 HMAC secret/hash, Redis key, reservation owner를 log 또는 response에 넣지 않는다.
- transition log가 필요하면 resource id, 고정 operation, 이전/다음 상태, 정제된 error code와 처리 건수만 사용한다.
- 인증 실패는 key 누락과 불일치를 구분하지 않는 고정 event만 허용한다. per-request log flood 위험 때문에 실제 rate 제한과 metric은 Phase 08 인프라에서 검토한다.
- GlobalExceptionAdvice를 광범위하게 수정하지 않고 내부 전용 advice가 내부 Controller exception을 먼저 처리해 원문 message 노출을 막는다.

## Sentry 처리

- 기존 `NewsletterTelemetryPrivacyConfig`의 단일 callback을 확장해 `/internal/**`를 method와 무관하게 민감 request로 판정한다.
- request data, query string, cookies와 전체 Sentry user context를 제거한다.
- case-insensitive header denylist에 `X-Internal-Api-Key`, Authorization, Cookie, Forwarded, X-Forwarded-For/Host/Proto/Port, X-Real-IP, CF-Connecting-IP, True-Client-IP와 동등한 proxy/IP header를 포함한다.
- env에서도 REMOTE_ADDR, REMOTE_HOST 및 HTTP_*로 전달된 인증/cookie/proxy/IP 값을 제거한다.
- internal path의 resource id는 허용하지만 test email이나 댓글 body가 포함된 request data는 남기지 않는다.
- one-click path token 치환과 transaction drop 동작은 그대로 유지한다.
- mock callback test로 outbound object를 검증하고 실제 Sentry 전송 및 reverse proxy access log는 Phase 08로 남긴다.

## API Key rate limit

- Phase 07은 새 Redis counter나 Lua script를 추가하지 않는다.
- 최소 32바이트 key, disabled-by-default, 외부 인프라 접근 제한과 동일한 401 응답을 MVP application 방어로 사용한다.
- 운영에서는 ALB/Security Group/firewall에서 internal route 접근원을 제한하고 401/404 비율 metric과 rate limit을 검토한다.
- application의 고정 인증 실패 log에는 key, URI와 IP를 넣지 않는다. log flood 및 credential stuffing 방어는 Phase 08 운영 검수 항목이다.

## 설정

main binding:

~~~yaml
internal:
  api:
    enabled: ${INTERNAL_API_ENABLED:false}
    key: ${INTERNAL_API_KEY:}
~~~

test profile도 기본 disabled와 빈 key를 사용한다. 실제 Security contract test만 property override로 32바이트 이상 고정 dummy key를 주입한다. `.env.example`에는 변수명, disabled 기본값, 최소 길이와 secret manager 주입 안내만 적고 실제 운영 key를 넣지 않는다.

별도 production profile 이름, key 파일 path, AWS secret 조회 code와 key rotation property는 추가하지 않는다.

## 테스트 계획

JUnit 5, Mockito, AssertJ, MockMvc, Mongo Query BSON과 실제 Spring SecurityFilterChain contract test를 사용한다. Testcontainers, 실제 Mongo/Redis/SES/Sentry와 실제 email은 사용하지 않는다.

### 내부 인증

1. key header 없음은 401과 result 없는 동일 BaseResponse다.
2. 잘못된 key는 header 없음과 동일한 401 code/message/body다.
3. 올바른 key는 내부 Controller까지 진행한다.
4. enabled=true의 32바이트 미만 UTF-8 key는 설정 기동을 실패한다.
5. enabled=true의 누락 key는 기동 실패하고 enabled=false의 누락 key는 내부 endpoint 404다.
6. verifier가 SHA-256 고정 길이 digest와 `MessageDigest.isEqual`을 사용하며 길이가 다른 candidate도 조기 문자열 equals로 비교하지 않는다.
7. key 원문과 digest가 log, exception, Authentication principal/credentials와 response에 없다.
8. `X-Internal-Api-Key`가 Sentry event와 transaction header에 없다.
9. 공개 API는 internal header 없이 성공한다.
10. JWT 없는 공개 API가 기존처럼 진행한다.
11. exams API가 internal matcher 때문에 차단되지 않는다.
12. one-click unsubscribe POST가 internal key 없이 공개 접근 가능하다.

### 댓글 운영 API

13. 댓글 목록을 기본 page 0, size 20으로 조회한다.
14. status 필터를 Service에 전달한다.
15. postId 필터를 Service에 전달한다.
16. slug를 내부 BlogPost로 해석해 조회한다.
17. createdAtFrom 경계가 `$gte`로 포함된다.
18. createdAtTo 경계가 `$lt`로 제외된다.
19. from >= to와 postId/slug 동시 입력은 400이다.
20. page 음수, size 0과 101은 400이고 size 100은 허용된다.
21. JSON response에 anonymousVisitorId, tokenHash, anon_session, avatarSeed/key, IP/hash, Redis/content hash, HMAC와 owner token이 없다.
22. VISIBLE 댓글을 HIDDEN으로 원자 전이한다.
23. PENDING 댓글을 HIDDEN으로 원자 전이한다.
24. HIDDEN 댓글을 VISIBLE로 복원하고 hidden metadata를 지운다.
25. 이미 HIDDEN 댓글 숨김은 409다.
26. VISIBLE 또는 PENDING 댓글 복원은 409다.
27. 다섯 HiddenReason만 허용하고 null/unknown은 400이다.
28. 없는 commentId는 404다.
29. internal 및 public 댓글 DELETE mapping이 없다.

### 뉴스레터 테스트 발송

30. 올바른 key와 allowlist recipient는 postId/status만 응답한다.
31. key 없음은 Service와 provider를 호출하지 않고 401이다.
32. allowlist 밖 recipient는 403이며 email과 allowlist를 echo하지 않는다.
33. master sending switch false는 provider 미호출과 409다.
34. test sending switch false는 provider 미호출과 409다.
35. 테스트 발송은 Campaign을 생성·변경하지 않는다.
36. 테스트 발송은 Delivery를 생성·변경하지 않는다.
37. Subscriber 조회와 실제 unsubscribe token 생성이 없다.
38. response와 log에 recipient email 전체가 없다.

### 예약 취소

39. postId의 SCHEDULED Campaign을 CANCELED로 바꾼다.
40. SENDING Campaign 취소는 409다.
41. SENT Campaign 취소는 409다.
42. FAILED Campaign 취소는 409다.
43. 이미 CANCELED Campaign 취소는 409다.
44. postId Campaign이 없으면 404다.
45. 두 동시 취소에서 조건부 findAndModify winner 하나만 성공하고 loser는 409다.

### 실패 재시도

46. retryable FAILED와 ACTIVE Subscriber를 같은 Delivery PENDING으로 등록한다.
47. SENT는 후보와 update에서 제외한다.
48. SKIPPED는 후보와 update에서 제외한다.
49. PROVIDER_RESULT_UNKNOWN은 query, Service와 update 모두에서 제외한다.
50. retryable=false는 제외한다.
51. attemptCount 4 이상은 제외한다.
52. UNSUBSCRIBED, BOUNCED와 없는 Subscriber는 requeue하지 않고 기존 정책에 따라 SKIPPED 처리할 수 있다.
53. 기존 Delivery id와 attemptCount를 유지한다.
54. Delivery insert/save와 새 Campaign 생성이 없다.
55. 실제 조건부 requeue winner 수만 response count에 포함한다.
56. candidate가 없거나 모든 조건부 전이가 race loser여서 PENDING/SKIPPED 0건이면 409다. inactive 후보가 SKIPPED로 전이되면 성공 count에 별도로 포함한다.

### Security 회귀

57. `GET /api/posts`는 internal key 없이 공개다.
58. 댓글 조회·작성은 internal key 없이 공개다.
59. subscribe와 JSON unsubscribe는 internal key 없이 공개다.
60. one-click unsubscribe POST는 공개이고 GET 상태 변경은 없다.
61. `/internal/**`만 internal Filter와 ROLE_INTERNAL rule을 적용받는다.
62. Swagger UI와 API docs의 기존 공개 접근 정책을 유지하며 internal Controller는 public docs에서 숨긴다.
63. SecurityConfig의 다른 path는 여전히 permitAll이고 JWT의 선택 인증 동작을 유지한다.
64. CORS와 CSRF 설정 diff가 internal matcher/Filter 추가 밖으로 확장되지 않는다.

### 금지 기능

65. 댓글 DELETE API가 없다.
66. 댓글 수정 API가 없다.
67. 게시글 생성·수정·삭제 API가 없다.
68. newsletter verify API가 없다.
69. NewsletterSubscriberStatus에 PENDING이 없다.
70. 관리자 UI source와 route가 없다.

### 추가 안전 test

71. enabled=false는 올바른 key를 보내도 404이며 Controller/Service를 호출하지 않는다.
72. 유효 JWT만 있고 internal key가 없으면 401, internal key만 맞으면 JWT 없이 성공한다.
73. 복수 `X-Internal-Api-Key` header는 모호하게 첫 값만 수용하지 않고 401이다.
74. internal Authentication은 fixed principal, ROLE_INTERNAL과 null credentials만 가진다.
75. 댓글 page의 distinct postId를 한 batch로 조회하고 postSlug를 보강하며 N+1 조회가 없다.
76. test provider exception은 502/500 generic body로 정제되고 provider message가 없다.
77. retry candidate는 campaignId와 eligibility 조건으로 `_id` 오름차순 최대 101개 id만 조회하고 앞의 100개를 처리하며 중간 경쟁에도 중복 count가 없다.
78. 옛 `web.tosunsaeng.domain.blog.comment`와 `web.tosunsaeng.domain.blog.newsletter` package가 없다.
79. test profile 기본 disabled로 전체 application context가 기동하고, internal Security contract test의 dummy key 활성화에서도 실제 외부 시스템을 호출하지 않는다.
80. auth, comment와 newsletter internal log capture에 key, email, content, token, provider body와 IP가 없다.

실행 예정:

- `git diff --check`
- `bash ./gradlew test --tests 'web.tosunsaeng.global.config.*'`
- `bash ./gradlew test --tests 'web.tosunsaeng.domain.comment.*'`
- `bash ./gradlew test --tests 'web.tosunsaeng.domain.newsletter.*'`
- `bash ./gradlew clean test bootJar`
- `rg`와 `git diff`로 DELETE/PATCH public comment, post write, verify, PENDING Subscriber, old package, key/email/token log, public matcher, CORS/CSRF와 JWT 변경을 정적 확인

## 공개 API 회귀 위험

- 현재 SecurityConfig가 전체 permitAll이므로 matcher 순서나 fallback을 잘못 바꾸면 blog/comment/newsletter/exams가 401 또는 403이 될 수 있다.
- `/internal/**` rule을 먼저 두고 마지막 `anyRequest().permitAll()`을 유지하며 representative public path와 실제 기존 Controller test를 함께 실행한다.
- 내부 advice가 annotation 전체에 적용되면 공개 error contract를 바꿀 수 있으므로 두 internal Controller에만 `assignableTypes`로 한정한다.
- comment moderation DTO 변경은 아직 HTTP에 사용되지 않은 내부 Service 계약에만 한정하고 public `BlogCommentResponseDTO`는 수정하지 않는다.
- newsletter operation 확장은 기존 subscribe/unsubscribe Service와 one-click Controller를 수정하지 않는다.

## 기존 JWT와의 충돌 가능성

- `.authenticated()`만 쓰면 기존 ROLE_USER JWT가 내부 API를 우회할 수 있다.
- internal `@Order(1)` chain은 ROLE_INTERNAL만 요구하고 기존 JWT/public `@Order(2)` chain과 분리된다.
- key 오류는 Filter에서 JWT 유무와 무관하게 동일 401로 종료한다.
- public route에서는 Filter가 빠지므로 JWT Filter가 기존처럼 선택적으로 SecurityContext를 채운다.
- Internal key를 Authorization Bearer와 합치지 않고 별도 header를 사용한다.
- JwtAuthenticationFilter, JwtTokenProvider, JWT secret과 기존 token credentials 동작은 Phase 07에서 변경하지 않는다.

## 기존 기능 영향

- public blog/comment/newsletter/exams Controller의 URI, request, response와 service 계약을 유지한다.
- comment validation rule 1부터 10, honeypot, anonymous cookie/profile과 Redis abuse prevention은 변경하지 않는다.
- BlogPost Document, public DTO와 공개 Criteria는 변경하지 않는다.
- NewsletterSubscriber 상태, stateless unsubscribe token, Campaign/Delivery 상태와 automatic Scheduler는 변경하지 않는다.
- retry candidate 조회와 inactive conditional update 조건만 기존 Delivery Repository에 추가·보강하며 Mongo index와 Document field는 바꾸지 않는다.
- Sentry privacy는 기존 newsletter redaction을 보존하면서 internal request를 추가로 제거한다.
- `build.gradle`과 AWS SDK, SES Sender, executor와 application scheduling 설정은 변경하지 않는다.

## 실제 통합 검증과 Phase 08 이관

- 실제 ALB 또는 reverse proxy에서 `/internal/**` 접근 제한
- API Key rotation과 무중단 dual-key 전환
- 실제 Sentry outbound header/data 제거
- 실제 server, ALB, CDN access log의 key 및 one-click path token 제거
- 실제 테스트 email 발송
- 실제 MongoDB comment/Campaign/Delivery 동시 상태 전이
- 배포 환경 firewall, Security Group과 private network 경계
- 인증 실패 rate limit과 metric/alert
- proxy prefix 또는 forwarded path 환경의 `/internal/**` matcher

## 위험 요소

### 운영 위험

- 단일 static API key는 유출 시 모든 내부 operation 권한을 제공하고 세분화된 operator audit가 없다.
- key 교체는 instance 재시작이 필요하며 dual-key grace period가 없어 rotation 순간 요청 실패가 생길 수 있다.
- enabled=false 기본값은 노출을 막지만 운영 환경변수 누락 시 내부 API가 404로 닫힌다. 배포 smoke check가 필요하다.
- application 인증만으로 public network 접근을 차단하지 못하므로 ALB, Security Group 또는 firewall 제한이 필수다.
- API key rate limit을 application에 두지 않아 반복 공격과 log flood는 인프라 방어에 의존한다.
- 현재 CORS가 광범위한 header를 허용하지만 key를 browser에 배포하지 않는 운영 규칙이 핵심이다.
- GlobalExceptionAdvice의 광범위한 stack trace/message 동작은 internal scoped advice로 우회하지만 다른 공개 endpoint의 기존 위험은 Phase 07 범위 밖이다.
- post 단위 retry가 많은 Delivery를 순회하면 HTTP 응답 시간이 길어질 수 있다. batch 100으로 memory를 제한하지만 실제 부하는 Phase 08에서 검증해야 한다.
- inactive retry 후보를 SKIPPED로 정리하면 retriedCount가 0이어도 skippedCount를 포함한 성공 응답이 가능하다. operation 자체는 상태를 변경하므로 호출자는 두 count를 모두 확인해야 한다.
- Sentry callback test는 실제 SDK integration, access log와 proxy capture까지 보장하지 않는다.

## 롤백 방법

- 구현 전에는 이 계획서와 상태 문서의 Phase 07 변경만 기존 Session Log를 보존하는 수동 역패치로 되돌린다.
- 구현 후 긴급 차단은 먼저 `INTERNAL_API_ENABLED=false`로 배포해 `/internal/**`를 404로 닫는다.
- code rollback은 internal Controller, Filter, verifier와 config를 후속 patch로 제거하고 SecurityConfig matcher, status code와 application binding을 수동 역패치한다.
- comment moderation DTO의 postSlug 보강과 newsletter post 단위 orchestration은 해당 내부 Controller 제거 후 후속 patch로 이전 Service 계약에 맞춘다.
- Phase 07은 새 Mongo Document, collection과 index를 만들지 않으므로 데이터 삭제 rollback은 수행하지 않는다.
- Phase 07 operation으로 이미 HIDDEN/CANCELED/PENDING/SKIPPED가 된 데이터는 Codex가 자동 되돌리지 않는다. 필요한 상태 변경은 승인된 운영 절차로 별도 수행한다.
- `git reset`, `git restore`, `git checkout`, `git clean`, 사용자 변경 삭제와 운영 DB 접근을 사용하지 않는다.

## 완료 조건

- 사용자의 조건부 승인을 반영한 APPROVED 계획 범위만 구현하고 모든 검증 성공 시에만 EXECUTED와 DONE으로 바꾼다.
- 실제 root blog/comment/newsletter package만 사용한다.
- 여섯 internal endpoint가 명시된 request/response와 오류 계약으로 존재한다.
- `/internal/**`는 disabled 시 닫히고 enabled 시 32바이트 이상 key와 ROLE_INTERNAL 없이는 접근할 수 없다.
- key 비교가 고정 길이 digest와 constant-time 비교를 사용하고 key가 Authentication, log, response와 Sentry에 없다.
- 유효 JWT만으로 internal endpoint를 우회하지 못하며 공개 API는 JWT와 internal key 없이 기존처럼 접근할 수 있다.
- 댓글 목록 `[from,to)`, pagination, safe response와 원자 hide/restore를 검증한다.
- 뉴스레터 test send는 두 switch/allowlist를 지키고 Campaign/Delivery/token을 만들지 않는다.
- Campaign cancel과 Delivery retry가 기존 Document의 조건부 원자 update만 사용하고 새 Delivery/Campaign을 만들지 않는다.
- PROVIDER_RESULT_UNKNOWN, SENT와 SKIPPED는 재발송하지 않는다.
- internal scoped advice가 400/404/409/502/500을 민감정보 없는 BaseResponse로 반환한다.
- Sentry에서 internal key, auth/cookie/proxy/IP header, request data와 user PII가 제거되고 기존 one-click 보호가 유지된다.
- 테스트 1부터 80, 기존 blog/comment/newsletter/exams test, `git diff --check`와 `bash ./gradlew clean test bootJar`가 모두 성공한다.
- 댓글 DELETE/수정, 게시글 쓰기, verify, PENDING Subscriber, 관리자 UI와 새 Redis rate limit이 없다.
- 실제 인프라·Sentry·Mongo·email 검증을 Phase 08 과제로 유지한다.
- 실패가 하나라도 있으면 계획을 EXECUTED 또는 Phase 07을 DONE으로 변경하지 않는다.

## 승인 확정 항목

1. `INTERNAL_API_ENABLED` 기본 false와 비활성 `/internal/**` generic 404
2. enabled=true이면 profile과 무관하게 key 누락, blank, UTF-8 32바이트 미만, 1024바이트 초과 또는 CR/LF를 기동 실패시키는 정책
3. 설정 key와 candidate의 SHA-256 fixed-length digest를 `MessageDigest.isEqual`로 비교하는 정책
4. `/internal/**` `@Order(1)` chain과 기존 JWT/public `@Order(2)` chain 분리 및 ROLE_INTERNAL 적용
5. allowlist 밖 test recipient 403, test switch 비활성 및 상태 충돌 409, provider 실패 502
6. post 단위 retry 한 번당 최대 100개, retriedCount/skippedCount/hasMore 응답과 HTTP 요청 중 provider 미호출
7. inactive retry 후보는 기존 Delivery를 SKIPPED로 정리하고 retriedCount에서 제외하는 정책
8. internal Controller 공개 Swagger `@Hidden`
9. application Redis rate limit과 multi-key rotation을 추가하지 않고 Phase 08 운영 검수로 남기는 정책

## 실제 구현 중 발생한 차이

- 조건부 승인된 제품 계약과 제외 범위에서 벗어난 차이는 없다.
- 최초 DRAFT의 JWT chain 삽입, allowlist 400, 전체 cursor 순회와 단일 retry count 제안은 조건부 승인에 따라 각각 별도 우선순위 chain, 403, 최대 101개 id 조회/100개 처리, retried/skipped/hasMore 응답으로 대체했다.
- 비정상적으로 긴 Header 거절을 구체화하기 위해 설정 및 요청 key 상한을 UTF-8 1024바이트로 두었다. 이 값은 응답이나 log에 노출하지 않는다.
- 민감 요청의 Sentry user PII는 개별 필드만 지우지 않고 user context 전체를 제거하도록 보수적으로 구현했다.
- 실제 생성·수정 파일은 예상 목록과 일치하며 `build.gradle`, 공개 Controller, JWT Filter/Provider, CORS 설정, Mongo Document/index와 exams source는 변경하지 않았다.

## 검증 결과

- `git diff --check` 성공.
- 요청된 소문자 선택자는 클래스 전체를 포착하지 않아 실제 관련 9개 클래스의 가장 좁은 선택자로 재실행했고 Phase 07 관련 80개가 failures/errors/skipped 0으로 성공했다.
- `bash ./gradlew clean test bootJar` 성공: 전체 421개, failures/errors/skipped 0, 60 MiB bootJar 생성.
- 전체 회귀 구성은 blog 66개, comment 162개, newsletter 174개, exams 1개와 나머지 application/global test 18개다.
- 정적 검사에서 댓글 DELETE/public PATCH, 게시글 쓰기, newsletter verify, Subscriber PENDING, 옛 blog 하위 comment/newsletter package, 내부 Redis rate limit, API key hardcoding과 email/token/provider body log가 없음을 확인했다.
- 내부 Controller `@Hidden`, 기존 public chain의 CORS/CSRF/JWT/permitAll 유지, one-click 공개 접근, 기본 kill switch `INTERNAL_API_ENABLED=false`를 확인했다.
- 실제 MongoDB, Redis, SES, Sentry, ALB/reverse proxy, access log와 실제 email은 호출하거나 검증하지 않았으며 Phase 08로 이관한다.
