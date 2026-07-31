# Phase 02: 블로그 공개 게시글 읽기·검색

- 상태: EXECUTED

## 목표

MongoDB에 운영자가 직접 저장한 블로그 게시글 중 공개 조건을 만족하는 글만 대상으로 목록, slug 상세, 제목 부분 검색을 제공한다. 상세 응답에는 공개 관련 글을 최대 3개 포함한다. 모든 응답은 기존 `BaseResponse` 구조를 사용하고, 게시글 생성·수정·삭제 경로와 댓글·뉴스레터 기능은 추가하지 않는다.

## 현재 코드 분석

### 작업 시작 조건과 관리 상태

- 현재 브랜치는 `feat/blog-mvp`이고 시작 시 `git status --short`는 출력이 없어 작업 트리가 깨끗했다.
- `scripts/codex-preflight.sh`는 `Preflight: PASS`로 완료됐다.
- Phase 00과 Phase 01은 모두 `DONE`이고 Phase 01 계획은 `EXECUTED`이므로 Phase 02 계획 수립의 선행 Phase 조건을 만족한다.
- Phase 02 계획서는 기존에 없었으며, 이번 작업은 사용자 지시에 따라 계획서와 상태 문서만 작성하는 `PLANNING` 작업이다.

### 기존 exams 구조

- `ExamRestController`는 class-level `@RequestMapping`, 생성자 주입, Service interface 호출, `BaseResponse.onSuccess(SuccessStatus, result)` 형태를 사용한다.
- `ExamService`와 `ExamServiceImpl`을 분리하고 구현체는 `@Service`, `@RequiredArgsConstructor`를 사용한다. 조회 일부에 `@Transactional(readOnly = true)`가 있으나 일관된 페이지 조회 선례는 없다.
- `ExamConverter`는 static 메서드로 Document와 중첩 Response DTO를 변환한다. Phase 02도 Controller에서 매핑하지 않고 Converter에서 응답 DTO를 조립한다.
- Request/Response DTO는 외부 class 안에 용도별 static nested class를 두는 방식이다. Phase 02는 request body가 없으므로 `BlogPostResponseDTO`만 만들고 query parameter는 Controller가 Service로 전달한다.
- Mongo Document는 `@Document`, `@Id`, Lombok builder/getter를 사용하며 Repository는 `MongoRepository<..., String>`를 확장한다. 필드명은 camelCase와 `@Field` 기반 snake_case가 혼재하지만 블로그 요구 필드가 camelCase로 확정됐으므로 `blog_posts` Document는 해당 이름을 그대로 사용한다.
- exams에는 `Page`, `Pageable`, `totalPages`, `totalElements`, `hasNext`를 사용하는 페이지네이션 선례가 없다. Phase 02는 별도의 게시글 페이지 응답 DTO를 정의해야 하며 기존 exams DTO는 변경하지 않는다.
- exams에서 날짜·시간은 영속 필드가 아닌 세션 ID 생성에 `LocalDateTime.now()`를 사용한다. 공개 시각은 절대 시점 비교와 MongoDB BSON Date 저장이 필요하므로 `Instant`와 주입된 `Clock`을 사용하는 것이 안전하다.
- 도메인 예외는 `ExamsException extends GeneralException` 형태이고 Service에서 `ErrorStatus`를 전달한다. Phase 02도 동일하게 `BlogPostException`을 둔다.

### 공통 응답과 예외

- 실제 상태 코드 경로는 요청에 적힌 `global/common/response/status/**`가 아니라 `src/main/java/web/tosunsaeng/global/error/code/status/**`다.
- `BaseResponse` JSON은 `isSuccess`, `code`, `message`, `result` 순서이며 null result는 생략한다.
- `GlobalExceptionAdvice`는 `GeneralException`의 `BaseErrorCode`에서 HTTP 상태를 가져와 `BaseResponse.onFailure`로 반환한다.
- `SuccessStatus`와 `ErrorStatus`는 전역 enum이므로 기존 상수를 수정하거나 이름을 바꾸지 않고 블로그 상수만 추가해야 한다.
- page/size의 숫자 형식 자체가 잘못된 경우에는 기존 `handleTypeMismatch`가 `COMMON400`을 반환한다. 숫자로 바인딩된 뒤 범위를 위반한 경우에는 Phase 02의 블로그 페이지 오류를 반환한다.

### 애플리케이션, 보안, 테스트 기반

- `TosunsaengApplication`은 `@EnableMongoRepositories(basePackages = "web.tosunsaeng.domain")`를 사용하므로 `web.tosunsaeng.domain.blog` 아래의 새 Repository는 별도 scan 변경 없이 등록된다.
- 기존 `SecurityConfig`는 모든 요청을 `permitAll`로 두고 있다. 확정 GET 경로는 현재 설정으로 공개되므로 이번 Phase에서 보안 설정을 변경하지 않는다.
- 유일한 기존 공개 API prefix는 exams의 `/api/v1/exams`지만 저장소 전체의 강제 versioning 규칙은 없다. 사용자 확정 경로인 `/api/posts`는 심각한 매핑 충돌이 없으므로 그대로 사용한다.
- `/api/posts/search`는 정적 경로라 `/{slug}`보다 Spring MVC에서 우선 매핑된다. 그 결과 `search`는 상세 조회 slug로 사용할 수 없는 예약어가 된다.
- 기존 테스트는 Spring Boot context와 exams Mongo Repository Bean 등록만 검증한다. `spring-boot-starter-test`는 있지만 내장 MongoDB나 Testcontainers 의존성은 없다.
- `build.gradle`은 Phase 00 이전 사용자 변경 보존 대상이고 이번 계획 수립에서도 수정 금지다. Phase 02 테스트는 새 의존성을 추가하지 않고 Mockito, AssertJ, MockMvc와 Mongo `Query` BSON 검증으로 구성한다.
- 운영 및 test 설정에는 Mongo 자동 인덱스 생성 설정이 없다. annotation만 추가하면 실제 인덱스 생성이 보장되지 않으므로 블로그 전용 명시적 초기화 방식을 권고한다.
- 사용자는 `2026-07-29` 이 계획을 명시적으로 승인하면서 안정 정렬, 검색 응답의 정규화 query, slug 정책, 세분화된 pagination 오류, 인덱스 실패 정책과 Phase 08 Mongo 통합 검증 이관을 확정했다.

## 구현 범위

사용자 승인 후 다음 순서로만 구현한다.

1. `BlogPostStatus`와 `BlogPost` Document를 추가하고 요구 필드와 `Instant` 시간 타입을 선언한다.
2. slug unique 및 `status + publishedAt` 복합 인덱스를 보장하는 블로그 전용 Mongo 인덱스 초기화 component를 추가한다.
3. 모든 공개 조회가 공유하는 공개 Criteria를 가진 custom Repository fragment와 MongoTemplate 구현을 추가한다.
4. 목록, 상세, 검색, 관련 글 응답 DTO와 Converter를 추가한다.
5. 주입 가능한 UTC `Clock` Bean과 `BlogPostService`/`BlogPostServiceImpl`을 추가한다.
6. 블로그 성공·오류 status와 `BlogPostException`을 추가한다.
7. 확정된 세 GET endpoint만 `BlogPostRestController`에 추가한다.
8. Repository query, Service, Controller, 인덱스 정의 테스트와 제외 API 부정 검증을 추가한다.
9. 관련 테스트와 전체 Gradle 검증, diff 검증, 요구사항·제외 범위 review를 수행한다.

## 제외 범위

- 게시글 생성 API
- 게시글 수정 API
- 게시글 삭제 API
- 관리자 게시글 API 또는 관리자 글쓰기 UI
- 게시글 등록 스크립트
- 댓글과 랜덤 닉네임
- 뉴스레터 구독과 이메일 발송
- `newsletterEnabled` 등 Newsletter 관련 BlogPost 필드와 인덱스
- 내부 운영 API
- `SecurityConfig` 변경
- Redis 기능
- 실제 Scheduling 작업 또는 `@Scheduled` 메서드
- `sitemap.xml`, `rss.xml`
- 프론트엔드 SEO metadata 생성 또는 Markdown 렌더링
- 기존 exams 코드·응답·경로 리팩터링
- 운영 MongoDB 접근, 운영 데이터 작성, 기존 인덱스 삭제
- 관련 없는 설정·의존성·인프라 변경

## 예상 변경 파일

### 예상 생성·수정 파일

승인 후 새로 생성할 애플리케이션 파일:

- `src/main/java/web/tosunsaeng/domain/blog/api/BlogPostRestController.java`
- `src/main/java/web/tosunsaeng/domain/blog/application/BlogPostService.java`
- `src/main/java/web/tosunsaeng/domain/blog/application/BlogPostServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/converter/BlogPostConverter.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/entity/BlogPost.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/enums/BlogPostStatus.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/policy/BlogPostSlugPolicy.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/dto/BlogPostResponseDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/exception/BlogPostException.java`
- `src/main/java/web/tosunsaeng/domain/blog/config/BlogPostMongoIndexInitializer.java`
- `src/main/java/web/tosunsaeng/global/config/ClockConfig.java`

승인 후 수정할 기존 애플리케이션 파일:

- `src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java` — 블로그 목록·상세·검색 성공 status 추가
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java` — Not Found, 검색 길이, 페이지 범위 오류 추가

승인 후 새로 생성할 테스트 파일:

- `src/test/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/application/BlogPostServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/api/BlogPostRestControllerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/config/BlogPostMongoIndexInitializerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/domain/policy/BlogPostSlugPolicyTest.java`

단계 관리 파일:

- `docs/blog-mvp/plans/PHASE-02-blog-read-search.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

변경하지 않을 파일:

- `src/main/java/web/tosunsaeng/domain/exams/**`
- `src/main/java/web/tosunsaeng/global/common/response/BaseResponse.java`
- `src/main/java/web/tosunsaeng/global/exception/**`
- `src/main/java/web/tosunsaeng/TosunsaengApplication.java`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- 기존 `src/test/**`
- `build.gradle`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`

예상 파일 밖의 소스·설정 변경이 필요하면 구현을 중단하고 차이와 선택지를 사용자에게 보고한다.

## API 계약

### 공통 페이지 규칙

- `page` 기본값은 `0`, 허용 범위는 0 이상이다.
- `size` 기본값은 `10`, 확정 허용 범위는 `1..100`이다.
- page 음수, size 1 미만, size 100 초과를 각각 명확한 블로그 pagination 오류로 반환한다.
- 목록과 검색의 `result`는 `posts`, `page`, `size`, `totalPages`, `totalElements`, `hasNext`를 이 순서의 의미로 포함한다.
- `totalElements`는 `long`, 나머지 page 수치는 `int`, `hasNext`는 boolean으로 정의한다.

### `GET /api/posts?page=0&size=10`

- 공개 게시글만 `publishedAt DESC`, `createdAt DESC`, `_id DESC`로 안정 정렬해 조회한다.
- 각 `posts` 항목은 `slug`, `title`, `summary`, `thumbnailUrl`, `authorName`, `publishedAt`만 노출한다.
- `contentMarkdown`, `id`, `status`, 내부 관련 slug 목록은 목록 응답에 포함하지 않는다.
- 성공 시 HTTP 200과 `SuccessStatus.BLOG_POST_LIST`를 사용한다.

### `GET /api/posts/{slug}`

- slug와 공개 조건을 하나의 Repository query에 적용한다.
- 상세 result는 `slug`, `title`, `summary`, `contentMarkdown`, `thumbnailUrl`, `authorName`, `seoTitle`, `seoDescription`, `publishedAt`, `updatedAt`, `relatedPosts`를 포함한다.
- `relatedPosts` 항목은 `slug`, `title`, `summary`, `thumbnailUrl`, `publishedAt`만 포함하고 최대 3개다.
- 존재하지 않는 slug와 비공개 상태는 동일한 HTTP 404 및 동일한 오류 code/message를 반환한다.
- 외부 응답에 `id`, `status`, `createdAt`, `relatedPostSlugs`를 노출하지 않는다.
- 성공 시 HTTP 200과 `SuccessStatus.BLOG_POST_DETAIL`을 사용한다.

### `GET /api/posts/search?q=토익스피킹&page=0&size=10`

- 검색 대상은 title 하나뿐이다.
- `q`는 optional binding 후 Service에서 null 여부와 정규화를 검사해 누락된 경우에도 블로그 검색 길이 오류로 통일한다.
- 정규화된 검색어와 공개 조건을 적용하고 목록과 같은 `publishedAt DESC`, `createdAt DESC`, `_id DESC`로 페이지 조회한다.
- 검색 result에는 정규화된 `query`를 posts와 page metadata 앞에 포함한다.
- 결과가 없으면 HTTP 200과 빈 `posts` 배열, 0인 total 값들을 반환한다.
- 성공 시 HTTP 200과 `SuccessStatus.BLOG_POST_SEARCH`를 사용한다.

## MongoDB Document 및 인덱스

collection 이름은 `blog_posts`로 정한다.

| 필드 | Java 타입 | 저장·노출 정책 |
|---|---|---|
| `id` | `String` | `@Id`, 외부 미노출 |
| `slug` | `String` | unique, 외부 식별자 |
| `title` | `String` | 목록·상세·검색 |
| `summary` | `String` | 목록·상세·관련 글 |
| `contentMarkdown` | `String` | Markdown 원문, 상세만 노출 |
| `thumbnailUrl` | `String` | 목록·상세·관련 글 |
| `authorName` | `String` | 목록·상세 |
| `status` | `BlogPostStatus` | `DRAFT`, `PUBLISHED`, `ARCHIVED`; 외부 미노출 |
| `seoTitle` | `String` | 상세만 노출 |
| `seoDescription` | `String` | 상세만 노출 |
| `relatedPostSlugs` | `List<String>` | 관련 글 계산 입력, 외부 미노출 |
| `publishedAt` | `Instant` | 공개 판정·정렬 |
| `createdAt` | `Instant` | 운영 데이터 필드, 이번 응답 미노출 |
| `updatedAt` | `Instant` | 상세 노출 |

`BlogPostSlugPolicy`는 slug가 null 또는 blank가 아니고 예약어 `search`가 아닌지 검사한다. 자동 생성이나 대소문자·하이픈 변환은 하지 않는다. 애플리케이션에서 새 BlogPost를 구성할 때 이 정책을 사용하고, 향후 DB 등록 스크립트가 추가되면 같은 정책을 재사용한다. 직접 MongoDB에 쓰는 작업은 애플리케이션 검증을 우회하므로 운영 데이터 작성자가 동일 규칙을 지켜야 한다.

인덱스는 annotation-only 방식을 사용하지 않고 `BlogPostMongoIndexInitializer`가 `MongoTemplate.indexOps(BlogPost.class).ensureIndex(...)`로 명시적으로 보장하는 방식을 권고한다. 현재 `spring.data.mongodb.auto-index-creation` 설정이 없어서 annotation만으로는 생성이 보장되지 않고, 전역 auto-index 설정을 켜면 다른 Document까지 영향을 받을 수 있기 때문이다.

- `uk_blog_posts_slug`: `{ slug: 1 }`, unique
- `idx_blog_posts_status_published_at`: `{ status: 1, publishedAt: -1 }`
- 초기화 component는 `test` profile에서 비활성화해 기존 외부 연결 없는 context test를 보존한다.
- `ensureIndex`를 동일 이름·정의로 호출하는 idempotent initializer로 만들고, 중복 slug 등 생성 실패는 catch해서 무시하지 않아 애플리케이션 시작을 실패시킨다. 성공 로그에는 index 이름만 남긴다.
- 인덱스 정의는 Mockito 기반 단위 테스트로 이름, key 순서, unique 여부와 초기화 반복 호출의 동일 정의를 검증한다.
- title regex index와 newsletter 관련 인덱스는 이번 Phase에 추가하지 않는다.
- Newsletter 관련 필드는 이번 Document에 추가하지 않는다.

## Repository query

`BlogPostRepository`는 `MongoRepository<BlogPost, String>`와 `BlogPostQueryRepository` fragment를 함께 확장한다. `BlogPostQueryRepositoryImpl`은 MongoTemplate을 사용하며 아래 메서드를 제공한다.

| 메서드 의미 | 핵심 query |
|---|---|
| 공개 목록 | 공개 Criteria + 안정 정렬 + skip/limit + 동일 Criteria count |
| 공개 slug 상세 | `slug == 값` + 공개 Criteria, `findOne` |
| 제목 검색 | escaped title regex + 공개 Criteria + 안정 정렬 + page/count |
| 지정 관련 글 | `slug in (...)` + 공개 Criteria, 단일 조회 |
| 최신 보충 글 | `slug nin (현재 글 + 이미 선택된 글)` + 공개 Criteria + 안정 정렬 + 남은 개수 limit |

공개 Criteria는 Repository 구현의 한 helper에서만 다음 BSON 의미를 만들고 모든 조회 메서드가 재사용한다.

```text
status == PUBLISHED
AND publishedAt != null
AND publishedAt <= now
```

- 안정 정렬은 `publishedAt DESC`, `createdAt DESC`, `_id DESC` 순서이며 목록, 검색, 최신 관련 글 보충에 동일하게 적용한다.
- 목록과 검색 count query는 content query와 정확히 같은 filter를 사용하되 skip/limit만 제외한다.
- 페이지 응답은 조회 결과와 count를 `Page`로 조립하고 Service/Converter에서 API page DTO로 바꾼다.
- 사용자 입력을 받는 regex는 Repository 안에서 반드시 `Pattern.quote(normalizedQuery)` 후 case-insensitive flag를 적용한다.
- 관련 글은 slug별 반복 query를 금지한다.

## 공개 여부 판정 방식

- `ClockConfig`에서 `Clock.systemUTC()` Bean을 제공하고 `BlogPostServiceImpl`이 constructor injection으로 받는다.
- Service method 한 번당 `Instant now = clock.instant()`를 한 번만 구하고 목록 content/count, 상세, 관련 글의 모든 Repository 호출에 같은 now를 전달한다.
- 실제 공개 filter 생성은 Controller나 Converter가 아니라 `BlogPostQueryRepositoryImpl` 한 곳에서 관리한다.
- 공개 경계는 사용자 확정 조건대로 Mongo `$lte`를 사용해 `publishedAt == now`도 공개하고 미래 시각만 제외한다.
- 상세 query 자체에 공개 조건을 포함하므로 존재하지 않음, DRAFT, ARCHIVED, 미래 발행, null publishedAt을 구분할 수 없고 모두 동일한 `BlogPostException`으로 처리된다.

## 관련 글 조회 방식

1. 상세 게시글의 `relatedPostSlugs`에서 null을 제거하고 현재 slug를 제외하며 `LinkedHashSet` 의미로 첫 등장 순서를 유지해 중복 제거한다.
2. 정리된 slug 전체를 `$in` 단일 query로 조회한다. DB 반환 순서는 신뢰하지 않고 `slug -> BlogPost` map을 만든 뒤 원래 지정 순서로 공개 글만 최대 3개 선택한다.
3. 3개보다 부족하면 현재 slug와 이미 선택한 slug를 `$nin`으로 제외하고 최신 공개 글을 남은 개수만큼 한 번에 조회한다.
4. 지정 관련 글과 최신 보충 글 모두 같은 now와 공개 Criteria를 사용한다.
5. 상세 본문 query 외 관련 글 query는 최대 두 번이므로 slug 수만큼 증가하는 N+1이 발생하지 않는다.

## 검색 처리 방식

- `q == null`을 먼저 검사하고 null, 빈 문자열, 공백-only, 정규화 후 2 code point 미만을 같은 최소 길이 오류로 처리한다.
- Java 21의 `String.strip()`으로 앞뒤 Unicode 공백을 제거한다.
- 길이는 사용자에게 보이는 문자 기준을 위해 `codePointCount`로 검사하며 허용 범위는 2..50이다.
- 50 code point를 초과하면 최대 길이 오류를 반환한다.
- 정규화된 문자열 자체를 Service에서 regex로 만들지 않고 Repository가 `Pattern.quote`로 literal 처리한 뒤 case-insensitive regex를 생성한다.
- title 외 summary, Markdown 본문, SEO 필드는 검색하지 않는다.
- 정규화된 검색어와 page/size 검증이 성공한 경우에만 Repository를 호출한다.

## 예외 및 응답

기존 enum 상수와 exams 응답을 변경하지 않고 아래 상수만 추가한다.

| 상황 | HTTP | status 상수 | code | result |
|---|---:|---|---|---|
| 게시글 목록 성공 | 200 | `BLOG_POST_LIST` | `BLOG_200` | page result |
| 게시글 상세 성공 | 200 | `BLOG_POST_DETAIL` | `BLOG_201` | detail result |
| 게시글 검색 성공 | 200 | `BLOG_POST_SEARCH` | `BLOG_202` | search page result |
| 게시글 없음 또는 비공개 | 404 | `_BLOG_POST_NOT_FOUND` | `BLOG_4004` | null로 생략 |
| 검색어 2자 미만·null | 400 | `_BLOG_SEARCH_QUERY_TOO_SHORT` | `BLOG_4001` | null로 생략 |
| 검색어 50자 초과 | 400 | `_BLOG_SEARCH_QUERY_TOO_LONG` | `BLOG_4002` | null로 생략 |
| page < 0 | 400 | `_BLOG_PAGE_NEGATIVE` | `BLOG_4003` | null로 생략 |
| size < 1 | 400 | `_BLOG_SIZE_TOO_SMALL` | `BLOG_4005` | null로 생략 |
| size > 100 | 400 | `_BLOG_SIZE_TOO_LARGE` | `BLOG_4006` | null로 생략 |

- page/size가 숫자로 변환되지 않는 요청은 기존 전역 `COMMON400` 형식을 유지한다.
- `BlogPostException extends GeneralException`이 위 `ErrorStatus`를 전달하고 `GlobalExceptionAdvice`가 기존 방식으로 응답한다.
- `BaseResponse`, `GeneralException`, `GlobalExceptionAdvice`의 구조는 변경하지 않는다.

## API 변경

추가되는 제품 API는 아래 세 GET mapping뿐이다.

- `GET /api/posts`
- `GET /api/posts/{slug}`
- `GET /api/posts/search`

별도 관련 글 endpoint는 만들지 않고 상세 result의 `relatedPosts`에 포함한다. POST, PUT, PATCH, DELETE mapping은 추가하지 않는다. 기존 exams API 경로와 응답은 변경하지 않는다.

## DB 변경

- `blog_posts` collection에 대응하는 새 Document mapping을 추가한다.
- slug unique index와 status/publishedAt compound index를 애플리케이션 시작 시 보장한다.
- migration, 초기 게시글, 운영 데이터, 등록 스크립트는 만들거나 실행하지 않는다.
- 인덱스 생성 전 기존 데이터에 중복 slug가 있으면 unique index 생성이 실패하므로 운영자는 배포 전에 데이터를 별도로 확인해야 하며 Codex는 운영 DB에 접근하지 않는다.

## 상태 전이

- 이번 계획 수립: Phase 02 `TODO` → `PLANNING`, 계획서 `DRAFT`
- 일반 workflow는 DRAFT 완료 시 `AWAITING_APPROVAL`을 사용하지만, 이번 사용자 지시가 Phase 02를 `PLANNING`으로 유지하도록 명시했으므로 계획 제출 시에도 `PLANNING`을 유지한다.
- 사용자가 `2026-07-29` 계획과 확정 결정 사항을 명시적으로 승인해 계획을 `APPROVED`, Phase 02를 `IN_PROGRESS`로 변경한다.
- 승인 범위 구현 후 Phase 02를 `VERIFYING`으로 전환했다.
- 관련 테스트 61개와 기존 테스트 2개, `./gradlew clean test bootJar`, 정적 범위 review가 모두 성공해 계획을 `EXECUTED`, Phase 02를 `DONE`, 현재 단계를 Phase 03 `TODO`로 변경한다.

## 테스트 계획

이번 계획 수립 작업에서는 Java/테스트/Gradle을 수정하거나 테스트를 실행하지 않는다. 승인 후 기존 test dependency만 사용해 아래 검증을 추가한다.

### Service 또는 Repository

1. 공개 게시글 목록 조회: captured Mongo Query에 공통 공개 Criteria가 있고 Service가 page result로 변환하는지 확인한다.
2. 안정 정렬: 목록 Query의 sort BSON이 `publishedAt: -1`, `createdAt: -1`, `_id: -1` 순서인지 확인한다.
3. DRAFT 제외: 공개 Criteria가 status를 정확히 `PUBLISHED`로 제한하는지 확인한다.
4. ARCHIVED 제외: 같은 status 제한으로 ARCHIVED가 포함될 경로가 없는지 확인한다.
5. 미래 발행 글 제외: `publishedAt <= fixedClock.instant()` 조건을 확인한다.
6. publishedAt null 제외: 명시적 non-null Criteria를 확인한다.
7. 공개 slug 상세 조회: slug와 공개 Criteria가 같은 findOne Query에 포함되고 공개 글을 상세 DTO로 변환하는지 확인한다.
8. 비공개 slug Not Found: Repository가 empty를 반환할 때 `_BLOG_POST_NOT_FOUND` 예외인지 확인한다.
9. 관련 글 최대 3개: 관련 후보가 많아도 3개만 반환하는지 확인한다.
10. 현재 글 제외: 지정 목록과 fallback 제외 집합 모두 현재 slug를 포함하는지 확인한다.
11. 관련 글 중복 제외: 중복 slug와 지정/fallback 중복이 한 번만 반환되는지 확인한다.
12. relatedPostSlugs 순서 유지: Repository 반환 순서를 섞어도 지정 순서로 재조립되는지 확인한다.
13. 부족한 관련 글을 최신 글로 보충: 남은 limit와 제외 slug를 사용하고 지정 글 뒤에 fallback 결과가 붙는지 확인한다.

### 검색

14. 제목 부분 검색: Query가 title에만 regex를 적용하는지 확인한다.
15. 검색어 trim: 앞뒤 공백을 제거한 값이 Repository로 전달되는지 확인한다.
16. 2자 미만 검색 거절: null, blank, 1 code point 입력 모두 최소 길이 예외이며 Repository를 호출하지 않는지 확인한다.
17. 50자 초과 검색 거절: 51 code point 입력이 최대 길이 예외인지 확인한다.
18. 정규식 특수문자 안전 처리: `.`, `*`, `[`, `(` 등이 포함된 입력의 captured `Pattern`이 `Pattern.quote` literal인지 확인한다.
19. 검색 결과가 없을 때 빈 배열: empty Page가 성공 BaseResponse의 빈 `posts`와 0 total로 변환되는지 확인한다.
20. 비공개 글 검색 제외: 검색 Query에도 같은 공개 Criteria가 포함되는지 확인한다.

### Controller

21. 목록 BaseResponse JSON: 기본 page/size 전달, 성공 code, result page 필드와 목록 공개 필드만 검증한다.
22. 상세 BaseResponse JSON: 상세 필드와 relatedPosts를 검증하고 status/id가 없는지 확인한다.
23. 검색 BaseResponse JSON: q/page/size 전달, 검색 성공 code와 page result를 검증한다.
24. Not Found 상태와 에러 응답: Service의 `BlogPostException`을 `GlobalExceptionAdvice`가 HTTP 404 및 동일 BaseResponse로 변환하는지 확인한다.
25. 잘못된 page와 size 응답: 음수 page, 0 size, 101 size에 각각 확정된 HTTP 400 code를 확인하고 size 100은 성공하며 비숫자 binding은 기존 `COMMON400`을 확인한다.

### 부정 테스트와 범위 review

26. `POST /api/posts`가 mapping되지 않아 405인지 확인한다.
27. `PUT`과 `PATCH /api/posts/{slug}`가 mapping되지 않아 405인지 확인한다.
28. `DELETE /api/posts/{slug}`가 mapping되지 않아 405인지 확인한다.
29. Controller mapping을 세 GET으로 제한하고 Phase 02 실제 diff에 comment/newsletter package나 API가 없는지 review한다. 댓글·뉴스레터는 후속 Phase에서 정상 추가될 예정이므로 전역적으로 영구 부재를 강제하는 회귀 테스트는 만들지 않는다.

### 인덱스와 전체 회귀

- `BlogPostMongoIndexInitializerTest`에서 slug index의 unique와 compound index key 순서·방향·이름을 검증한다.
- `TosunsaengApplicationTests`와 `ExamsRepositoryScanTest`를 포함한 전체 기존 테스트를 다시 실행한다.
- 승인 구현 완료 후 `./gradlew clean test bootJar`와 `git diff --check`를 실행한다.
- 고정 요구사항, 제외 범위, 승인 계획, 실제 mapping 및 변경 파일을 대조한다.

## 기존 exams 영향

- exams Controller, Service, Converter, DTO, Document, Repository, 예외는 수정하지 않는다.
- `SuccessStatus`와 `ErrorStatus`에는 새 블로그 상수만 append하고 기존 이름, code, message를 유지한다.
- `BaseResponse`와 `GlobalExceptionAdvice`를 재사용하되 수정하지 않아 exams JSON 형태를 보존한다.
- Mongo Repository scan 범위는 이미 전체 domain이므로 `TosunsaengApplication`을 수정하지 않는다.
- 새 `Clock` Bean은 현재 다른 `Clock` Bean이나 exams 주입 지점이 없어 exams 실행 흐름을 바꾸지 않는다.
- 블로그 인덱스 초기화는 `BlogPost` collection만 대상으로 하고 exams collection의 index를 생성·변경·삭제하지 않는다.
- 기존 context test의 외부 Mongo 연결 없는 특성을 보존하기 위해 test profile에서 인덱스 초기화를 실행하지 않는다.

## 위험 요소

- 대소문자 무시 unanchored 부분 regex는 일반 Mongo index로 효율화하기 어려워 데이터가 커지면 검색이 느려질 수 있다. 이번 Phase에서는 요구 동작의 정확성과 안전한 escaping을 우선하고 text index나 검색 엔진을 추가하지 않는다.
- 기존 test dependency만 사용한 Query BSON 단위 테스트는 실제 MongoDB query planner와 serialization 전체를 통합 검증하지 못한다. Testcontainers나 별도 test Mongo 통합 테스트는 `build.gradle` 변경과 Docker 실행 범위에 대한 별도 승인이 필요하다.
- unique slug index는 기존 `blog_posts` 데이터에 중복 또는 여러 missing slug가 있으면 시작 시 생성에 실패할 수 있다. Codex는 운영 DB를 검사하거나 정리할 수 없다.
- 명시적 인덱스 초기화는 비-test 애플리케이션 시작 시 MongoDB 연결을 필요로 한다. DB 일시 장애 시 시작 실패를 허용할지 운영 정책 검토가 필요하다.
- `/api/posts/search`와 `/{slug}`가 같은 depth이므로 `search` slug의 상세 조회는 불가능하다. 운영 데이터 규칙에서 `search`를 예약어로 보장해야 한다.
- 실제 MongoDB 인덱스와 query 동작 통합 테스트는 이번 Phase 필수 범위에서 제외하고 Phase 08 전체 검수 과제로 남긴다.
- 관련 slug 목록이 매우 크거나 stale하면 `$in` query와 메모리 map 비용이 늘 수 있다. 목록은 운영자 관리 데이터라는 전제이며 이번 Phase에서 임의 길이 제한 필드를 추가하지 않는다.
- Markdown 원문은 서버가 렌더링하지 않고 그대로 반환한다. 프론트엔드 렌더링 시 sanitization은 필요하지만 이번 Phase 범위 밖이다.
- MongoDB에 운영자가 직접 넣는 문자열 status, Instant 호환 날짜, 필수 slug 형식이 Java enum/타입과 다르면 역직렬화 또는 조회 문제가 생길 수 있다. 등록 스크립트나 schema validation은 제외 범위다.

## 롤백 방법

- 사용자 변경 여부를 먼저 확인한 뒤 Phase 02에서 새로 추가한 Java와 테스트 파일만 수동 역패치하고, 기존 status enum에서는 Phase 02가 추가한 상수만 수동 제거한다.
- 금지된 `git reset`, `git restore`, `git checkout`, `git clean`은 사용하지 않는다.
- 코드 롤백은 MongoDB에 생성된 collection, 게시글 데이터, 인덱스를 자동 삭제하지 않는다. 인덱스 제거가 필요하면 운영자가 정확한 index 이름과 데이터 영향을 확인해 별도 운영 절차로 결정하며 Codex는 운영 DB 명령을 실행하지 않는다.
- `ClockConfig` 제거 전 다른 Phase나 사용자 코드가 Bean을 사용하게 됐는지 확인한다.
- 관리 문서의 기존 Session Log는 삭제하거나 수정하지 않고 후속 정정 기록을 append한다.

## 완료 조건

- 사용자가 API code 의미, 최대 size 100, `Instant`/UTC Clock, 안정 정렬, slug 정책, programmatic index 초기화, query 단위 테스트 전략을 명시적으로 승인했다.
- 승인된 예상 파일과 범위 안에서만 구현한다.
- BlogPost가 요구된 필드와 상태만 가지며 Newsletter 필드가 없다.
- 모든 목록·상세·검색·관련 글 Repository query가 동일한 `PUBLISHED + publishedAt non-null + publishedAt <= now` 조건을 사용한다.
- 목록과 검색은 publishedAt/createdAt/id 내림차순의 안정 정렬과 올바른 page metadata를 반환하고 목록에 Markdown 본문을 노출하지 않는다.
- 상세는 비공개 원인을 구분하지 않는 동일 Not Found를 반환하고 내부 status를 노출하지 않는다.
- 관련 글은 순서, 중복 제외, 현재 글 제외, 최대 3개, 최신 보충, N+1 방지를 만족한다.
- 검색은 null/strip/2..50 code point/Pattern.quote/case-insensitive/title-only 규칙을 만족한다.
- 세 GET 외 게시글 mapping과 Phase 02 범위 밖 API가 추가되지 않는다.
- 기존 exams 파일, `BaseResponse`, 전역 advice, SecurityConfig, Gradle, application 설정을 변경하지 않는다.
- 계획된 테스트와 기존 테스트가 모두 성공한다.
- `./gradlew clean test bootJar`와 `git diff --check`가 성공한다.
- 고정 제품 요구사항, 제외 범위, 승인 계획, 실제 diff 사이에 미승인 차이가 없다.
- 계획서의 실제 차이·검증 결과와 `IMPLEMENTATION_STATUS.md`를 갱신한 뒤에만 계획 `EXECUTED`, Phase 02 `DONE`으로 변경한다.

## 실제 구현 중 발생한 차이

- DRAFT 제출 뒤 사용자 승인 시 안정 정렬(`publishedAt`, `createdAt`, `id` DESC), 검색 result의 `query`, 분리된 slug 정책, pagination 오류 세분화, initializer 실패 정책, Phase 08 Mongo 통합 검증 이관이 확정됐다. 이는 승인 메시지에 포함된 조건이므로 승인 범위로 반영한다.
- 승인 시 확정된 재사용 가능한 slug 규칙을 위해 예상 파일에 `BlogPostSlugPolicy.java`와 `BlogPostSlugPolicyTest.java`를 추가했다.
- 검색 content/count query의 BSON 동등성 보강 테스트에서 동일 의미의 regex `Pattern`을 두 번 생성한 차이로 1건이 실패했다. `Criteria`를 한 번 생성해 content/count query가 같은 객체를 재사용하도록 구현을 좁게 수정했고 재검증에 성공했다. API, DB filter, 응답 계약의 차이는 없다.
- 승인된 예상 파일과 실제 변경 사이에 그 밖의 차이는 없고 제외 범위를 구현하지 않았다.

## 검증 결과

- 계획 수립 전 필수 관리 문서를 규정 순서로 읽었다.
- 시작 브랜치 `feat/blog-mvp`, 깨끗한 작업 트리, preflight PASS를 확인했다.
- 지정된 exams 전체, 공통 응답, 실제 status package, 전역 예외, 애플리케이션 class, 전체 테스트, `build.gradle`을 읽기 전용으로 분석했다.
- 페이지네이션 선례 부재, `Instant` 영속 시간 선례 부재, Mongo auto-index 설정 부재, 테스트용 Mongo 의존성 부재를 확인했다.
- 이번 계획 수립에서 구현 테스트는 실행하지 않았고 Java·테스트·Gradle·application 파일을 수정하지 않았다.
- 계획서 생성 직후 필수 관리 문서와 새 Phase 02 계획서를 규정 순서로 다시 읽었다.
- 계획서에 `PLANS.md` 필수 heading과 사용자 지정 heading이 모두 있음을 확인했다.
- 변경 파일은 이 계획서와 `IMPLEMENTATION_STATUS.md` 두 개뿐이며 다른 Phase 상태는 변경하지 않았다.
- 두 변경 문서의 trailing whitespace 검색 결과가 없고 `git diff --check`가 성공했다.
- Phase 02는 사용자 지시대로 `PLANNING`, 계획은 `DRAFT`로 유지했으며 구현 테스트와 Gradle build는 계획 전용 작업이라 실행하지 않았다.

### Phase 02 구현 및 검증

- 사용자 명시적 승인 후 계획을 `APPROVED`, Phase 02를 `IN_PROGRESS`로 변경하고 승인 조건을 계획과 Session Log에 기록했다.
- BlogPost 14개 필드와 `DRAFT`, `PUBLISHED`, `ARCHIVED` 상태, `Instant`, UTC `Clock`, `search` 예약어를 포함한 slug 정책을 구현했다.
- MongoTemplate custom Repository의 한 helper에서 `PUBLISHED`, publishedAt exists/non-null/`$lte` 공개 Criteria를 재사용했다.
- 목록·검색·최신 관련 글 보충에 publishedAt/createdAt/`_id` DESC 안정 정렬을 적용하고 목록·검색 content/count query가 같은 filter를 사용하게 했다.
- 지정 관련 글은 `$in` 한 번, 부족한 최신 글은 `$nin`과 limit 한 번으로 조회하도록 구현해 N+1을 만들지 않았다.
- `Pattern.quote`와 case-insensitive title regex, Unicode code point 2..50, strip, 공통 pagination 0/1..100을 구현했다.
- programmatic initializer는 `ensureIndex`로 slug unique와 status/publishedAt 복합 인덱스를 생성하며 예외를 삼키지 않고 test profile에서 비활성화했다.
- 첫 Phase 02 테스트 실행은 57개 모두 성공했다.
- 보강 후 61개 중 검색 content/count Query 동등성 1개가 실패했고 원인을 수정한 뒤 Phase 02 테스트 61개가 모두 성공했다.
- `./gradlew clean test bootJar`: `BUILD SUCCESSFUL`; Phase 02 테스트 61개와 기존 context/exams Repository 테스트 2개, 총 63개가 failures/errors/skipped 0으로 성공했다.
- `git diff --check`: 성공. 신규·변경 문서 및 Java/test 파일의 trailing whitespace 검색 결과도 없었다.
- Controller mapping은 `/api/posts` 아래 GET 세 개뿐이고 POST/PUT/PATCH/DELETE 및 댓글·뉴스레터 API가 없음을 확인했다.
- `build.gradle`, 운영/test application 설정, `SecurityConfig`, 기존 exams 비즈니스 코드는 변경하지 않았다.
- 새 실제 비밀값, Newsletter 필드, Redis, `@Scheduled`, Testcontainers 참조가 없음을 확인했다.
- 실제 MongoDB index/query 통합 테스트는 승인된 제한대로 실행하지 않았고 Phase 08 전체 검수 과제로 남긴다.
- Codex review 결과 고정 요구사항, 승인 조건, 제외 범위, 실제 변경 사이에 미승인 차이가 없다.
