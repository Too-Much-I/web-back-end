# Blog MVP 테스트 가이드

## 테스트 계층

| 계층 | Gradle task | Docker | 책임 |
|---|---|---:|---|
| unit/contract | `test` | 불필요 | domain policy, mock Repository, BSON/index definition, MockMvc, Security/Sentry/API 계약 |
| integration | `integrationTest` | 필수 | MongoDB 7.0, Redis 7.2 실제 query/index/Lua/TTL/원자성/동시성 |
| build | `bootJar` | 불필요 | 실행 가능한 Java 21 artifact |
| image | `docker build` | 필수 | 실제 `Dockerfile` image 생성 |
| 수동 운영 | checklist | 환경별 | SES/DKIM/Gmail, Sentry/access log, ALB/SG, avatar asset |

Phase 07 baseline은 421개 unit/contract test, failures/errors/skipped 0이다. Phase 08은 기존 test를 삭제하거나 비활성화하지 않고 보안/API 계약 test와 별도 integration suite를 추가한다.

## unit/contract test

```bash
bash ./gradlew clean test
```

검증 범위:

- blog 공개 query BSON, pagination, regex escape, related post
- comment rule 1~10, cookie/profile, rate-limit contract, moderation
- newsletter subscribe/unsubscribe, token key rotation, Campaign/Delivery/Scheduler policy, SES request mapping
- `/internal/**` 전용 SecurityFilterChain과 API Key digest 비교
- public/exams/Swagger 회귀
- RFC 8058 form POST, GET 무상태, redirect 부재
- Sentry redaction과 generic exception response
- JWT secret 누락/짧은 값 fail-fast
- final API mapping과 금지 API 부재

`test` task는 Testcontainers를 시작하지 않는다.

## integrationTest 구성

```bash
bash ./gradlew integrationTest
```

- source: `src/integrationTest/java`
- task: `integrationTest`
- Testcontainers BOM: `1.21.4`
- Mongo image: `mongo:7.0`
- Redis image: `redis:7.2-alpine`
- `maxParallelForks=1`
- container lifecycle은 Testcontainers가 관리한다.
- 운영 MongoDB/Redis URI를 사용하지 않는다.
- 실제 AWS credential, Sentry outbound와 SES sender를 사용하지 않는다.
- Docker를 찾지 못하면 명확히 실패하며 skip으로 성공 처리하지 않는다.

### MongoDB suite

`BlogPostMongoIntegrationTest`:

- slug unique와 publication/reconciliation index
- PUBLISHED/current 경계, DRAFT/ARCHIVED/future/null 제외
- 안정 정렬, literal regex, related slug batch
- 누락 newsletterEnabled false 처리
- initializer 반복 실행

`BlogCommentMongoIntegrationTest`:

- tokenHash unique와 comment index
- VISIBLE 목록 query
- 운영 `[from,to)`
- VISIBLE/PENDING -> HIDDEN, HIDDEN -> VISIBLE
- 동시 상태 변경 winner 한 건
- distinct postId batch slug 보강

`NewsletterSubscriberMongoIntegrationTest`:

- email unique
- 동시 신규 subscribe 한 Document
- DuplicateKeyException 복구
- 동시 UNSUBSCRIBED 재구독과 tokenVersion +1
- ACTIVE/BOUNCED/UNSUBSCRIBED 해지 전이와 stale token fencing

`NewsletterCampaignDeliveryMongoIntegrationTest`:

- Campaign postId와 Delivery postId+subscriberId unique
- 두 worker claim winner 한 건
- old claimToken fencing
- stale recovery 전/후 provider 호출 경계
- retry query와 Campaign count
- manual retry cursor/batch 100, 기존 Delivery 재사용

`NewsletterSchedulerMongoIntegrationTest`:

- explicit newsletter opt-in과 15분/즉시 예약
- 중복 Campaign/Delivery 방지
- subscriber cursor batch 100/205 recipients
- kill switch false/true backlog 재개
- deterministic fake sender
- PROVIDER_RESULT_UNKNOWN 자동 재시도 금지
- provider 최대 4회와 Campaign 최종 집계
- 비활성 Subscriber SKIPPED

### Redis suite

`BlogCommentRedisIntegrationTest`:

- visitor 10초/10분/하루, IP 10분/하루
- duplicate owner reservation/release
- blocker 발생 시 partial mutation 없음
- 신규 TTL과 기존 TTL 비연장
- fixed-window expiry와 동시 허용량
- Mongo save 실패 후 duplicate cleanup/counter 유지

`NewsletterRedisIntegrationTest`:

- 기본 10분 10회, 하루 30회
- 두 key 단일 Lua 원자 증가
- blocker 발생 시 두 counter 모두 무변경
- 최대 TTL 기반 Retry-After
- TTL 비연장과 동시 허용량
- 실제 연결 실패 fail-open

Redis integration은 standalone/single-primary만 검증한다. Redis Cluster는 multi-key Lua의 `CROSSSLOT` 가능성 때문에 지원하지 않는다.

## 결정적 Scheduler test

- production `@Scheduled` thread에 시간을 맡기지 않는다.
- mutable UTC Clock으로 예약, backoff, claim expiry를 이동한다.
- 실제 Mongo Repository와 deterministic fake `NewsletterEmailSender`를 연결한다.
- 실제 SES는 호출하지 않는다.
- concurrency는 latch/barrier와 bounded executor로 시작점을 맞춘다.
- assertion은 최종 Document 수, status, claimToken fencing과 provider 호출 횟수를 함께 확인한다.

## Security/API test

자동 검증:

- internal disabled 404
- enabled key 누락/오류 동일 401
- 정상 key `ROLE_INTERNAL`, null credentials
- JWT만으로 internal 접근 불가
- internal chain이 public/JWT chain보다 우선
- 공개 blog/comment/newsletter/one-click/exams/Swagger 접근 유지
- internal Controller OpenAPI 숨김
- 게시글 write, 댓글 edit/delete, verify와 GET unsubscribe mapping 부재
- one-click GET 상태 불변
- key/token/email/comment/IP sentinel가 response/log/Sentry에 없음

Security contract가 실제 ALB, SG와 firewall을 증명하지는 않는다. 해당 검증은 수동 checklist다.

## test profile

`src/test/resources/application-test.yml`은 다음 안전 장치를 가진다.

- 비운영 dummy HMAC/JWT secret
- newsletter sending/test sending false
- logging provider
- internal API false
- comment/newsletter rate limit false
- Sentry DSN empty, enabled false, PII false
- example.test URL/email과 test avatar option

test dummy를 운영 환경에 복사하지 않는다. integration test는 container endpoint를 test 코드에서 직접 사용하며 운영 URI를 읽지 않는다.

## 전체 검증 명령

권장 순서:

```bash
git diff --check
bash ./gradlew clean test
bash ./gradlew integrationTest
bash ./gradlew bootJar
docker compose -f compose.local.yml config
docker build -t to-teacher-backend:phase08 .
```

단일 Gradle graph 확인:

```bash
bash ./gradlew clean test integrationTest bootJar
```

Docker socket이 없는 환경에서는 권한 변경이나 우회를 하지 않는다. integrationTest와 image build를 성공으로 기록하지 않고 CI 또는 사용자 로컬의 동일 명령 성공 증적을 기다린다.

## CI gate

GitHub Actions main push 순서:

```text
checkout
-> Java 21
-> Gradle wrapper 실행 준비
-> clean test
-> integrationTest
-> bootJar
-> Docker build/push
-> 기존 SSH 배포
```

각 단계 실패는 뒤 단계를 막아야 한다. integration test에는 운영 secret을 제공하지 않는다. 실제 workflow push/deploy는 Codex 검증 범위가 아니다.

## Docker와 compose

```bash
docker compose -f compose.local.yml config
docker build -t to-teacher-backend:phase08 .
```

- Compose는 loopback MongoDB 7.0과 Redis 7.2-alpine 개발 service만 정의한다.
- Dockerfile은 Eclipse Temurin Java 21을 사용한다.
- image push, registry login과 deploy는 로컬 검증에서 수행하지 않는다.
- 오래된 Java 17 오탈자 `Dokerfile`은 실제 참조가 없음을 확인한 뒤 제거했다.

## 수동 운영 테스트

| 항목 | 담당 | 자동화하지 않는 이유 | 필수 증적 |
|---|---|---|---|
| SES identity/DKIM/sandbox/quota | 메일 운영자 | AWS 계정 상태 필요 | console capture/승인 기록 |
| 실제 email/HTML/plain/UTM | 메일 운영자 | 외부 전송 | 승인 recipient와 마스킹된 message ID |
| Gmail one-click UI | 메일 운영자 | client 정책 의존 | 수신 원문/UI 결과 |
| Sentry outbound redaction | 보안/관측 담당자 | 실제 project 필요 | sentinel 부재 검색 |
| ALB/proxy/CDN access log | 인프라 담당자 | 외부 log 설정 | token/key sentinel 부재 |
| 내부 API network boundary | 인프라 담당자 | SG/firewall 필요 | 허용/차단 source 결과 |
| avatar 파일 | 프론트 담당자 | asset가 다른 저장소에 있음 | manifest, 200/MIME/cache |
| Docker CI | CI | main workflow 환경 | 성공 run URL |

## 자동 검증으로 증명하지 못하는 것

- production replica failover와 network partition
- Redis Cluster
- 실제 SES acceptance 이후 inbox delivery
- bounce/complaint 자동 처리
- DKIM에 unsubscribe header가 실제 서명되는지
- Gmail UI 노출 보장
- frontend history/Referer와 CDN redaction
- production Sentry, ALB, access log 정책
- 실제 avatar 파일 존재와 404 fallback
- 운영 key rotation의 무중단성

## 결과 기록

2026-07-31 Phase 08 최종 실행 결과:

```text
unit tests: PASS — 433, skipped/failures/errors 0
integration tests: PASS — 32, skipped/failures/errors 0
bootJar: PASS — 약 60 MiB
compose config: PASS
Docker image build: PASS — to-teacher-backend:phase08, Temurin Java 21.0.11
manual operations: PENDING
```

첫 integration 실행은 Testcontainers 1.20.4의 Docker API 1.32 기본값이 Docker Engine 29에서 거절돼 실패했다. API 1.44를 기본 사용하는 1.21.4로 최소 상향한 뒤 실제 MongoDB Scheduler test가 claim heartbeat no-op 판정 결함을 발견했고, `matchedCount` fencing으로 수정한 후 전체 suite와 clean graph를 재실행해 성공했다.

어떤 실패 이력도 삭제하거나 성공으로 바꾸지 않는다. 운영 수동 항목은 검증 전이므로 `PENDING`으로 남긴다.
