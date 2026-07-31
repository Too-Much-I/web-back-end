# Blog MVP API

- 기준: Phase 08 구현 코드
- 응답 시각 형식: UTC 기준 ISO-8601 `Instant`
- 공개 API는 현재 Security 정책상 JWT나 내부 API Key 없이 접근할 수 있다.
- 내부 API는 Swagger/OpenAPI 문서에서 숨겨져 있으며 `X-Internal-Api-Key` 인증을 사용한다.

## 공통 응답

대부분의 JSON 응답은 다음 구조다.

```json
{
  "isSuccess": true,
  "code": "BLOG_200",
  "message": "게시글 목록을 조회했습니다.",
  "result": {}
}
```

실패 시 `isSuccess=false`이며 민감한 예외 원문, stack trace, provider 응답은 반환하지 않는다. `result`가 `null`이면 JSON에서 생략될 수 있다.

```json
{
  "isSuccess": false,
  "code": "COMMON400",
  "message": "잘못된 요청입니다."
}
```

## 공개 게시글 API

공개 게시글은 `status=PUBLISHED`, `publishedAt != null`, `publishedAt <= 현재 시각`을 모두 만족한다. 목록과 검색은 `publishedAt`, `createdAt`, `_id` 내림차순으로 안정 정렬된다.

### 게시글 목록

```http
GET /api/posts?page=0&size=10
```

- `page`: 기본 0, 0 이상
- `size`: 기본 10, 1~100
- 성공: HTTP 200, code `BLOG_200`

`result`:

```json
{
  "posts": [
    {
      "slug": "sample-post",
      "title": "제목",
      "summary": "요약",
      "thumbnailUrl": "https://static.example.test/thumbnail.webp",
      "authorName": "토선생",
      "publishedAt": "2026-07-31T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalPages": 1,
  "totalElements": 1,
  "hasNext": false
}
```

### 게시글 제목 검색

```http
GET /api/posts/search?q=검색어&page=0&size=10
```

- `q`: trim 후 2~50자
- 정규식 메타 문자는 검색 문법이 아니라 literal 문자로 처리한다.
- pagination 규칙은 목록과 같다.
- 성공: HTTP 200, code `BLOG_202`
- `result`: `query`, `posts`, `page`, `size`, `totalPages`, `totalElements`, `hasNext`

### 게시글 상세

```http
GET /api/posts/{slug}
```

- 성공: HTTP 200, code `BLOG_201`
- 없는 글 또는 비공개 글: HTTP 404, code `BLOG_4004`
- `result`: `slug`, `title`, `summary`, `contentMarkdown`, `thumbnailUrl`, `authorName`, `seoTitle`, `seoDescription`, `publishedAt`, `updatedAt`, `relatedPosts`
- 각 관련 글은 `slug`, `title`, `summary`, `thumbnailUrl`, `publishedAt`만 포함한다.

### 게시글 오류

| HTTP | code | 의미 |
|---|---|---|
| 400 | `BLOG_4001` | 검색어가 너무 짧음 |
| 400 | `BLOG_4002` | 검색어가 너무 김 |
| 400 | `BLOG_4003` | page가 음수 |
| 400 | `BLOG_4005` | size가 1 미만 |
| 400 | `BLOG_4006` | size가 100 초과 |
| 404 | `BLOG_4004` | 공개 게시글 없음 |

## 공개 댓글 API

### 댓글 목록

```http
GET /api/posts/{slug}/comments?page=0&size=20
```

- `page`: 기본 0, 0 이상
- `size`: 기본 20, 1~100
- VISIBLE 댓글만 `createdAt`, `_id` 내림차순으로 반환한다.
- 성공: HTTP 200, code `COMMENT_200`
- 각 댓글: `id`, `nickname`, `avatarSeed`, `avatarImageUrl`, `content`, `createdAt`
- 페이지 정보: `page`, `size`, `totalPages`, `totalElements`, `hasNext`

### 익명 댓글 작성

```http
POST /api/posts/{slug}/comments
Content-Type: application/json

{
  "content": "댓글 내용",
  "website": ""
}
```

- `content`는 JSON 문자열이어야 한다.
- `website`는 bot 탐지용 honeypot이다. 정상 클라이언트는 빈 문자열 또는 누락 값을 사용한다.
- 새 방문자는 `anon_session` HttpOnly cookie를 받을 수 있다.
- 성공: HTTP 201, code `COMMENT_201`
- 성공 `result`: `id`, `nickname`, `avatarSeed`, `avatarImageUrl`, `content`, `createdAt`
- honeypot 값이 있으면 저장하지 않고 HTTP 202, code `COMMENT_203`, result 없음으로 일반 응답한다.

### 익명 프로필 재생성

```http
POST /api/comments/nickname/regenerate
```

- `anon_session`이 없거나 유효하지 않으면 새 익명 세션을 만들 수 있다.
- 성공: HTTP 200, code `COMMENT_202`
- `result`: `nickname`, `avatarSeed`, `avatarImageUrl`

### 댓글 규칙 1~10

규칙 번호와 순서는 고정이다. rule 3은 trim 정규화이며 violation으로 반환하지 않는다. 여러 위반은 `ruleNumber` 오름차순이다.

| 번호 | ruleCode | 계약 |
|---:|---|---|
| 1 | `COMMENT_MIN_LENGTH` | trim 후 Unicode code point 2자 이상 |
| 2 | `COMMENT_MAX_LENGTH` | Unicode code point 500자 이하 |
| 3 | `COMMENT_TRIM` | 앞뒤 공백 제거, violation 미반환 |
| 4 | `COMMENT_PLAIN_TEXT_ONLY` | JSON 문자열 일반 텍스트만 허용 |
| 5 | `COMMENT_HTML_NOT_ALLOWED` | HTML 차단 |
| 6 | `COMMENT_MARKDOWN_NOT_ALLOWED` | Markdown 차단 |
| 7 | `COMMENT_URL_NOT_ALLOWED` | URL과 이메일 주소 차단 |
| 8 | `COMMENT_EMPTY` | 빈 내용 차단 |
| 9 | `COMMENT_SPAM_PATTERN` | 과도한 문자·구문 반복 차단 |
| 10 | `COMMENT_POST_NOT_PUBLIC` | 공개 게시글이 아니면 차단 |

검증 실패는 HTTP 400, code `COMMENT_4001`이다.

```json
{
  "isSuccess": false,
  "code": "COMMENT_4001",
  "message": "댓글 작성 규칙을 확인해 주세요.",
  "result": {
    "violations": [
      {
        "ruleNumber": 1,
        "ruleCode": "COMMENT_MIN_LENGTH",
        "message": "댓글은 최소 2자 이상 입력해 주세요."
      }
    ]
  }
}
```

### 댓글 rate limit

댓글 제한은 visitor 10초/10분/하루, IP 10분/하루와 동일 내용 reservation을 함께 판정한다. 제한 시 HTTP 429, code `COMMENT_4290`, `Retry-After` header를 반환한다.

```json
{
  "isSuccess": false,
  "code": "COMMENT_4290",
  "message": "댓글 작성 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
  "result": {
    "retryAfterSeconds": 10,
    "limitScope": "VISITOR_SHORT"
  }
}
```

가능한 `limitScope`: `DUPLICATE`, `VISITOR_SHORT`, `VISITOR_MEDIUM`, `VISITOR_DAILY`, `IP_MEDIUM`, `IP_DAILY`.

## 공개 뉴스레터 API

### 구독

```http
POST /api/newsletter/subscribe
Content-Type: application/json

{
  "email": "reader@example.test",
  "consent": true
}
```

- email은 trim 및 lowercase 정규화 후 최대 254자로 검증한다.
- 유효한 email과 동의는 인증 과정 없이 즉시 `ACTIVE`다.
- 이미 ACTIVE면 멱등 성공, UNSUBSCRIBED면 tokenVersion을 올려 ACTIVE로 전환한다.
- BOUNCED 재구독은 HTTP 409, code `NEWSLETTER_4091`이다.
- 성공: HTTP 200, code `NEWSLETTER_200`, `result.status="ACTIVE"`

구독 오류:

| HTTP | code | 의미 |
|---|---|---|
| 400 | `NEWSLETTER_4001` | email 누락 |
| 400 | `NEWSLETTER_4002` | email 형식 오류 |
| 400 | `NEWSLETTER_4003` | email 254자 초과 |
| 400 | `NEWSLETTER_4004` | 동의 누락 또는 false |
| 409 | `NEWSLETTER_4091` | 현재 상태에서 구독 불가 |
| 429 | `NEWSLETTER_4290` | IP rate limit |

rate limit 응답에는 `Retry-After` header와 `result.retryAfterSeconds`가 있다. 기본 제한은 IP당 10분 10회, 하루 30회다. Redis 연결 장애에는 fail-open하지만 서버 오류 응답을 위조하지 않는다.

### JSON 구독 해지

```http
POST /api/newsletter/unsubscribe
Content-Type: application/json

{
  "token": "<opaque-token>"
}
```

- token은 email 링크가 제공한 서명 token이다.
- 성공 또는 이미 해지됨: HTTP 200, code `NEWSLETTER_201`, `result.status="UNSUBSCRIBED"`
- 잘못된 token: HTTP 400, code `NEWSLETTER_4005`
- 모든 응답은 `Cache-Control: no-store`다.
- token을 query parameter로 받는 fallback은 없다.

### RFC 8058 one-click 해지

```http
POST /api/newsletter/one-click-unsubscribe/{token}
Content-Type: application/x-www-form-urlencoded

List-Unsubscribe=One-Click
```

- `multipart/form-data`도 허용한다.
- 정확한 form field/value만 상태를 변경한다.
- 성공: HTTP 204, body와 redirect 없음, `Cache-Control: no-store`
- 잘못된 field 또는 token: HTTP 400, code `NEWSLETTER_4005`
- GET은 상태를 변경하지 않으며 handler가 없다.
- cookie, 로그인, JWT가 필요하지 않다.
- active 및 설정된 previous signing key와 현재 Subscriber tokenVersion을 검증한다.

## 내부 API 인증

모든 내부 요청은 다음 header를 사용한다.

```http
X-Internal-Api-Key: <key>
```

- `INTERNAL_API_ENABLED=false`: key 유무와 관계없이 HTTP 404, code `COMMON404`
- enabled + header 누락/오류: HTTP 401, code `INTERNAL_4010`
- enabled + 정상 key: `ROLE_INTERNAL`로 요청 진행
- JWT만으로 내부 API에 접근할 수 없다.
- API Key 원문과 digest는 principal, 응답, 로그에 들어가지 않는다.

## 내부 댓글 API

### 운영 댓글 목록

```http
GET /internal/comments?status=VISIBLE&postId=<id>&slug=<slug>&createdAtFrom=<instant>&createdAtTo=<instant>&page=0&size=20
```

- 모든 filter는 선택이다.
- `status`: `VISIBLE`, `PENDING`, `HIDDEN`
- 기간: `createdAt >= createdAtFrom`, `createdAt < createdAtTo`
- from이 to와 같거나 이후면 400이다.
- 기본 page 0, 기본 size 20, 최대 size 100
- 성공: HTTP 200, code `INTERNAL_COMMENT_200`
- 각 항목: `commentId`, `postId`, `postSlug`, `nickname`, `avatarImageUrl`, `content`, `status`, `hiddenReason`, `createdAt`, `hiddenAt`
- 익명 token/hash, IP, Redis key, content hash와 rate-limit owner는 반환하지 않는다.

### 댓글 숨김

```http
PATCH /internal/comments/{commentId}/hide
Content-Type: application/json

{
  "reason": "SPAM"
}
```

- reason: `SPAM`, `ABUSE`, `ADVERTISEMENT`, `PERSONAL_INFORMATION`, `OTHER`
- 허용 전이: VISIBLE/PENDING -> HIDDEN
- 성공: HTTP 200, code `INTERNAL_COMMENT_201`
- `result`: `id`, `status`, `hiddenReason`, `hiddenAt`

### 댓글 복원

```http
PATCH /internal/comments/{commentId}/restore
```

- 허용 전이: HIDDEN -> VISIBLE
- 성공: HTTP 200, code `INTERNAL_COMMENT_202`
- `hiddenReason`과 `hiddenAt`은 null이다.

댓글 운영 오류:

| HTTP | code | 의미 |
|---|---|---|
| 400 | `COMMON400` 또는 `COMMENT_4002` | query/JSON/기간/page/size 오류 |
| 400 | `COMMENT_4003` | 숨김 사유 누락 |
| 404 | `COMMENT_4004` | 댓글 없음 |
| 409 | `COMMENT_4091` | 허용되지 않은 상태 전이 |

## 내부 뉴스레터 API

### 테스트 발송

```http
POST /internal/newsletter/posts/{postId}/test
Content-Type: application/json

{
  "email": "operator-test@example.test"
}
```

- `NEWSLETTER_SENDING_ENABLED=true`, `NEWSLETTER_TEST_SENDING_ENABLED=true`, allowlist 포함이 모두 필요하다.
- Subscriber 등록 여부와 무관하다.
- Campaign/Delivery를 만들거나 변경하지 않고 실제 구독 해지 token도 만들지 않는다.
- 성공: HTTP 200, code `INTERNAL_NEWSLETTER_200`, `result={"postId":"...","status":"SENT"}`
- 응답과 일반 로그에 email 전체를 포함하지 않는다.

### 예약 취소

```http
POST /internal/newsletter/posts/{postId}/cancel
```

- 허용 전이: SCHEDULED -> CANCELED
- 성공: HTTP 200, code `INTERNAL_NEWSLETTER_201`, status `CANCELED`
- Campaign 없음: 404
- SENDING/SENT/FAILED/CANCELED: 409

### 실패 Delivery 재시도 등록

```http
POST /internal/newsletter/posts/{postId}/retry
```

- 요청당 최대 100개 기존 FAILED Delivery만 처리한다.
- `retryable=true`, provider 호출 4회 미만, `PROVIDER_RESULT_UNKNOWN` 아님, 현재 ACTIVE Subscriber만 PENDING으로 되돌린다.
- 비활성/누락 Subscriber의 후보는 SKIPPED로 바꾼다.
- 새 Delivery를 만들지 않고 HTTP 요청 중 provider를 호출하지 않는다.
- 성공: HTTP 200, code `INTERNAL_NEWSLETTER_202`

```json
{
  "postId": "post-id",
  "retriedCount": 100,
  "skippedCount": 0,
  "hasMore": true
}
```

newsletter 운영 오류:

| HTTP | code | 의미 |
|---|---|---|
| 400 | `COMMON400`/newsletter 400 code | JSON 또는 email 형식 오류 |
| 403 | `NEWSLETTER_4030` | test recipient가 allowlist 밖 |
| 404 | `NEWSLETTER_4040` | post 또는 Campaign 없음 |
| 409 | `NEWSLETTER_4092` | 취소/재시도 상태 충돌 또는 처리 대상 없음 |
| 409 | `NEWSLETTER_4093` | master/test switch 비활성 |
| 502 | `NEWSLETTER_5020` | test provider 요청 실패 |

## 기존 exams 계약

Phase 08에서 다음 기존 계약을 변경하지 않는다.

```http
GET /api/v1/exams/{examId}/summary
GET /api/v1/exams/{examId}/questions?questionNumber=<number>&retryCount=0
```

- `questions`는 `questionNumber` 필수, `retryCount` 기본 0인 문항 단건 조회다.
- 저장소에 존재하는 다른 exams 세션·업로드·callback API도 Phase 08에서 삭제하거나 변경하지 않는다.

## 제공하지 않는 API와 기능

다음 mapping은 존재하지 않는다.

```http
POST   /api/posts
PUT    /api/posts/{slug}
PATCH  /api/posts/{slug}
DELETE /api/posts/{slug}

PATCH  /api/comments/{commentId}
DELETE /api/comments/{commentId}
DELETE /internal/comments/{commentId}

GET  /api/newsletter/verify
POST /api/newsletter/verify
GET  /api/newsletter/unsubscribe
```

게시글 관리자 UI, 댓글 수정·삭제, 이메일 인증, verify 흐름, NewsletterSubscriber `PENDING` 상태도 제공하지 않는다.
