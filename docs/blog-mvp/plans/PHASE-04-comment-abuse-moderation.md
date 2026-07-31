# Phase 04: 댓글 스팸 방지와 운영 상태 관리

- 상태: EXECUTED

## 목표

Phase 03의 공개 댓글 작성 계약과 고정 댓글 규칙 1~10을 유지하면서 Redis 기반 남용 방지 계층을 추가한다. 익명 방문자의 tokenHash와 별도 HMAC 처리한 client IP를 기준으로 여러 시간 창을 한 번의 원자적 Redis 연산에서 판정하고, 동일 방문자·게시글·정규화 내용의 반복 작성도 제한한다.

댓글 작성 요청의 website 허니팟을 실제로 적용하되 탐지 기준을 상세히 노출하지 않고, validation·허니팟·rate limit 실패 시 AnonymousVisitor와 BlogComment를 저장하지 않는다. Redis 연결 장애와 timeout은 fail-open으로 제한하되 Lua 계약 오류나 애플리케이션 결함은 숨기지 않는다.

향후 Phase 07의 인증된 내부 Controller가 사용할 수 있도록 운영용 댓글 목록 조회, 숨김, 복원 Application Service와 원자적 상태 전이를 준비한다. 이번 Phase에서는 운영 HTTP API, API Key 인증, 댓글 삭제를 만들지 않는다.

## 작업 시작 조건

- 계획 수립 시작 시 실제 브랜치는 feat/blog-mvp다.
- git status --short 출력은 없어 작업 트리가 깨끗하다.
- scripts/codex-preflight.sh는 Current branch: feat/blog-mvp와 Preflight: PASS를 출력했다.
- Phase 00~03은 모두 DONE이다.
- docs/blog-mvp/plans/PHASE-03-anonymous-comments.md의 상태는 EXECUTED다.
- 현재 단계는 Phase 04이고 계획 수립 전 상태는 TODO다.
- 이 계획 수립에서는 이 계획서와 docs/blog-mvp/IMPLEMENTATION_STATUS.md만 변경한다.
- 2026-07-30 사용자가 계획을 명시적으로 승인하고 Redis topology, Lua blocker 응답, tie 우선순위, 허니팟 trim, 처리 순서와 제외 범위를 확정했다.

## 현재 코드 분석

### 댓글 작성과 validation 순서

- BlogCommentRestController는 댓글 목록 GET, 댓글 작성 POST, 익명 프로필 재생성 POST만 노출한다. 댓글 작성은 raw anon_session cookie를 BlogCommentService에 전달하고 결과에 새 token이 있을 때만 Set-Cookie를 추가한다.
- BlogCommentServiceImpl.createComment는 Clock의 현재 시각으로 공개 BlogPost를 조회한 뒤 CommentValidator에 content와 공개 여부를 함께 전달한다. violation이 없으면 AnonymousVisitorService.resolve가 방문자를 조회·생성·저장하고, 그 다음 VISIBLE BlogComment를 저장한다.
- CommentValidator는 JsonNode의 textual 여부를 먼저 판정하고 strip한 문자열을 사용한다. 규칙 1~10을 TreeMap에 수집하므로 rule 3은 violation에 포함하지 않고 나머지는 중복 없이 번호순으로 반환한다.
- website 필드는 CreateCommentRequest에 이미 존재하지만 Phase 03에서는 무시한다.
- Phase 04의 요구 순서를 지키려면 허니팟을 CommentValidator보다 먼저 판정하고, 정상 validation 뒤 Redis 제한을 통과하기 전에는 AnonymousVisitorService가 save 또는 lastSeenAt 갱신을 하지 않도록 방문자 준비와 확정 단계를 분리해야 한다.
- 공개 게시글 판정은 BlogPostRepository.findPublicPostBySlug(slug, now)의 PUBLISHED, publishedAt non-null, publishedAt lte now 조건을 계속 재사용한다. 규칙 10의 외부 계약과 기존 게시글 공개 조건은 변경하지 않는다.

### 익명 token과 방문자 저장

- AnonymousTokenManager는 SecureRandom으로 32-byte raw token을 만들고 BLOG_ANONYMOUS_TOKEN_SECRET 기반 HMAC-SHA256을 Base64URL tokenHash로 만든다.
- raw token은 cookie에만 전달되고 AnonymousVisitor에는 tokenHash만 저장된다.
- AnonymousVisitorServiceImpl.resolve는 유효 cookie의 기존 방문자를 찾으면 lastSeenAt을 갱신해 즉시 저장하고, 없으면 token·profile을 만든 뒤 즉시 저장한다. 이 구조를 그대로 두면 rate limit 실패 시 방문자 미저장 조건을 만족할 수 없다.
- 신규 방문자의 Redis 기준 식별자를 저장 전에 얻을 수 있도록 prepare 단계에서 unsaved visitor 후보, tokenHash, 필요한 새 cookie token을 만들고, 제한 통과 후 commit 단계에서만 save하도록 계약을 분리한다.
- 기존 프로필 재생성 endpoint는 댓글 작성 제한 대상이 아니므로 현재 regenerate 동작을 유지한다.

### 기존 Redis 구성

- build.gradle에는 spring-boot-starter-data-redis가 이미 있으므로 Phase 04를 위해 의존성을 추가할 필요가 없다.
- RedisConfig는 RedisTemplate<String, Object> 한 개를 만들고 key와 value serializer에 StringRedisSerializer를 사용한다.
- exams는 exam:status:{examId} namespace에서 단순 get/set과 1시간 TTL만 사용한다. Lua, rate limit, 장애 분류, 공통 Redis key factory는 없다.
- exams의 Redis 예외는 별도 fail-open 정책 없이 기존 호출 흐름으로 전파된다. Phase 04의 fail-open은 blog comment 전용 adapter 내부에만 두고 exams의 동작을 변경하지 않는다.
- blog namespace는 blog:comment:*로 분리해 exam:status:*와 충돌하지 않게 한다.
- 기존 RedisTemplate을 그대로 주입해 문자열 key·argument·result를 사용하고, RedisConfig 또는 exams serializer를 변경하지 않는다.
- 승인 후 재확인 결과 저장소에는 Redis Cluster, Sentinel, 다중 primary, node 목록 설정이 없고 application.yml은 REDIS_HOST/REDIS_PORT 단일 endpoint만 사용한다. compose.local.yml도 단일 redis:7.2-alpine container 한 개만 정의하므로 Phase 04는 standalone/single-primary 전제를 적용한다.

### 예외와 응답

- BaseResponse는 isSuccess, code, message, result 구조이고 failure에도 non-null result를 지원한다.
- BlogCommentExceptionAdvice는 BlogCommentRestController에만 적용되며 CommentValidationException과 댓글 범위의 HttpMessageNotReadableException을 구조화해 처리한다.
- GlobalExceptionAdvice는 exams를 포함한 전역 응답을 처리하므로 Phase 04의 429 payload와 Retry-After는 BlogCommentExceptionAdvice의 구체 handler로 추가한다.
- ErrorStatus에는 COMMENT_4001 validation code만 있고 SuccessStatus에는 COMMENT_200~202가 있다. 새 상수는 이 값들과 충돌하지 않게 append한다.
- Redis/Lua 구현 오류를 일반 Exception으로 그대로 노출하면 기존 전역 handler가 exception message를 result에 넣을 수 있다. 댓글 Redis adapter는 non-connectivity 오류를 fail-open하지 않되 외부에는 generic internal error만 전달하고 raw Redis exception message는 응답에 넣지 않는다.

### client IP와 Security

- 현재 코드에는 getRemoteAddr, X-Forwarded-For, ForwardedHeaderFilter, RemoteIpValve 또는 server.forward-headers-strategy 설정이 없다.
- SecurityConfig는 모든 요청을 허용하며 Phase 04에서 변경할 이유가 없다.
- 기본 구현은 HttpServletRequest.getRemoteAddr()만 사용하고 X-Forwarded-For나 Forwarded header를 직접 읽지 않는다.
- CloudFront·ALB·reverse proxy 환경은 edge가 외부 forwarded header를 제거·재작성하고 신뢰 proxy 범위를 설정한 뒤 Spring/Tomcat이 제공하는 remote address를 사용해야 한다. 이 인프라 설정은 이번 Phase에서 임의로 추가하지 않는다.

### 댓글 Document, Repository, 인덱스

- BlogComment에는 status, createdAt, updatedAt, hiddenAt, hiddenReason가 이미 있다. hiddenReason만 String이므로 고정 enum을 도입할 때 타입을 HiddenReason으로 좁히면 된다.
- CommentStatus는 VISIBLE, PENDING, HIDDEN만 있고 삭제 상태는 없다.
- 공개 목록 query는 postId와 VISIBLE status를 사용하고 createdAt DESC, id DESC로 정렬한다.
- 기존 programmatic initializer에는 postId ASC + status ASC + createdAt DESC와 anonymousVisitorId ASC 인덱스가 있다.
- 운영 조회의 status 단독 필터와 createdAt 정렬에는 기존 postId 선행 인덱스를 사용할 수 없으므로 status ASC + createdAt DESC 인덱스를 추가한다. 기존 postId + status + createdAt 인덱스는 중복 생성하지 않고 그대로 재사용한다.
- 운영 조회에서 slug를 지원하려면 BlogComment가 postId만 저장하므로 Application Service가 BlogPostRepository의 내부 slug lookup으로 postId를 먼저 해석해야 한다. 이 lookup은 공개 게시글 조건을 적용하지 않으며 기존 게시글 API 동작은 바꾸지 않는다.

### 테스트 기반

- 댓글 tests는 Mockito, AssertJ, standalone MockMvc, Mongo Query BSON과 IndexDefinition 검증을 사용한다.
- test profile은 실제 Mongo 연결을 요구하지 않으며 Redis 통합 test, embedded Redis, Testcontainers가 없다.
- Phase 04에서는 RedisTemplate 호출 계약, Lua resource와 key/argument/result 계약, 장애 분류, Service 흐름을 mock 기반으로 검증한다.
- 실제 Redis의 Lua 실행, TTL, 다중 instance 동시성은 mock만으로 증명할 수 없다. 새 의존성을 임의로 추가하지 않고 Phase 08의 실제 Redis 통합 검증으로 명시적으로 남긴다.

## 구현 범위

사용자 승인 후 다음 범위만 구현한다.

1. blog comment 전용 abuse ConfigurationProperties와 Lua script Bean을 추가한다.
2. client IP resolver, 별도 HMAC hasher, Redis key factory를 추가한다.
3. visitor 3개 창과 IP 2개 창 및 duplicate reservation을 한 번에 판정하는 Redis adapter를 추가한다.
4. Redis 연결·timeout과 구현 오류를 구분하는 classifier 및 fail-open orchestration을 추가한다.
5. AnonymousVisitor의 prepare/commit 계약을 추가해 제한 실패 전 save를 막는다.
6. website 허니팟을 댓글 작성의 가장 이른 application 단계에 적용한다.
7. 댓글 작성 Service에 validation, 공개 게시글, HMAC, Redis admission, visitor commit, comment save, duplicate reservation release 흐름을 연결한다.
8. 429 BaseResponse와 Retry-After header, generic 202 honeypot 응답을 댓글 전용 advice/controller에 추가한다.
9. HiddenReason과 숨김·복원 상태 전이, 운영 조회 Service와 repository query를 추가한다.
10. status + createdAt MongoDB 인덱스를 기존 programmatic initializer에 추가한다.
11. 관련 unit·mock integration·MockMvc·회귀 tests를 추가한다.

## 제외 범위

- 댓글 수정
- 댓글 삭제 및 삭제 상태
- 운영자 댓글 삭제
- 자동 스팸 판정과 정상 댓글의 PENDING 저장
- 운영자 HTTP Controller
- /internal/comments 또는 다른 /internal 댓글 endpoint
- 내부 API Key 인증
- SecurityConfig 변경
- 애플리케이션 코드에서 X-Forwarded-For 직접 신뢰
- CloudFront, ALB, reverse proxy 또는 Spring forward-header 운영 설정 변경
- CAPTCHA
- 대댓글
- 좋아요
- 이미지 업로드
- 뉴스레터와 이메일 발송
- 게시글 공개 API와 공개 Criteria 변경
- 실제 운영 Redis 또는 MongoDB 접근
- embedded Redis, Testcontainers 또는 새 test dependency
- sliding window, sorted-set 기반 정교한 rate limit
- IP 또는 IP hash의 MongoDB 저장
- raw token, raw IP, raw content의 Redis key 저장
- 기존 exams Redis namespace, serializer, 예외 동작 또는 비즈니스 로직 변경
- 관련 없는 리팩터링

## 예상 변경 파일

다음은 예상 생성·수정 파일이다.

승인 후 예상 생성 파일:

- src/main/java/web/tosunsaeng/domain/blog/comment/api/support/ClientIpResolver.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/CommentAbusePreventionService.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/CommentAbusePreventionServiceImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentModerationService.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentModerationServiceImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentAbuseConfig.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentAbuseProperties.java
- src/main/java/web/tosunsaeng/domain/blog/comment/converter/BlogCommentModerationConverter.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/CommentLimitScope.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/HiddenReason.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitHasher.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitKeyFactory.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/RedisFailureClassifier.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/CommentRateLimitRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/RedisCommentRateLimitRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentModerationDTO.java
- src/main/java/web/tosunsaeng/domain/blog/comment/exception/CommentRateLimitException.java
- src/main/resources/redis/blog-comment-admission.lua
- src/main/resources/redis/blog-comment-duplicate-release.lua

승인 후 예상 수정 파일:

- .env.example
- src/main/resources/application.yml
- src/test/resources/application-test.yml
- src/main/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestController.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorService.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentService.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializer.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/entity/BlogComment.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentResponseDTO.java
- src/main/java/web/tosunsaeng/domain/blog/comment/exception/BlogCommentExceptionAdvice.java
- src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostRepository.java
- src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java
- src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java
- docs/blog-mvp/plans/PHASE-04-comment-abuse-moderation.md
- docs/blog-mvp/IMPLEMENTATION_STATUS.md

승인 후 예상 생성 test 파일:

- src/test/java/web/tosunsaeng/domain/blog/comment/api/support/ClientIpResolverTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/application/CommentAbusePreventionServiceImplTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentModerationServiceImplTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentAbusePropertiesTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/entity/BlogCommentStateTransitionTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitHasherTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitKeyFactoryTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/RedisFailureClassifierTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/repository/RedisCommentRateLimitRepositoryTest.java

승인 후 예상 수정 test 파일:

- src/test/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestControllerTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImplTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImplTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializerTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImplTest.java

보호하며 수정하지 않을 파일:

- build.gradle
- src/main/java/web/tosunsaeng/global/config/RedisConfig.java
- src/main/java/web/tosunsaeng/global/config/SecurityConfig.java
- src/main/java/web/tosunsaeng/global/exception/GlobalExceptionAdvice.java
- src/main/java/web/tosunsaeng/global/common/response/BaseResponse.java
- src/main/java/web/tosunsaeng/domain/exams/**
- 기존 게시글 Controller, Service, Converter, Document와 공개 query 구현

승인 뒤 예상 파일 밖 소스 변경이 필요하면 구현을 멈추고 사용자에게 차이와 이유를 보고한다.

## API 변경 및 계약

### 기존 공개 API 유지

- GET /api/posts/{slug}/comments?page=0&size=20
- POST /api/posts/{slug}/comments
- POST /api/comments/nickname/regenerate

댓글 목록과 프로필 재생성의 성공 계약은 변경하지 않는다. 댓글 작성 정상 성공은 기존 HTTP 201 Created와 CreatedCommentResult를 유지한다.

### 허니팟 요청

- website가 null이거나 trim 후 빈 문자열이면 정상 흐름을 계속한다.
- website를 trim한 뒤 한 글자라도 남으면 봇 요청으로 보고 즉시 종료한다.
- HTTP 202 Accepted를 사용한다.
- 새 COMMENT 계열 SuccessStatus와 일반적인 접수 메시지를 사용한다.
- BaseResponse의 result는 null로 두어 commentId, nickname, cookie token 또는 탐지 기준을 반환하지 않는다.
- Set-Cookie를 만들지 않고 visitor, comment, Redis counter와 duplicate key를 모두 만들지 않는다.
- 일반 사용자는 website를 빈 문자열로 보내므로 정상 201 계약에 영향이 없다.

### rate limit 실패

- HTTP 429 Too Many Requests
- Retry-After header: 양의 정수 초
- BaseResponse result:
  - retryAfterSeconds
  - limitScope
- limitScope:
  - VISITOR_SHORT
  - VISITOR_MEDIUM
  - VISITOR_DAILY
  - IP_MEDIUM
  - IP_DAILY
  - DUPLICATE
- 내부 hash, Redis key, raw token, raw IP와 댓글 내용은 반환하지 않는다.
- CommentRateLimitException 전용 handler를 BlogCommentExceptionAdvice에 추가한다.
- ErrorStatus에는 기존 COMMENT_4001과 충돌하지 않는 HTTP 429용 COMMENT 계열 상수를 append한다.

### 운영 기능의 HTTP 계약

- 이번 Phase에는 운영 Controller와 URI가 없다.
- 운영 Service DTO와 domain exception만 정의하고 HTTP status mapping과 API Key 인증은 Phase 07에서 확정한다.
- 댓글 PATCH, DELETE와 /internal/comments mapping 부재를 회귀 test로 고정한다.

## DB 변경

### MongoDB

- 새 collection은 만들지 않는다.
- BlogComment.hiddenReason의 Java 타입을 String에서 HiddenReason enum으로 좁힌다.
- 기존 null 값과 MongoDB string enum 저장 형식을 유지한다.
- 추가 index:
  - status ASC + createdAt DESC
- 기존 index 재사용:
  - postId ASC + status ASC + createdAt DESC
  - anonymousVisitorId ASC
- initializer는 기존처럼 ensureIndex, 고정 이름, idempotent, 오류 비흡수, test profile 비활성화 정책을 따른다.
- IP, IP hash, rate limit counter, duplicate content hash는 MongoDB에 저장하지 않는다.

### Redis

- Redis data는 임시 counter와 duplicate reservation뿐이며 영구 DB schema가 아니다.
- 실제 Redis key와 TTL은 아래 Redis key 설계와 시간 창 정책을 따른다.
- 실제 Redis integration과 운영 topology 검증은 Phase 08에 남긴다.

## Redis key 설계

CommentRateLimitKeyFactory 한 곳에서 다음 namespace만 생성한다.

- blog:comment:rate:visitor:short:{tokenHash}
- blog:comment:rate:visitor:medium:{tokenHash}
- blog:comment:rate:visitor:daily:{tokenHash}
- blog:comment:rate:ip:medium:{ipHash}
- blog:comment:rate:ip:daily:{ipHash}
- blog:comment:duplicate:{tokenHash}:{postId}:{contentHash}

중괄호는 이 문서의 변수 표기이며 실제 key에 Redis Cluster hash-tag 문법을 임의로 넣지 않는다.

- tokenHash는 Phase 03 HMAC 결과이며 raw anon_session token을 넣지 않는다.
- ipHash는 BLOG_COMMENT_RATE_LIMIT_SECRET 기반 HMAC-SHA256 결과다.
- contentHash는 같은 secret에 별도 domain prefix를 붙여 normalized content를 HMAC-SHA256한 결과다.
- postId는 MongoDB 내부 ID만 사용하고 slug나 댓글 내용을 key에 넣지 않는다.
- 모든 digest는 패딩 없는 Base64URL로 만들어 key separator, 공백, 제어문자를 포함하지 않는다.
- key factory는 exam:status:* 또는 다른 domain key를 생성할 수 없게 고정 prefix와 scope enum을 사용한다.
- key 전체와 digest도 로그나 외부 응답에 출력하지 않는다.

현재 설정은 단일 Redis host/port이고 cluster topology가 확인되지 않았다. 서로 다른 visitor/IP key를 한 Lua script에서 조작하는 방식은 Redis Cluster의 cross-slot 제약과 충돌할 수 있으므로 Phase 04는 standalone 또는 single-primary Redis를 전제로 한다. 실제 배포가 Redis Cluster라면 공통 hash slot 또는 별도 원자성 설계가 필요하므로 구현 전에 사용자가 topology를 확인해야 한다.

## Lua 또는 원자적 처리 전략

### admission script

blog-comment-admission.lua 한 번의 RedisTemplate.execute 호출에 visitor counter 3개, IP counter 2개, duplicate key 1개를 KEYS로 전달한다. limit, window seconds, duplicate TTL, 요청별 opaque reservation ID는 ARGV로 전달한다.

script 처리:

1. 모든 limit, window와 reservation argument의 형식을 쓰기 전에 검증한다.
2. duplicate key 존재 여부와 TTL을 읽는다.
3. 다섯 counter의 현재 값과 TTL을 읽는다.
4. 잘못된 type, 음수 count 또는 TTL 없는 기존 counter는 구현·운영 오류로 반환하고 fail-open 대상으로 보지 않는다.
5. duplicate와 모든 counter에 대해 이번 요청을 추가했을 때의 제한 초과 여부를 계산한다.
6. blocker가 하나 이상이면 어떤 counter도 증가시키지 않고 duplicate reservation도 만들지 않는다.
7. blocker가 여러 개면 Lua는 각 blocker의 scope와 남은 TTL을 모두 반환한다.
8. application은 실제로 모든 제한을 통과할 수 있는 가장 이른 시각을 반환하기 위해 blocker TTL의 최댓값을 retryAfterSeconds로 사용한다.
9. 동일한 최대 TTL이면 DUPLICATE, VISITOR_DAILY, IP_DAILY, VISITOR_MEDIUM, IP_MEDIUM, VISITOR_SHORT 순서로 limitScope를 안정적으로 선택한다.
10. 모두 허용되면 다섯 counter를 증가시킨다.
11. 새 counter, 즉 증가 결과가 1인 key에만 해당 window TTL을 설정한다.
12. 기존 counter TTL은 연장하지 않는다.
13. duplicate key를 reservation ID 값과 10분 TTL로 NX 생성한다.
14. ALLOWED 또는 DENIED + blocker scope/TTL 목록만 반환하며 key와 hash는 반환하지 않는다.

모든 read/decision/write는 한 Lua script 실행 안에서 이루어져 GET 후 Java INCR 경쟁을 만들지 않는다. script는 mutation 전에 type과 argument를 검증해 runtime error 후 부분 write 가능성을 줄인다.

### duplicate reservation과 Mongo 실패

- 중복 key를 댓글 save 후 처음 기록하면 두 동시 요청이 모두 MongoDB에 저장될 수 있다.
- 따라서 admission script에서 rate counter 승인과 함께 요청별 opaque reservation을 원자적으로 선점하는 방식을 권고한다.
- 댓글 save 성공 시 reservation key를 그대로 두는 것이 중복 방지 확정이며 추가 write는 필요하지 않다.
- visitor 또는 comment Mongo save가 실패하면 blog-comment-duplicate-release.lua가 value가 해당 reservation ID와 같은 경우에만 DEL한다.
- 다른 요청의 reservation을 지우는 일반 DEL은 사용하지 않는다.
- 승인 시 증가한 작성 횟수 counter는 Mongo 실패 시 감소시키지 않는다. 원자적 rollback이 불가능하고 다른 요청 count를 잘못 감소시킬 수 있기 때문이다.
- release 중 Redis 연결 장애가 나면 warning event만 남기고 원래 Mongo 예외를 유지한다. orphan reservation은 최대 10분 뒤 만료된다.

### unit test와 실제 Redis test 경계

- Phase 04 unit test는 script resource 존재, key/ARGV 순서, 한 번의 execute 호출, 결과 decoding, no-extra-call, 오류 분류를 검증한다.
- mock RedisTemplate은 실제 Lua 원자성, TTL 감소와 동시 실행을 증명하지 못한다.
- Phase 08에서 실제 배포와 같은 Redis topology를 사용해 limit 경계, TTL, no-partial-increment, concurrent request, duplicate reservation/release를 통합 검증한다.

## rate limit 시간 창

calendar 정렬 fixed window가 아니라 첫 승인 시점부터 TTL이 시작되는 creation-time window를 사용한다.

| Scope | Limit | TTL |
|---|---:|---:|
| VISITOR_SHORT | 1 | 10초 |
| VISITOR_MEDIUM | 5 | 600초 |
| VISITOR_DAILY | 20 | 86400초 |
| IP_MEDIUM | 10 | 600초 |
| IP_DAILY | 50 | 86400초 |
| DUPLICATE | 1 | 600초 |

- 첫 허용 요청이 key를 만들고 TTL을 설정한다.
- 후속 허용 요청은 count만 증가시키고 TTL을 연장하지 않는다.
- TTL 0은 외부 응답에서 최소 1초로 올림한다.
- key 생성은 항상 count와 expire를 같은 Lua 실행에서 수행해 정상 코드 경로에서 TTL 없는 counter가 생기지 않게 한다.
- TTL 없는 기존 counter는 자동으로 영구 보존하거나 조용히 수리하지 않고 구현 오류로 감지한다.
- 창 만료 직전과 직후에 요청하면 짧은 시간에 두 window의 허용량을 사용할 수 있다. 이 fixed-window burst 특성은 MVP에서 허용하고 sliding window는 제외한다.
- 일일 창은 KST 자정 기준이 아니라 첫 승인 후 86400초다.

BLOG_COMMENT_RATE_LIMIT_ENABLED의 운영 기본값은 true다. test profile은 false로 두고 rate limit tests가 properties와 mock adapter를 명시적으로 활성화한다.

운영 조절 가치가 있는 다음 값을 BlogCommentAbuseProperties로 묶는다.

- secret
- enabled
- duplicateTtlSeconds
- visitorShortLimit / visitorShortWindowSeconds
- visitorMediumLimit / visitorMediumWindowSeconds
- visitorDailyLimit / visitorDailyWindowSeconds
- ipMediumLimit / ipMediumWindowSeconds
- ipDailyLimit / ipDailyWindowSeconds

limit와 window는 양수이고 short < medium < daily 관계를 만족하도록 시작 시 검증한다.

## client IP 처리

### 기본 로컬·직접 연결

- ClientIpResolver는 HttpServletRequest.getRemoteAddr()만 읽는다.
- BlogCommentRestController가 resolver 결과를 application service에 전달하고 Service는 즉시 HMAC digest로 바꾼다.
- request header의 X-Forwarded-For와 Forwarded는 읽지 않는다.
- remoteAddr가 null 또는 blank면 우회 가능한 공용 fallback을 쓰지 않고 generic internal error로 중단한다.

### 신뢰된 reverse proxy 환경

- CloudFront/ALB가 외부 client의 forwarded header를 제거하고 신뢰된 값만 재작성해야 한다.
- Spring/Tomcat forward-header 처리와 trusted proxy 범위는 배포 설정에서 함께 구성해야 한다.
- 그 구성이 검증된 경우에도 application code는 첫 X-Forwarded-For 값을 직접 파싱하지 않고 container가 제공하는 remote address만 사용한다.
- 이 설정 변경과 실제 header chain 검증은 Phase 08 및 배포 운영 과제로 남긴다.

### 위조 header

- 기본 환경에서는 공격자가 X-Forwarded-For를 보내도 resolver 결과에 영향이 없다.
- SecurityConfig는 수정하지 않는다.

## HMAC 처리

- IP와 normalized content용 secret은 BLOG_COMMENT_RATE_LIMIT_SECRET에서만 주입한다.
- BLOG_ANONYMOUS_TOKEN_SECRET을 default나 fallback으로 자동 재사용하지 않는다.
- enabled=true일 때 UTF-8 32 byte 미만 secret은 시작 실패로 처리한다.
- 두 환경변수 값이 우연히 같으면 설정 오류로 시작 실패시키는 것을 권고한다.
- HmacSHA256과 패딩 없는 Base64URL을 사용한다.
- IP digest에는 ip + NUL domain separation, content digest에는 duplicate-content + NUL domain separation을 적용해 같은 secret의 용도를 분리한다.
- normalized content는 CommentValidator가 반환한 strip 결과를 그대로 사용한다. 별도의 Unicode 호환 정규화로 저장 내용이나 Phase 03 validation 의미를 바꾸지 않는다.
- raw IP, raw token, raw content, secret과 digest를 로그, 예외 메시지 또는 응답에 넣지 않는다.
- .env.example에는 변수명과 안전한 주입 설명만 추가하며 실제 secret은 넣지 않는다.

## 허니팟 응답 정책

권고 정책은 generic HTTP 202 Accepted다.

- Request 역직렬화가 성공한 뒤 service 첫 분기에서 website를 검사한다.
- website가 null이거나 trim 후 empty이면 정상 사용자로 처리한다.
- trim 후 값이 남는 경우만 허니팟으로 처리한다.
- content validation, 공개 게시글 query, token 준비, IP HMAC, Redis와 MongoDB를 모두 호출하지 않는다.
- BaseResponse success code와 message는 요청이 접수됐다는 일반 의미만 전달한다.
- result와 Set-Cookie는 없다.
- COMMENT_SPAM_PATTERN rule 9 violation이나 429로 노출하지 않는다.
- malformed JSON은 허니팟 단계에 도달할 수 없으므로 기존 댓글 전용 rule 4 응답을 유지한다.

## 중복 댓글 정책

- 동일 visitor tokenHash
- 동일 BlogPost id
- 동일 CommentValidator normalizedContent

위 세 값이 같은 요청은 600초 동안 중복이다.

- normalized content 원문 대신 HMAC digest를 key에 넣는다.
- 기본 실패 응답은 다른 작성 제한과 같은 HTTP 429이며 limitScope는 DUPLICATE다.
- duplicate TTL이 visitor short TTL보다 길므로 즉시 같은 내용 재요청은 보통 DUPLICATE와 약 600초 Retry-After를 반환한다.
- 다른 게시글, 다른 방문자 또는 다른 normalized content는 duplicate key가 다르다. 다만 일반 visitor/IP window limit은 별도로 적용된다.
- Redis 연결·timeout 장애에서는 duplicate 제한도 fail-open한다.
- Mongo save 전 reservation으로 동시 중복을 막고 save 실패 시 owner-checked release를 수행한다.

## Redis 장애 정책

### fail-open 대상

- RedisConnectionFailureException
- Spring data access timeout
- Lettuce connection 또는 command timeout처럼 cause type으로 명확히 판정 가능한 연결성 장애

fail-open 시:

- 기존 댓글 규칙 1~10 validation은 이미 완료된 상태다.
- 정상 댓글의 visitor와 comment 저장을 계속한다.
- 사용자 응답에 Redis 장애를 노출하지 않는다.
- warning에는 고정 event name, operation, exception class만 기록하고 IP, token, content, key, digest, exception message를 넣지 않는다.
- 현재 project에 별도 metrics registry가 없으므로 새 dependency를 추가하지 않고 blog.comment.redis.degraded 구조화 event를 Phase 08 metric 연결 대상으로 기록한다.

### fail-open하지 않는 대상

- NullPointerException과 IllegalArgumentException
- Lua syntax 또는 contract 오류
- result decoding 오류
- 잘못된 key/ARGV 수
- Redis WRONGTYPE와 TTL invariant 위반
- 예상하지 못한 application exception

이 오류는 comment save 전에 generic 500으로 실패시킨다. 외부 result에 Redis message를 넣지 않고, 구현 오류가 정상 요청으로 조용히 통과하지 않게 한다.

RedisFailureClassifier는 broad Exception 또는 모든 DataAccessException을 catch하지 않고 cause type allowlist만 사용한다. 문자열 message 기반 판정은 하지 않는다.

## 댓글 저장 처리 순서

1. Spring이 CreateCommentRequest를 역직렬화한다.
2. website 허니팟을 확인하고 탐지 시 202로 즉시 종료한다.
3. CommentValidator를 공개 게시글로 간주해 호출하여 content 타입 판정, strip과 규칙 1~9를 먼저 평가한다.
4. content violation이 있으면 CommentValidationException을 던지고 BlogPost·Redis·visitor·comment를 호출하지 않는다.
5. Clock으로 now를 한 번 얻고 slug가 가리키는 공개 BlogPost를 확인한다. 미존재·비공개이면 rule 10 violation만 반환한다.
6. AnonymousVisitorService.prepare가 기존 visitor 또는 unsaved 신규 visitor 후보와 tokenHash를 준비한다. save와 lastSeenAt 변경은 하지 않는다.
7. ClientIpResolver가 제공한 request.getRemoteAddr()를 별도 HMAC으로 바꾼다.
8. normalized content를 HMAC하고 여섯 Redis key를 만든다.
9. admission Lua를 한 번 실행해 rate limit과 duplicate reservation을 판정한다.
10. 제한이면 가장 긴 blocker TTL과 승인된 tie 우선순위로 429를 반환하고 visitor/comment를 저장하지 않는다.
11. Redis 연결·timeout이면 warning 후 fail-open하고, 구현 오류면 generic 500으로 중단한다.
12. 허용 또는 fail-open일 때 AnonymousVisitorService.commit이 기존 visitor lastSeenAt 갱신 또는 신규 visitor 저장을 수행한다.
13. visitor profile snapshot으로 VISIBLE BlogComment를 저장한다.
14. admission에서 duplicate reservation을 만든 경우 comment save 성공으로 reservation을 확정해 TTL까지 유지한다.
15. visitor/comment 등 Mongo 흐름이 실패하면 현재 요청 owner와 일치하는 duplicate reservation만 release한다.
16. 새 visitor token이 있는 정상 201 성공에서만 Set-Cookie를 반환한다.

규칙 10은 공개 BlogPost 확인 단계에서 평가한다. content 규칙 1~9 위반은 승인된 처리 순서에 따라 먼저 반환하므로 그 요청에서는 BlogPost query와 rule 10 평가를 수행하지 않는다.

신규 tokenHash unique 충돌은 기존 bounded retry 의미를 보존한다. prepare/commit 사이 경쟁으로 DuplicateKeyException이 발생하면 reservation을 owner-checked release한 뒤 새 token 후보에 대해 admission부터 제한된 횟수만 재시도한다. 이미 승인된 이전 후보 counter는 경쟁 안전성 때문에 감소시키지 않으며, 이 경로는 암호학적으로 극히 희박한 잔여 위험으로 기록한다.

## 상태 전이 — 숨김·복원

HiddenReason:

- SPAM
- ABUSE
- ADVERTISEMENT
- PERSONAL_INFORMATION
- OTHER

허용 전이:

- VISIBLE -> HIDDEN
- PENDING -> HIDDEN
- HIDDEN -> VISIBLE

숨김:

- reason은 null일 수 없다.
- status를 HIDDEN으로 변경한다.
- hiddenAt과 updatedAt을 주입된 Clock의 같은 now로 기록한다.
- hiddenReason을 기록한다.

복원:

- status를 VISIBLE로 변경한다.
- hiddenAt과 hiddenReason을 null로 제거한다.
- updatedAt을 Clock의 now로 갱신한다.

금지 전이:

- HIDDEN -> HIDDEN
- VISIBLE -> VISIBLE 복원 시도
- PENDING -> VISIBLE 직접 복원
- 삭제 상태 또는 삭제 전이 전체

다중 instance의 read-modify-save 경쟁을 피하도록 custom repository의 조건부 Mongo findAndModify를 사용한다.

- hide criteria: id 일치 AND status in [VISIBLE, PENDING]
- restore criteria: id 일치 AND status == HIDDEN
- update와 상태 조건을 원자적으로 적용하고 변경 후 document를 반환한다.
- 조건부 update가 비면 findById로 미존재와 상태 충돌을 구분한다.
- 미존재는 comment not found domain error, 잘못된 전이는 comment state conflict domain error로 처리한다.

## 운영 조회 Service

BlogCommentModerationService는 Controller 없이 다음 application 계약을 제공한다.

- list(filter)
- hide(commentId, HiddenReason)
- restore(commentId)

filter:

- status 선택
- postId 또는 slug 중 하나 선택
- createdFromInclusive
- createdToExclusive
- page
- size

정책:

- postId와 slug가 동시에 있으면 명확한 잘못된 filter 오류로 거절한다.
- slug는 BlogPostRepository의 비공개 조건 없는 내부 findBySlug로 postId를 해석한다.
- 없는 slug는 내부 상태를 노출하지 않고 빈 page를 반환한다.
- page는 0 이상, size는 1..100으로 제한한다.
- createdAt은 from 이상, to 미만으로 조회한다.
- from이 to보다 늦거나 같으면 잘못된 filter다.
- 정렬은 createdAt DESC, id DESC다.
- response에는 id, postId, nickname, avatarSeed, avatarImageUrl, content, status, createdAt, updatedAt, hiddenAt, hiddenReason을 포함한다.
- anonymousVisitorId, tokenHash, IP, IP hash, cookie와 Redis key는 포함하지 않는다.
- 이 Service를 호출하는 인증된 Controller와 URI는 Phase 07에서 추가한다.

## Repository query

BlogCommentQueryRepository에 다음 의미를 추가한다.

- 운영 filter page query와 동일 criteria count query
- VISIBLE/PENDING -> HIDDEN 조건부 findAndModify
- HIDDEN -> VISIBLE 조건부 findAndModify

운영 목록 criteria:

- status가 있으면 equality
- resolved postId가 있으면 equality
- createdFromInclusive가 있으면 gte
- createdToExclusive가 있으면 lt

content query와 count query는 동일 criteria를 사용한다. content query만 page와 createdAt DESC, id DESC sort를 적용한다.

BlogPostRepository에는 운영 filter의 slug 해석용 Optional<BlogPost> findBySlug(String slug) derived method만 추가한다. 기존 findPublicPostBySlug와 공개 Criteria는 변경하지 않는다.

## API 오류 계약

추가 public error:

| 상황 | HTTP | BaseResponse result |
|---|---:|---|
| visitor/IP rate limit | 429 | retryAfterSeconds, limitScope |
| duplicate comment | 429 | retryAfterSeconds, DUPLICATE |
| Redis connection/timeout | 기존 정상 201 | 장애 정보 없음 |
| Redis/Lua 구현 오류 | 500 | generic internal error, 상세 없음 |

추가 public success:

| 상황 | HTTP | result |
|---|---:|---|
| 허니팟 탐지 | 202 | 없음 |

운영 Service domain error:

- 댓글 없음
- 허용되지 않은 상태 전이
- hiddenReason 누락
- 잘못된 filter 또는 pagination

실제 enum constant와 code는 구현 직전 ErrorStatus·SuccessStatus 충돌을 다시 검색하고 COMMENT 계열 현재 형식에 맞춰 append한다. 기존 COMMENT_200~202, COMMENT_4001과 BLOG/exams code는 바꾸지 않는다.

## 설정

application.yml의 blog.comment.abuse 아래에 최소 properties binding만 추가한다. 환경변수 후보:

- BLOG_COMMENT_RATE_LIMIT_SECRET
- BLOG_COMMENT_RATE_LIMIT_ENABLED
- BLOG_COMMENT_DUPLICATE_TTL_SECONDS
- BLOG_COMMENT_VISITOR_SHORT_LIMIT
- BLOG_COMMENT_VISITOR_SHORT_WINDOW_SECONDS
- BLOG_COMMENT_VISITOR_MEDIUM_LIMIT
- BLOG_COMMENT_VISITOR_MEDIUM_WINDOW_SECONDS
- BLOG_COMMENT_VISITOR_DAILY_LIMIT
- BLOG_COMMENT_VISITOR_DAILY_WINDOW_SECONDS
- BLOG_COMMENT_IP_MEDIUM_LIMIT
- BLOG_COMMENT_IP_MEDIUM_WINDOW_SECONDS
- BLOG_COMMENT_IP_DAILY_LIMIT
- BLOG_COMMENT_IP_DAILY_WINDOW_SECONDS

안전한 기본 제한값은 각각 1/10, 5/600, 20/86400, 10/600, 50/86400, duplicate 600초다.

.env.example에는 secret을 비워 두고 변수의 목적과 익명 token secret과 분리해야 한다는 설명을 추가한다. application-test.yml에는 32 byte 이상의 test-only dummy secret과 enabled=false만 넣고 실제 secret을 넣지 않는다.

## 테스트 계획

### Redis 작성 제한

1. 첫 댓글은 admission 허용 결과를 받고 다섯 counter와 duplicate reservation 계약을 사용한다.
2. visitor short counter가 1이고 TTL이 남으면 10초 내 두 번째 요청을 VISITOR_SHORT로 제한한다.
3. short key 만료를 모사하면 다음 요청을 허용한다.
4. visitor medium 창에서 5개까지 허용한다.
5. 6번째는 VISITOR_MEDIUM으로 제한한다.
6. visitor daily 창에서 20개까지 허용한다.
7. 21번째는 VISITOR_DAILY로 제한한다.
8. IP medium 창에서 10개까지 허용한다.
9. 11번째는 IP_MEDIUM으로 제한한다.
10. IP daily 창에서 50개까지 허용한다.
11. 51번째는 IP_DAILY로 제한한다.
12. 여러 blocker가 있으면 모든 제한을 실제로 통과할 수 있는 시각인 최대 남은 TTL과 그 scope를 반환한다.
13. 제한 결과에서 Redis adapter가 추가 increment 또는 reservation call을 하지 않고 Lua 한 번만 실행하는 계약을 확인한다.
14. 새 counter에 정확한 window TTL argument를 전달한다.
15. 기존 counter TTL을 연장하지 않는 Lua 계약과 resource 내용을 확인한다.
16. 동시 요청은 application에서 하나의 Lua admission 호출만 사용하고, 실제 concurrent Lua 경계 검증은 Phase 08 대상으로 기록한다.
17. 모든 blog key가 blog:comment:*이고 exam:status:*와 충돌하지 않는다.

### Redis 장애

18. Redis connection failure는 fail-open하고 정상 comment 흐름을 계속한다.
19. Redis command timeout은 fail-open한다.
20. Lua syntax/contract 또는 application 오류는 fail-open하지 않고 generic internal error로 전파한다.
21. warning logger argument와 응답에 raw IP, raw token, raw content, key와 digest가 없다.

### IP 처리

22. 기본 resolver가 request.getRemoteAddr()를 사용한다.
23. 임의 X-Forwarded-For를 보내도 remoteAddr 결과를 사용한다.
24. 신뢰 proxy mode를 application code가 임의 활성화하지 않으며 운영 설정 전에는 forwarding header를 사용하지 않는다.
25. IP 원문은 Redis key와 Mongo document, response, log에 없다.

### 허니팟

26. website 값이 있으면 BlogComment를 저장하지 않는다.
27. website 값이 있으면 신규·기존 AnonymousVisitor 모두 저장 또는 touch하지 않는다.
28. 202 BaseResponse가 탐지 rule, commentId와 cookie를 노출하지 않는다.
29. null, 빈 문자열 또는 trim 후 빈 website는 정상 validation과 작성 흐름으로 진행한다.

### 중복 댓글

30. 동일 visitor·post·normalized content는 10분 내 DUPLICATE 429다.
31. 다른 post이면 duplicate key가 달라 허용한다.
32. 다른 visitor이면 허용한다.
33. 다른 content이면 허용한다.
34. 앞뒤 공백만 다른 content는 같은 normalized digest로 제한한다.
35. raw content는 Redis key, script ARGV, log와 response에 없다.
36. duplicate TTL 만료를 모사하면 다시 허용한다.
37. duplicate 검사 Redis 장애는 fail-open한다.

### 처리 순서

38. comment validation 실패 시 Redis admission을 호출하지 않는다.
39. validation 실패 시 AnonymousVisitor save/touch가 없다.
40. 허니팟 요청 시 Redis admission을 호출하지 않는다.
41. rate limit 실패 시 BlogComment save가 없다.
42. rate limit 실패 시 신규 visitor save와 기존 visitor touch가 없다.
43. validation과 admission을 통과하거나 연결 장애 fail-open일 때만 comment를 저장한다.

추가 순서 test:

- Mongo save 실패 시 owner가 같은 duplicate reservation만 release한다.
- Mongo save 실패 시 rate counter를 임의 감소시키지 않는다.
- 정상 save 시 duplicate reservation을 TTL까지 유지한다.
- invalid/missing cookie의 신규 tokenHash도 visitor save 전에 admission에 사용한다.
- comment validation failure에는 Set-Cookie가 없다.

### 숨김·복원

44. VISIBLE -> HIDDEN을 허용한다.
45. PENDING -> HIDDEN을 허용한다.
46. HIDDEN -> VISIBLE을 허용한다.
47. 숨김은 hiddenAt을 Clock의 now로 기록한다.
48. 숨김은 HiddenReason을 기록한다.
49. 복원은 hiddenAt을 제거한다.
50. 복원은 hiddenReason을 제거한다.
51. VISIBLE 복원 시도는 state conflict다.
52. HIDDEN 재숨김은 state conflict다.
53. CommentStatus와 repository update에 삭제 상태·삭제 전이가 없다.

추가 상태 test:

- 숨김·복원은 updatedAt을 갱신한다.
- hiddenReason null은 거절한다.
- 조건부 findAndModify criteria가 허용 source status를 고정한다.
- 동시 상태 변경으로 conditional update가 비면 명확한 conflict를 반환한다.

### 운영 조회 Service

54. status filter가 Mongo criteria에 적용된다.
55. postId filter와 slug-to-postId 해석이 적용된다.
56. createdFromInclusive와 createdToExclusive 기간 filter가 적용된다.
57. page 0 이상, size 1..100과 createdAt DESC, id DESC 및 동일 count criteria를 사용한다.
58. DTO/JSON serialization 대상에 anonymousVisitorId, tokenHash, IP, cookie가 없다.

추가 운영 조회 test:

- postId와 slug 동시 입력 거절
- 없는 slug는 빈 page
- 잘못된 기간 거절
- status + createdAt index 정의, 이름, idempotent ensureIndex와 오류 비흡수

### Controller 및 회귀

59. 429 BaseResponse의 isSuccess, code, message, result 구조를 검증한다.
60. Retry-After header가 retryAfterSeconds와 일치한다.
61. 모든 limitScope serialization 값을 검증한다.
62. 정상 댓글 작성은 기존 201, snapshot, Set-Cookie 계약을 유지한다.
63. 고정 rule 1~10 validation failure result.violations 계약을 유지한다.
64. 댓글 PATCH endpoint가 없다.
65. 댓글 DELETE endpoint가 없다.
66. /internal 댓글 endpoint가 아직 없다.
67. 게시글 목록·상세·검색 tests가 통과한다.
68. exams tests와 전체 application context가 통과한다.

### properties, HMAC, 보안 추가 test

- rate limit enabled의 기본값 true
- test profile에서 enabled=false
- limit/window가 0 또는 음수이면 시작 실패
- window 순서가 잘못되면 시작 실패
- secret 누락·32 byte 미만이면 enabled 상태에서 시작 실패
- BLOG_ANONYMOUS_TOKEN_SECRET과 자동 fallback이 없고 같은 secret 거절 정책
- 같은 IP는 같은 digest, secret이 다르면 다른 digest
- content와 IP domain separation
- key에 raw token, IP, content, email이 없음
- ErrorStatus와 SuccessStatus code 충돌 없음
- RedisConfig와 exams source가 변경되지 않음
- SecurityConfig와 GlobalExceptionAdvice가 변경되지 않음
- 실제 secret 하드코딩 없음

### 실제 Redis 검증 이관

Phase 08에서 실제 배포 topology와 같은 Redis를 사용해 다음을 검증한다.

- Lua syntax와 return decoding
- 실제 TTL 생성·유지·만료
- 다섯 counter 중 하나가 제한될 때 no partial increment
- 다중 thread와 다중 application instance 동시 요청
- duplicate NX reservation과 owner-checked release
- Redis restart, connection loss와 command timeout
- standalone 또는 cluster topology 적합성

## 기존 댓글·게시글·exams 영향

- 공개 댓글 목록과 프로필 재생성 API는 응답과 Service 의미를 유지한다.
- 정상 댓글 작성은 기존 validation, 201, trim 저장, profile snapshot, cookie 계약을 유지하고 admission 단계만 중간에 추가한다.
- rule 1~10 번호, code, 순서와 rule 3 비노출을 변경하지 않는다.
- BlogPost 공개 Criteria와 공개 Controller/Service는 수정하지 않는다.
- BlogPostRepository에는 운영 slug filter를 위한 비공개 조건 없는 derived lookup만 추가하고 기존 method를 변경하지 않는다.
- RedisConfig, exam:status namespace, exams serializer와 예외 동작은 변경하지 않는다.
- SecurityConfig와 GlobalExceptionAdvice는 변경하지 않는다.
- 운영 Service는 Controller에 연결하지 않아 외부 attack surface를 추가하지 않는다.

## 개인정보 및 로깅 위험

- raw cookie token이 Redis key, MongoDB, JSON, log 또는 exception에 들어갈 위험
- X-Forwarded-For 위조로 IP limit을 우회할 위험
- raw IP나 normalized content를 key에 넣어 Redis 운영자가 내용을 식별할 위험
- fail-open warning 또는 Redis exception stack에 key/digest가 출력될 위험
- 낮은 entropy content의 일반 SHA-256을 역추측할 위험
- shared HMAC secret 또는 짧은 secret으로 분리 정책이 무너질 위험
- Redis 장애 fail-open 기간에 abuse 방어가 사라질 위험
- fixed-window 경계 burst
- invalid cookie를 반복해 visitor limit을 회피하는 공격. IP limit이 보완하지만 완전한 방지는 아니다.
- Mongo save와 Redis admission 사이 분산 transaction이 없어 counter와 reservation이 DB 결과와 완전히 원자적이지 않은 위험
- 기존 application.yml의 Sentry send-default-pii 설정은 이번 범위에서 변경하지 않으므로 request data 수집 여부를 Phase 08에서 별도 확인해야 한다.

완화:

- raw 값 즉시 HMAC, domain separation, Base64URL digest와 central key factory
- secret 최소 길이, 별도 env, 실제 값 미기록
- header 직접 파싱 금지
- 연결성 오류만 type allowlist fail-open
- sanitized warning event와 generic 외부 500
- owner-checked duplicate release와 bounded TTL
- 실제 Redis/Mongo/Sentry 통합 검증을 Phase 08 완료 조건에 기록

## 위험 요소

1. 현재 Redis가 Cluster이면 여러 identity key를 쓰는 Lua가 CROSSSLOT으로 실패할 수 있다.
2. mock test로 실제 Lua 원자성, TTL과 concurrent limit을 증명할 수 없다.
3. creation-time fixed window는 경계 burst를 허용한다.
4. fail-open은 가용성을 높이지만 Redis 장애 중 abuse를 허용한다.
5. Redis admission과 Mongo save는 분산 transaction이 아니므로 승인 counter는 DB 실패에도 남는다.
6. duplicate reservation release가 실패하면 정상 재시도가 최대 10분 막힐 수 있다.
7. 운영 proxy 신뢰 설정이 잘못되면 모든 사용자가 한 proxy IP로 묶이거나 spoof 우회가 생길 수 있다.
8. 운영 조회가 status 없이 postId만 사용하면 기존 compound index가 정렬을 완전히 충족하지 못할 수 있다.
9. 숨김·복원 conditional update 뒤 미존재/충돌 구분을 위한 추가 read 사이에도 상태가 바뀔 수 있으므로 외부에는 안정적인 conflict 의미만 보장한다.
10. 기존 Sentry PII 설정과 실제 request capture 범위가 아직 검증되지 않았다.

## 롤백 방법

- 구현 중 문제 발생 시 git reset, restore, checkout, clean을 사용하지 않는다.
- 현재 Phase에서 추가한 Controller branch, abuse Service/Repository/config, Lua resource, moderation Service와 enum/index 변경을 파일별 수동 역패치한다.
- application.yml과 application-test.yml에서는 blog.comment.abuse binding과 test dummy 값만 수동 제거한다.
- ErrorStatus와 SuccessStatus에서는 새 COMMENT 상수만 수동 제거하고 기존 값을 보존한다.
- BlogComment.hiddenReason를 되돌릴 경우 실제 HIDDEN data가 생성됐는지 먼저 확인하고 enum string 호환성을 검토한 후 별도 승인된 migration 또는 후속 patch로 처리한다.
- Redis key는 모두 TTL이 있으므로 기능 비활성화 뒤 자연 만료시키며 운영 Redis에 직접 접속하거나 수동 삭제하지 않는다.
- 사용자 소유 변경과 다른 Phase 파일은 되돌리지 않는다.

## 완료 조건

- 사용자가 이 DRAFT 계획과 아래 결정 항목을 명시적으로 승인한다.
- 계획 상태를 APPROVED, Phase 04를 IN_PROGRESS로 바꾼 뒤에만 소스를 수정한다.
- 승인된 세 공개 API 외 endpoint를 추가하지 않는다.
- visitor/IP/duplicate 제한과 허니팟 처리 순서를 구현한다.
- Redis 연결·timeout만 fail-open하고 구현 오류는 generic 500으로 실패한다.
- validation·허니팟·rate limit 실패 시 visitor와 comment save가 없다.
- 숨김·복원 조건부 상태 전이와 운영 조회 Service를 Controller 없이 구현한다.
- 댓글 삭제 상태, method, API가 없다.
- 실제 secret, raw token/IP/content log, Redis key 응답 노출이 없다.
- 계획한 unit/mock/MockMvc tests가 통과한다.
- 기존 댓글, 게시글, exams tests가 통과한다.
- git diff --check가 성공한다.
- ./gradlew clean test bootJar가 성공한다.
- Redis, MongoDB와 proxy 실제 통합 검증이 Phase 08 과제로 상태 문서에 남는다.
- 실패가 하나라도 있으면 Phase 04를 DONE이나 계획을 EXECUTED로 변경하지 않는다.
- 전부 성공한 경우에만 실제 차이와 검증 결과를 기록하고 Phase 04 DONE, 계획 EXECUTED, Current phase 05, Phase 05 TODO로 변경한다.

## 승인으로 확정된 사항

1. Redis topology는 저장소의 단일 host/port 설정을 근거로 standalone/single-primary를 전제하고 Cluster 지원은 추가하지 않는다.
2. duplicate는 pre-save Lua reservation과 Mongo 실패 시 owner-checked release를 사용한다.
3. retryAfterSeconds는 blocker TTL 최댓값이며 동률 scope 우선순위는 DUPLICATE, VISITOR_DAILY, IP_DAILY, VISITOR_MEDIUM, IP_MEDIUM, VISITOR_SHORT다.
4. 허니팟은 website trim 후 값이 있을 때 HTTP 202, COMMENT 계열 generic success, result와 cookie 없음으로 처리한다.
5. 시간 창은 첫 승인 시점 기준 10초/600초/86400초 TTL fixed window다.
6. 운영 환경의 rate limit secret은 32 byte 이상이고 anonymous token secret과 같은 값이면 시작 실패한다.
7. Phase 04는 request.getRemoteAddr()만 사용하고 신뢰 proxy/forward-header 설정은 범위 밖이다.
8. Phase 04는 mock/Lua 계약 test를 수행하고 실제 Redis 동시성·TTL·topology test는 Phase 08로 이관한다.
9. 운영 조회는 postId와 slug 동시 filter를 거절하고 기간을 from inclusive/to exclusive로 정의한다.
10. 운영 조회의 slug filter를 위해 BlogPostRepository에 내부 findBySlug derived method 하나만 추가하고 공개 게시글 API·Service·Criteria는 바꾸지 않는다.

## 실제 구현 중 발생한 차이

승인된 기능 범위와 공개 API 계약의 차이는 없다.

- 허니팟에서 `getRemoteAddr()`조차 읽지 않는 처리 순서를 보장하기 위해 Controller가 client IP 문자열을 즉시 계산하지 않고 `Supplier<String>`로 Service에 전달하고, rate limit이 실제로 실행될 때만 `ClientIpResolver`를 호출하도록 구현했다.
- 과도한 운영 환경변수 세분화를 피하기 위해 application.yml에는 secret, enabled, duplicate TTL 세 값만 명시적으로 노출했다. visitor/IP limit과 window는 검증되는 `ConfigurationProperties` 기본값으로 유지하며 외부 Spring property override는 가능하다.
- 예상 수정 파일이었던 `BlogCommentRepository.java`는 custom repository interface 확장만으로 새 계약이 자동 반영되어 실제 내용 변경이 필요하지 않았다.
- 실제 Redis, MongoDB, proxy, Cluster 지원이나 통합 테스트는 추가하지 않았고 승인대로 Phase 08에 남겼다.

## 검증 결과

- `git diff --check`: 성공
- `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'`: 성공, 152 tests, failures/errors/skipped 0
- `./gradlew clean test bootJar`: `BUILD SUCCESSFUL`, 전체 215 tests, failures/errors/skipped 0, 실행 가능한 bootJar 생성
- Lua 계약: 여섯 key를 한 번에 전달하고 차단 시 mutation 전 반환, 새 counter에만 TTL 설정, duplicate owner reservation과 owner 일치 release를 mock/resource test로 확인했다.
- 장애 계약: Redis connection과 command timeout fail-open, Lua·decode·application 오류 generic 500, warning에 raw IP/content/tokenHash가 없음을 확인했다.
- API 계약: 정상 댓글 201, 허니팟 202/result·cookie 없음, rate limit 429/Retry-After/result scope, 기존 validation 응답을 확인했다.
- 운영 계약: status/postId/slug/[from,to) query, stable pagination/count, VISIBLE·PENDING에서 HIDDEN, HIDDEN에서 VISIBLE 조건부 update와 hidden metadata 처리를 확인했다.
- 정적 검사: 댓글 PATCH·DELETE와 `/internal/comments` Controller, X-Forwarded-For 신뢰, AWS/S3 호출, Redis Cluster 흉내 코드, 실제 secret 하드코딩이 없음을 확인했다.
- 보호 범위: `build.gradle`, `RedisConfig`, `SecurityConfig`, `GlobalExceptionAdvice`, exams source는 변경하지 않았다.
- 실패 이력: 초기 컴파일에서 record accessor와 같은 이름의 static factory 두 건을 발견해 이름을 수정했고, 새 Mongo index 추가 후 기존 index test의 호출 수 기대값 한 건을 보강했다. 이후 관련 및 전체 검증은 모두 성공했다.
- 실제 Redis Lua syntax·TTL·원자성·동시성, 실제 Mongo query/index, proxy address 전달과 Redis Cluster 비호환 재설계는 Phase 08 검증 과제다.
