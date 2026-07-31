# Phase 06: 뉴스레터 자동 예약 발송

- 상태: EXECUTED

## 목표

운영자가 API가 아닌 MongoDB 또는 운영 스크립트로 작성한 BlogPost를 주기적으로 재탐색하여 뉴스레터 대상 게시글을 놓치지 않고 Campaign을 생성한다. PUBLISHED, publishedAt 존재, newsletterEnabled=true인 글은 publishedAt 15분 뒤에 발송하도록 예약하고, 발견 시 이미 그 시각이 지났으면 즉시 실행 가능한 시각으로 예약한다.

Campaign을 원자적으로 claim한 뒤 ACTIVE NewsletterSubscriber를 제한된 batch로 순회하여 NewsletterDelivery를 만들고, postId와 subscriberId의 unique 제약으로 같은 게시글을 같은 구독자에게 두 번 보내는 논리적 중복을 막는다. 실제 provider 호출 직전 Subscriber 상태를 다시 확인해 UNSUBSCRIBED와 BOUNCED는 SKIPPED로 종료한다.

Application Service가 AWS SES에 직접 의존하지 않도록 NewsletterEmailSender를 두고 local/test의 LoggingNewsletterEmailSender와 production의 AWS SES v2 SesNewsletterEmailSender를 선택 가능하게 한다. 기본 kill switch는 false이고, false일 때 provider 호출이나 SENT 기록이 일어나지 않으며 이후 true로 바꾸면 안전하게 재개할 수 있어야 한다.

HTML과 plain text 본문, 게시글 UTM 링크, Phase 05 stateless token을 이용한 사용자 해지 링크를 만든다. RFC 8058용 헤더와 별도 공개 POST endpoint를 Phase 06에 포함하되, 내부 운영 Controller와 API Key 인증은 Phase 07로 남긴다. Phase 07 Controller가 호출할 테스트 발송, SCHEDULED 예약 취소, 실패 Delivery와 Campaign 수동 재시도 Application Service까지만 준비한다.

## 작업 시작 조건

- 계획 수립 시작 브랜치는 feat/blog-mvp다.
- git status --short 출력은 없어 작업 트리가 깨끗하다.
- scripts/codex-preflight.sh는 Current branch: feat/blog-mvp와 Preflight: PASS를 출력했다.
- Phase 00부터 Phase 05까지 모두 DONE이다.
- docs/blog-mvp/plans/PHASE-05-newsletter-subscription.md는 EXECUTED다.
- Current phase는 Phase 06이고 계획 수립 전 상태는 TODO다.
- 이번 계획 수립에서는 이 계획서와 docs/blog-mvp/IMPLEMENTATION_STATUS.md만 변경한다.
- 사용자 승인 전 Java, test, build.gradle, application 설정, Mongo Document와 Scheduler를 수정하거나 생성하지 않는다.
- 2026-07-31 사용자가 이 계획을 조건부 승인했고, 승인 메시지에서 확정한 claim 만료, kill switch 무상태, AWS SDK 개별 버전 유지, base URL 분리, path token one-click과 test allowlist를 아래 계획에 반영한다.
- 조건부 승인 반영 후 계획은 APPROVED, Phase 06은 IN_PROGRESS로 전환하고 Current phase는 Phase 06을 유지한다.

## 현재 코드 분석

### 실제 package와 구성

- 게시글은 web.tosunsaeng.domain.blog, 뉴스레터는 web.tosunsaeng.domain.newsletter, 댓글은 web.tosunsaeng.domain.comment에 있다.
- src/main 및 src/test 어디에도 web.tosunsaeng.domain.blog.newsletter 또는 web.tosunsaeng.domain.blog.comment package가 남아 있지 않다.
- TosunsaengApplication은 web.tosunsaeng.domain 전체를 Mongo Repository scan하므로 Phase 06 Repository를 위해 scan 범위를 수정할 필요가 없다.
- 댓글 package는 Phase 06 기능에 직접 의존하지 않는다. 기존 댓글 API, 고정 rule 1부터 10, rate limit과 moderation은 회귀 검증 대상으로만 둔다.

### BlogPost

- BlogPost collection은 blog_posts다.
- 실제 필드는 id, slug, title, summary, contentMarkdown, thumbnailUrl, authorName, status, seoTitle, seoDescription, relatedPostSlugs, publishedAt, createdAt, updatedAt이다.
- 상태는 BlogPostStatus.DRAFT, PUBLISHED, ARCHIVED다.
- 현재 newsletterEnabled 또는 Campaign 연계 필드는 없다.
- 공개 조회는 BlogPostQueryRepositoryImpl의 공통 Criteria로 status=PUBLISHED, publishedAt 존재 및 non-null, publishedAt less than or equal to Clock의 now를 적용한다.
- 게시글은 공개 생성 API가 없고 DB 또는 운영 스크립트로 직접 작성되므로 애플리케이션 발행 이벤트만으로는 자동 발송을 보장할 수 없다.
- BlogPostMongoIndexInitializer는 slug unique와 status, publishedAt 복합 index만 programmatic ensureIndex한다.

### Phase 05 NewsletterSubscriber와 해지 token

- NewsletterSubscriber collection은 newsletter_subscribers다.
- 실제 필드는 id, email, status, tokenVersion, consentAt, subscribedAt, unsubscribedAt, createdAt, updatedAt이다.
- 상태는 ACTIVE, UNSUBSCRIBED, BOUNCED뿐이며 PENDING은 없다.
- 현재 Repository는 email 조회, UNSUBSCRIBED 재활성화 findAndModify, id와 tokenVersion 기반 해지 findAndModify를 제공한다. ACTIVE 구독자의 batch 또는 cursor 조회는 아직 없다.
- NewsletterMongoIndexInitializer는 email unique와 status index를 programmatic ensureIndex하며 test profile에서는 비활성화된다.
- NewsletterUnsubscribeTokenManager.createToken은 subscriberId와 tokenVersion으로 만료 없는 stateless HMAC token을 만들고 verify는 active 및 previous key를 검증한다.
- token payload에는 email이 없고 raw token과 hash는 MongoDB에 저장하지 않는다. Phase 06은 이 구현을 수정해 별도 token 체계를 만들지 않고 그대로 재사용한다.
- 공개 API는 JSON body의 POST /api/newsletter/subscribe와 POST /api/newsletter/unsubscribe 두 개다. GET unsubscribe와 verify endpoint는 없다.
- NewsletterTelemetryPrivacyConfig는 현재 두 newsletter POST의 body, query, cookie, 일부 header 및 user PII를 Sentry 전송 전에 제거한다.

### Scheduling, Clock과 비동기 실행

- SchedulingConfig는 별도 Configuration에서 EnableScheduling만 활성화한다.
- newsletter 또는 다른 domain의 Scheduled method는 현재 없다.
- UTC Clock Bean은 Clock.systemUTC이며 기존 blog, comment, newsletter Service가 주입받아 사용한다.
- exams에는 executor를 명시하지 않은 CompletableFuture.runAsync 호출이 두 곳 있다. 이는 JVM 공용 pool과 무제한 작업 제출을 사용할 수 있어 newsletter 발송에는 재사용하지 않는다.
- Spring 기본 Scheduler thread에서 Campaign 전체 생성이나 provider 네트워크 호출을 오래 실행하지 않도록 newsletter 전용 bounded executor가 필요하다.

### AWS SDK와 S3 설정

- build.gradle의 AWS SDK 의존성은 software.amazon.awssdk:s3:2.29.52 하나다. SES v2 module과 AWS SDK BOM은 없다.
- S3Config는 AWS_ACCESS_KEY와 AWS_SECRET_KEY를 읽어 StaticCredentialsProvider와 S3Presigner를 만든다.
- application.yml의 AWS region은 ap-northeast-2로 고정돼 있고 AWS_REGION 환경변수 binding은 없다.
- Phase 06은 기존 S3Config를 리팩터링하지 않는다. SES client에는 access key를 새로 하드코딩하거나 복사하지 않고 DefaultCredentialsProvider 체인과 AWS_REGION을 사용해 EC2 IAM Role을 우선한다.
- 조건부 승인은 Phase 06만을 위한 BOM 도입을 금지한다. 기존 S3와 동일하게 sesv2를 개별 버전 2.29.52로 추가하고 전체 AWS SDK 버전은 올리지 않는다.

### application 설정

- application.yml의 newsletter 설정은 unsubscribe-token과 subscribe-rate-limit 두 묶음뿐이다.
- application-test.yml에는 안전한 dummy token key와 rate limit disabled 설정만 있다.
- NEWSLETTER_SENDING_ENABLED, NEWSLETTER_EMAIL_PROVIDER, NEWSLETTER_FROM_EMAIL, NEWSLETTER_FROM_NAME, NEWSLETTER_PUBLIC_BASE_URL, NEWSLETTER_API_BASE_URL, NEWSLETTER_SEND_DELAY_MINUTES, NEWSLETTER_BATCH_SIZE, test send, executor, scheduler 주기, claim TTL과 AWS_REGION 설정은 아직 없다.
- 발송 설정을 별도 NewsletterDeliveryProperties로 분리해 Phase 05 token 및 subscribe rate limit 설정과 의미를 섞지 않는다.

### Mongo 원자 update와 index 선례

- NewsletterSubscriberQueryRepositoryImpl과 BlogCommentQueryRepositoryImpl은 MongoTemplate.findAndModify, 조건 Criteria, returnNew=true를 사용한다.
- BlogPostMongoIndexInitializer, BlogCommentMongoIndexInitializer, NewsletterMongoIndexInitializer는 ApplicationRunner, named Index, ensureIndex, Profile not test 패턴을 사용하고 오류를 삼키지 않는다.
- Campaign claim, Delivery claim, 예약 취소, stale recovery와 수동 재시도도 같은 조건부 원자 update 패턴을 따른다.

## 구현 범위

1. BlogPost에 명시적 opt-in 필드 newsletterEnabled를 최소 추가한다.
2. 주기적 reconciliation으로 대상 BlogPost를 cursor batch로 스캔하고 NewsletterCampaign을 멱등 생성한다.
3. 기본 publishedAt plus 15분, 이미 지난 시각은 now로 scheduledAt을 계산한다.
4. NewsletterCampaign과 NewsletterDelivery Document, 상태, Repository와 programmatic index를 추가한다.
5. SCHEDULED Campaign을 Mongo findAndModify로 원자 claim한다.
6. claim된 Campaign에서 ACTIVE Subscriber를 id cursor와 기본 batch 100으로 순회해 Delivery를 idempotent upsert한다.
7. PENDING Delivery와 retry 가능한 FAILED Delivery를 원자 claim하고 별도 bounded executor에서 처리한다.
8. 발송 직전 Subscriber와 BlogPost 상태를 다시 확인하고 비활성이면 SKIPPED 처리한다.
9. NewsletterEmailSender와 Logging 및 SES v2 구현을 추가한다.
10. HTML 및 plain text template, 게시글 UTM URL, 사용자 해지 URL과 RFC 8058 header를 생성한다.
11. 별도 공개 POST /api/newsletter/one-click-unsubscribe endpoint를 구현하고 기존 JSON unsubscribe 계약은 유지한다.
12. 일시 오류의 최초 발송 plus 최대 3회 retry, 최종 실패와 오류 분류를 구현한다.
13. NEWSLETTER_SENDING_ENABLED master kill switch를 기본 false로 적용한다.
14. Campaign 및 Delivery stale SENDING 복구와 Campaign 완료 집계를 구현한다.
15. Phase 07 Controller가 사용할 테스트 발송, 예약 취소, 실패 Delivery 및 Campaign 재시도 Service를 추가한다.
16. mock, contract, Query BSON, index definition과 scheduler/service 단위 테스트를 추가한다.
17. 기존 newsletter subscribe/unsubscribe, blog, comment, exams 회귀와 전체 build를 검증한다.

## 제외 범위

- 이메일 인증, verification email과 verify API
- NewsletterSubscriber PENDING 상태
- 게시글 생성, 수정, 삭제 공개 API
- 운영자 HTTP Controller
- /internal/newsletter/** endpoint
- 내부 API Key 인증
- SecurityConfig 변경
- 관리자 UI와 뉴스레터 editor
- SES bounce webhook과 complaint webhook
- SES event destination, SNS와 EventBridge 소비자
- send API 응답만으로 NewsletterSubscriber를 BOUNCED로 변경하는 동작
- SMTP sender
- 사용자별 맞춤 content, A/B test와 전체 게시글 본문 발송
- 실제 AWS SES sandbox 또는 production 호출
- 운영 MongoDB, Redis, AWS와 실제 구독자 접근
- Testcontainers와 새 실제 MongoDB 또는 Redis 통합 test
- 실제 email client one-click 검증
- CloudFront 또는 frontend 해지 확인 화면
- 대량 구독자 부하 test
- 기존 S3Config credential 구조 리팩터링
- exams의 기존 CompletableFuture 개선
- 기존 comment, blog, exams의 관련 없는 리팩터링
- web.tosunsaeng.domain.blog.comment 또는 web.tosunsaeng.domain.blog.newsletter package 재생성

## 예상 변경 파일

### 이번 DRAFT 계획 수립에서 생성 및 수정

- 생성: docs/blog-mvp/plans/PHASE-06-newsletter-delivery.md
- 수정: docs/blog-mvp/IMPLEMENTATION_STATUS.md

### 승인 후 예상 수정 main 및 설정 파일

- build.gradle
- .env.example
- src/main/resources/application.yml
- src/test/resources/application-test.yml
- src/main/java/web/tosunsaeng/domain/blog/domain/entity/BlogPost.java
- src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepository.java
- src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImpl.java
- src/main/java/web/tosunsaeng/domain/blog/config/BlogPostMongoIndexInitializer.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterService.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterServiceImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/config/NewsletterMongoIndexInitializer.java
- src/main/java/web/tosunsaeng/domain/newsletter/config/NewsletterTelemetryPrivacyConfig.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterSubscriberQueryRepository.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterSubscriberQueryRepositoryImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterSubscriberRepository.java
- src/main/java/web/tosunsaeng/domain/newsletter/exception/NewsletterExceptionAdvice.java

SchedulingConfig와 SecurityConfig는 분석 대상이지만 수정하지 않는다. BlogPost 공개 DTO와 Controller에도 newsletterEnabled를 노출하지 않는다.

### 승인 후 예상 생성 main 파일

아래 이름은 책임 경계를 고정하기 위한 예상안이다. 같은 package 안에서 안전하게 합칠 수 있는 작은 value type은 파일 수를 줄일 수 있지만, 예상 범위 밖 source가 필요하면 구현을 중단하고 보고한다.

- src/main/java/web/tosunsaeng/domain/newsletter/api/NewsletterOneClickUnsubscribeController.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterCampaignService.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterCampaignServiceImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterDeliveryService.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterDeliveryServiceImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterOperationsService.java
- src/main/java/web/tosunsaeng/domain/newsletter/application/NewsletterOperationsServiceImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/config/NewsletterDeliveryConfig.java
- src/main/java/web/tosunsaeng/domain/newsletter/config/NewsletterDeliveryProperties.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/entity/NewsletterCampaign.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/entity/NewsletterDelivery.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/enums/NewsletterCampaignStatus.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/enums/NewsletterDeliveryStatus.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/enums/NewsletterEmailProvider.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/enums/NewsletterFailureType.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/policy/NewsletterRetryPolicy.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/policy/NewsletterLinkBuilder.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/policy/NewsletterEmailTemplateRenderer.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/policy/NewsletterEmailFailureClassifier.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterCampaignRepository.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterCampaignQueryRepository.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterCampaignQueryRepositoryImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterDeliveryRepository.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterDeliveryQueryRepository.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/repository/NewsletterDeliveryQueryRepositoryImpl.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/sender/NewsletterEmailSender.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/sender/NewsletterEmailMessage.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/sender/NewsletterEmailSendResult.java
- src/main/java/web/tosunsaeng/domain/newsletter/domain/sender/NewsletterEmailSendException.java
- src/main/java/web/tosunsaeng/domain/newsletter/infrastructure/email/LoggingNewsletterEmailSender.java
- src/main/java/web/tosunsaeng/domain/newsletter/infrastructure/email/SesNewsletterEmailSender.java
- src/main/java/web/tosunsaeng/domain/newsletter/scheduler/NewsletterCampaignScheduler.java
- src/main/java/web/tosunsaeng/domain/newsletter/scheduler/NewsletterDeliveryScheduler.java
- src/main/java/web/tosunsaeng/domain/newsletter/scheduler/NewsletterMaintenanceScheduler.java

### 승인 후 예상 test 파일

- 기존 BlogPost entity, query와 index test 수정
- 기존 NewsletterSubscriber query, newsletter properties, token, Controller, advice와 telemetry privacy test 수정
- NewsletterCampaign과 NewsletterDelivery 상태 전이 test 생성
- Campaign 및 Delivery custom Repository Query BSON과 atomic update test 생성
- Campaign reconciliation, delivery 생성, 발송, retry, 완료 집계와 운영 Service test 생성
- EmailSender contract, Logging sender, SES request mapping과 failure classification test 생성
- link builder, HTML 및 plain text renderer, RFC 8058 header test 생성
- 세 Scheduler delegation, kill switch, bounded executor 설정 test 생성
- one-click Controller MockMvc test 생성

구체 경로는 모두 src/test/java/web/tosunsaeng/domain/newsletter/** 또는 기존 src/test/java/web/tosunsaeng/domain/blog/** 아래로 제한한다.

## BlogPost 또는 Campaign 연계 방식 비교

| 방식 | 장점 | 단점 | 판단 |
|---|---|---|---|
| BlogPost에 newsletterEnabled를 추가하고 별도 Campaign으로 실행 상태 관리 | DB 작성 시 글별 opt-in이 명확하고 reconciliation이 자동 대상을 찾을 수 있다. Campaign은 발송 이력과 상태를 독립 관리한다. | BlogPost schema와 query/index 수정이 필요하다. | 권고 |
| BlogPost는 변경하지 않고 Campaign만 사용 | BlogPost schema 변경이 없다. | Campaign을 누군가 먼저 만들어야 하므로 DB 직접 작성 글의 자동 탐색 조건을 표현할 수 없다. Campaign 존재 자체가 opt-in과 실행 상태를 동시에 뜻해 책임이 섞인다. | 요구사항에 부적합 |

권고안은 BlogPost에 primitive boolean newsletterEnabled 하나만 추가하고 Campaign은 별도 Document로 둔다. 기존 Mongo document에 필드가 없으면 false로 읽히며 query도 newsletterEnabled=true만 선택하므로 기존 글이 의도치 않게 발송되지 않는다. 운영자는 DB 또는 스크립트로 글을 만들 때 명시적으로 true를 넣어야 한다.

Campaign은 postId unique로 한 게시글당 하나만 존재한다. 완료, 실패 또는 취소 Campaign을 삭제하거나 새로 만들어 재발송하지 않는다. newsletterEnabled를 false에서 true로 다시 바꿔도 기존 Campaign이 있으면 자동 재예약되지 않는다.

## Campaign 모델과 상태 전이

### NewsletterCampaign

collection은 newsletter_campaigns를 사용한다.

| 필드 | 타입 및 정책 |
|---|---|
| id | Mongo String Id |
| postId | BlogPost id, unique |
| status | SCHEDULED, SENDING, SENT, FAILED, CANCELED |
| scheduledAt | publishedAt plus delay와 발견 now 중 더 늦은 실행 시각 |
| claimedAt | 마지막 claim 시각 |
| claimToken | SecureRandom 32-byte로 생성한 opaque URL-safe token, 로그 비노출 |
| claimExpiresAt | Campaign claim 만료 시각 |
| completedAt | SENT 또는 FAILED 최종 완료 시각 |
| canceledAt | CANCELED 시각 |
| totalRecipients | Delivery 생성 완료 전에는 null, 완료 후 생성된 Delivery 총수 |
| sentCount | SENT 수 |
| failedCount | 최종 FAILED 수 |
| skippedCount | SKIPPED 수 |
| lastErrorCode | 고정 enum 기반 정제된 내부 오류 code만 저장 |
| createdAt | 생성 시각 |
| updatedAt | 상태, 집계 또는 heartbeat 변경 시각 |

Campaign field를 승인 목록 밖으로 확장하지 않는다. Delivery 생성 중에는 totalRecipients를 null로 유지하고 전체 ACTIVE cursor 순회가 완료된 뒤 DB count로 확정해 완료 집계의 gate로 사용한다. 중간 장애 후에는 cursor를 처음부터 다시 순회하되 unique upsert가 이미 생성된 Delivery를 멱등 처리한다. claimToken은 stale claim을 재확보한 뒤 이전 worker가 상태를 덮어쓰지 못하게 한다.

### Campaign 상태 전이

| 현재 | 다음 | 주체와 조건 |
|---|---|---|
| 없음 | SCHEDULED | reconciliation이 unique postId insert |
| SCHEDULED | SENDING | sending enabled, scheduledAt less than or equal to now인 한 건을 원자 claim |
| SCHEDULED | CANCELED | 운영 취소 Service의 조건부 원자 update |
| SENDING | SENDING | claimExpiresAt이 지난 Delivery 생성 Campaign을 새 SecureRandom token으로 재claim |
| SENDING | SENT | 생성 완료, 모든 Delivery 최종 상태, failedCount=0 |
| SENDING | FAILED | 생성 완료, 모든 Delivery 최종 상태, failedCount가 1 이상이거나 Campaign 자체 영구 오류 |
| FAILED | SENDING | 수동 Campaign retry가 안전한 FAILED Delivery를 같은 Document로 재queue한 뒤 조건부 전이 |

SENT와 CANCELED는 자동 또는 수동 재예약하지 않는다. 취소는 오직 SCHEDULED에서만 허용하며 SENDING, SENT, FAILED, CANCELED에서는 취소 전이를 만들지 않는다.

Campaign이 Delivery 생성 완료 후 retry 대기 Delivery를 가지고 있으면 SENDING으로 유지한다. kill switch가 false이면 신규 claim만 막고 기존 SENDING Campaign의 status, claimToken과 claimExpiresAt을 변경하지 않는다.

## Delivery 모델과 상태 전이

### NewsletterDelivery

collection은 newsletter_deliveries를 사용한다. email과 unsubscribe token은 저장하지 않고 발송 직전에 Subscriber를 다시 읽는다.

| 필드 | 타입 및 정책 |
|---|---|
| id | Mongo String Id |
| campaignId | Campaign id |
| postId | BlogPost id |
| subscriberId | NewsletterSubscriber id |
| status | PENDING, SENDING, SENT, FAILED, SKIPPED |
| attemptCount | 실제 provider 호출 시작 횟수 |
| nextRetryAt | 초기 또는 retry claim 가능 시각, 최종 상태는 null |
| claimToken | stale worker fencing token |
| claimExpiresAt | Delivery claim 만료 시각 |
| providerCallStartedAt | provider 호출 직전 기록, 결과 미확정 복구 판단 |
| providerMessageId | provider가 성공 응답한 message id |
| sentAt | SENT 시각 |
| failedAt | 최종 FAILED 시각 |
| skippedAt | SKIPPED 시각 |
| lastErrorCode | enum 기반 정제된 내부 code만 저장 |
| retryable | 현재 FAILED가 자동 또는 수동 retry 후보인지 표시 |
| createdAt | 생성 시각 |
| updatedAt | 마지막 전이 시각 |

### Delivery 상태 전이

| 현재 | 다음 | 조건 |
|---|---|---|
| 없음 | PENDING | ACTIVE Subscriber batch에서 idempotent upsert |
| PENDING | SENDING | due Delivery를 원자 claim |
| FAILED | SENDING | nextRetryAt이 도래한 retryable 실패를 원자 claim |
| SENDING | SENT | provider가 message id와 함께 성공 응답 |
| SENDING | FAILED | retryable 실패면 nextRetryAt 설정, 영구 또는 최대 시도면 failedAt과 nextRetryAt null |
| SENDING | SKIPPED | provider 호출 전 Subscriber가 ACTIVE가 아니거나 게시글이 부적격 |
| SENDING | PENDING | providerCallStartedAt 없는 stale worker를 안전 회수 |
| FAILED | PENDING | 운영 수동 retry가 같은 Delivery를 조건부 재queue |

SENT는 어떤 자동 또는 수동 경로에서도 다시 PENDING이나 SENDING으로 가지 않는다. 같은 postId와 subscriberId로 새 Delivery를 만들지 않는다.

providerCallStartedAt이 있는 stale SENDING은 SES가 수락했지만 응답 저장 전에 프로세스가 죽었을 가능성이 있다. SES SendEmail에는 애플리케이션이 사용할 강한 idempotency key가 없으므로 자동 재발송하지 않고 PROVIDER_RESULT_UNKNOWN, retryable=false인 최종 FAILED로 종료한다. 이 오류는 수동 retry에서도 거절해 운영자 수동 검토 전 동일 Subscriber에게 재발송하지 않는다. provider 경계에서 exactly-once 발송을 보장하지 않는다.

## 자동 예약 전략

발행 event가 아니라 매 60초 reconciliation을 권고한다.

1. BlogPost를 status=PUBLISHED, publishedAt exists 및 non-null, newsletterEnabled=true 조건으로 id 오름차순 cursor batch 조회한다. publishedAt이 미래인 글도 포함한다.
2. batch의 postId에 대해 기존 Campaign postId를 한 번에 조회해 애플리케이션에서 없는 글만 고른다.
3. scheduled candidate는 publishedAt plus NEWSLETTER_SEND_DELAY_MINUTES다.
4. candidate가 now 이후면 candidate를, candidate가 now와 같거나 이전이면 now를 scheduledAt으로 쓴다.
5. Campaign SCHEDULED insert가 duplicate key이면 다른 instance가 먼저 만든 정상 멱등 결과로 처리한다.
6. 다음 batch로 진행하고 한 cycle이 끝나면 종료한다.

Blog 수가 작은 MVP에서는 매 cycle 전체 eligible 글을 cursor로 훑는 단순 reconciliation을 택한다. 기존 Campaign postId batch 조회로 불필요한 duplicate exception을 줄이고, unique index를 최종 동시성 보장으로 둔다. 오래된 글의 newsletterEnabled가 나중에 true가 되는 경우도 놓치지 않는다.

publishedAt 계산 overflow, 필수 title, summary 또는 slug 부재 같은 데이터 오류는 원문 content를 로그에 남기지 않고 안전한 오류 code로 기록한다. DRAFT, ARCHIVED, publishedAt null, newsletterEnabled false와 누락 필드는 Campaign 후보가 아니다.

## Scheduler 구성

하나의 거대한 Scheduled method는 한 역할의 지연과 오류가 전체 흐름을 막고 test 경계가 흐려진다. 반대로 여섯 개 이상의 class로 한 method씩 나누면 MVP에 과한 파일 수가 된다. 세 개 adapter class와 역할별 짧은 method를 권고한다.

| Scheduler | 작업 | 기본 주기 |
|---|---|---:|
| NewsletterCampaignScheduler | 새 Campaign reconciliation | 60초 fixed delay |
| NewsletterCampaignScheduler | due Campaign claim 및 recipient 생성 task 제출 | 10초 fixed delay |
| NewsletterDeliveryScheduler | PENDING Delivery 발송 task 제출 | 5초 fixed delay |
| NewsletterDeliveryScheduler | due FAILED retry task 제출 | 30초 fixed delay |
| NewsletterMaintenanceScheduler | stale Campaign 및 Delivery claim 복구 | 60초 fixed delay |
| NewsletterMaintenanceScheduler | Campaign count 집계 및 완료 | 30초 fixed delay |

Scheduled method는 Clock으로 now를 얻고 Application Service 또는 bounded executor에 짧게 위임한다. provider 호출과 전체 subscriber loop를 Spring 기본 scheduler thread에서 실행하지 않는다. 각 worker는 DB claim을 task 내부에서 수행하므로 queue 거절 시 아직 claim되지 않은 작업이 안전하게 다음 tick에 남는다.

실행 주기는 NewsletterDeliveryProperties의 안전한 기본값으로 두되 .env.example에는 운영에 필수인 사용자 지정 환경변수만 노출한다. 주기 변경이 제품 동작에 영향을 주면 승인된 property 범위 안에서만 조정한다.

## 원자적 claim

Campaign claim은 다음 단일 Mongo findAndModify로 처리한다.

~~~text
query:
  status = SCHEDULED
  scheduledAt <= now
sort:
  scheduledAt ASC
  _id ASC
update:
  status = SENDING
  claimedAt = now
  claimToken = SecureRandom 32-byte URL-safe token
  claimExpiresAt = now + configured claim TTL
  updatedAt = now
options:
  returnNew = true
  upsert = false
~~~

find, 애플리케이션 판단, save 순서로 claim하지 않는다. 조건에 일치한 Document를 실제로 반환받은 worker만 recipient 생성을 시작한다. batch heartbeat, totalRecipients 확정과 후속 상태 update는 campaign id, status=SENDING, 현재 claimToken 조건을 포함해 stale worker를 fencing한다.

만료된 SENDING Campaign은 status를 SCHEDULED로 바꾸지 않고 status=SENDING, claimExpiresAt<=now 조건의 findAndModify로 새 claimToken, claimedAt과 claimExpiresAt을 부여한다. kill switch가 false이면 stale recovery도 상태를 변경하지 않는다.

Delivery의 PENDING claim과 FAILED retry claim은 Delivery 자체의 status와 nextRetryAt 조건을 사용한 findAndModify로 한 건씩 가져온다. Campaign은 다른 collection이므로 같은 update 조건에 섞지 않는다. claim 직후 Campaign이 SENDING인지 별도로 재확인하고 아니면 providerCallStartedAt과 attemptCount를 건드리지 않은 채 claimToken 조건으로 PENDING에 되돌린다. Delivery claim 후 providerCallStartedAt과 attemptCount를 설정하는 update 역시 claimToken이 일치할 때만 성공해야 한다.

두 Scheduler가 동시에 실행되는 단위 test에서는 같은 Campaign에 대해 한 repository claim만 성공하도록 fake atomic repository 또는 mock sequence와 barrier를 사용한다. 실제 Mongo findAndModify 원자성과 unique index 경쟁은 Testcontainers를 추가하지 않고 Phase 08에서 검증한다.

## batch 처리

- 기본 NEWSLETTER_BATCH_SIZE는 100을 권고한다.
- ACTIVE Subscriber는 _id ASC cursor로 조회하고 한 batch만 메모리에 둔다.
- worker는 메모리에만 lastSeenId를 유지하고 매 batch upsert 후 현재 claimToken으로 claimExpiresAt heartbeat를 연장한다.
- crash 후 stale Campaign이 새 token으로 재claim되면 ACTIVE cursor를 처음부터 다시 읽지만 postId, subscriberId unique upsert가 중복 생성을 막는다.
- Delivery 생성은 unordered bulk upsert와 setOnInsert를 사용해 duplicate를 정상 멱등 결과로 처리한다.
- 모든 ACTIVE Subscriber를 한 번에 List로 읽거나 offset page로 순회하지 않는다. 상태 변경 중 offset skip이 발생할 수 있기 때문이다.
- Delivery에는 email을 snapshot 저장하지 않는다. 발송 worker가 subscriberId로 현재 Subscriber를 읽는다.
- batch 끝에 더 이상 결과가 없으면 campaignId Delivery count로 totalRecipients를 null에서 확정하고 claimExpiresAt을 제거하되 claimToken은 terminal Campaign 전이 fencing에 사용하도록 유지한다.
- completion 집계는 count를 증분만 믿지 않고 상태별 DB count를 다시 계산해 crash 전후 수치를 복구한다.
- 구독자가 증가하면 우선 batch size를 100에서 조정하고 worker 수는 SES quota와 EC2 memory를 함께 확인한 뒤 별도 승인으로 조정한다.

## EmailSender 추상화

NewsletterEmailSender는 AWS type을 노출하지 않는 application port다.

입력 NewsletterEmailMessage는 recipient email, subject, HTML body, plain text body와 허용된 header map을 가진다. 출력 NewsletterEmailSendResult는 providerMessageId만 제공한다. 예외 NewsletterEmailSendException은 NewsletterFailureType, retryable 여부, provider outcome known 여부와 안전한 error code만 제공하고 provider 원문은 보존하지 않는다.

### LoggingNewsletterEmailSender

- NEWSLETTER_EMAIL_PROVIDER=logging일 때 선택한다.
- local과 test의 기본 provider다.
- 실제 email network 호출을 하지 않는다.
- sending enabled인 명시적 simulation에서만 deterministic한 synthetic providerMessageId를 반환한다.
- 로그에는 event name, campaignId, deliveryId, postId와 성공 여부만 남긴다.
- email 전체 주소, unsubscribe token, HTML, plain text, 제목과 provider request를 로그에 남기지 않는다.

### provider 선택

- property 기반 ConditionalOnProperty로 logging 또는 ses 구현 하나만 Bean으로 만든다.
- 기본 provider는 logging이다.
- production profile이라는 이유만으로 SES를 자동 선택하지 않는다. 운영자가 provider=ses와 sending enabled=true를 각각 명시해야 한다.
- kill switch가 false이면 선택된 Sender가 무엇이든 호출하지 않는다.

## SES 구현

- AWS SES v2 sync client를 사용한다.
- build.gradle의 기존 software.amazon.awssdk:s3:2.29.52 개별 버전 체계를 유지하고 software.amazon.awssdk:sesv2:2.29.52만 추가한다. Phase 06을 위해 BOM을 도입하거나 AWS SDK 버전을 올리지 않는다.
- SesV2Client는 AWS_REGION과 DefaultCredentialsProvider를 사용한다.
- AWS_ACCESS_KEY와 AWS_SECRET_KEY를 새 SES 코드, newsletter property 또는 문서에 추가하지 않는다.
- EC2 IAM Role 또는 표준 AWS credential chain을 우선하며 기존 S3Config의 static credential 설정은 이번 Phase에서 변경하지 않는다.
- API call timeout을 유한하게 설정하고 SDK 내부 자동 retry는 한 번의 application attempt 의미를 흐리거나 중복을 만들지 않도록 비활성화 또는 max attempt 1로 제한한다. retry는 NewsletterRetryPolicy가 관리한다.
- Java 소스 수정 전에 2.29.52 SES v2 공식 API model과 실제 SDK class를 모두 확인한다. Message.Builder의 Simple custom header 지원이 확인된 경우에만 subject, HTML, plain text, List-Unsubscribe와 List-Unsubscribe-Post를 Simple request에 매핑한다.
- SES가 금지하는 예약 header를 설정하지 않는다. 현재 SDK가 Simple custom header를 안전하게 지원하지 않으면 Raw MIME으로 자동 전환하지 않고 소스 구현을 중단한 뒤 제약과 대안을 보고한다.
- SES 성공 응답의 messageId만 providerMessageId에 저장한다.
- SES request 전체, response 전체와 exception 원문을 로그 또는 Mongo lastError에 저장하지 않는다.

코드 밖 운영 준비 항목:

- NEWSLETTER_FROM_EMAIL domain identity 검증
- DKIM 활성화와 DNS record 반영
- SPF 및 DMARC 정책 검토
- SES sandbox 해제와 production access
- ap-northeast-2 또는 선택 region의 sending quota 확인
- instance role에 최소 ses:SendEmail 권한 부여
- custom List-Unsubscribe header가 DKIM 서명 및 실제 수신 header에 남는지 Phase 08에서 확인

## kill switch

- NEWSLETTER_SENDING_ENABLED 기본값은 false다.
- false에서도 reconciliation은 SCHEDULED Campaign을 만든다.
- false에서는 SCHEDULED Campaign 신규 claim, Delivery PENDING/retry claim, provider 호출과 테스트 발송을 모두 막는다.
- false인 사실만으로 Campaign과 Delivery의 status, claimToken, claimExpiresAt, providerCallStartedAt, attemptCount 또는 terminal 시각을 변경하지 않는다.
- 따라서 기존 SENDING Campaign을 SCHEDULED로 바꾸거나 SENDING Delivery를 PENDING으로 되돌리는 kill-switch 전이를 만들지 않는다.
- stale recovery는 sending enabled=true인 동안에만 별도 claim 만료 정책으로 실행하며 kill switch 자체의 부수 효과가 아니다.
- true로 복구하면 아직 SCHEDULED인 Campaign과 PENDING 또는 retry 조건을 만족한 FAILED Delivery를 정상 처리한다.
- 이미 SES에 제출된 in-flight 요청은 kill switch로 회수할 수 없고, provider 결과 미확정 경계에서 exactly-once를 보장하지 않는다.

## 이메일 템플릿

제목:

~~~text
[토선생] {게시글 제목}
~~~

HTML과 plain text는 동일 정보를 제공한다.

- 새 글 발행 안내
- BlogPost.title
- BlogPost.summary
- 글 읽으러 가기 link
- 구독 해지 link
- NEWSLETTER_FROM_NAME 기반 발신 서비스 정보

contentMarkdown 전체를 이메일에 넣지 않는다. title, summary와 from name은 HTML escape하고 CR/LF header injection을 거절한다. HTML body와 plain text body를 로그에 남기지 않는다.

게시글 URL:

~~~text
{NEWSLETTER_PUBLIC_BASE_URL}/blog/{slug}?utm_source=newsletter&utm_medium=email&utm_campaign=post_notification
~~~

URI builder로 base URL, slug path segment와 query parameter를 조립한다. 문자열 이어붙이기로 query 또는 header injection을 허용하지 않는다.

## 구독 해지 링크

실제 Delivery는 provider 호출 직전에 현재 ACTIVE Subscriber의 id와 tokenVersion으로 NewsletterUnsubscribeTokenManager.createToken을 호출한다. token은 메모리의 email message에만 존재하고 Campaign, Delivery와 log에 저장하지 않는다.

사용자 본문 link:

~~~text
{NEWSLETTER_PUBLIC_BASE_URL}/newsletter/unsubscribe?token={token}
~~~

이 URL은 frontend 확인 page를 가리킨다. GET으로 상태를 바꾸지 않고 사용자가 확인한 뒤 기존 JSON body POST /api/newsletter/unsubscribe를 호출한다. Phase 06은 frontend를 구현하지 않는다.

발송 직전 Subscriber가 UNSUBSCRIBED 또는 BOUNCED이면 token과 email message를 만들기 전에 Delivery를 SKIPPED로 처리한다. ACTIVE라도 tokenVersion은 가장 최근 값을 사용한다.

## RFC 8058 전략

### 선택지 비교

| 선택지 | 장점 | 위험 |
|---|---|---|
| 기존 POST /api/newsletter/unsubscribe가 JSON과 RFC form을 함께 수용 | endpoint 수가 적다. | Phase 05의 JSON body only 및 query fallback 금지 계약이 흐려지고 Content-Type, 응답, token 위치와 advice가 혼합된다. |
| 별도 one-click endpoint | 일반 사용자 확인 흐름과 email client 자동 POST 계약을 격리하고 기존 API를 보존한다. | 공개 endpoint 하나와 별도 보안 test가 필요하다. |

별도 endpoint를 권고하고 Phase 06에 실제 구현한다. Header가 가리키는 endpoint 없이 header만 먼저 내보내면 email client가 실패하므로 Phase 07로 미루지 않는다.

~~~http
POST /api/newsletter/one-click-unsubscribe/{token}
Content-Type: application/x-www-form-urlencoded

List-Unsubscribe=One-Click
~~~

- path token과 form field를 모두 검증한다. token은 Phase 05 stateless opaque token을 재사용한다.
- form field 값은 정확히 One-Click이어야 한다.
- application/x-www-form-urlencoded를 필수 지원하고 Spring이 동일 @RequestParam 계약으로 안전하게 binding할 수 있는 multipart/form-data도 수용한다.
- token 검증과 상태 전이는 기존 NewsletterUnsubscribeTokenManager 및 NewsletterService의 공통 내부 method를 재사용한다.
- 정상 처리는 redirect 없는 204 No Content와 Cache-Control: no-store를 사용한다.
- GET mapping은 만들지 않고 GET은 Subscriber를 조회하거나 변경하지 않는다.
- 잘못된 token, 누락 form field와 잘못된 Content-Type은 상태를 변경하지 않는다.
- endpoint는 공개 API지만 bearer token 검증이 필수다.
- NewsletterTelemetryPrivacyConfig에 one-click POST를 추가해 path token이 있는 request URL과 transaction을 고정 placeholder로 정규화하고 body와 관련 PII를 Sentry에서 제거한다.
- cookie나 login에 의존하지 않고 ACTIVE와 BOUNCED를 UNSUBSCRIBED로 전이하며 이미 UNSUBSCRIBED면 멱등 성공한다.
- 응답에 email, subscriberId, tokenVersion을 넣지 않고 일반 application log에 request URL, path token과 form body를 남기지 않는다.

Email header:

~~~text
List-Unsubscribe: <{NEWSLETTER_API_BASE_URL}/api/newsletter/one-click-unsubscribe/{token}>
List-Unsubscribe-Post: List-Unsubscribe=One-Click
~~~

NEWSLETTER_PUBLIC_BASE_URL은 게시글과 frontend 확인 page에만, NEWSLETTER_API_BASE_URL은 RFC endpoint에만 사용한다. 단일 public origin을 가정하지 않고 두 base URL은 운영에서 모두 HTTPS를 요구한다. path token이 proxy/CDN/access log에 노출될 위험은 Phase 08 운영 검수로 기록한다.

## retry 정책

요구사항의 최대 3회 재시도와 세 개 retry 간격을 문자 그대로 적용해 최초 발송 1회 plus 재시도 3회, 총 provider 호출 최대 4회를 권고한다.

attemptCount는 provider 호출을 시작하기 직전 claimToken 조건부 update가 성공한 횟수다. 단순 Delivery claim, subscriber 재확인 실패, kill switch 차단과 template 생성 실패 전에는 증가하지 않는다.

| attemptCount 실패 | 다음 동작 |
|---:|---|
| 1 | 5분 뒤 retry |
| 2 | 30분 뒤 retry |
| 3 | 2시간 뒤 retry |
| 4 | nextRetryAt 없이 최종 FAILED |

영구 오류는 attemptCount가 1이어도 즉시 최종 FAILED다. retry 전 Subscriber 상태를 다시 읽고 ACTIVE가 아니면 SKIPPED다. SENT는 retry 대상이 아니다.

수동 retry는 같은 Delivery를 PENDING으로 되돌리고 새 Document를 만들지 않는다. 기존 attemptCount는 감사 의미로 유지하며 수동 provider 호출도 증가한다. 수동 실패 뒤 자동 retry 세 회를 새로 부여하지 않고 해당 시도 실패는 다시 최종 FAILED로 둔다.

## 오류 분류

| 분류 | 예 | 자동 retry |
|---|---|---|
| TRANSIENT_PROVIDER | 명시적 throttling, SES가 반환한 5xx, 연결 성립 전 일시 네트워크 실패 | 가능 |
| PERMANENT_REQUEST | MessageRejected, 잘못된 request 또는 header | 불가 |
| INVALID_EMAIL | provider 호출 전 email validation 또는 주소 형식 거절 | 불가 |
| AUTH_CONFIGURATION | credential, AccessDenied, 미검증 identity, region 및 from 설정 | 불가 |
| APPLICATION_ERROR | template, 상태 전이, SDK mapping 계약 오류 | 불가 |
| PROVIDER_RESULT_UNKNOWN | request 전송 뒤 timeout 또는 성공 응답 저장 전 crash | 자동 및 수동 retry 불가 |

provider가 명시적으로 실패 응답을 반환한 경우와 응답을 받지 못한 경우를 구분한다. exception type, HTTP status와 안전한 SES error code allowlist만 분류에 사용하고 provider message 원문은 저장하지 않는다.

SES send 성공은 provider가 message를 수락했다는 뜻일 뿐 최종 delivery 또는 bounce 부재를 뜻하지 않는다. send 응답만으로 Subscriber를 BOUNCED로 바꾸지 않는다. SES bounce 및 complaint event 연동은 Phase 08 또는 후속 Phase로 이관한다.

## 실행기와 backpressure

newsletter 전용 ThreadPoolTaskExecutor 한 개를 권고한다.

- core pool size 2
- max pool size 2
- queue capacity 100
- thread name prefix newsletter-
- AbortPolicy 기반 거절
- waitForTasksToCompleteOnShutdown true
- await termination 30초

worker 수 2는 프리티어 성격의 현재 server thread 제한과 SES quota를 고려한 MVP 기본값이다. queue가 차면 Scheduler가 provider 작업을 직접 실행하지 않고 제출을 중단하며 다음 tick에서 다시 시도한다. claim은 worker 내부에서 하므로 queue 거절로 SENDING이 남지 않는다.

Campaign recipient 생성도 batch 100으로 제한하고 매 batch 후 cursor와 heartbeat를 저장한다. 무제한 thread, JVM common pool과 무제한 queue를 사용하지 않는다.

graceful shutdown 시간 안에 끝나지 않은 provider 호출은 강제 재발송하지 않는다. 다음 시작의 stale recovery가 providerCallStartedAt 여부로 안전한 재queue 또는 outcome unknown 최종 실패를 결정한다.

## 운영 Service

### 테스트 발송 Service

- 입력은 postId와 명시적 test recipient email이다.
- NewsletterEmailNormalizer로 email 형식을 검증하되 Subscriber 등록 여부를 조회하거나 요구하지 않는다.
- NewsletterCampaign과 NewsletterDelivery를 생성하거나 변경하지 않는다.
- NEWSLETTER_SENDING_ENABLED=true와 NEWSLETTER_TEST_SENDING_ENABLED=true를 모두 만족할 때만 provider를 호출하고 아니면 명확한 disabled 결과를 반환한다.
- 정규화된 test recipient가 NEWSLETTER_TEST_RECIPIENT_ALLOWLIST에 정확히 포함된 경우에만 호출하고 allowlist 밖은 거절한다.
- provider 선택은 NEWSLETTER_EMAIL_PROVIDER를 따른다.
- 실제 subscriber token이 없으므로 test email은 제목에 테스트 표시를 넣고 상태를 바꾸는 unsubscribe token, 수동 해지 link 및 RFC 8058 header를 넣지 않는다. renderer 구조와 게시글 URL은 production과 공유한다.
- test 발송 이력용 새 Document는 만들지 않는다. event, postId, provider, 결과 code만 포함한 sanitized structured log를 사용하고 recipient email과 body는 제외한다.
- 실제 email client의 one-click header test는 Phase 08에서 test 전용 subscriber와 SES sandbox로 수행한다.

### 예약 취소 Service

- campaignId와 status=SCHEDULED 조건의 findAndModify로 CANCELED, canceledAt, updatedAt을 기록한다.
- SENDING, SENT, FAILED, CANCELED은 취소하지 않는다.
- Controller는 Phase 07에서 구현한다.

### 실패 재시도 Service

- Delivery retry는 status=FAILED, retryable=true, attemptCount<4, PROVIDER_RESULT_UNKNOWN이 아닌 조건으로 같은 Document를 PENDING으로 바꾸고 nextRetryAt, claimToken, claimExpiresAt을 제거한다.
- SENT, SKIPPED, retryable=false와 PROVIDER_RESULT_UNKNOWN은 거절한다.
- Subscriber를 다시 읽어 ACTIVE면 requeue하고 UNSUBSCRIBED 또는 BOUNCED면 SKIPPED로 바꾼다.
- Delivery를 성공적으로 requeue한 뒤 해당 Campaign이 FAILED면 새 Campaign을 만들지 않고 조건부로 SENDING으로 되돌린다.
- 새 Delivery 또는 새 Campaign을 만들지 않는다.
- Subscriber가 ACTIVE가 아니면 Delivery를 SKIPPED로 종료하고 Campaign은 completion 집계가 재판단한다.
- Controller는 Phase 07에서 구현한다.

## MongoDB 인덱스

기존 programmatic initializer 패턴과 named ensureIndex를 사용한다.

### BlogPost

- status ASC, newsletterEnabled ASC, publishedAt ASC, _id ASC reconciliation index 추가

### NewsletterCampaign

- postId ASC unique
- status ASC, scheduledAt ASC
- status ASC, claimExpiresAt ASC
- status ASC, updatedAt ASC

### NewsletterDelivery

- postId ASC, subscriberId ASC unique
- campaignId ASC, status ASC
- status ASC, nextRetryAt ASC
- status ASC, claimExpiresAt ASC

기존 NewsletterSubscriber email unique와 status index는 유지한다. 실제 MongoDB index 생성, unique 경쟁과 findAndModify 동시성은 mock 정의만으로 완전히 증명하지 않고 Phase 08에서 통합 검증한다.

## 개인정보 및 로깅

일반 application log, exception message, Campaign.lastErrorCode와 Delivery.lastErrorCode에 다음을 넣지 않는다.

- email 전체 주소
- unsubscribe token, signature와 decoded payload
- provider request 및 response 전체
- AWS credential과 credential provider 세부값
- HTML 및 plain text 전체
- SES exception 원문과 수신자 정보가 포함될 수 있는 provider message

Delivery에는 email을 저장하지 않고 subscriberId만 저장한다. token은 발송 직전에 만들고 DB에 저장하지 않는다. Logging sender도 email, token과 body를 출력하지 않는다.

lastErrorCode는 고정 enum code만 허용한다. providerMessageId는 성공 응답에서만 저장하고 전체 response는 버린다.

one-click URL의 path token은 RFC 구조상 필요하므로 NewsletterTelemetryPrivacyConfig에서 해당 path와 transaction name을 고정된 placeholder로 정규화하고 request body를 제거한다. reverse proxy, CDN과 load balancer access log redaction은 애플리케이션으로 보장할 수 없어 Phase 08 운영 검수로 기록한다.

## 설정과 환경변수

NewsletterDeliveryProperties 권고 항목:

| 환경변수 | 기본값 | 정책 |
|---|---|---|
| NEWSLETTER_SENDING_ENABLED | false | 모든 provider 호출의 master switch |
| NEWSLETTER_EMAIL_PROVIDER | logging | logging 또는 ses |
| NEWSLETTER_FROM_EMAIL | 빈 값 | 실제 또는 test send enabled 시 유효값 필요 |
| NEWSLETTER_FROM_NAME | 토선생 | CR/LF 금지 |
| NEWSLETTER_PUBLIC_BASE_URL | 빈 값 | 게시글과 frontend 해지 page 절대 URL, 운영 HTTPS |
| NEWSLETTER_API_BASE_URL | 빈 값 | RFC endpoint 절대 URL, 운영 HTTPS |
| NEWSLETTER_SEND_DELAY_MINUTES | 15 | 0 이상, 상한 검증 |
| NEWSLETTER_BATCH_SIZE | 100 | 1부터 1000 |
| NEWSLETTER_WORKER_COUNT | 2 | 1부터 16, core=max bounded worker |
| NEWSLETTER_QUEUE_CAPACITY | 100 | 1부터 10000, bounded queue |
| NEWSLETTER_CLAIM_TTL_SECONDS | 300 | 양수, Campaign과 Delivery claim 만료 |
| NEWSLETTER_RECONCILIATION_DELAY_MS | 60000 | fixedDelay |
| NEWSLETTER_CAMPAIGN_DELAY_MS | 10000 | fixedDelay |
| NEWSLETTER_DELIVERY_DELAY_MS | 5000 | fixedDelay |
| NEWSLETTER_RETRY_DELAY_MS | 30000 | fixedDelay |
| NEWSLETTER_COMPLETION_DELAY_MS | 30000 | fixedDelay |
| NEWSLETTER_STALE_RECOVERY_DELAY_MS | 60000 | fixedDelay |
| NEWSLETTER_TEST_SENDING_ENABLED | false | test provider 추가 switch |
| NEWSLETTER_TEST_RECIPIENT_ALLOWLIST | 빈 목록 | 정규화 email 정확 일치 |
| AWS_REGION | ap-northeast-2 | SES client region |

main application.yml에는 위 binding과 안전한 기본값만 추가한다. application-test.yml은 sending disabled, provider logging, example.test base URL과 dummy from email을 사용한다. .env.example에는 값 이름, 기본값과 IAM Role 권고만 적고 실제 credential, from 주소와 production URL을 넣지 않는다.

from email, public/API base URL과 SES region은 provider 호출 전에 fail fast 검증한다. sending disabled인 local context는 실제 운영 from email이 없어도 기동 가능해야 한다. send delay, batch, worker, queue, claim TTL과 scheduler delay는 양수와 상한을 검증한다.

## API 변경

Phase 06에서 추가하는 HTTP API는 공개 RFC 8058 endpoint 하나다.

- 추가: POST /api/newsletter/one-click-unsubscribe/{token}
- 미추가: 해당 path의 GET
- 유지: POST /api/newsletter/subscribe
- 유지: JSON body POST /api/newsletter/unsubscribe
- 미추가: verify API
- 미추가: /internal/newsletter/**

기존 JSON unsubscribe의 consumes, request body와 BaseResponse 계약을 바꾸지 않는다. one-click은 별도 Controller와 path token, form Content-Type 계약, redirect 없는 204 응답을 사용한다. SecurityConfig는 변경하지 않는다.

## DB 변경

- blog_posts에 newsletterEnabled boolean을 추가한다. 기존 누락 값은 false 취급한다.
- newsletter_campaigns collection을 추가한다.
- newsletter_deliveries collection을 추가한다.
- newsletter_subscribers의 기존 필드와 상태 enum은 변경하지 않는다.
- raw email 사본, unsubscribe token과 token hash를 Campaign 또는 Delivery에 추가하지 않는다.
- schema migration script, 기존 Document backfill과 운영 DB index 작업은 이번 코드 작업에서 실행하지 않는다.

## 상태 전이

상세 Campaign 및 Delivery 표를 위 절의 승인 기준으로 사용한다. 공통 원칙은 다음과 같다.

- 상태 전이는 entity 불변식과 custom Repository 조건을 같은 의미로 유지한다.
- claim, cancel, stale recovery와 manual retry는 save 기반 read-modify-write를 사용하지 않는다.
- terminal SENT와 SKIPPED를 자동 재발송 상태로 되돌리지 않는다.
- Campaign CANCELED와 SENT는 자동 재예약하지 않는다.
- Campaign FAILED는 새 Campaign 생성이 아니라 기존 실패 Delivery의 수동 retry만 허용한다.
- 모든 시각은 주입된 UTC Clock을 사용한다.

## 테스트 계획

Testcontainers를 추가하지 않고 JUnit 5, Mockito, AssertJ, MockMvc, Mongo Query BSON 및 IndexDefinition contract를 사용한다.

### Campaign 탐색

1. PUBLISHED, publishedAt 존재, newsletterEnabled=true 글을 예약한다.
2. DRAFT 글을 제외한다.
3. ARCHIVED 글을 제외한다.
4. 미래 publishedAt 글을 publishedAt plus delay로 예약한다.
5. publishedAt null과 누락을 제외한다.
6. newsletterEnabled=false와 누락을 제외한다.
7. postId unique 충돌을 멱등 처리해 Campaign을 중복 생성하지 않는다.
8. SENT, FAILED 또는 CANCELED을 포함해 Campaign이 이미 있는 글을 자동 재예약하지 않는다.
9. 기본 delay가 정확히 15분이다.
10. delay가 지난 글은 Clock의 now로 즉시 예약한다.

### Campaign claim

11. due SCHEDULED가 원자적으로 SENDING과 claimToken을 얻는다.
12. 두 Scheduler가 동시에 claim할 때 같은 Campaign은 하나만 성공한다.
13. CANCELED은 claim할 수 없다.
14. SENT는 claim할 수 없다.
15. delivery 생성 미완료 stale SENDING은 status를 바꾸지 않고 새 SecureRandom token으로 재claim하며 totalRecipients가 확정된 Campaign은 재claim하지 않는다.

### Delivery 생성

16. ACTIVE Subscriber만 Delivery 생성 후보로 조회한다.
17. UNSUBSCRIBED는 제외한다.
18. BOUNCED는 제외한다.
19. postId와 subscriberId unique upsert로 중복 Delivery가 없다.
20. _id cursor와 batch 100으로 pagination한다.
21. upsert 후 crash를 처음 cursor부터 재실행해도 중복이 없고 totalRecipients 확정으로 generation 완료를 복구한다.

### EmailSender

22. local 및 test의 Logging sender는 실제 network 발송을 하지 않는다.
23. ses property에서 SES v2 request를 만들고 DefaultCredentialsProvider 및 region 계약을 사용한다.
24. SES 성공 messageId를 providerMessageId에 저장한다.
25. email에 HTML과 plain text body가 모두 있다.
26. 게시글 URL에 세 UTM parameter와 encoded slug가 있다.
27. 본문 사용자 해지 link가 frontend 확인 page를 가리킨다.
28. 실제 Delivery email에 두 RFC 8058 header가 있다.
29. email, token, body, provider request 및 response가 log에 노출되지 않는다.

### Kill switch

30. NEWSLETTER_SENDING_ENABLED 기본값은 false다.
31. false에서 logging 및 SES provider를 호출하지 않는다.
32. false에서 Delivery를 SENT로 기록하지 않는다.
33. false는 기존 SENDING Campaign과 Delivery의 상태를 강제 변경하지 않고 true 복구 후 아직 SCHEDULED/PENDING인 항목을 처리한다.

### 발송

34. PENDING은 claimToken 기반 SENDING을 거쳐 SENT가 된다.
35. providerCallStartedAt 직전에 Subscriber 상태를 재확인한다.
36. UNSUBSCRIBED와 BOUNCED Subscriber는 SKIPPED가 되고 token 및 provider 호출이 없다.
37. 일시 provider 오류는 실패 상태와 nextRetryAt을 기록한다.
38. 영구 오류는 nextRetryAt 없이 최종 FAILED다.
39. 두 worker 경쟁과 stale recovery에서도 같은 Delivery의 논리적 이중 claim 및 재발송이 없다.

### 재시도

40. attemptCount 1 실패는 5분 뒤다.
41. attemptCount 2 실패는 30분 뒤다.
42. attemptCount 3 실패는 2시간 뒤다.
43. attemptCount 4 실패와 영구 오류는 최대 시도를 초과해 최종 FAILED다.
44. retry claim 뒤 Subscriber가 해지됐으면 SKIPPED다.
45. SENT와 PROVIDER_RESULT_UNKNOWN은 자동 및 수동 retry가 불가능하다.

### Campaign 완료

46. delivery generation 완료 후 모든 Delivery가 최종 상태면 Campaign을 종료한다.
47. 일부 최종 FAILED가 있어도 무기한 SENDING으로 남지 않고 Campaign FAILED가 된다.
48. totalRecipients, sentCount, failedCount와 skippedCount를 DB 상태로 재집계한다.
49. SENT와 FAILED에 completedAt을 Clock의 now로 기록한다.

### 운영 Service

50. 명시적 test email과 postId로 test sender를 호출한다.
51. test 발송은 NewsletterDelivery를 생성하지 않는다.
52. test 발송은 NewsletterCampaign 상태와 count를 변경하지 않는다.
53. SCHEDULED Campaign을 조건부 CANCELED로 바꾼다.
54. SENDING Campaign 취소는 conflict이며 변경이 없다.
55. retryable=true, attemptCount<4이며 Subscriber가 ACTIVE인 FAILED Delivery만 같은 Document로 requeue한다.
56. SENT Delivery는 새 Document 생성과 재발송이 모두 불가능하다.

### RFC 8058

57. 정상 path token, form Content-Type과 List-Unsubscribe=One-Click POST가 기존 해지 상태 전이를 수행하고 redirect 없는 204를 반환한다.
58. one-click GET은 mapping 또는 상태 변경이 없다.
59. 잘못된, 오래된 또는 위조 token은 상태를 변경하지 않고 generic 오류다.
60. application/x-www-form-urlencoded와 정확한 form field만 허용하고 JSON 혼합을 거절한다.
61. NEWSLETTER_API_BASE_URL을 사용한 List-Unsubscribe 및 List-Unsubscribe-Post header URL과 값이 정확하다.

### 회귀 및 범위

62. 기존 subscribe와 JSON body unsubscribe API가 정상이다.
63. GET 또는 POST verify API가 없다.
64. NewsletterSubscriberStatus에 PENDING이 없다.
65. /internal/newsletter/** Controller가 없다.
66. SecurityConfig diff가 없다.
67. web.tosunsaeng.domain.comment 전체 test와 고정 API가 정상이다.
68. BlogPost 목록, 상세와 검색 API가 정상이며 newsletterEnabled는 응답에 노출되지 않는다.
69. exams test, application context와 bootJar가 정상이다.

### 추가 안전 test

70. Campaign과 Delivery index 이름, key 순서, unique, idempotent ensureIndex와 오류 전파를 검증한다.
71. providerCallStartedAt 없는 stale Delivery는 PENDING, 있는 stale Delivery는 PROVIDER_RESULT_UNKNOWN, retryable=false 최종 FAILED다.
72. old claimToken worker의 cursor, send start와 terminal update가 모두 거절된다.
73. executor가 worker 2, bounded queue 100, scheduler thread 실행 금지와 graceful shutdown 설정을 가진다.
74. SES SDK 내부 retry가 application attemptCount 밖에서 반복되지 않는다.
75. HTML dynamic value escape와 CR/LF header injection 거절을 검증한다.
76. test 발송이 두 switch와 allowlist를 검증하고 subscriber 조회, token 발급, 해지 link, RFC header와 delivery persistence를 하지 않는다.
77. one-click path token, form body, email 및 token이 Sentry event와 transaction에서 제거된다.
78. public/API base URL, email provider와 설정 validation이 disabled local, logging과 ses 조합별로 fail fast한다.
79. 옛 web.tosunsaeng.domain.blog.comment 및 web.tosunsaeng.domain.blog.newsletter package가 없다.

### 조건부 승인 추가 test

80. kill switch false가 SENDING Campaign과 Delivery의 어떤 field도 강제 변경하지 않는다.
81. provider 호출 전 stale Delivery만 claimToken/claimExpiresAt을 제거하고 PENDING으로 복구한다.
82. provider 호출 후 stale Delivery는 PROVIDER_RESULT_UNKNOWN이며 자동과 수동 retry에서 모두 제외한다.
83. failedCount가 0이면 Campaign SENT, 1 이상이면 FAILED이고 skippedCount만 있는 Campaign은 SENT다.
84. 게시글/수동 해지 URL은 PUBLIC base, RFC endpoint는 API base를 사용한다.
85. one-click response는 HTTP redirect를 반환하지 않는다.
86. GET one-click은 Subscriber 상태를 변경하지 않는다.
87. form field가 누락되거나 잘못되면 해지하지 않는다.
88. one-click이 Phase 05 active key와 previous key token 검증을 모두 유지한다.
89. SES v2 2.29.52 Simple Message model에 custom header가 있고 request mapping이 두 허용 header만 생성한다.
90. Raw MIME type, raw content 또는 자동 fallback 코드가 없다.
91. test recipient allowlist 내 주소만 허용한다.
92. kill switch false 또는 test switch false에서 test provider를 호출하지 않는다.
93. email, token, HTML/plain body와 provider request/response가 로그에 노출되지 않는다.
94. 기존 subscribe와 JSON body unsubscribe API의 request/response 회귀가 없다.
95. comment와 blog package 전체 test가 성공한다.
96. exams, application context와 bootJar가 성공한다.

실행 예정:

- git diff --check
- bash ./gradlew test --tests 'web.tosunsaeng.domain.newsletter.*'
- bash ./gradlew test --tests 'web.tosunsaeng.domain.blog.*'
- bash ./gradlew test --tests 'web.tosunsaeng.domain.comment.*'
- bash ./gradlew clean test bootJar
- rg와 git diff로 verify, PENDING Subscriber, internal Controller, SecurityConfig 변경, raw email 및 token log와 금지 package를 정적 확인

실제 Mongo claim 및 unique index, 실제 SES sandbox 및 production 발송, 실제 Redis, SES bounce와 complaint event, 실제 email client one-click, CloudFront 또는 frontend 해지 화면과 대량 부하 test는 Phase 08로 기록한다.

## 기존 기능 영향

- BlogPost에 내부 newsletterEnabled 필드와 reconciliation query를 추가하지만 기존 공개 DTO, Controller, 공개 Criteria와 URI는 유지한다.
- NewsletterSubscriber field와 상태는 바꾸지 않고 ACTIVE cursor 조회만 확장한다.
- 기존 subscribe와 JSON unsubscribe의 Content-Type, request, response와 tokenVersion 동작을 유지한다.
- NewsletterTelemetryPrivacyConfig는 새 one-click POST만 민감 path에 추가하고 다른 endpoint를 변경하지 않는다.
- existing comment package, 고정 rule, Redis rate limit, moderation와 API를 수정하지 않는다.
- SecurityConfig, GlobalExceptionAdvice, SchedulingConfig, S3Config와 exams business code를 수정하지 않는다.
- AWS SDK 개별 버전 2.29.52를 유지하고 sesv2 추가가 기존 S3 dependency resolution을 바꾸지 않는지 전체 build로 확인한다.

## 위험 요소

- SES SendEmail은 application의 postId, subscriberId를 강한 idempotency key로 중복 제거하지 않는다. provider가 수락한 뒤 응답을 잃은 경계에서는 정확히 한 번을 증명할 수 없어 자동 retry를 포기하는 정책이 필요하다.
- PROVIDER_RESULT_UNKNOWN을 재발송하지 않으면 실제로 보내지지 않은 email이 누락될 수 있다. 반대로 운영자가 강제 재발송하면 중복 위험이 있다.
- BlogPost와 Campaign은 서로 다른 collection이라 newsletterEnabled 변경과 claim을 단일 원자 transaction으로 묶지 않는다. claim 직후와 provider 호출 직전 재확인으로 줄이지만 이미 전송된 email은 취소할 수 없다.
- kill switch 환경변수 변경은 실행 중 JVM에 즉시 반영되지 않을 수 있고 in-flight SES 요청은 회수할 수 없다.
- false에서도 Campaign을 만들므로 오랫동안 disabled이면 due backlog가 생긴다. enable 전 예상 recipient와 SES quota를 운영자가 확인해야 한다.
- cursor 순회 중 새 ACTIVE Subscriber는 cursor 위치에 따라 현재 Campaign에 포함되지 않을 수 있다. 이미 지난 cursor의 신규 구독자는 다음 게시글부터 받는다.
- 기존 Mongo document의 newsletterEnabled 누락은 false라 안전하지만 운영 스크립트가 true를 빼먹으면 발송되지 않는다.
- SDK 2.29.52의 Simple custom header 지원은 확인했지만 실제 SES와 수신 email client에서 header 보존 및 one-click 노출 여부는 Phase 08 통합 검증이 필요하다.
- 개별 버전을 유지해도 sesv2 module 추가가 dependency graph에 영향을 줄 수 있어 전체 regression이 필수다.
- 기존 S3Config는 static credential이고 SES는 default chain이라 운영 credential 전략이 일관되지 않지만 이번 Phase에서 S3를 함께 리팩터링하지 않는다.
- RFC 8058 token은 URL path에 있어 Sentry 외 CDN, proxy와 access log에 노출될 수 있다.
- NEWSLETTER_PUBLIC_BASE_URL과 NEWSLETTER_API_BASE_URL 오설정, HTTPS 미사용 또는 routing 오류면 게시글과 one-click link가 동작하지 않는다.
- SES sandbox, identity, DKIM, IAM과 quota가 준비되지 않으면 code가 맞아도 production send가 실패한다.
- 비동기 bounce와 complaint를 처리하지 않으므로 Phase 06만으로 BOUNCED 상태가 자동 갱신되지 않는다.
- bounded executor queue가 가득 차면 처리 지연이 생기지만 메모리와 thread 폭증보다 안전한 backpressure를 선택한다.

## 롤백 방법

- 구현 전에는 이 계획서와 상태 문서만 사용자 변경과 이전 Session Log를 보존하는 수동 역패치로 되돌린다.
- 구현 후 긴급 중지는 먼저 NEWSLETTER_SENDING_ENABLED=false로 배포하고 모든 instance가 새 설정으로 재시작됐는지 확인한다.
- code rollback은 Phase 06에서 추가한 Scheduler, Service, Sender, Campaign 및 Delivery code와 설정 binding을 후속 patch로 제거하고 BlogPost field 및 Repository 확장을 수동으로 되돌린다.
- 이미 생성된 Campaign, Delivery와 index를 Codex가 삭제하지 않는다. 데이터 보존 상태에서 이전 artifact가 알 수 없는 Mongo field와 collection을 무시할 수 있는지 검토한다.
- Campaign, Delivery 또는 Subscriber를 자동 삭제하거나 providerMessageId를 정리하지 않는다.
- Mongo index drop, 운영 데이터 migration과 실제 email 취소는 별도 사용자 승인 및 운영 절차 없이는 수행하지 않는다.
- git reset, git restore, git checkout, git clean과 사용자 변경을 되돌리는 명령을 사용하지 않는다.

## 완료 조건

- 사용자의 2026-07-31 조건부 승인을 계획과 Session Log에 기록하고 계획 APPROVED, Phase 06 IN_PROGRESS를 반영한다.
- 실제 package는 web.tosunsaeng.domain.blog, web.tosunsaeng.domain.newsletter, web.tosunsaeng.domain.comment만 사용한다.
- BlogPost explicit opt-in과 reconciliation이 미래, 과거 및 직접 DB 작성 글을 조건대로 예약한다.
- postId Campaign unique와 postId, subscriberId Delivery unique가 정의된다.
- Campaign 및 Delivery claim, cancel, stale recovery와 retry가 조건부 원자 update다.
- ACTIVE Subscriber를 cursor batch 100으로 처리하고 발송 직전 상태를 재확인한다.
- kill switch 기본 false에서 provider 호출과 SENT 오기록이 없고 true에서 안전하게 재개한다.
- 최초 발송 plus retry 3회와 5분, 30분, 2시간 정책이 구현된다.
- Logging sender와 SES v2 sender가 property로 격리되고 Application Service가 AWS type에 의존하지 않는다.
- SES는 AWS_REGION과 기본 credential chain을 사용하고 새 access key를 코드 또는 설정에 추가하지 않는다.
- HTML 및 plain text, 게시글 UTM, frontend 해지 link와 RFC 8058 header가 구현된다.
- 별도 one-click POST만 추가하고 GET 상태 변경, verify, internal Controller와 SecurityConfig 변경이 없다.
- 테스트 발송, SCHEDULED 취소와 실패 retry Service가 Controller 없이 준비된다.
- email, token, body, provider request 및 response와 credential이 log 또는 lastErrorCode에 노출되지 않는다.
- 테스트 계획과 관련 test가 모두 성공한다.
- git diff --check와 bash ./gradlew clean test bootJar가 성공한다.
- 실제 Mongo, SES, Redis, email client, frontend와 부하 통합 항목을 Phase 08에 남긴다.
- 실패가 하나라도 있으면 계획을 EXECUTED 또는 Phase 06을 DONE으로 변경하지 않는다.

## 조건부 승인으로 확정된 결정

1. BlogPost에는 primitive boolean newsletterEnabled를 추가하고 Campaign은 별도 Document로 둔다.
2. 기존 필드가 없는 글은 false이며 명시적으로 true인 글만 자동 예약한다.
3. 기본 batch는 100, newsletter executor는 worker 2와 queue 100이다.
4. kill switch false에서도 SCHEDULED Campaign은 만들되 claim, provider와 test send는 막고 기존 SENDING Campaign/Delivery 상태는 변경하지 않는다.
5. retry는 최초 1회 plus 재시도 3회, 총 4회이며 attemptCount는 실제 provider 호출 시작 횟수다.
6. PROVIDER_RESULT_UNKNOWN은 retryable=false로 자동과 수동 retry에서 모두 제외한다.
7. RFC 8058은 기존 JSON endpoint와 섞지 않고 별도 POST /api/newsletter/one-click-unsubscribe/{token}로 Phase 06에 구현한다.
8. NEWSLETTER_PUBLIC_BASE_URL과 NEWSLETTER_API_BASE_URL을 분리하고 운영에서 두 URL 모두 HTTPS를 요구한다.
9. 기존 S3와 같은 개별 버전 2.29.52로 sesv2를 추가하고 BOM migration과 전체 AWS SDK upgrade를 하지 않는다.
10. test send는 master/test switch와 allowlist를 모두 통과해야 하며 subscriber token, 해지 link 및 RFC header를 넣지 않는다.
11. SES custom header가 SDK 2.29.52에서 지원되지 않으면 임의 Raw MIME 구현을 진행하지 않고 중단해 dependency와 전략을 재승인받는다.

## 실제 구현 중 발생한 차이

- AWS 공식 SES v2 service model과 실제 `sesv2-2.29.52` JAR의 `Message.Builder.headers` 및 `MessageHeader.Builder`를 확인해 승인 gate를 통과했다. Simple Message로 두 header를 구현했고 Raw MIME fallback은 추가하지 않았다.
- 예상 파일의 `NewsletterEmailFailureClassifier`는 별도 class로 만들지 않고 SES HTTP status 기반 안전한 분류를 `SesNewsletterEmailSender` 내부에 한정했다. Application Service에는 AWS type이나 provider 세부값이 노출되지 않는다.
- 기존 `NewsletterService`, `NewsletterServiceImpl`, `NewsletterSubscriberRepository`는 수정할 필요가 없었다. one-click은 기존 `NewsletterService.unsubscribe`를 그대로 호출하고 ACTIVE cursor는 기존 custom query interface와 구현만 확장했다.
- 기존 anonymous comment의 `SecureRandom` Bean과 타입 충돌하지 않도록 예상 목록에 없던 `NewsletterClaimTokenGenerator`를 추가했다. 이 component가 자체 `SecureRandom`으로 32 byte URL-safe claim token을 만들며 기존 comment 설정이나 주입 계약은 변경하지 않았다.
- Sentry 7.14의 `SentryTransaction`에는 안전한 transaction name setter가 없어 path token이 있는 one-click transaction은 before-send callback에서 drop한다. event request URL은 `{token}` placeholder로 치환한다.
- test profile에서 실제 Mongo background 작업을 시작하지 않도록 세 Scheduler adapter에 `@Profile("!test")`를 적용했다. Scheduler 자체의 fixed-delay와 executor 위임 계약은 직접 단위 test한다.
- 구현 검토 중 Delivery claim 후 Campaign이 여전히 SENDING인지 provider 전에 재확인하고, 아니면 providerCallStartedAt이 없는 현재 claimToken만 PENDING으로 해제하는 fencing을 명시적으로 보강했다. 이는 승인 계획의 상태 재확인 계약을 구현한 것이다.
- 위 차이는 제품 API, retry 횟수, kill switch, Campaign/Delivery 상태, 개인정보 또는 제외 범위를 변경하지 않는다.

## 검증 결과

- 선행 조건은 `feat/blog-mvp`, Phase 05 `DONE` 및 계획 `EXECUTED`로 통과했다. preflight의 clean-tree 항목만 직전 Codex가 작성한 알려진 Phase 06 문서 두 개 때문에 실패했고 다른 gate는 통과했다.
- SES v2 2.29.52 공식 model과 실제 SDK class의 Simple custom header 지원을 Java 구현 전에 확인했다. 기존 S3와 동일한 개별 버전 `2.29.52`의 `sesv2`만 추가했고 BOM migration이나 AWS SDK upgrade는 없었다.
- `git diff --check`: 성공. 변경 main/test/docs/config의 trailing whitespace 검색 결과가 없었다.
- `bash ./gradlew test --tests 'web.tosunsaeng.domain.newsletter.*'`: `BUILD SUCCESSFUL`, newsletter 149개, failures/errors/skipped 0.
- `bash ./gradlew clean test bootJar`: `BUILD SUCCESSFUL`, 전체 369개, failures/errors/skipped 0, bootJar 생성 성공.
- 전체 회귀 구성은 newsletter 149개, blog 66개, comment 152개, exams 1개와 application context 1개다.
- 첫 전체 build는 기존 `anonymousSecureRandom`과 초기 newsletter `SecureRandom` Bean의 타입 충돌로 context 2건이 실패했다. newsletter 전용 token generator로 Bean 노출을 제거한 뒤 context/exams 선택 test와 필수 전체 build를 다시 실행해 성공했다.
- 구현 중 compile 검증에서 잘못된 `BulkOperationException` package와 Sentry transaction name setter 사용을 각각 수정했다. 이후 `compileJava`와 `compileTestJava`가 성공했다.
- Query BSON 및 mock contract로 Campaign/Delivery atomic claim, old-token fencing, unique upsert key, retry/stale/completion 상태와 programmatic index 정의를 검증했다. 실제 Mongo 동시성 및 unique 경쟁은 승인대로 실행하지 않았다.
- SES request mapping test는 Simple HTML/plain body, 두 custom header, providerMessageId, retry 0, 오류 분류와 민감 provider message 비노출을 검증했다. 실제 SES 호출은 실행하지 않았다.
- MockMvc는 기존 subscribe/JSON unsubscribe 회귀, 별도 form one-click POST, multipart, 잘못된 field/token/Content-Type, redirect 부재와 GET 상태 불변을 검증했다.
- 정적 review에서 verify API, Subscriber PENDING, `/internal/newsletter/**`, SecurityConfig 변경, Raw MIME, 옛 blog.comment/blog.newsletter package, AWS key 추가, 전체 email/token/body/provider payload log가 없음을 확인했다.
- kill switch 기본 false와 SENDING 무변경, stale provider 경계, `PROVIDER_RESULT_UNKNOWN` 자동·수동 retry 제외, public/API base URL 분리, test send allowlist와 dual switch를 단위 test로 확인했다.
- 실제 MongoDB claim/index, 실제 SES sandbox/production, 실제 Redis, bounce/complaint event, 실제 email client one-click, frontend/CloudFront 및 path token access log, DKIM header 서명과 대량 부하는 Phase 08 과제로 유지한다.
- 모든 완료 조건을 충족해 계획을 `EXECUTED`, Phase 06을 `DONE`, Current phase를 Phase 07로 전환한다. Phase 07은 `TODO`로 유지한다.
