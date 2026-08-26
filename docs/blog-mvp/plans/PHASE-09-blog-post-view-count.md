# Phase 09: 게시글 조회수 내부 집계

- 상태: EXECUTED

## 목표

공개 게시글 상세 API `GET /api/posts/{slug}`가 공개 조건을 충족하는 게시글을 성공적으로 찾을 때마다 해당 `blog_posts` Document의 `viewCount`를 MongoDB에서 원자적으로 1 증가시킨다. 조회수는 운영 데이터로만 저장하고 기존 목록, 상세, 검색 및 관련 글 응답에는 노출하지 않는다.

집계 단위는 고유 방문자가 아니라 공개 게시글을 찾은 상세 API 호출이다. 로그인, 익명 cookie, IP, User-Agent 또는 시간 창을 이용한 중복 제거는 하지 않는다. 존재하지 않거나 공개 조건을 충족하지 않는 게시글과 상세 API 이외의 조회는 증가시키지 않는다.

## 현재 코드 분석

- 계획 수립 시 브랜치는 `feat/blog-mvp`이고 `git status --short` 출력은 없어 작업 트리가 깨끗하다.
- Phase 00~08과 각 계획은 모두 `DONE`/`EXECUTED`다. Phase 09는 완료된 MVP에 합의된 DB 내부 조회수 집계를 추가하는 후속 단계다.
- `BlogPost`에는 현재 조회수 필드가 없으며 `BlogPostResponseDTO`의 목록·상세·검색·관련 글 DTO에도 조회수가 없다.
- `BlogPostServiceImpl.getPublicPost`는 slug를 검증한 뒤 `findPublicPostBySlug(slug, now)`로 공개 글을 읽고 관련 글을 조회해 상세 DTO로 변환한다.
- `BlogPostQueryRepositoryImpl.findPublicPostBySlug`는 `status == PUBLISHED`, `publishedAt != null`, `publishedAt <= now`, slug 일치를 적용한 `findOne` 읽기다.
- 같은 `findPublicPostBySlug`를 댓글 작성·조회의 공개 게시글 검증에서도 사용한다. 기존 메서드 자체에 증가 부작용을 추가하면 댓글 요청까지 조회수로 잘못 집계되므로 변경할 수 없다.
- Phase 08에서 실제 MongoDB 7.0 Testcontainers와 `BlogPostMongoIntegrationTest`가 준비되어 있어 `$inc`, 누락 필드, 비공개 조건과 동시 증가를 실제 서버에서 검증할 수 있다.
- MongoDB의 단일 Document update는 원자적이며 `$inc`는 누락된 숫자 필드를 증가값으로 생성한다. 따라서 별도 read 후 save보다 공개 조건을 포함한 `findAndModify` 한 번이 증가 유실과 TOCTOU를 피한다.

## 구현 범위

사용자의 명시적 승인 후 다음 범위만 구현한다.

1. `BlogPost`에 primitive `long viewCount`를 추가하고 builder 입력과 MongoDB 매핑을 제공한다.
2. `BlogPostQueryRepository`에 상세 API 전용 `findPublicPostBySlugAndIncrementViewCount(String slug, Instant now)`를 추가한다.
3. Repository 구현에서 기존 공개 Criteria와 slug 조건을 하나의 query로 구성하고 `Update.inc("viewCount", 1L)`를 적용한 `findAndModify`를 실행한다.
4. `FindAndModifyOptions.returnNew(true)`로 증가가 반영된 Document를 반환한다. 반환된 entity의 조회수는 응답 변환에 사용하지 않는다.
5. `BlogPostServiceImpl.getPublicPost`만 새 증가 메서드를 호출하도록 바꾼다.
6. 댓글·뉴스레터·관련 글·목록·검색이 사용하는 기존 read-only Repository 메서드는 유지한다.
7. 단위 테스트와 실제 MongoDB 통합 테스트로 query/update 계약, 기존 데이터 호환성, 비공개·미존재 미증가와 동시 요청 원자성을 검증한다.
8. 관련 테스트, 전체 unit/integration/build 검증과 요구사항·API 미노출 정적 검수를 수행한다.

## 제외 범위

- 목록, 상세, 검색, 관련 글 또는 새 API를 통한 조회수 노출
- 조회수 조회·수정·초기화용 공개 또는 내부 운영 API
- 조회수 기반 정렬, 인기 글, 추천, 통계 dashboard와 analytics 연동
- 로그인 사용자, 익명 cookie, IP, User-Agent, fingerprint 기반 고유 방문자 계산
- 같은 방문자의 반복 조회 제거, bot/crawler 필터링과 시간 창 제한
- Redis counter, message queue, event collection, 배치 또는 비동기 DB flush
- 기존 Document 전체에 `viewCount: 0`을 쓰는 일괄 backfill이나 운영 DB 직접 접근
- 조회수 증가에 따른 `updatedAt` 변경
- 기존 API method/path/status/response DTO와 OpenAPI 응답 schema 변경
- 새 MongoDB collection 또는 index
- 댓글, 뉴스레터, exams, 보안, 배포와 인프라 동작 변경

## 예상 변경 파일

계획 수립에서 생성·수정하는 파일:

- `docs/blog-mvp/plans/PHASE-09-blog-post-view-count.md`
- `docs/blog-mvp/REQUIREMENTS.md`
- `docs/blog-mvp/WORKFLOW.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`
- `PLANS.md`

승인 후 수정할 main 파일:

- `src/main/java/web/tosunsaeng/domain/blog/domain/entity/BlogPost.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/application/BlogPostServiceImpl.java`

승인 후 수정할 테스트 파일:

- `src/test/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/application/BlogPostServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/api/BlogPostRestControllerTest.java`
- `src/integrationTest/java/web/tosunsaeng/integration/mongo/BlogPostMongoIntegrationTest.java`

구현 진행과 완료 기록을 위해 수정할 문서:

- `docs/blog-mvp/plans/PHASE-09-blog-post-view-count.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

명시적으로 변경하지 않을 파일:

- `src/main/java/web/tosunsaeng/domain/blog/dto/BlogPostResponseDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/api/BlogPostRestController.java`
- `src/main/java/web/tosunsaeng/domain/blog/converter/BlogPostConverter.java`
- `src/main/java/web/tosunsaeng/domain/comment/**`
- `src/main/java/web/tosunsaeng/domain/newsletter/**`
- `src/main/java/web/tosunsaeng/domain/exams/**`
- `build.gradle`, application 설정, Security 설정, CI, Docker와 Redis 파일
- 운영 MongoDB 데이터와 인덱스

예상 파일 밖의 소스·테스트·설정 변경이 필요하면 구현을 중단하고 차이와 선택지를 사용자에게 보고한다.

## API 변경

외부 API 계약 변경은 없다.

- 기존 `GET /api/posts/{slug}`의 method, path, HTTP status, 성공·오류 code와 응답 JSON을 그대로 유지한다.
- `PostSummary`, `RelatedPostSummary`, `PostPageResult`, `PostSearchResult`, `PostDetailResult`에 `viewCount`를 추가하지 않는다.
- 존재하지 않는 slug, DRAFT, ARCHIVED, 미래 발행과 `publishedAt` 누락·null은 기존과 같은 404를 반환하며 조회수를 생성하거나 증가시키지 않는다.
- 증가용 별도 endpoint를 추가하지 않는다.

## DB 변경

기존 `blog_posts` Document에 다음 내부 필드를 추가한다.

| 필드 | BSON/Java 타입 | 기본·호환 정책 | 외부 노출 |
|---|---|---|---|
| `viewCount` | BSON int64 / Java `long` | 누락 시 0으로 매핑, 첫 성공 상세 조회의 `$inc: 1`로 1 생성 | 없음 |

상세 API 전용 원자 연산은 다음 의미를 가진다.

```text
filter:
  slug == requestedSlug
  AND status == PUBLISHED
  AND publishedAt != null
  AND publishedAt <= requestNow

update:
  $inc: { viewCount: NumberLong(1) }

options:
  returnNew: true
  upsert: false
```

- `upsert`를 사용하지 않으므로 미존재·비공개 요청이 새 Document를 만들지 않는다.
- 기존 Document에 필드가 없어도 `$inc`가 int64 값 1을 생성하므로 배포 전 backfill은 필요 없다.
- `viewCount`가 숫자가 아닌 잘못된 운영 데이터이면 MongoDB 오류를 숨기지 않고 요청을 실패시켜 데이터 결함을 드러낸다.
- `viewCount` 증가만 수행하며 `updatedAt`은 변경하지 않는다.
- 조회수로 query하거나 정렬하지 않으므로 새 index는 추가하지 않는다.
- 단일 Document `$inc`의 원자성을 이용해 동시에 성공한 N개 상세 요청 뒤 정확히 N 증가하도록 한다.

## 상태 전이

게시글 제품 상태 `DRAFT`, `PUBLISHED`, `ARCHIVED`는 변경하지 않는다. 조회수의 상태 전이만 다음과 같다.

```text
viewCount 누락(논리값 0) --상세 요청에서 공개 글 발견--> 1
viewCount N             --상세 요청에서 공개 글 발견--> N + 1
미존재/비공개/상세 외 조회                        --> 변경 없음
```

- 상세 응답을 구성할 공개 게시글을 원자 증가 연산으로 찾지 못하면 기존 `_BLOG_POST_NOT_FOUND`를 반환한다.
- MongoDB 증가 연산이 실패하면 조회수를 누락한 성공 응답을 반환하지 않고 기존 전역 예외 정책에 따라 요청 자체를 실패시킨다.
- 관련 글 조회나 DTO 변환이 증가 후 예기치 않게 실패하면 이미 완료된 조회수 증가는 rollback하지 않는다. MongoDB transaction을 새로 도입하지 않으며 요구사항의 집계 경계는 HTTP 응답 완료가 아니라 "상세 요청에서 공개 게시글을 성공적으로 찾은 시점"이다.

## 테스트 계획

단위 및 contract 테스트:

1. 증가 메서드가 기존 공개 Criteria와 slug를 함께 사용하고 `findAndModify`를 한 번 호출하는지 검증한다.
2. update가 `viewCount`만 int64 1 증가시키며 `updatedAt` 변경과 upsert가 없고 return-new가 활성화되는지 검증한다.
3. 상세 Service만 증가 메서드를 사용하고 목록·검색과 기존 read-only 상세 검증 경로는 사용하지 않는지 검증한다.
4. invalid slug와 Repository empty 결과는 기존 404이고 관련 글 query를 실행하지 않는지 검증한다.
5. Controller 상세 JSON에 `viewCount`가 없고 기존 응답 계약이 그대로인지 명시적으로 검증한다.

MongoDB 7.0 Testcontainers 통합 테스트:

1. `viewCount`가 없는 raw 기존 Document를 entity로 읽으면 0이고 첫 성공 상세 조회 뒤 BSON int64 1이 저장되는지 검증한다.
2. 기존 `viewCount = N` 글이 상세 조회마다 N+1로 증가하는지 검증한다.
3. 미존재, DRAFT, ARCHIVED, 미래 발행, `publishedAt` 누락·null은 Document를 변경하지 않는지 검증한다.
4. 목록, 검색, 관련 글 및 기존 `findPublicPostBySlug` 호출은 조회수를 변경하지 않는지 검증한다.
5. 여러 thread가 같은 공개 게시글을 동시에 증가시킨 뒤 실패 없이 완료된 호출 수만큼 정확히 누적되는지 검증한다.
6. 증가가 `updatedAt`과 다른 게시글 필드를 변경하지 않는지 검증한다.

필수 검증:

- 관련 blog 단위/Controller 테스트
- `bash ./gradlew clean test`
- `bash ./gradlew integrationTest`
- `bash ./gradlew bootJar`
- `bash ./gradlew clean test integrationTest bootJar`
- `git diff --check`
- DTO/API schema에 `viewCount`가 없는지 정적 검색
- 승인 계획, 고정 요구사항, 제외 범위와 실제 변경 파일 대조

## 위험 요소

- 한 게시글로 쓰기가 집중되면 단일 MongoDB Document가 write contention 지점이 된다. Phase 09는 정확한 원자 집계와 단순성을 우선하며 실제 부하가 이 한계를 보일 때 별도 승인으로 분산 counter를 설계한다.
- 모든 성공 상세 호출을 세므로 새로고침, prefetch, crawler와 같은 사용자의 실제 열람이 아닌 요청도 포함된다. 이번 지표는 고유 방문자 수가 아니라 상세 endpoint hit count다.
- 증가 후 관련 글 조회 또는 응답 변환이 실패하면 DB 증가는 남는다. 관련 글 조회까지 포함한 multi-document transaction은 과도하며 조회수 집계의 의미와도 맞지 않아 도입하지 않는다.
- 필드 누락은 안전하게 처리하지만 기존 `viewCount`가 문자열, double, 음수 등 잘못된 값이면 별도 보정하지 않는다. 오류나 비정상 값은 운영 데이터 문제로 보고 후속 승인 없이 자동 수정하지 않는다.
- Java `long`과 BSON int64 상한 도달 처리는 현실적인 MVP 범위를 벗어난다. overflow가 실제 위험이 되면 별도 요구사항과 migration 계획이 필요하다.

## 롤백 방법

금지된 Git 명령이나 운영 DB 직접 수정을 사용하지 않는다.

- 구현 전 DRAFT 계획 철회는 Phase 09 상태와 요구사항·Workflow·Plans 문서를 후속 승인된 수동 역패치로 정리한다.
- 구현 후 애플리케이션 롤백이 필요하면 상세 Service를 기존 read-only Repository 메서드로 되돌리고 증가 전용 메서드와 entity 필드를 후속 승인된 변경으로 제거한다.
- 이미 저장된 `viewCount`는 애플리케이션이 알 수 없는 추가 BSON 필드로 남아도 기존 읽기 동작을 방해하지 않으므로 자동 삭제하지 않는다.
- 운영 데이터에서 필드를 제거하거나 값을 보정해야 한다면 Codex가 운영 DB에 접근하지 않고 운영자가 별도 검토·승인된 migration 절차를 수행한다.

## 완료 조건

- 사용자가 이 DRAFT 계획을 명시적으로 승인했다.
- 승인 범위의 파일만 변경했다.
- 공개 상세 조회만 `viewCount`를 원자 증가시키고 다른 조회 경로는 증가시키지 않는다.
- 누락 필드 호환, 비공개 미증가, 동시 증가 유실 방지가 실제 MongoDB 통합 테스트로 확인됐다.
- API method/path/status/DTO/JSON에 변경이 없고 조회수가 외부에 노출되지 않는다.
- 관련 테스트와 `clean test`, `integrationTest`, `bootJar`, 결합 build가 모두 성공한다.
- `git diff --check`와 고정 요구사항·제외 범위 review가 성공한다.
- 실제 구현 차이와 명령·테스트 결과를 계획서 및 `IMPLEMENTATION_STATUS.md`에 기록한다.

하나라도 충족하지 못하면 Phase 09를 `DONE` 또는 계획을 `EXECUTED`로 변경하지 않는다.

## 실제 구현 중 발생한 차이

승인 계획과 실제 제품 동작, API, DB 필드, 상태 전이 및 변경 파일 범위의 차이는 없다.

- 계획대로 primitive `long viewCount`를 추가했다.
- 상세 API 전용 `findPublicPostBySlugAndIncrementViewCount`와 공개 Criteria 기반 `findAndModify`, `$inc: 1L`, return-new, no-upsert를 적용했다.
- 댓글 등 기존 사용처의 read-only `findPublicPostBySlug`는 그대로 유지했다.
- DTO·Controller·Converter를 변경하지 않았으며 Controller test assertion만 보강했다.
- 기존 데이터 backfill, 새 index, 설정·보안·인프라 변경은 수행하지 않았다.
- 최초 Gradle 실행은 sandbox가 사용자 Gradle wrapper lock 파일 접근을 거부해 실패했지만 제품·테스트 결함이 아니었고, 승인된 실행 환경에서 모든 검증이 성공했다.

## 검증 결과

- 계획 수립 전 브랜치: `feat/blog-mvp`
- 계획 수립 전 작업 트리: 깨끗함
- Phase 00~08: 모두 `DONE`, 계획 `EXECUTED`
- 정적 분석: 현재 조회수 필드·증가 로직·응답 노출이 없고 기존 read-only slug 조회가 댓글 경로에서도 재사용됨을 확인함
- 사용자 승인: `2026-08-26 15:50:20 KST (+09:00)`에 사용자가 `Phase 09 계획 승인`으로 명시적으로 승인함
- 관련 단위·Controller 선택 테스트: `BUILD SUCCESSFUL`
- Phase 09 MongoDB 통합 선택 테스트: `BUILD SUCCESSFUL`
- `./gradlew clean test integrationTest bootJar`: `BUILD SUCCESSFUL`, unit 446개와 integration 35개 failures/errors/skipped 0
- `./gradlew clean test bootJar`: `BUILD SUCCESSFUL`, unit 446개 failures/errors/skipped 0, 약 60 MiB 실행 JAR 생성
- `./gradlew integrationTest`: `BUILD SUCCESSFUL`, MongoDB 7.0·Redis 7.2-alpine 통합 테스트 35개 failures/errors/skipped 0
- MongoDB 확인: 누락 `viewCount`는 Java 0으로 읽고 첫 상세 조회에서 BSON int64 1 생성, `updatedAt` 불변, 비공개·미존재·상세 외 조회 미증가 확인
- 동시성 확인: 동일 공개 게시글에 동시 40회 원자 증가 후 정확히 40 누적됨
- API 확인: DTO·Controller·Converter에 `viewCount` 참조가 없고 상세 JSON 미노출 assertion 성공
- 정적 검증: `git diff --check` 성공, 승인 파일 밖 source/config 변경 없음
- 완료 시각: `2026-08-26 15:56:43 KST (+09:00)`
