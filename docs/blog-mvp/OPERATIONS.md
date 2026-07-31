# Blog MVP 운영 가이드

이 문서는 운영 담당자가 저장소 밖의 환경을 확인하고 승인된 내부 API를 사용하는 절차다. Codex나 애플리케이션이 운영 AWS 자원을 만들거나 실제 수신자에게 임의 발송하는 절차가 아니다.

## 역할

| 역할 | 책임 |
|---|---|
| 애플리케이션 운영자 | 게시글 등록, Campaign/Delivery 점검, 내부 API 사용, kill switch |
| 인프라 담당자 | MongoDB/Redis, ALB·Security Group·방화벽, access log |
| 메일 운영자 | SES identity·DKIM·quota, 통제된 test email, Gmail 확인 |
| 프론트 담당자 | 정적 avatar 파일과 frontend 구독 해지 확인 화면 |
| 보안/관측 담당자 | API Key rotation, Sentry·log redaction 증적 |

## MongoDB 게시글 등록

게시글 작성 API는 없다. 권한이 제한된 운영 도구로 `blog_posts`에 직접 등록한다.

필드:

- 필수 운영 값: `slug`, `title`, `summary`, `contentMarkdown`, `authorName`, `status`, `createdAt`, `updatedAt`
- 공개에 필요: `status=PUBLISHED`, `publishedAt`이 null이 아니고 현재 이하
- newsletter opt-in: `newsletterEnabled=true`인 경우에만 발송 후보
- 선택: `thumbnailUrl`, `seoTitle`, `seoDescription`, `relatedPostSlugs`
- `relatedPostSlugs`는 빈 배열을 사용할 수 있다.

등록 전 확인:

1. slug는 비어 있지 않고 `search`가 아니어야 한다.
2. `uk_blog_posts_slug` unique index와 충돌하지 않아야 한다.
3. `publishedAt`, `createdAt`, `updatedAt`은 UTC ISO-8601로 준비한다.
4. DRAFT 또는 ARCHIVED는 공개되지 않는다.
5. 기존 Document에 `newsletterEnabled`가 없으면 false로 취급한다.
6. 발송을 원하지 않으면 false를 명시한다.
7. 관련 slug가 실제 공개 글인지 확인한다.

개념 예시이며 운영 URI나 credential을 문서/명령 이력에 넣지 않는다.

```javascript
db.blog_posts.insertOne({
  slug: "sample-post",
  title: "제목",
  summary: "요약",
  contentMarkdown: "본문",
  authorName: "토선생",
  status: "PUBLISHED",
  relatedPostSlugs: [],
  publishedAt: ISODate("2026-07-31T00:00:00Z"),
  newsletterEnabled: false,
  createdAt: ISODate("2026-07-31T00:00:00Z"),
  updatedAt: ISODate("2026-07-31T00:00:00Z")
})
```

등록 후 `GET /api/posts/{slug}`와 목록을 smoke test한다. 공개 시각이 미래라면 그 시각 전 404는 정상이다.

## Newsletter Campaign 운영

### 생성 조건

60초 기본 reconciliation 주기로 다음 조건을 만족하는 글에 Campaign을 만든다.

- `PUBLISHED`
- `publishedAt != null`
- `newsletterEnabled=true`
- 해당 `postId` Campaign 없음

예약 시각은 `publishedAt + 15분`이며 이미 지났으면 reconciliation 시각에 즉시 예약한다. 하나의 postId에는 unique Campaign 하나만 존재한다.

### 상태 해석

| 상태 | 의미 | 운영 조치 |
|---|---|---|
| SCHEDULED | 예약됨 | 필요 시 내부 cancel API |
| SENDING | Delivery 생성·발송·집계 진행 | claim 만료와 worker 상태 확인 |
| SENT | 모든 recipient가 SENT/SKIPPED, terminal failure 없음 | 완료 |
| FAILED | terminal failure 존재 | 원인 코드 검토 후 승인된 retry API |
| CANCELED | 발송 시작 전 취소 | 자동 재개 안 됨 |

Campaign의 `totalRecipients`, `sentCount`, `failedCount`, `skippedCount`, `lastErrorCode`, `updatedAt`을 확인한다. Delivery에는 email 원문 대신 subscriberId가 있으며 `attemptCount`, `nextRetryAt`, `providerCallStartedAt`, `lastErrorCode`, `retryable`을 확인한다.

### kill switch

`NEWSLETTER_SENDING_ENABLED=false`가 기본이다.

- false에서도 reconciliation은 SCHEDULED Campaign을 만들 수 있다.
- Campaign/Delivery claim, retry, provider 호출, test send는 진행하지 않는다.
- 환경변수 변경은 property refresh가 없으므로 재시작/재배포가 필요하다.
- false 전환이 이미 SENDING인 Document를 강제로 변경하지 않는다.
- true로 복귀하면 기존 SCHEDULED/PENDING backlog를 Scheduler가 처리한다.

장애 시 먼저 false로 배포하고, in-flight Delivery와 `providerCallStartedAt`을 확인한 뒤 원인을 조사한다.

## 내부 API 사용

내부 API는 public internet에 직접 노출하지 않는다. `INTERNAL_API_ENABLED=true`, 32바이트 이상 key, ALB/SG/firewall source 제한을 모두 준비한다. 명령 이력에 key가 남지 않도록 안전한 secret 주입 방식을 사용한다.

### 뉴스레터 test send

```http
POST /internal/newsletter/posts/{postId}/test
X-Internal-Api-Key: <secret>
Content-Type: application/json

{"email":"approved-recipient@example.test"}
```

선행 조건:

1. master switch true
2. `NEWSLETTER_TEST_SENDING_ENABLED=true`
3. 수신 주소가 `NEWSLETTER_TEST_RECIPIENT_ALLOWLIST`에 포함
4. provider/From/SES 설정 검증

Campaign과 Delivery는 생성되지 않고 실제 unsubscribe token도 만들지 않는다. 응답의 `postId`와 status, provider의 마스킹된 message ID 증적만 보관한다. 전체 email과 provider body를 로그에 남기지 않는다.

### 예약 취소

```http
POST /internal/newsletter/posts/{postId}/cancel
```

SCHEDULED만 CANCELED로 바뀐다. SENDING 이후에는 409이며 운영자가 Document를 직접 강제 변경하지 않는다. 동시 요청 중 조건부 update 한 건만 성공한다.

### 실패 재시도 등록

```http
POST /internal/newsletter/posts/{postId}/retry
```

- 한 요청은 최대 100개를 처리한다.
- `hasMore=true`면 같은 API를 다시 호출한다.
- ACTIVE Subscriber의 eligible FAILED만 기존 Delivery를 PENDING으로 되돌린다.
- 비활성 Subscriber는 SKIPPED로 기록한다.
- provider 호출은 HTTP 요청이 아니라 Scheduler가 수행한다.
- 처리 대상이 전혀 없으면 409다.

`PROVIDER_RESULT_UNKNOWN`, `retryable=false`, provider 호출 4회 도달, SENT/SKIPPED는 재시도하지 않는다.

## 댓글 운영

### 목록

`GET /internal/comments`에서 status, postId, slug, `[createdAtFrom, createdAtTo)`, page, size를 사용한다. 긴 기간 전체 조회 대신 필요한 기간과 최대 size 100을 사용한다.

응답의 댓글 content는 운영 판단용 개인정보일 수 있다. 별도 파일이나 ticket에 원문을 복제하지 않는다.

### 숨김과 복원

- VISIBLE/PENDING -> HIDDEN: `PATCH /internal/comments/{id}/hide`
- HIDDEN -> VISIBLE: `PATCH /internal/comments/{id}/restore`
- 숨김 사유: SPAM, ABUSE, ADVERTISEMENT, PERSONAL_INFORMATION, OTHER
- 잘못된 상태는 409다.
- 실제 삭제, public PATCH/DELETE, internal DELETE는 제공하지 않는다.

## provider 실패와 재시도

application provider 호출은 최초 1회와 최대 3회 재시도, 총 4회다.

| 실패한 호출 횟수 | 다음 자동 재시도 |
|---:|---|
| 1 | 5분 후 |
| 2 | 30분 후 |
| 3 | 2시간 후 |
| 4 | 자동 재시도 없음 |

### PROVIDER_RESULT_UNKNOWN

provider 호출 시작 후 응답을 저장하기 전에 claim이 stale이 되면 실제 수락 여부를 알 수 없다.

1. 자동·수동 재발송하지 않는다.
2. Delivery ID, postId, provider 시작 시각, 안전한 error code만 수집한다.
3. SES event/message ID와 수신 증적을 운영자가 대조한다.
4. 수락 여부가 불명확하면 중복 위험을 우선해 발송하지 않는다.
5. 상태 강제 변경은 별도 incident 승인과 감사 기록 없이는 하지 않는다.

SES는 application idempotency key를 제공하지 않아 exactly-once를 보장하지 않는다.

## SES 장애 대응

1. `NEWSLETTER_SENDING_ENABLED=false`로 신규 provider 호출을 차단한다.
2. SES region, identity, sandbox, quota, IAM Role, From 설정을 확인한다.
3. throttling/identity/auth configuration 등 정제된 error code와 CloudWatch/SES 지표를 대조한다.
4. provider request/response 원문이나 credential을 ticket/log에 붙이지 않는다.
5. 설정 수정 후 allowlist test send 한 건을 운영 담당자가 수행한다.
6. DKIM과 두 List-Unsubscribe header를 확인한 뒤 switch를 복구한다.

SES bounce/complaint webhook은 현재 없다. BOUNCED 자동 갱신이 보장되지 않으며 별도 후속 기능이다.

## Redis 장애 대응

- 지원 topology: standalone 또는 single-primary
- 미지원: Redis Cluster
- 이유: 여러 key를 한 Lua에서 처리해 Cluster에서 `CROSSSLOT` 가능

댓글과 newsletter 구독 rate limit은 명확한 Redis 연결/timeout 장애에서 fail-open한다. 이때 핵심 API는 계속 동작하지만 남용 방어가 약해진다.

1. Redis health, memory, eviction, latency와 연결 수를 확인한다.
2. fail-open warning count를 관측한다. IP 원문과 Redis key 원문은 기록하지 않는다.
3. 외부 WAF를 새로 임의 적용하지 말고 기존 인프라 제한을 강화할지 incident에서 결정한다.
4. 복구 후 Lua/TTL smoke와 rate-limit metric을 확인한다.
5. Cluster로 전환하지 않는다. 전환은 key hash tag와 원자성 재설계가 선행돼야 한다.

## API Key rotation

MVP는 단일 active key만 지원하며 무중단 dual-key grace period가 없다.

1. `INTERNAL_API_ENABLED=false`로 배포하거나 짧은 점검 시간을 공지한다.
2. `/internal/**`가 key가 있어도 404인지 확인한다.
3. secret manager에 32바이트 이상 새 key를 배치하고 애플리케이션을 재시작한다.
4. 내부 운영 클라이언트의 key를 안전하게 교체한다.
5. `INTERNAL_API_ENABLED=true`로 배포한다.
6. key 없음/오류 401, 정상 key 성공, JWT만 401을 smoke test한다.
7. 이전 key를 폐기하고 Sentry/access log에 원문이 없는지 sentinel로 확인한다.

key를 shell history, URL, ticket, chat, application principal에 넣지 않는다.

## 정적 avatar 운영

MVP 제공 경로는 프론트 정적 파일이다.

```text
base URL: https://to-teacher.com
object path: /character-image/{filename}
backend imageKey: character-image/{filename}
```

프론트 담당자는 배포 환경의 `avatar-options` manifest를 유지한다. 저장소 test fixture filename을 운영 값으로 복사하지 않는다.

각 option별 확인:

- HTTPS 200
- 올바른 image MIME type
- 장기 Cache-Control 및 immutable/version 정책
- `character-image/` 아래 상대 경로
- query, fragment, `..`, absolute URL 없음
- 404 fallback 동작
- 이미 댓글 snapshot에 사용한 key 삭제·덮어쓰기 금지
- 변경 이미지는 새 version filename 사용

CloudFront 전환은 후속 인프라 개선이며 백엔드가 S3/CloudFront API를 호출하도록 바꾸지 않는다.

## 장애 기록 원칙

기록 가능:

- resource ID
- 상태 전이
- 정제된 error code
- 처리 건수와 시각
- 마스킹된 provider message ID

기록 금지:

- Internal API Key와 digest
- 전체 email, 댓글 content 복제본, IP 원문
- anon_session, unsubscribe/one-click token
- HMAC/JWT/AWS secret
- provider request/response 전체
