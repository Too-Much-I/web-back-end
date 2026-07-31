# Blog MVP 보안 설계와 알려진 위험

## 범위

이 문서는 blog, 익명 comment, newsletter와 `/internal/**` 경계의 현재 보안 계약을 설명한다. 운영 네트워크, AWS와 Sentry 실제 설정은 저장소 밖 수동 검증 대상이다.

## secret 분리

다음 값은 서로 다른 secret을 사용하고 저장소에 하드코딩하지 않는다.

- `JWT_SECRET_KEY`
- `INTERNAL_API_KEY`
- `BLOG_ANONYMOUS_TOKEN_SECRET`
- `BLOG_COMMENT_RATE_LIMIT_SECRET`
- `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_SECRET`
- active/previous `NEWSLETTER_UNSUBSCRIBE_TOKEN_*` secret
- AWS credential

JWT, 내부 key와 HMAC secret은 UTF-8 기준 최소 32바이트다. JWT는 운영 fallback이 없으므로 누락, blank 또는 짧은 값이면 시작에 실패한다. test profile만 명시적인 비운영 dummy를 사용한다.

secret은 환경별 secret manager 또는 배포 시스템에서 주입한다. application log, exception message, Sentry, shell history, ticket, URL과 MongoDB Document에 넣지 않는다.

## 익명 댓글 token

- 브라우저에는 `anon_session` raw token을 HttpOnly, SameSite=Lax cookie로 둔다.
- MongoDB에는 raw token 대신 HMAC-SHA256 `tokenHash`만 저장한다.
- cookie Secure 여부와 만료일은 환경 설정이며 운영은 Secure=true다.
- HMAC secret rotation 시 기존 cookie와 방문자 연결은 끊길 수 있지만 이미 작성한 댓글 snapshot은 유지된다.
- 댓글에는 nickname, avatarSeed와 avatarImageKey snapshot을 저장해 이후 프로필 변경이 과거 댓글을 바꾸지 않는다.

SameSite=Lax는 frontend와 API가 서로 다른 site이면 cookie 전달을 보장하지 않는다. 실제 배포 domain을 smoke test해야 한다.

## 댓글 입력과 남용 방지

- 일반 텍스트만 허용하고 HTML, Markdown, URL/email, spam 반복을 차단한다.
- rule 1~10을 고정하고 rule 3 trim은 violation으로 노출하지 않는다.
- visitor/IP/content는 별도 HMAC domain으로 hash하고 raw IP/content를 Redis key에 넣지 않는다.
- 여러 fixed-window counter와 duplicate reservation을 단일 Lua로 판정한다.
- Mongo 저장 실패 시 owner token이 일치하는 duplicate reservation만 삭제하며 counter는 rollback하지 않는다.
- `getRemoteAddr()`만 사용하며 임의 `X-Forwarded-For`를 애플리케이션이 직접 신뢰하지 않는다.

fixed window 경계 burst는 가능하다. Redis가 연결/timeout 장애면 fail-open하므로 가용성은 유지하지만 abuse 방어가 약해진다.

## Newsletter 구독과 token

- 이메일 인증과 `PENDING` Subscriber는 없다. 유효한 동의는 즉시 ACTIVE다.
- email은 정규화하고 MongoDB unique index로 중복을 막는다.
- unsubscribe token은 email을 넣지 않는 서명 bearer token이며 MongoDB에 원문/hash를 저장하지 않는다.
- payload는 keyId, subscriberId, tokenVersion, issuedAt 등 검증에 필요한 식별 정보만 담는다.
- active signing key와 previous verification key를 지원한다.
- 재구독은 tokenVersion을 증가시켜 이전 token이 새 ACTIVE 상태를 해지하지 못하게 한다.
- JSON unsubscribe는 body token만 허용하며 query fallback과 GET endpoint가 없다.

frontend 확인 링크의 query token은 browser history, Referer와 CDN log 위험이 있다. frontend는 `Referrer-Policy: no-referrer`, third-party resource 최소화, query redaction과 사용자 확인 전 POST 금지를 적용해야 한다.

## RFC 8058 path token

one-click 계약은 다음과 같아 path에 bearer token이 있다.

```http
POST /api/newsletter/one-click-unsubscribe/{token}
```

- 정확한 form `List-Unsubscribe=One-Click`만 처리한다.
- GET은 상태를 바꾸지 않는다.
- 성공은 redirect 없는 204와 `Cache-Control: no-store`다.
- application Sentry event URL은 `{token}`으로 치환하고 해당 transaction은 drop한다.
- exception 응답과 application log에 token을 넣지 않는다.

reverse proxy, ALB, CDN access log는 application callback만으로 통제할 수 없다. 해당 path를 마스킹하거나 access log에서 제외하고 보존 기간과 접근 권한을 최소화해야 한다. token을 POST body로 옮기는 API 변경은 RFC email client 호환성 검토와 별도 승인 없이는 수행하지 않는다.

## 내부 API Key 인증

- `/internal/**`는 우선순위 `@Order(1)` 전용 stateless SecurityFilterChain이다.
- 기존 public/JWT chain은 `@Order(2)`이며 정책을 변경하지 않는다.
- disabled면 key가 있어도 404다.
- enabled에서 key 누락/오류는 같은 401 응답이다.
- JWT만으로 내부 API에 접근할 수 없다.
- 성공 Authentication은 fixed non-sensitive principal, null credentials, `ROLE_INTERNAL`만 가진다.
- key 원문을 `String.equals`로만 비교하지 않는다.
- configured/request key를 SHA-256 digest로 만든 뒤 `MessageDigest.isEqual`로 비교한다.
- 비정상적으로 긴 header와 중복 header를 거절한다.

MVP는 single static key다. 운영자별 식별, RBAC, dual-key grace period와 무중단 rotation이 없다. ALB·Security Group·firewall로 접근 source를 제한하고, key 교체 시 짧은 중단 절차를 사용한다. 별도 Redis rate limit은 없다.

## JWT

- 운영 `JWT_SECRET_KEY`는 외부 주입이고 UTF-8 32바이트 이상이다.
- hardcoded fallback은 없다.
- 누락/blank/짧은 secret은 fail-fast다.
- Phase 08은 JWT token/API 계약을 변경하지 않는다.

현재 public API chain은 기존 요구대로 permitAll이다. 내부 API 보호와 혼동하지 않는다.

## 개인정보와 logging

응답·일반 log·Sentry에 포함하지 않는다.

- `X-Internal-Api-Key`, Authorization, Cookie
- anon_session, unsubscribe token, one-click token
- email 전체, 댓글 content 전체, IP 원문
- HMAC/JWT/AWS secret
- provider request/response 전체
- Mongo/Redis/AWS raw exception message와 stack trace

허용하는 운영 log는 resource ID, 상태 전이, 정제된 error code, 처리 건수와 provider 종류다.

`GlobalExceptionAdvice`는 예상하지 못한 예외와 JSON parsing 오류에 raw message를 반환하지 않는다. 명시적인 도메인 `ErrorStatus` 메시지는 유지한다.

Sentry 설정:

- `send-default-pii=false`
- DSN 외부 주입
- request body/query/cookie 제거
- auth/internal/proxy/IP header와 관련 env 제거
- user context, extras, breadcrumbs, exception value와 message 제거
- one-click 실제 token URL 제거

실제 outbound event와 transaction, server/proxy access log는 비운영 sentinel로 별도 확인해야 한다.

## Redis 보안 및 topology

지원:

```text
standalone 또는 single-primary
```

미지원:

```text
Redis Cluster
```

댓글은 여러 counter와 duplicate key, newsletter는 두 IP counter를 multi-key Lua로 처리한다. Cluster에서는 slot이 다르면 `CROSSSLOT`이 발생할 수 있다. Phase 08은 hash tag나 비원자 fallback을 추가하지 않는다.

Redis connection/timeout은 fail-open하지만 Lua syntax, wrong type, codec, HMAC과 application 오류는 generic server error다. fail-open 기간에는 인프라 접근 제한과 안전한 metric/alert가 필요하다.

## Newsletter 발송 안전성

- `NEWSLETTER_SENDING_ENABLED=false`가 기본이다.
- test send는 master/test switch와 allowlist를 모두 요구한다.
- Campaign postId, Delivery postId+subscriberId unique index가 중복 Document를 막는다.
- Campaign/Delivery claimToken과 claimExpiresAt이 stale worker write를 fencing한다.
- provider 호출 직전 Subscriber ACTIVE, BlogPost opt-in/public, Campaign SENDING을 다시 확인한다.
- provider 호출은 총 최대 4회다.
- worker와 queue는 bounded다.

SES는 application idempotency key를 제공하지 않는다. provider가 수락한 후 결과 저장 전 장애가 나면 exactly-once를 보장할 수 없다. 이 경계는 `PROVIDER_RESULT_UNKNOWN`, retryable=false로 종료하고 자동·수동 재발송하지 않는다.

SES bounce/complaint webhook이 없어 실제 bounce/complaint가 Subscriber BOUNCED로 자동 반영되지 않는다. 출시 후 별도 기능과 개인정보 영향 검토가 필요하다.

## 정적 avatar key

- backend는 `https://to-teacher.com` base URL과 `character-image/{filename}` key를 결합한다.
- S3/CloudFront API를 호출하지 않는다.
- key는 query, fragment, absolute URL, leading slash와 `..`를 허용하지 않는다.
- 실제 파일은 frontend 저장소가 관리한다.
- 이미 댓글 snapshot에 사용한 key는 삭제하거나 덮어쓰지 않는다.
- 새 이미지는 versioned filename을 사용한다.

정적 host의 MIME, Cache-Control, CORS와 404 fallback은 프론트 담당자가 검증한다.

## 알려진 잔여 위험

- 실제 SES identity, DKIM, sandbox, quota와 Gmail unsubscribe UI는 수동 검증 전이다.
- actual Sentry/ALB/CDN/access log redaction은 수동 검증 전이다.
- internal API는 operator별 audit/RBAC가 없다.
- fixed-window burst와 Redis fail-open abuse 위험이 있다.
- Redis Cluster는 지원하지 않는다.
- SES exactly-once와 bounce/complaint 처리가 보장되지 않는다.
- path/query bearer token이 application 밖 log/history에 노출될 수 있다.
- avatar asset manifest와 실제 200 응답은 frontend 저장소에 의존한다.
- 기존 exams에는 JVM common pool을 쓰는 비동기 경로와 별도 AWS S3 credential 설정이 있다. Phase 08의 관련 없는 exams 리팩터링 범위가 아니므로 운영 용량·credential 정책에서 별도 검토한다.
