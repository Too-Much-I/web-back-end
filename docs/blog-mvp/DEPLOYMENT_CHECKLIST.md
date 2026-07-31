# Blog MVP 배포 체크리스트

이 문서의 `[ ]` 항목은 실제 환경 증적이 필요한 미완료 항목이다. Phase 08 자동 테스트가 성공해도 이 수동 checklist가 완료되기 전에는 “운영 출시 승인 완료”가 아니다.

## 배포 식별

- [ ] 배포 commit/tag와 bootJar checksum 기록
- [ ] CI unit/integration/bootJar/Docker build 성공 URL 보관
- [ ] 승인자, 작업자, 작업 시각과 rollback artifact 기록
- [ ] main push가 즉시 배포됨을 확인하고 점검 시간 확보

## 환경변수와 secret

실제 값은 ticket나 이 문서에 적지 않는다.

| 변수 | 필수 조건/기본값 |
|---|---|
| `MONGODB_URI` | 필수, 운영 DB URI |
| `REDIS_HOST`, `REDIS_PORT` | 기본 localhost/6379, 운영 standalone endpoint |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | 기존 exams S3 설정이 요구; secret manager 사용, 저장소 금지 |
| `AWS_S3_BUCKET_NAME` | 기존 exams S3 bucket |
| `BLOG_ANONYMOUS_TOKEN_SECRET` | 32바이트 이상 |
| `BLOG_ANONYMOUS_COOKIE_SECURE` | 운영 true |
| `BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS` | 기본 180, 양수 |
| `BLOG_ANONYMOUS_AVATAR_BASE_URL` | `https://to-teacher.com` |
| `BLOG_COMMENT_RATE_LIMIT_SECRET` | 32바이트 이상, anonymous secret과 분리 |
| `BLOG_COMMENT_RATE_LIMIT_ENABLED` | 기본 true |
| `BLOG_COMMENT_DUPLICATE_TTL_SECONDS` | 기본 600 |
| `NEWSLETTER_UNSUBSCRIBE_TOKEN_SECRET` | active HMAC, 32바이트 이상 |
| `NEWSLETTER_UNSUBSCRIBE_TOKEN_KEY_ID` | 1~64자의 안전한 식별자 |
| previous unsubscribe keys | 외부 config tree/secret manager, active와 다른 key/secret |
| `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_SECRET` | 32바이트 이상, 다른 HMAC과 분리 |
| `NEWSLETTER_SUBSCRIBE_RATE_LIMIT_ENABLED` | 기본 true |
| `NEWSLETTER_SENDING_ENABLED` | 기본 false |
| `NEWSLETTER_EMAIL_PROVIDER` | `logging` 또는 `ses`; 운영 발송 전 별도 승인 |
| `NEWSLETTER_FROM_EMAIL` | sending=true면 필수 |
| `NEWSLETTER_FROM_NAME` | 기본 토선생, CR/LF 금지 |
| `NEWSLETTER_PUBLIC_BASE_URL` | sending=true면 query/fragment 없는 HTTPS origin |
| `NEWSLETTER_API_BASE_URL` | sending=true면 query/fragment 없는 HTTPS API origin |
| `NEWSLETTER_SEND_DELAY_MINUTES` | 기본 15 |
| `NEWSLETTER_BATCH_SIZE` | 기본 100 |
| `NEWSLETTER_WORKER_COUNT` | 기본 2, 최대 16 |
| `NEWSLETTER_QUEUE_CAPACITY` | 기본 100, bounded |
| `NEWSLETTER_CLAIM_TTL_SECONDS` | 기본 300 |
| `NEWSLETTER_RECONCILIATION_DELAY_MS` | 기본 60000 |
| `NEWSLETTER_CAMPAIGN_DELAY_MS` | 기본 10000 |
| `NEWSLETTER_DELIVERY_DELAY_MS` | 기본 5000 |
| `NEWSLETTER_RETRY_DELAY_MS` | 기본 30000 |
| `NEWSLETTER_COMPLETION_DELAY_MS` | 기본 30000 |
| `NEWSLETTER_STALE_RECOVERY_DELAY_MS` | 기본 60000 |
| `NEWSLETTER_TEST_SENDING_ENABLED` | 기본 false |
| `NEWSLETTER_TEST_RECIPIENT_ALLOWLIST` | 승인된 비운영 수신자만 |
| `AWS_REGION` | 기본 ap-northeast-2, SES identity와 일치 |
| `INTERNAL_API_ENABLED` | 기본 false |
| `INTERNAL_API_KEY` | enabled=true면 UTF-8 32~1024바이트 |
| `JWT_SECRET_KEY` | 필수, UTF-8 32바이트 이상, fallback 없음 |
| `SENTRY_DSN` | 환경별 외부 주입, 미설정 시 전송 없음 |

- [ ] secret 간 동일값이 없는지 값 노출 없이 validation
- [ ] test dummy와 example 값이 운영에 배포되지 않음
- [ ] `NEWSLETTER_SENDING_ENABLED=false`, `NEWSLETTER_TEST_SENDING_ENABLED=false`로 최초 기동
- [ ] `INTERNAL_API_ENABLED=false`로 최초 기동
- [ ] Sentry `send-default-pii=false`

## build와 container

- [ ] Java 21 확인
- [ ] `bash ./gradlew clean test` 성공
- [ ] `bash ./gradlew integrationTest` 성공; skip 0, Mongo/Redis container 시작 증적
- [ ] `bash ./gradlew bootJar` 성공
- [ ] `docker compose -f compose.local.yml config` 성공
- [ ] `docker build -t to-teacher-backend:phase08 .` 성공
- [ ] image 안의 Java major version 21 확인
- [ ] CI 순서가 test -> integrationTest -> bootJar -> Docker build/push -> deploy
- [ ] 실패 단계 이후 Docker push/SSH deploy가 실행되지 않음

빌드는 Java 21 `Dockerfile`만 사용한다. 미사용 Java 17 오탈자 `Dokerfile`은 저장소 참조 확인 후 Phase 08에서 제거됐다.

## MongoDB

- [ ] 운영 backup/restore 지점 확인
- [ ] 기존 duplicate slug, visitor tokenHash, subscriber email, Campaign postId, Delivery postId+subscriberId 사전 검사
- [ ] 애플리케이션 시작 시 다음 index 생성 성공

```text
uk_blog_posts_slug
idx_blog_posts_status_published_at
idx_blog_posts_newsletter_reconciliation
uk_anonymous_visitors_token_hash
idx_blog_comments_post_status_created_at
idx_blog_comments_anonymous_visitor_id
idx_blog_comments_status_created_at
uk_newsletter_subscribers_email
idx_newsletter_subscribers_status
uk_newsletter_campaigns_post_id
idx_newsletter_campaigns_status_scheduled_at
idx_newsletter_campaigns_status_claim_expires_at
idx_newsletter_campaigns_status_updated_at
uk_newsletter_deliveries_post_subscriber
idx_newsletter_deliveries_campaign_status
idx_newsletter_deliveries_status_next_retry_at
idx_newsletter_deliveries_status_claim_expires_at
```

- [ ] initializer 재시작 시 idempotent
- [ ] index 생성 실패 시 배포 중단 및 기존 artifact rollback
- [ ] 공개 query, 댓글 `[from,to)`, Campaign/Delivery claim smoke

운영 DB에서 Codex가 index drop, data cleanup 또는 migration을 실행하지 않는다.

## Redis

- [ ] topology가 standalone 또는 single-primary
- [ ] Redis Cluster가 아님; multi-key Lua `CROSSSLOT` 위험 검토
- [ ] TLS/인증/네트워크 접근 정책 확인
- [ ] health, latency, maxmemory, eviction, persistence와 alert 확인
- [ ] comment Lua/TTL/duplicate reservation smoke
- [ ] newsletter 두 counter/Lua/Retry-After smoke
- [ ] 연결 장애 fail-open metric/경고 확인

## SES와 DKIM — 메일 운영자

- [ ] `AWS_REGION`과 SES identity region 일치
- [ ] 발신 domain identity verified
- [ ] DKIM 활성 및 DNS record verified
- [ ] sandbox 해제와 production access 승인
- [ ] 24시간 quota와 초당 rate 충분
- [ ] EC2 IAM Role/default credential chain의 최소 `ses:SendEmail` 권한
- [ ] `NEWSLETTER_FROM_EMAIL`과 From name 승인
- [ ] SDK Simple Message custom header 지원 확인; Raw MIME 없음
- [ ] allowlist test recipient만 사용
- [ ] 통제된 실제 test email 한 건 발송 — Codex 수행 금지
- [ ] SES acceptance와 마스킹된 provider message ID 증적
- [ ] plain text와 HTML body 확인
- [ ] 게시글 UTM link와 frontend unsubscribe link 확인
- [ ] `List-Unsubscribe` 확인
- [ ] `List-Unsubscribe-Post: List-Unsubscribe=One-Click` 확인
- [ ] `DKIM-Signature h=`에 두 unsubscribe header 포함
- [ ] Gmail 등 대상 client unsubscribe UI 확인 또는 미노출 사유 기록

## RFC 8058

- [ ] active key token form POST -> 204/no-store/no redirect
- [ ] previous key token form POST -> 204
- [ ] 정확한 `List-Unsubscribe=One-Click`만 상태 변경
- [ ] GET/JSON/잘못된 field는 상태 불변
- [ ] cookie/JWT/login 없이 처리
- [ ] 재구독 후 과거 tokenVersion 거절
- [ ] one-click path token이 application/Sentry log에 없음
- [ ] ALB/proxy/CDN access log에서 path 마스킹 또는 제외
- [ ] access log 보존 기간과 접근 권한 최소화

## 정적 avatar — 프론트 담당자

- [ ] `BLOG_ANONYMOUS_AVATAR_BASE_URL=https://to-teacher.com`
- [ ] 운영 `avatar-options` noun/imageKey manifest 첨부
- [ ] 모든 `https://to-teacher.com/character-image/{filename}` HTTP 200
- [ ] image MIME type와 Cache-Control 확인
- [ ] 필요한 CORS 확인
- [ ] 누락 파일 404 fallback 확인
- [ ] 과거 key 삭제·덮어쓰기 금지 정책
- [ ] 새 이미지는 versioned filename
- [ ] path traversal/query/fragment/absolute URL 없음

실제 filename은 프론트 manifest 없이 이 저장소에서 임의 확정하지 않는다.

## 내부 API — 인프라/보안 담당자

- [ ] disabled + key 없음/있음 모두 404
- [ ] enabled + key 없음/오류 모두 동일 401
- [ ] 정상 key로 대표 GET/PATCH/POST 성공
- [ ] JWT만으로 401
- [ ] 공개 API는 internal key 없이 정상
- [ ] Swagger/OpenAPI에서 `/internal/**` 없음
- [ ] ALB·Security Group·firewall source 제한
- [ ] public internet에서 내부 API 접근 불가
- [ ] 인증 실패 metric과 key/IP 없는 안전한 경고 log
- [ ] 단일 key rotation 절차 rehearsal
- [ ] operator별 audit 부재 위험 승인

## Sentry와 log — 보안/관측 담당자

비운영 sentinel을 사용해 다음 위치를 검색한다.

- [ ] application stdout/stderr
- [ ] container/runtime log
- [ ] reverse proxy/ALB/CDN access log
- [ ] 비운영 Sentry event/transaction
- [ ] Spring exception 응답
- [ ] Mongo duplicate/Redis/provider failure 경로

다음 값은 한 건도 없어야 한다.

- [ ] `X-Internal-Api-Key`, Authorization, Cookie
- [ ] anon_session, unsubscribe/one-click token
- [ ] 전체 email과 댓글 content
- [ ] IP 원문, HMAC/JWT/AWS secret
- [ ] provider request/response 전체

- [ ] Sentry one-click URL이 `{token}` 또는 transaction 미전송
- [ ] log retention, 접근 권한과 삭제 절차 승인

## API smoke

- [ ] 게시글 목록/상세/검색
- [ ] 댓글 목록/작성/profile 재생성
- [ ] newsletter subscribe/JSON unsubscribe
- [ ] one-click POST와 GET 무상태
- [ ] internal 댓글 목록/hide/restore
- [ ] internal newsletter test/cancel/retry는 통제된 fixture로만 확인
- [ ] exams summary/questions 기존 계약
- [ ] 금지 API가 handler mapping에 없음

## rollback

- [ ] 이전 Java 21 image와 bootJar checksum 확보
- [ ] DB schema/index를 파괴적으로 되돌릴 필요가 없는지 확인
- [ ] newsletter kill switch false 전환 절차 준비
- [ ] internal API disabled 전환 절차 준비
- [ ] rollback 후 public/exams smoke와 Sentry/log 검증 담당자 지정
- [ ] `PROVIDER_RESULT_UNKNOWN` Delivery를 재발송하지 않음

## 출시 NO-GO 조건

다음 중 하나라도 해당하면 출시하지 않는다.

- unit/integration/bootJar/Docker build 실패 또는 integration skip
- Mongo unique duplicate 미해결/index 생성 실패
- Redis Cluster 또는 Lua/TTL 실패
- sending/internal 기본값이 true
- secret fallback, 짧은 key 또는 test dummy 사용
- API Key/email/token/comment/IP/credential log 노출
- SES identity/DKIM/sandbox/quota/IAM 미검증
- RFC 8058 header/DKIM/POST 미검증
- avatar manifest/파일 미검증
- rollback artifact 또는 담당자 없음
