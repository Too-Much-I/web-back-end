# Phase 03: 익명 댓글과 번호 기반 validation

- 상태: EXECUTED

## 목표

공개 조건을 만족하는 블로그 게시글에 한해 익명 댓글 목록 조회와 작성을 제공한다. 브라우저에는 원본 익명 토큰을 HttpOnly 쿠키로 보관하고 MongoDB에는 HMAC-SHA256 결과만 저장해 같은 브라우저의 익명 프로필을 유지한다. 댓글에는 작성 당시 nickname, avatarSeed, avatarImageKey를 snapshot으로 저장하고 공개 응답에는 정적 프로필 이미지의 avatarImageUrl을 함께 제공한다.

댓글 입력은 고정 규칙 1~10을 모두 평가하고, 위반 사항을 중복 없이 ruleNumber 오름차순으로 반환한다. rule 3은 앞뒤 공백 제거를 수행하는 정규화 단계일 뿐 violation으로 반환하지 않는다. 댓글 수정·삭제, 운영자 기능, Redis rate limit, 뉴스레터는 추가하지 않는다.

## 현재 코드 분석

### 작업 시작 조건과 관리 상태

- 실제 현재 브랜치는 feat/blog-mvp다.
- 계획 수립 시작 시 git status --short 출력이 없어 작업 트리가 깨끗했다.
- scripts/codex-preflight.sh는 Current branch: feat/blog-mvp와 Preflight: PASS를 출력했다.
- Phase 00, 01, 02는 모두 DONE이고 Phase 02 계획서는 EXECUTED다.
- 현재 단계는 Phase 03 TODO였고 기존 Phase 03 계획서는 없었다.
- 계획 수립 작업에서는 계획서와 IMPLEMENTATION_STATUS.md만 변경했고 Java, 테스트, Gradle, application 설정을 수정하지 않았다. 2026-07-30 사용자의 조건부 승인 내용을 이 계획에 반영한 뒤 승인 범위 구현을 시작한다.

### 기존 blog 구조와 공개 게시글 판정

- BlogPostRestController는 /api/posts 아래의 GET 세 경로만 제공하며 BaseResponse.onSuccess와 SuccessStatus를 사용한다.
- BlogPostService와 BlogPostServiceImpl이 분리돼 있고, Converter가 Document를 응답 DTO로 변환한다.
- BlogPost는 Instant 기반 publishedAt, createdAt, updatedAt을 사용한다.
- ClockConfig는 Clock.systemUTC() Bean을 이미 제공하므로 Phase 03에서 새 Clock Bean을 만들 필요가 없다.
- BlogPostQueryRepositoryImpl의 publicCriteria가 PUBLISHED, publishedAt exists, publishedAt non-null, publishedAt lte now 조건을 한 곳에서 구성한다.
- BlogPostRepository.findPublicPostBySlug(slug, now)는 존재하지 않는 글, DRAFT, ARCHIVED, 미래 발행 글, publishedAt null 글을 모두 Optional.empty로 숨긴다.
- 댓글 목록과 댓글 작성의 게시글 공개 판정은 이 메서드와 주입된 Clock을 그대로 사용한다. BlogPost 공개 Criteria를 복제하거나 BlogPost Controller를 호출하지 않는다.
- BlogPostServiceImpl의 pagination 검증은 private이므로 댓글 서비스가 이를 호출할 수 없다. 기존 게시글 코드를 리팩터링하지 않고 댓글 서비스가 같은 ErrorStatus와 0 이상 page, 1..100 size 정책을 독립적으로 적용한다.
- BlogPostMongoIndexInitializer는 MongoTemplate과 ensureIndex를 사용하는 programmatic, idempotent, non-test 초기화 선례를 제공한다. 댓글 인덱스도 별도 initializer에서 같은 방식으로 관리한다.

### 기존 exams 및 공통 계층

- exams는 Controller, Service interface, ServiceImpl, static Converter, 중첩 Request/Response DTO, Mongo Document, MongoRepository, 도메인 예외 구조를 사용한다.
- exams에는 익명 쿠키를 생성·조회·갱신하는 선례가 없다.
- BaseResponse는 isSuccess, code, message, result 구조이며 onFailure도 non-null result를 이미 지원한다. 따라서 validation result.violations를 위해 BaseResponse를 수정할 필요가 없다.
- GeneralException은 BaseErrorCode 하나를 보유하고 GlobalExceptionAdvice가 일반 오류를 result 없는 BaseResponse로 변환한다.
- GlobalExceptionAdvice에는 HttpMessageNotReadableException을 포함한 광범위한 Exception handler가 있으며 JSON 파싱 오류 메시지를 result 문자열로 반환하고 로그를 남긴다.
- 댓글 validation은 구조화된 violations가 필요하므로 CommentValidationException 전용 handler가 필요하다. 기존 GlobalExceptionAdvice를 수정하면 exams의 파싱 오류와 일반 예외 응답이 바뀔 수 있으므로 수정하지 않는다.
- SuccessStatus와 ErrorStatus에는 COMMENT 계열 code가 아직 없고 BLOG_200..202, BLOG_4001..4006이 사용 중이다.

### 설정, 쿠키, Redis, 테스트 기반

- global/config와 전체 main/test source에는 기존 ResponseCookie, CookieValue, Set-Cookie 생성 선례가 없다.
- SecurityConfig는 전체 요청을 permitAll로 허용하고 credentials를 허용한다. Phase 03 공개 API에는 별도 인증 변경이 필요하지 않으며 SecurityConfig를 수정하지 않는다.
- CorsConfig와 SecurityConfig가 credentials를 허용하므로 브라우저 클라이언트는 쿠키 사용 시 credentials 옵션을 명시해야 한다.
- RedisConfig와 RedisTemplate은 기존 exams가 사용하지만 댓글 도메인은 이를 주입하거나 호출하지 않는다. Phase 03 기능은 MongoDB와 프로세스 내 검증만으로 구성할 수 있으므로 Redis rate limit을 Phase 04로 완전히 제외할 수 있다.
- 운영 application 설정에는 익명 댓글 secret, cookie, avatar option 설정이 없다. 조건부 승인에 따라 main application.yml에는 환경변수와 ConfigurationProperties를 연결하는 최소 binding만 추가한다.
- 기존 S3Config는 ap-northeast-2 리전의 S3Presigner를 만들고 exams가 AWS_S3_BUCKET_NAME을 사용한다. 프로필 이미지는 to-teacher-web-blog bucket의 character-image/ 아래에 운영자가 미리 배치하는 정적 자산이므로 기존 exams bucket 설정이나 presigner를 재사용하지 않는다.
- 정적 프로필 이미지는 비공개 S3와 CloudFront OAC 구조를 전제로 하며 S3 upload, list, HEAD, presigned URL 생성 없이 외부 설정의 object key와 CloudFront base URL로 응답 URL만 조립한다. 실제 CloudFront 주소와 운영 파일명은 코드에 하드코딩하지 않는다.
- Spring Boot 3.4.2, Java 21, starter-web, validation, data-mongodb, starter-test가 이미 있어 새 Gradle 의존성이 필요하지 않다.
- 테스트는 실제 MongoDB나 Testcontainers 없이 Mockito, AssertJ, MockMvc, Mongo Query BSON, IndexDefinition을 검증하는 Phase 02 방식을 재사용할 수 있다.

## 구현 범위

사용자 승인 후 다음 순서로만 구현한다.

1. AnonymousVisitor, BlogComment, CommentStatus, CommentRule 모델을 추가한다.
2. 익명 session 설정, 암호학적 토큰 생성, HMAC-SHA256, cookie factory, SecureRandom 기반 프로필 생성기, 정적 avatar image catalog와 URL resolver를 추가한다.
3. AnonymousVisitor Repository와 방문자 resolve/regenerate Service를 추가한다.
4. BlogComment Repository custom query와 댓글용 programmatic index initializer를 추가한다.
5. CommentValidator와 반복 패턴 정책을 추가하고 고정 규칙 1~10을 순서대로 평가한다.
6. Request/Response DTO, Converter, BlogComment Service interface/implementation을 추가한다.
7. 댓글 목록, 댓글 작성, 닉네임 재생성 API만 Controller에 추가한다.
8. CommentValidationException과 댓글 Controller 범위의 전용 advice를 추가한다.
9. 기존 enum에는 충돌하지 않는 댓글 성공·오류 상수만 append한다.
10. Repository, index, visitor, validator, Service, Controller 테스트와 제외 API 부정 테스트를 추가한다.
11. 관련 테스트, 전체 회귀, bootJar, diff 검사와 범위 review를 수행한다.

## 제외 범위

- 댓글 수정 API와 기능
- 댓글 삭제 API와 기능
- 운영자 댓글 삭제
- 댓글 숨김과 복원
- 자동 스팸 상태 전이
- Redis rate limit
- IP 저장 또는 IP 기반 제한
- 최근 동일 댓글 DB 비교
- 시간당 작성 횟수 제한
- CAPTCHA
- 대댓글
- 좋아요
- 사용자가 올리는 댓글 이미지와 첨부 파일
- S3 객체 upload, delete, list, HEAD 및 presigned URL 발급
- URL 허용
- 회원 댓글과 로그인 연동
- 게시글 기능 또는 공개 Criteria 변경
- 게시글 생성·수정·삭제 API
- 허니팟 차단 실행
- 뉴스레터와 이메일 발송
- 내부 운영 API
- SecurityConfig 변경
- RedisConfig 변경
- Scheduling 작업
- 실제 MongoDB 통합 테스트
- Testcontainers
- build.gradle 변경
- application.yml의 승인된 properties binding 밖 변경
- 기존 exams 비즈니스 로직과 응답 변경
- 관련 없는 리팩터링

website 필드는 향후 호환을 위해 optional JSON 필드로 수신하되 Phase 03에서는 검사, 저장, 로그 출력을 하지 않는다. 별도 고정 rule code가 없는 숨은 차단을 추가하지 않고 실제 허니팟 판정은 Phase 04로 미룬다.

## 예상 변경 파일

### 예상 생성·수정 파일

#### 예상 생성 파일

애플리케이션:

- src/main/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestController.java
- src/main/java/web/tosunsaeng/domain/blog/comment/api/support/AnonymousCookieFactory.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentService.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorService.java
- src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousSessionConfig.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousSessionProperties.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousProfileConfig.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousProfileProperties.java
- src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializer.java
- src/main/java/web/tosunsaeng/domain/blog/comment/converter/BlogCommentConverter.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/entity/AnonymousVisitor.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/entity/BlogComment.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/CommentRule.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/CommentStatus.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousAvatarImageCatalog.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousProfileGenerator.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AvatarImageUrlResolver.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousTokenManager.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentSpamPatternPolicy.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentValidator.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/AnonymousVisitorRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepository.java
- src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImpl.java
- src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentRequestDTO.java
- src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentResponseDTO.java
- src/main/java/web/tosunsaeng/domain/blog/comment/exception/BlogCommentException.java
- src/main/java/web/tosunsaeng/domain/blog/comment/exception/CommentValidationException.java
- src/main/java/web/tosunsaeng/domain/blog/comment/exception/BlogCommentExceptionAdvice.java

테스트:

- src/test/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestControllerTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImplTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImplTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializerTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousAvatarImageCatalogTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousProfileGeneratorTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousTokenManagerTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AvatarImageUrlResolverTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentValidatorTest.java
- src/test/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImplTest.java

#### 예상 수정 파일

- src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java
- src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java
- .env.example
- src/main/resources/application.yml
- src/test/resources/application-test.yml
- docs/blog-mvp/plans/PHASE-03-anonymous-comments.md
- docs/blog-mvp/IMPLEMENTATION_STATUS.md

.env.example에는 실제 값이 아닌 BLOG_ANONYMOUS_TOKEN_SECRET, BLOG_ANONYMOUS_COOKIE_SECURE, BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS, BLOG_ANONYMOUS_AVATAR_BASE_URL 변수명과 안전한 설명만 추가한다. main application.yml에는 이 네 환경변수의 최소 binding만 추가하고 운영 avatar option은 외부 설정으로 받는다. application-test.yml에는 안전한 더미 secret, https://cdn.example.test base URL과 두 개 이상의 예시 avatar option만 추가한다.

예상 파일 밖의 소스·설정 변경이 필요하면 구현을 중단하고 차이, 영향, 선택지를 사용자에게 보고한다.

## API 계약

### 공통 페이지 정책

- page 기본값: 0
- page 허용 범위: 0 이상
- size 기본값: 20
- 제안 최대 size: 100
- size 허용 범위: 1..100
- page와 size 숫자 형식 오류는 기존 COMMON400을 유지한다.
- 범위 오류는 기존 _BLOG_PAGE_NEGATIVE, _BLOG_SIZE_TOO_SMALL, _BLOG_SIZE_TOO_LARGE를 재사용한다.
- 댓글 목록 result는 comments, page, size, totalPages, totalElements, hasNext를 포함한다.

### GET /api/posts/{slug}/comments?page=0&size=20

- Clock에서 한 번 구한 now로 BlogPostRepository.findPublicPostBySlug를 호출한다.
- 공개 게시글이 아니면 게시글 상세와 동일한 HTTP 404, BLOG_4004, 동일 메시지를 반환한다.
- 공개 게시글의 id를 postId로 사용해 VISIBLE 댓글만 조회한다.
- 정렬은 createdAt DESC, _id DESC로 안정화한다. API가 보장하는 주 정렬은 createdAt DESC다.
- 응답 항목은 id, nickname, avatarSeed, avatarImageUrl, content, createdAt만 포함한다.
- anonymousVisitorId, tokenHash, isMine, 수정·삭제 가능 여부, IP 정보, status와 hidden 필드는 노출하지 않는다.
- 성공은 HTTP 200, 제안 상수 BLOG_COMMENT_LIST, code COMMENT_200을 사용한다.

응답 result 의미:

    {
      "comments": [
        {
          "id": "comment-id",
          "nickname": "차분한 돌고래",
          "avatarSeed": "public-avatar-seed",
          "avatarImageUrl": "https://cdn.example.test/character-image/example-otter-v1.webp",
          "content": "Part 2에서는 몇 문장 정도 말하면 되나요?",
          "createdAt": "2026-07-30T01:00:00Z"
        }
      ],
      "page": 0,
      "size": 20,
      "totalPages": 1,
      "totalElements": 1,
      "hasNext": false
    }

### POST /api/posts/{slug}/comments

요청:

    {
      "content": "Part 2에서는 몇 문장 정도 말하면 되나요?",
      "website": ""
    }

- content의 JSON node 타입을 먼저 판정하며 문자열만 정상 입력으로 인정한다.
- 정상 문자열은 Unicode-aware strip으로 앞뒤 공백을 제거한다.
- website는 optional이며 Phase 03에서는 무시하고 저장하지 않는다.
- 공개 게시글 여부를 rule 10으로 평가하고 content 규칙과 함께 모든 violation을 수집한다.
- violation이 하나라도 있으면 AnonymousVisitor와 BlogComment save를 모두 호출하지 않는다.
- violation이 없을 때만 cookie token을 resolve하고 VISIBLE 댓글을 저장한다.
- 댓글에는 현재 방문자의 nickname, avatarSeed, avatarImageKey를 snapshot으로 저장한다.
- 성공 result에는 id, nickname, avatarSeed, avatarImageUrl, 정규화된 content, createdAt만 포함한다.
- 기존 유효 cookie이면 같은 방문자를 사용한다.
- cookie가 없거나 형식이 잘못됐거나 DB에 대응 방문자가 없으면 새 방문자와 새 cookie를 만든다.
- 댓글 작성은 리소스 생성이므로 HTTP 201을 권고한다. Set-Cookie header가 필요해 ResponseEntity 안에 BaseResponse를 담는다.
- 제안 상수 BLOG_COMMENT_CREATED, code COMMENT_201을 사용한다.

### POST /api/comments/nickname/regenerate

- request body는 없다.
- 유효한 cookie가 있으면 같은 AnonymousVisitor의 nickname, avatarSeed, avatarImageKey, lastSeenAt을 갱신한다.
- cookie가 없거나 유효하지 않으면 새 AnonymousVisitor를 만들고 Set-Cookie를 반환한다.
- 기존 BlogComment 문서는 어떤 경우에도 update하지 않는다.
- 응답 result는 nickname, avatarSeed, avatarImageUrl을 포함한다.
- 원본 token, tokenHash, visitor id는 JSON에 포함하지 않는다.
- 성공은 HTTP 200, 제안 상수 ANONYMOUS_PROFILE_REGENERATED, code COMMENT_202를 사용한다.

### 제공하지 않는 API

- PATCH /api/comments/{commentId}
- DELETE /api/comments/{commentId}
- 운영자 댓글 삭제
- 댓글 숨김·복원
- 대댓글
- 좋아요
- 사용자 이미지·링크 첨부

존재하지 않는 method는 Spring MVC 결과에 따라 404 또는 405일 수 있으나 어느 경우에도 Service나 Repository가 호출되지 않아야 한다.

## AnonymousVisitor 모델

collection 이름은 anonymous_visitors로 제안한다.

| 필드 | Java 타입 | 정책 |
|---|---|---|
| id | String | Mongo @Id, 외부 미노출 |
| tokenHash | String | HMAC-SHA256 Base64URL 결과, unique, 외부 미노출 |
| nickname | String | 현재 익명 프로필 |
| avatarSeed | String | nickname과 독립 생성, 응답 가능 |
| avatarImageKey | String | character-image/ 아래의 사전 등록 정적 이미지 key, 외부 미노출 |
| createdAt | Instant | 최초 생성 시 Clock 시각 |
| lastSeenAt | Instant | 성공 댓글 작성과 재생성 시 Clock 시각 |

- 원본 token은 Document 필드로 만들지 않는다.
- nickname, avatarSeed, avatarImageKey를 한 번에 갱신하는 메서드만 제공하고 임의 setter를 열지 않는다.
- 재생성은 AnonymousVisitor만 변경하며 기존 댓글 snapshot을 조회하거나 일괄 수정하지 않는다.
- cookie 삭제나 secret 회전으로 연결이 끊긴 기존 방문자 문서는 자동 삭제하지 않는다. TTL 정책은 별도 합의 없이는 추가하지 않는다.

## BlogComment 모델

collection 이름은 blog_comments로 제안한다.

| 필드 | Java 타입 | 정책 |
|---|---|---|
| id | String | Mongo @Id, 응답 식별자 |
| postId | String | 공개 BlogPost의 Mongo id |
| anonymousVisitorId | String | 작성 방문자 id, 외부 미노출 |
| nickname | String | 작성 시점 snapshot |
| avatarSeed | String | 작성 시점 snapshot |
| avatarImageKey | String | 작성 시점 정적 이미지 key snapshot, 외부에는 URL로 변환 |
| content | String | strip 후 검증을 통과한 일반 텍스트 |
| status | CommentStatus | Phase 03 정상 저장은 VISIBLE |
| createdAt | Instant | Clock 시각 |
| updatedAt | Instant | 최초 저장 시 createdAt과 동일 |
| hiddenAt | Instant | Phase 03에서는 null |
| hiddenReason | String | Phase 03에서는 null |

CommentStatus는 VISIBLE, PENDING, HIDDEN을 정의한다. PENDING과 HIDDEN은 Phase 04 모델 호환용 상태값일 뿐 Phase 03에서 상태 전이, 자동 스팸 판정, 숨김 API를 구현하지 않는다.

## 쿠키와 토큰 처리

### 책임 위치 비교

| 선택지 | 장점 | 단점 |
|---|---|---|
| Controller 단독 | HTTP cookie를 직접 다루기 쉽다 | token hash, DB resolve, 프로필 생성 orchestration이 Controller에 섞인다 |
| Service 단독 | 유스케이스가 한 곳에 모인다 | ResponseCookie와 HTTP header가 application 계층에 침투한다 |
| 별도 Resolver와 cookie factory | token/visitor 처리와 HTTP cookie 직렬화를 분리하고 테스트하기 쉽다 | 내부 result 타입이 하나 더 필요하다 |

권고안은 역할을 나누는 세 번째 방식이다.

- Controller: @CookieValue로 anon_session 원문을 읽고, 내부 결과가 새 token 발급을 요구할 때만 Set-Cookie header를 붙인다.
- AnonymousCookieFactory: 이름, HttpOnly, SameSite, Path, Secure, Max-Age를 한 곳에서 구성한다.
- AnonymousVisitorService: token 형식 검사, HMAC lookup, 새 방문자 생성, lastSeenAt, profile regenerate를 담당한다.
- BlogCommentService: content와 게시글 validation을 먼저 완료한 뒤 AnonymousVisitorService를 호출하고 댓글을 저장한다.
- 내부 resolution 결과는 visitor와 optional rawTokenToSet을 가질 수 있지만 public DTO에는 raw token 필드를 두지 않는다.

### token과 HMAC

- 새 token은 SecureRandom으로 32 byte를 만들고 Base64 URL-safe, no-padding 문자열로 인코딩한다.
- cookie 입력은 예상 Base64URL 형식과 길이를 먼저 검사한다. 비정상적으로 긴 값이나 decode 실패는 invalid cookie로 처리한다.
- HMAC-SHA256 key는 필수 환경변수 BLOG_ANONYMOUS_TOKEN_SECRET에서 주입한다.
- secret은 UTF-8 기준 최소 32 byte를 요구하며 비어 있거나 더 짧으면 애플리케이션 시작을 실패시킨다.
- Mongo tokenHash는 HMAC 결과를 Base64 URL-safe, no-padding으로 저장한다.
- HMAC Mac 인스턴스는 thread-safe라고 가정해 공유하지 않고 호출별 생성 또는 안전한 factory로 생성한다.
- tokenHash는 lookup key이며 원본 token을 복원할 수 없다.
- token collision 또는 hash unique 충돌 시 제한된 횟수만 새 token 생성을 재시도하고 계속 실패하면 저장을 중단해 내부 오류로 처리한다.
- 원본 token과 tokenHash를 로그, exception message, Sentry context, 응답 body에 넣지 않는다.
- test profile은 application-test.yml의 명백한 32 byte 이상 더미 secret을 사용하고 단위 테스트도 명시적인 test key를 전달한다.

### cookie 속성

- 이름: anon_session
- HttpOnly: true
- SameSite: Lax
- Path: /
- Domain: 지정하지 않는 host-only cookie
- Max-Age 기본값: 180일, 15,552,000초
- Secure: 기본 true
- 로컬 HTTP: BLOG_ANONYMOUS_COOKIE_SECURE=false로 명시
- BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS는 양수만 허용하고 overflow 없이 초로 변환한다.
- 새 방문자 생성 또는 invalid cookie 교체 때 Set-Cookie를 반환한다. 유효 cookie 사용 때마다 원문을 재전송하지 않는다.
- 클라이언트는 cross-origin 호출에서 credentials를 포함해야 한다.

AnonymousProfileProperties는 main application.yml의 최소 placeholder binding을 통해 BLOG_ANONYMOUS_TOKEN_SECRET, BLOG_ANONYMOUS_COOKIE_SECURE, BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS, BLOG_ANONYMOUS_AVATAR_BASE_URL과 외부 avatar-options 목록을 받는다. 운영과 test 모두 같은 설정 검증을 사용하며 test profile은 application-test.yml의 안전한 더미 값과 두 개 이상의 option으로 실제 외부 연결을 피한다.

## 랜덤 프로필 생성

- nickname은 코드에서 관리하는 immutable 형용사 목록 중 하나와 외부 설정 avatar option의 noun을 조합해 형용사 + 공백 + 명사로 만든다.
- 형용사 예시 후보는 차분한, 꼼꼼한, 명랑한, 용감한, 따뜻한이며 실제 목록은 null, blank, 중복이 없고 두 개 이상인지 테스트한다.
- noun과 imageKey는 하나의 immutable avatar option으로 묶어 항상 1:1로 선택한다. nickname이 수달이면 같은 option의 수달 이미지만 사용한다.
- 형용사 목록이나 avatar option이 유효하지 않으면 임의 fallback profile로 숨기지 않고 애플리케이션 초기화를 실패시킨다.
- production random source는 SecureRandom Bean을 사용하고 테스트에서는 결정적 stub을 주입한다.
- avatarSeed는 nickname 문자열에서 파생하지 않고 별도의 16 random byte를 Base64URL로 인코딩한다.
- 정적 avatar image는 CloudFront OAC 뒤의 비공개 S3 character-image/ prefix 아래에 운영자가 미리 올린다. 애플리케이션은 업로드하거나 bucket을 조회하지 않는다.
- AnonymousAvatarImageCatalog는 외부 설정 avatar-options를 immutable copy로 관리한다. option이 2개 미만이거나 noun/imageKey가 blank이거나 noun/imageKey가 중복이면 시작을 실패시킨다.
- imageKey는 character-image/{filename} 상대 경로만 허용한다. 선행 /, .., http/https 절대 URL, query, fragment, 빈 filename을 거절한다.
- 실제 운영 object filename과 확장자는 외부 설정에서 제공하고 코드나 main application.yml에 임의 기본값을 두지 않는다. application-test.yml의 example-otter-v1.webp와 example-penguin-v1.webp는 테스트 전용이다.
- Mongo Document에는 배포 host에 종속되는 전체 URL 대신 avatarImageKey를 저장한다. 응답의 avatarImageUrl은 AvatarImageUrlResolver가 BLOG_ANONYMOUS_AVATAR_BASE_URL과 key를 안전하게 결합해 만든다.
- base URL은 absolute HTTPS, host 존재, user-info/query/fragment 부재를 검증하고 운영 요청의 Host header나 사용자 입력으로 만들지 않는다.
- 공개 S3 URL과 presigned URL은 사용하지 않는다. base URL은 배포 환경의 CloudFront 주소이며 실제 값을 하드코딩하지 않고 test에서는 https://cdn.example.test를 사용한다.
- 재생성은 기존 adjective와 다른 adjective, 기존 option과 다른 option, 새로운 avatarSeed를 제한된 횟수 안에 선택한다. 따라서 nickname, avatarImageKey, avatarImageUrl이 함께 바뀐다.
- 제한 재시도 후에도 새 adjective, option, seed를 만들지 못하면 기존 값을 성공으로 반환하지 않고 내부 생성 실패로 처리한다.
- 확률적 비동일성을 테스트하지 않는다. 결정적 random stub이 반환한 새 adjective-option-seed가 visitor에 함께 반영되고 기존 comment snapshot에는 반영되지 않는지를 검증한다.
- avatarImageUrl에는 raw token, tokenHash, secret 또는 사용자 입력을 포함하지 않는다. URL 생성 중 S3 SDK, presigner, AWS credential과 network를 사용하지 않는다.

## CommentRule 설계

CommentRule enum은 아래 번호, code, 기본 메시지를 변경 없이 보유한다.

| 번호 | ruleCode | 기본 메시지 |
|---:|---|---|
| 1 | COMMENT_MIN_LENGTH | 댓글은 최소 2자 이상 입력해 주세요. |
| 2 | COMMENT_MAX_LENGTH | 댓글은 최대 500자까지 입력할 수 있습니다. |
| 3 | COMMENT_TRIM | 댓글 앞뒤 공백을 제거합니다. |
| 4 | COMMENT_PLAIN_TEXT_ONLY | 댓글은 JSON 문자열 형식의 일반 텍스트만 입력해 주세요. |
| 5 | COMMENT_HTML_NOT_ALLOWED | HTML은 댓글에 사용할 수 없습니다. |
| 6 | COMMENT_MARKDOWN_NOT_ALLOWED | Markdown 문법은 댓글에 사용할 수 없습니다. |
| 7 | COMMENT_URL_NOT_ALLOWED | URL과 이메일 주소는 댓글에 입력할 수 없습니다. |
| 8 | COMMENT_EMPTY | 댓글 내용을 입력해 주세요. |
| 9 | COMMENT_SPAM_PATTERN | 과도하게 반복되는 내용을 줄여 주세요. |
| 10 | COMMENT_POST_NOT_PUBLIC | 댓글을 작성할 수 있는 공개 게시글을 찾을 수 없습니다. |

Violation DTO는 ruleNumber, ruleCode, message만 노출한다. enum declaration 순서에 의존하지 않고 ruleNumber로 정렬하며 동일 ruleNumber는 한 번만 포함한다.

### Rule 1, 2, 3, 8

- JSON 문자열이면 String.strip()으로 Unicode-aware 앞뒤 공백 제거를 수행한다.
- 길이는 normalized.codePointCount로 계산한다.
- 0 code point이면 rule 1과 rule 8을 함께 추가한다.
- 1 code point이면 rule 1만 추가한다.
- 2..500 code point는 길이 규칙을 통과한다.
- 501 이상이면 rule 2를 추가한다.
- rule 3은 정규화 수행 사실만 나타내며 violations에 추가하지 않는다.
- 저장과 성공 응답에는 normalized content만 사용한다.

### Rule 4

- Request DTO는 content를 JsonNode로 받아 Jackson이 객체, 배열, 숫자, boolean을 String으로 강제 변환하지 않게 한다.
- content.isTextual()인 경우만 정상 문자열로 취급한다.
- missing, null, 객체, 배열, 숫자, boolean이면 rule 4를 추가하고 안전한 normalized empty 값으로 나머지 규칙을 계속 평가한다.
- 이 경우 rule 1과 rule 8도 조건에 맞으면 함께 반환하므로 일반적인 null/non-string 결과는 1, 4, 8 순서가 된다.
- JSON 문법 자체가 깨졌거나 request body가 읽히지 않는 경우 댓글 Controller 범위의 HttpMessageNotReadableException handler가 최소 rule 4 violation을 반환한다.
- DTO schema에는 content가 string이라는 외부 계약을 명시한다.

### Rule 5

- HTML을 제거한 뒤 저장하지 않고 요청을 거절한다.
- 대소문자를 구분하지 않고 일반 start/end/self-closing tag를 검출한다. script, a, img, iframe, div를 포함하되 특정 tag allowlist를 만들지 않는다.
- HTML comment, doctype, processing instruction 형태도 차단한다.
- javascript: scheme과 onerror=, onclick= 같은 실행 가능한 event handler assignment를 차단한다.
- encoded markup 우회를 막기 위해 named entity와 decimal/hex numeric entity 형식을 모두 차단한다.
- 따라서 &lt;, &gt;, &amp;, &#60;, &#x3c;처럼 semicolon까지 갖춘 entity는 rule 5다.
- 일반 문장의 단독 <, >, &, 수학 비교식은 tag/entity 형태가 아니면 허용한다.

### Rule 6

명확한 Markdown 구조만 차단한다.

- fenced code block: line 시작의 triple backtick 또는 triple tilde
- ATX heading: line 시작의 1..6개 # 다음 공백
- blockquote: line 시작의 >
- unordered/ordered list: line 시작의 -, +, *, 숫자 다음 공백
- Markdown link와 image
- reference link
- paired emphasis: *text*, **text**, _text_, __text__
- inline code backtick pair
- strikethrough pair
- horizontal rule

다음은 허용 예시다.

- 시험에서 #1이 가장 어려워요.
- 3 * 4만큼 연습했어요.
- 점수는 5 > 3이라고 생각해요.
- 정말 정말 좋아요!
- 괄호, 대시, 단일 별표 같은 일반 특수문자 문장

패턴은 line boundary와 paired delimiter를 요구해 일반 문장의 단일 특수문자를 과도하게 막지 않는다.

### Rule 7

- http://와 https:// scheme을 대소문자 구분 없이 차단한다.
- www. 접두 도메인을 차단한다.
- example.com, sub.example.co.kr 형태의 일반 ASCII domain을 label/TLD 경계로 검출한다.
- 단순 contains("http")는 사용하지 않는다.
- 개인정보와 스팸 방지 목적상 user@example.com 형태의 이메일 주소도 rule 7로 차단한다.
- 공백으로 분리된 name @ example 문장처럼 명확한 이메일 형식이 아닌 입력은 이메일 패턴만으로 차단하지 않는다.
- Markdown link에 URL이 있으면 rule 6과 rule 7을 모두 반환한다.
- HTML anchor에 URL이 있으면 rule 5와 rule 7을 모두 반환한다.

### Rule 9

Phase 03의 rule 9는 현재 문자열 내부의 연속 반복만 검사한다.

- 동일 Unicode code point 10회 이상 연속을 차단한다.
- detection용으로 연속 whitespace를 하나로 정규화하되 저장 문자열은 변경하지 않는다.
- 2..20 code point의 동일 contiguous 구문이 연속 5회 이상 반복되면 차단한다.
- 공백으로 구분된 1..5개 token 묶음도 총 2..20 code point 범위에서 동일 묶음이 연속 5회 이상이면 차단한다.
- content 최대 500 code point를 전제로 code point 배열과 token 배열을 순회하는 bounded algorithm을 사용하고 사용자 입력 backtracking regex를 피한다.
- 상수는 MAX_ALLOWED_IDENTICAL_RUN=9, MIN_REPEAT_UNIT=2, MAX_REPEAT_UNIT=20, MAX_REPEAT_TOKENS=5, REPEAT_COUNT=5처럼 별도 정책 class에 명시한다.
- 허용 예시: ㅋㅋㅋ, 하하하, 정말 정말 좋아요!
- 차단 예시: 동일 ㅋ 10회, 도배 도배 도배 도배 도배, ababababab
- IP, cookie별 횟수, 최근 동일 댓글, 시간 창 검사는 Phase 04로 미룬다.

### Rule 10

- Service method마다 Clock.instant()를 한 번 구한다.
- BlogPostSlugPolicy가 거절하는 slug는 Repository를 호출하지 않고 비공개로 취급한다.
- 유효 slug는 BlogPostRepository.findPublicPostBySlug(slug, now)를 호출한다.
- Optional.empty이면 존재하지 않음, DRAFT, ARCHIVED, 미래 발행, publishedAt null을 구분하지 않고 rule 10 하나로 표현한다.
- publishedAt == now는 Phase 02 public Criteria의 lte 때문에 공개다.
- 댓글 목록에서는 같은 empty 결과를 기존 BLOG_4004 Not Found로 반환한다.
- 댓글 작성에서는 다른 content violation과 함께 rule 10을 validation result에 포함한다.

## Validation 처리 순서

1. Request content JsonNode의 존재 여부와 textual 타입을 판정한다.
2. 문자열이면 strip하고 아니면 안전한 empty normalized 값과 rule 4 후보를 만든다.
3. BlogPostSlugPolicy와 BlogPostRepository를 이용해 같은 now 기준의 공개 게시글 여부를 구한다.
4. rule 1부터 10까지 번호순으로 독립 평가한다.
5. rule 3은 normalize만 수행하고 violation에는 넣지 않는다.
6. EnumMap 또는 ruleNumber key map으로 동일 규칙 중복을 제거한다.
7. 최종 violation을 ruleNumber 오름차순 불변 List로 만든다.
8. 하나라도 있으면 CommentValidationException을 던진다.
9. validation 성공 후에만 AnonymousVisitor를 resolve/create한다.
10. 마지막으로 VISIBLE BlogComment를 한 번 save한다.

null 또는 non-string일 때 HTML, Markdown, URL, spam 검사에는 empty normalized 문자열을 전달해 NullPointerException이나 ClassCastException이 발생하지 않게 한다. 각 detector는 false만 반환하고 rule 1, 4, 8을 계속 수집한다.

## Validation 실패 응답

제안 오류 상수는 _COMMENT_VALIDATION_FAILED, HTTP 400, code COMMENT_4001이며 메시지는 "댓글 작성 규칙을 확인해 주세요."로 정한다.

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
          },
          {
            "ruleNumber": 8,
            "ruleCode": "COMMENT_EMPTY",
            "message": "댓글 내용을 입력해 주세요."
          }
        ]
      }
    }

권고 방식:

- CommentValidationException은 GeneralException을 확장하고 정렬·중복 제거가 끝난 violations를 추가로 보유한다.
- BlogCommentExceptionAdvice는 BlogCommentRestController에만 적용되는 RestControllerAdvice로 만든다.
- 이 advice에 CommentValidationException과 HttpMessageNotReadableException의 구체 handler만 둔다.
- advice 우선순위를 GlobalExceptionAdvice보다 높게 명시해 댓글 parsing/validation 오류만 구조화 응답으로 처리한다.
- handler는 BaseResponse.onFailure(ErrorStatus._COMMENT_VALIDATION_FAILED, result)를 사용한다.
- 그 밖의 BlogCommentException은 기존 GlobalExceptionAdvice와 result 없는 응답을 그대로 사용한다.
- BaseResponse, GeneralException, GlobalExceptionAdvice는 수정하지 않는다.

이 방식은 제한된 공통 payload 확장보다 변경 범위가 작고 exams의 validation, JSON parsing, GeneralException 응답을 바꾸지 않는다.

## Repository query

### AnonymousVisitorRepository

- MongoRepository<AnonymousVisitor, String>를 확장한다.
- Optional<AnonymousVisitor> findByTokenHash(String tokenHash)를 제공한다.
- raw token 기반 query는 만들지 않는다.

### BlogCommentRepository

- MongoRepository<BlogComment, String>와 BlogCommentQueryRepository fragment를 함께 확장한다.
- custom 구현은 MongoTemplate을 사용한다.
- findVisibleCommentsByPostId(postId, pageable)는 postId 일치, status VISIBLE 조건을 적용한다.
- content query는 createdAt DESC, _id DESC, skip, limit를 사용한다.
- count query는 content query와 정확히 같은 filter를 사용하고 sort, skip, limit는 사용하지 않는다.
- 게시글 공개 여부는 댓글 query에 복제하지 않고 Service가 먼저 BlogPostRepository의 공개 query로 검증한다.
- 댓글 작성은 MongoRepository.save를 한 번 호출한다.

실제 흐름:

    Clock now
      -> BlogPostRepository.findPublicPostBySlug(slug, now)
      -> 공개 post id
      -> BlogCommentRepository.findVisibleCommentsByPostId(postId, pageable)

댓글마다 게시글이나 방문자를 다시 조회하지 않으며, 목록 응답은 Comment snapshot만 사용하므로 N+1이 발생하지 않는다.

## DB 변경

programmatic initializer가 아래 named index를 ensureIndex로 보장한다.

| collection | index 이름 | keys/options |
|---|---|---|
| anonymous_visitors | uk_anonymous_visitors_token_hash | tokenHash ASC, unique |
| blog_comments | idx_blog_comments_post_status_created_at | postId ASC, status ASC, createdAt DESC |
| blog_comments | idx_blog_comments_anonymous_visitor_id | anonymousVisitorId ASC |

- 별도 BlogCommentMongoIndexInitializer를 만들고 BlogPostMongoIndexInitializer는 수정하지 않는다.
- initializer는 test profile에서 비활성화한다.
- 같은 이름과 정의의 ensureIndex를 반복 호출하는 idempotent 구조로 만든다.
- unique 생성 실패, Mongo 연결 실패, index option 충돌을 catch해서 무시하지 않는다.
- 성공 로그에는 index 이름만 기록하고 comment content, token, tokenHash, visitor id를 기록하지 않는다.
- index 단위 테스트는 key 순서, 방향, 이름, unique 여부, 반복 호출의 동일 정의를 검증한다.
- 실제 MongoDB index/query 통합 검증과 query planner 검증은 Phase 08 과제로 남긴다.

## 예외 처리

| 상황 | HTTP | 제안 enum/code | result |
|---|---:|---|---|
| 댓글 목록 성공 | 200 | BLOG_COMMENT_LIST / COMMENT_200 | page result |
| 댓글 작성 성공 | 201 | BLOG_COMMENT_CREATED / COMMENT_201 | created comment |
| 프로필 재생성 성공 | 200 | ANONYMOUS_PROFILE_REGENERATED / COMMENT_202 | profile |
| 댓글 validation 실패 | 400 | _COMMENT_VALIDATION_FAILED / COMMENT_4001 | violations |
| 댓글 목록의 게시글 없음·비공개 | 404 | 기존 _BLOG_POST_NOT_FOUND / BLOG_4004 | null |
| page 음수 | 400 | 기존 _BLOG_PAGE_NEGATIVE / BLOG_4003 | null |
| size 1 미만 | 400 | 기존 _BLOG_SIZE_TOO_SMALL / BLOG_4005 | null |
| size 100 초과 | 400 | 기존 _BLOG_SIZE_TOO_LARGE / BLOG_4006 | null |
| page/size 타입 오류 | 400 | 기존 _BAD_REQUEST / COMMON400 | 기존 result |

- 기존 enum 이름, code, message는 변경하지 않고 새 COMMENT 상수만 append한다.
- secret 설정 오류, image base URL 오류, 프로필 후보군 오류는 내부 구성 오류로 fail fast 처리하고 외부 상세를 노출하지 않는다.
- invalid cookie는 클라이언트 오류로 반환하지 않고 새 방문자 발급으로 복구한다.

## API 변경

추가:

- GET /api/posts/{slug}/comments
- POST /api/posts/{slug}/comments
- POST /api/comments/nickname/regenerate

추가하지 않음:

- PATCH /api/comments/{commentId}
- DELETE /api/comments/{commentId}
- 기타 댓글, 운영자, Redis, 뉴스레터 endpoint

기존 GET /api/posts, GET /api/posts/{slug}, GET /api/posts/search와 exams API는 변경하지 않는다.

## 상태 전이

- 계획 수립 시작: Phase 03 TODO에서 PLANNING으로 변경
- 조건부 승인 반영: 계획서 APPROVED, Phase 03 IN_PROGRESS
- Current phase: Phase 03 유지
- 승인 근거: 사용자가 2026-07-30 Phase 03 계획을 조건부 승인하고 API, 설정, CloudFront, validation, 테스트 범위를 확정했다.
- 구현 후 VERIFYING에서 관련 테스트, 전체 build, diff와 범위 review를 수행한다.
- 모든 검증 성공 후에만 계획 EXECUTED와 Phase 03 DONE을 검토한다.

## 테스트 계획

이번 계획 수립 작업에서는 Java/테스트/Gradle을 수정하거나 테스트를 실행하지 않는다. 승인 후 기존 dependency만으로 다음을 구현한다.

### 익명 방문자

1. cookie가 없을 때 SecureRandom token, 방문자, Set-Cookie용 resolution을 새로 만든다.
2. 같은 cookie token의 HMAC lookup으로 같은 방문자를 반환한다.
3. 저장되는 tokenHash가 원본 token과 다르고 test key의 HMAC-SHA256 예상값과 일치한다.
4. index definition에서 tokenHash 이름과 unique 옵션을 검증한다.
5. 기존 방문자의 nickname과 avatarImageKey가 결정적 재생성 값으로 갱신된다.
6. 기존 방문자의 avatarSeed가 nickname 및 avatarImageKey와 독립된 16 random byte의 Base64URL 값으로 갱신된다.
7. profile 재생성 전 저장된 BlogComment의 nickname, avatarSeed, avatarImageKey snapshot이 update되지 않는다.
8. malformed, 너무 긴, decode 실패, DB 미존재 cookie는 새 방문자로 교체된다.
9. 성공 JSON, validation JSON, image URL, captured log에 원본 token이 없고 tokenHash도 응답에 없다.

### 댓글 규칙

10. strip 후 1 code point는 rule 1이다.
11. emoji를 포함한 정확히 2 code point는 허용한다.
12. 정확히 500 code point는 허용한다.
13. 501 code point는 rule 2다.
14. surrogate pair emoji를 UTF-16 length가 아닌 code point로 계산한다.
15. 앞뒤 space, tab, newline, Unicode whitespace를 제거한다.
16. 정규화가 발생해도 rule 3은 violations에 없다.
17. missing, null, object, array, number, boolean content는 rule 4이며 runtime type error가 없다.
18. 일반 HTML tag와 div/img/iframe/a tag를 rule 5로 차단한다.
19. script tag, javascript:, event handler, encoded entity 우회를 rule 5로 차단한다.
20. line-start Markdown heading을 rule 6으로 차단한다.
21. Markdown link/image/reference link를 rule 6으로 차단한다.
22. #1, 3 * 4, 5 > 3, 단독 특수문자를 포함한 일반 문장은 허용한다.
23. http:// URL을 rule 7로 차단한다.
24. https:// URL을 rule 7로 차단한다.
25. www. domain을 rule 7로 차단한다.
26. example.com과 sub.example.co.kr를 rule 7로 차단한다.
27. empty string은 rule 1과 8이다.
28. space-only는 strip 후 rule 1과 8이다.
29. tab/newline-only는 strip 후 rule 1과 8이다.
30. 동일 code point 10회 이상을 rule 9로 차단하고 9회 이하 경계를 확인한다.
31. 2..20 code point 짧은 구문 5회 반복을 rule 9로 차단하고 4회 경계를 확인한다.
32. 정말 정말 좋아요와 같은 정상적인 짧은 반복 표현은 허용한다.
33. 존재하지 않는 게시글은 rule 10이다.
34. DRAFT 게시글은 public Repository empty 결과를 통해 rule 10이다.
35. ARCHIVED 게시글은 같은 rule 10이다.
36. 미래 발행 글은 같은 rule 10이다.
37. publishedAt null 글은 같은 rule 10이다.
38. publishedAt == fixed Clock now인 공개 글은 작성 가능하다.

추가 규칙 경계:

- 이메일 주소는 rule 7이고 공백으로 분리된 명확하지 않은 형태는 허용한다.
- named/numeric HTML entity 차단과 일반 ampersand 허용을 함께 확인한다.
- fenced code, blockquote, list, paired emphasis, inline code, strikethrough, horizontal rule을 확인한다.
- website 값은 Phase 03 저장과 violation에 영향을 주지 않는다.

### 다중 위반

39. empty comment violations는 정확히 1, 8 순서다.
40. HTML anchor와 URL 동시 입력은 5, 7 순서다.
41. Markdown link와 URL 동시 입력은 6, 7 순서다.
42. 여러 detector가 같은 rule을 발견해도 동일 ruleNumber는 한 번만 반환한다.
43. violation이 있으면 AnonymousVisitorRepository와 BlogCommentRepository save를 호출하지 않는다.
44. 모든 조합의 최종 violations가 ruleNumber 오름차순인지 확인한다.

추가로 non-string empty 입력의 1, 4, 8 순서와 content violation + non-public post의 rule 10 결합을 확인한다.

### 댓글 저장과 조회

45. 정상 댓글을 VISIBLE로 저장하고 hiddenAt/hiddenReason은 null이다.
46. 저장 content와 성공 result가 strip된 동일 문자열이다.
47. nickname, avatarSeed, avatarImageKey가 visitor 현재 값의 snapshot으로 저장되고 응답에는 key 대신 resolved avatarImageUrl이 있다.
48. query criteria가 postId와 VISIBLE만 포함하고 PENDING/HIDDEN을 제외한다.
49. query sort가 createdAt DESC, _id DESC다.
50. page skip/limit와 같은 filter의 count query를 사용한다.
51. 비공개 게시글 댓글 목록은 기존 BLOG_4004 Not Found이고 comment query를 호출하지 않는다.

추가로 목록 최대 size 100, page 음수, size 0, size 101과 빈 댓글 page 성공을 확인한다.

### Controller

52. 댓글 목록이 BaseResponse, avatarImageUrl, comments/page metadata를 반환하고 avatarImageKey 등 내부 필드를 노출하지 않는다.
53. 댓글 작성 성공이 HTTP 201 BaseResponse, avatarImageUrl, 정규화된 content를 반환한다.
54. validation 실패가 HTTP 400과 result.violations를 반환한다.
55. 새 방문자 응답 Set-Cookie에 anon_session, HttpOnly, SameSite=Lax, Path=/, Max-Age, 환경별 Secure가 있다.
56. 닉네임 재생성 응답에 nickname/avatarSeed/avatarImageUrl이 있고 필요한 경우 새 cookie를 설정하며 avatarImageKey는 노출하지 않는다.
57. PATCH /api/comments/{id}가 404 또는 405이고 Service가 실행되지 않는다.
58. DELETE /api/comments/{id}가 404 또는 405이고 Service가 실행되지 않는다.

추가로 malformed JSON이 댓글 전용 rule 4 응답이고 기존 GlobalExceptionAdvice 형식을 바꾸지 않는지 확인한다.

### 회귀 및 부정 테스트

59. 기존 게시글 목록·상세·검색 Controller와 Service 테스트가 통과한다.
60. 게시글 POST, PATCH, DELETE API가 여전히 없다.
61. 뉴스레터 API가 없다.
62. 새 comment source에 RedisTemplate, rate limit key, IP 추출이 없다.
63. 내부 운영자 댓글 API와 hide/restore mapping이 없다.

### 랜덤 프로필 이미지

64. 코드 형용사 목록과 외부 avatar option의 noun/imageKey에 null, blank, 중복이 없고 catalog를 외부에서 변경할 수 없다.
65. avatar option이 2개 미만이면 설정 검증이 실패한다.
66. production configuration은 SecureRandom Bean을 제공하고 generator가 직접 new Random 또는 ThreadLocalRandom을 만들지 않는다.
67. 결정적 random stub으로 adjective와 같은 avatar option의 noun/imageKey, 별도 16 byte avatarSeed 선택 결과를 정확히 검증한다.
68. image key는 character-image/ prefix만 허용하고 상위 경로 이동, query, fragment, blank filename을 거절한다.
69. AvatarImageUrlResolver가 https://cdn.example.test와 key를 정확히 결합하고 slash 경계 및 URL encoding을 안전하게 처리한다.
70. 재생성에서 기존 adjective 또는 avatar option과 같은 값이 나오면 제한 재시도하고 소진 시 기존 profile을 성공으로 반환하지 않는다.
71. 결정적 새 profile이 visitor에는 반영되지만 이미 저장된 comment snapshot에는 반영되지 않는다.
72. 목록, 작성, 재생성 세 응답의 avatarImageUrl에 raw token, tokenHash, secret이 없고 internal avatarImageKey도 JSON에 없다.
73. 생성, 조회, 재생성 흐름이 S3Presigner, S3 client, AWS network를 호출하지 않는다.

### 인덱스, 전체 검증

- AnonymousVisitor 및 BlogComment index 이름, key 방향, unique, 반복 ensureIndex 정의를 mock으로 검증한다.
- 실제 MongoDB 연결은 요구하지 않는다.
- 실제 비공개 S3 object 존재, CloudFront OAC와 cache header 검증은 요구하지 않고 Phase 08 실제 연동 검수 대상으로 남긴다.
- 기존 TosunsaengApplicationTests와 ExamsRepositoryScanTest를 포함한 전체 테스트를 실행한다.
- git diff --check를 실행한다.
- ./gradlew clean test bootJar를 실행한다.
- 실제 secret, raw token 로그, 금지 mapping, Redis, SecurityConfig, application.yml, exams 변경 여부를 정적 review한다.
- 실제 MongoDB index/query 통합 테스트는 Phase 08 열린 과제로 기록한다.

## 보안 및 개인정보 고려

- raw token은 HttpOnly cookie에만 존재하고 Mongo, JSON, 로그에 저장하지 않는다.
- tokenHash는 단순 SHA-256이 아니라 server secret을 사용하는 HMAC-SHA256으로 생성한다.
- Secure 기본값 true, SameSite=Lax, host-only, Path=/를 적용한다.
- local HTTP만 명시 설정으로 Secure=false를 허용한다.
- HMAC secret, cookie raw token, tokenHash, 댓글 content를 application log에 기록하지 않는다.
- invalid cookie 오류 세부를 외부에 노출하지 않고 새 identity로 교체한다.
- nickname, avatarSeed, avatarImageKey는 개인정보 입력 없이 server가 생성한다.
- avatarImageUrl은 검증된 base URL과 immutable catalog key만으로 만들고 cookie, token, 사용자 입력을 섞지 않는다.
- 댓글 response에 anonymousVisitorId, tokenHash, avatarImageKey, IP, 소유 여부를 포함하지 않는다.
- URL과 이메일을 rule 7로 차단해 개인정보와 스팸 노출을 줄인다.
- website는 저장하지 않는다.
- SecurityConfig의 CSRF 비활성화는 기존 상태이며 이번 Phase에서 변경할 수 없다. SameSite=Lax가 cross-site POST cookie 전송을 제한하지만 완전한 CSRF 대책으로 간주하지 않고 위험에 기록한다.

## 기존 게시글·exams 영향

- 기존 BlogPost Document, Controller, Service, Converter, Repository 구현과 공개 Criteria를 수정하지 않는다.
- 댓글 Service가 BlogPostRepository의 기존 public query를 읽기 용도로 재사용한다.
- BlogPostMongoIndexInitializer와 blog_posts index를 수정하지 않는다.
- ClockConfig를 재사용하고 새 Clock Bean을 만들지 않는다.
- BaseResponse와 GlobalExceptionAdvice를 수정하지 않는다.
- SuccessStatus와 ErrorStatus에는 새 상수만 append하고 기존 상수와 code를 유지한다.
- 새 advice는 BlogCommentRestController에만 적용해 exams 및 게시글 parsing/error 응답에 영향을 주지 않는다.
- exams source와 기존 tests는 수정하지 않고 전체 회귀만 실행한다.
- RedisConfig와 기존 exams Redis 동작은 변경하지 않는다.
- 기존 S3Config, S3Presigner, AWS_S3_BUCKET_NAME과 exams의 S3 동작을 수정하거나 재사용하지 않는다. 프로필 URL resolver는 AWS SDK와 network 의존성이 없다.

## 조건부 승인 확정 사항

1. 댓글 목록은 page 0, size 20 기본값과 size 최대 100, createdAt DESC/_id DESC 안정 정렬을 사용한다.
2. 댓글 작성은 HTTP 201, 목록과 프로필 재생성은 HTTP 200이며 COMMENT_200..202와 COMMENT_4001 형식을 사용한다.
3. HMAC secret은 BLOG_ANONYMOUS_TOKEN_SECRET의 UTF-8 32 byte 이상 값이고 누락·길이 위반은 시작 실패다.
4. cookie는 HttpOnly, SameSite=Lax, Path=/이고 BLOG_ANONYMOUS_COOKIE_SECURE와 BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS로 설정한다.
5. Request content는 JsonNode로 타입을 판정하고 non-string은 일반적으로 1, 4, 8을 함께 반환하며 malformed JSON은 최소 rule 4를 반환한다.
6. 명확한 HTML tag/실행 패턴, Markdown 구조, URL과 이메일을 각각 승인된 규칙으로 차단하고 일반 단일 특수문자는 허용한다.
7. rule 9는 동일 code point 10회, 2..20 code point 구문 5회 연속을 차단 임계값으로 한다.
8. website는 수신만 하고 저장·차단하지 않으며 허니팟과 Redis rate limit은 Phase 04로 미룬다.
9. 공개 응답은 avatarImageUrl만 제공하고 Mongo에는 avatarImageKey snapshot을 저장한다.
10. 이미지 제공은 CloudFront OAC와 비공개 S3 구조만 사용하며 공개 S3 URL, presigned URL, AWS SDK/API 호출을 사용하지 않는다.
11. BLOG_ANONYMOUS_AVATAR_BASE_URL은 운영 HTTPS CloudFront 주소이며 실제 값을 하드코딩하지 않는다.
12. 외부 avatar option은 noun과 character-image/{filename} imageKey의 1:1 mapping이고 운영 설정에 2개 이상 있어야 한다.
13. 재생성은 이전 adjective와 다른 adjective, 이전 option과 다른 option, 새 avatarSeed를 선택하고 기존 댓글 snapshot을 변경하지 않는다.
14. .env.example에는 변수명과 설명만, application.yml에는 최소 properties binding만, application-test.yml에는 안전한 더미 설정만 추가한다.
15. 이미 사용한 imageKey와 object는 삭제·덮어쓰기하지 않고 변경 시 versioned 새 key를 추가하며 실제 CloudFront/S3 검증은 Phase 08에 남긴다.

## 위험 요소

- HMAC secret을 바꾸면 기존 cookie의 hash가 달라져 모든 기존 브라우저가 새 익명 방문자로 인식된다. 기존 댓글 snapshot은 유지되지만 profile continuity는 끊긴다.
- cookie가 삭제되거나 Max-Age가 끝난 방문자는 기존 댓글과 다시 연결할 수 없다. 수정·삭제 API가 없으므로 복구 endpoint를 만들지 않는다.
- HMAC key와 Secure 설정은 배포 환경변수에 의존한다. secret 누락은 안전을 위해 non-test 시작 실패로 처리한다.
- SameSite=Lax는 요구사항이지만 프론트와 API가 서로 다른 site로 배포되면 fetch에 cookie가 전달되지 않을 수 있다. 배포 도메인 구조와 credentials 설정 확인이 필요하다.
- SecurityConfig는 CSRF disabled 상태다. 이번 Phase에서는 변경 금지이며 cookie 기반 쓰기 API의 방어는 SameSite 정책에 의존한다.
- random token unique 충돌 가능성은 극히 낮지만 DB unique index와 제한 재시도로 처리해야 한다.
- BLOG_ANONYMOUS_AVATAR_BASE_URL의 CloudFront host가 잘못되거나 object key가 실제 비공개 S3 파일과 다르면 API는 성공해도 이미지가 깨진다. Phase 03 단위 테스트는 운영 연동을 검증하지 않는다.
- comment에는 avatarImageKey를 snapshot으로 남기므로 object를 삭제하거나 같은 key의 이미지를 교체하면 과거 댓글의 표시도 깨지거나 바뀐다. key와 object 내용은 immutable 자산으로 운영해야 한다.
- full URL을 저장하지 않아 CloudFront domain을 바꿀 수 있지만 캐시 전파, OAC와 실제 object 접근은 Phase 08 연동 검수까지 보장되지 않는다.
- visitor 생성 후 comment save가 실패하면 visitor만 남을 수 있다. 실제 Mongo multi-document transaction은 replica set과 통합 검증이 필요해 이번 범위에 추가하지 않는다.
- regex와 parser가 지나치게 넓으면 정상 문장을 차단하고 좁으면 우회가 가능하다. 명확한 패턴, 허용/차단 경계 테스트, 500 code point 상한으로 위험을 줄인다.
- 모든 HTML entity를 차단하면 &copy; 같은 실행 불가능한 entity도 거절된다. 안전한 일반 텍스트 정책을 우선한 제안이며 사용자 승인이 필요하다.
- domain regex는 일부 정상적인 점 구분 영문을 domain으로 오인할 수 있다. boundary와 TLD 조건을 테스트해야 한다.
- 반복 구문 탐지는 언어 표현을 오탐할 수 있다. 승인된 10회/5회 임계값과 정상 반복 예시의 경계를 테스트해야 한다.
- createdAt 동률 안정화를 위한 _id DESC는 요구 index의 마지막 key가 아니어서 대규모 데이터에서는 sort 비용을 실제 Mongo에서 확인해야 한다.
- Query BSON과 mock index 테스트는 Mongo serialization, unique 충돌, query planner를 통합 검증하지 못한다. Phase 08에 실제 Mongo 검증이 필요하다.
- PENDING/HIDDEN enum을 미리 정의하지만 Phase 03에는 이를 생성·변경할 API가 없다. 향후 Phase 04가 상태 전이를 구현할 때 별도 승인 계획이 필요하다.

## 롤백 방법

- 사용자 변경 여부를 먼저 확인한 뒤 Phase 03에서 새로 추가한 comment Java/test 파일만 수동 역패치한다.
- SuccessStatus와 ErrorStatus에서는 Phase 03이 append한 상수만 수동 제거한다.
- .env.example에서는 Phase 03 placeholder만 수동 제거한다.
- Phase 03 profile catalog와 URL resolver만 수동 제거하며 기존 S3Config와 exams S3 코드는 건드리지 않는다.
- 기존 BlogPost, exams, BaseResponse, GlobalExceptionAdvice 변경이 없어 해당 파일의 롤백은 발생하지 않아야 한다.
- 애플리케이션 파일 롤백은 이미 MongoDB에 생성된 anonymous_visitors, blog_comments 데이터나 index를 자동 삭제하지 않는다.
- S3 object는 Phase 03 애플리케이션이 생성하지 않으므로 롤백 시 자동 삭제하지 않는다. 운영자가 올린 character-image/ 자산의 보존·삭제는 별도 운영 절차로 결정한다.
- index나 데이터 제거가 필요하면 운영자가 정확한 collection/index와 보존 영향을 확인한 별도 운영 절차로 결정한다. Codex는 운영 DB에 접근하지 않는다.
- 금지된 git reset, git restore, git checkout, git clean은 사용하지 않는다.
- IMPLEMENTATION_STATUS.md의 이전 Session Log를 수정·삭제하지 않고 후속 정정 기록을 append한다.

## 완료 조건

- 사용자가 조건부 승인에서 확정한 API, cookie, HMAC, CloudFront, avatar option, validation, 임계값, 응답 code와 expected files만 구현한다.
- 세 공개 API만 추가되고 댓글 수정·삭제 및 범위 밖 API가 없다.
- 공개 게시글 판정이 기존 BlogPostRepository query와 Clock을 재사용한다.
- AnonymousVisitor에 raw token이 없고 tokenHash unique index가 있다.
- cookie가 HttpOnly, SameSite=Lax, Path=/, 환경별 Secure, 승인된 Max-Age를 만족한다.
- AnonymousVisitor와 BlogComment가 nickname, avatarSeed, avatarImageKey를 현재값/작성시점 snapshot으로 갖고 정상 댓글은 VISIBLE로만 저장된다.
- 목록, 작성, 프로필 재생성 응답에 avatarImageUrl이 있고 avatarImageKey는 외부에 노출되지 않는다.
- profile generator가 immutable adjective 목록과 외부 noun-image option catalog, SecureRandom Bean, 별도 16-byte avatarSeed를 사용하고 재생성 시 adjective와 option을 모두 바꾼다.
- avatarImageUrl은 BLOG_ANONYMOUS_AVATAR_BASE_URL과 character-image/ key로 조립하며 S3 SDK, presigner, network를 호출하지 않는다.
- rule 1~10 번호, code, 순서가 고정되고 rule 3은 violation에 없다.
- 모든 applicable violation이 중복 없이 ruleNumber 오름차순이다.
- violation이 있으면 visitor와 comment save가 호출되지 않는다.
- HTML, Markdown, URL, 이메일, 반복 패턴의 승인 경계를 테스트한다.
- 비공개 게시글 원인을 외부에 노출하지 않는다.
- 목록은 VISIBLE만 createdAt DESC로 페이지 조회하고 내부 식별 정보를 노출하지 않는다.
- 기존 게시글, exams, BaseResponse, GlobalExceptionAdvice, SecurityConfig, Redis, Gradle을 변경하지 않고 main/test application 설정은 승인된 properties binding과 더미 값만 수정한다.
- 계획된 테스트와 기존 전체 테스트가 성공한다.
- git diff --check와 ./gradlew clean test bootJar가 성공한다.
- 실제 secret이나 raw token이 source, response, log에 없다.
- 승인 계획, 고정 요구사항, 제외 범위, 실제 diff 사이에 미승인 차이가 없다.
- 실제 MongoDB 통합 검증과 S3/CloudFront object 접근 검증이 Phase 08 과제로 기록된다.
- 위 조건을 모두 통과하기 전 Phase 03을 DONE 또는 계획을 EXECUTED로 변경하지 않는다.

## 실제 구현 중 발생한 차이

구현 전 조건부 승인으로 DRAFT 제안과 달라진 사항은 BLOG_ANONYMOUS_* 환경변수명, UTF-8 secret 검증, CloudFront OAC 전용 제공, 외부 noun-image option 1:1 mapping, application 설정의 최소 binding, rule 9의 10회/5회 임계값이다. 모두 사용자가 승인 메시지에서 직접 확정했으며 이 APPROVED 계획에 반영했다. 구현 중 추가 차이가 필요하면 중단하고 재승인을 받는다.

- 실제 구현 파일은 이 계획의 예상 생성·수정 파일과 일치하며 예상 파일 밖 소스·설정 변경은 없었다.
- 댓글 테스트 첫 보강 실행에서 `example.com을`처럼 한국어 조사와 붙은 ASCII domain을 놓치는 경계를 발견했다. 승인된 일반 domain 차단 의미를 충족하도록 domain 경계를 ASCII domain 문자 기준으로 수정했다.
- 최종 review에서 `condition=value`를 event handler로 오인할 수 있는 패턴과 한국어 바로 뒤의 `http://` 경계를 보완했다. 명확한 실행 가능 HTML과 URL만 차단하고 일반 문장을 허용한다는 승인 의미 안의 정확도 수정이며 API, DB, 보안 정책 또는 범위 변경은 아니다.
- cookie Max-Age 일수를 `Duration`으로 변환할 수 없는 overflow 설정도 시작 시 실패하도록 계획의 overflow 방지 정책을 구현했다.
- 그 밖에 승인 계획과 실제 구현 사이의 차이는 없다.

## 검증 결과

- 계획 수립 전 AGENTS.md, PLANS.md, REQUIREMENTS.md, WORKFLOW.md, IMPLEMENTATION_STATUS.md, Phase 02 계획서를 다시 읽었다.
- 시작 브랜치 feat/blog-mvp, 깨끗한 작업 트리, preflight PASS를 확인했다.
- Phase 02 DONE과 계획 EXECUTED를 확인했다.
- 기존 domain/blog 전체, domain/exams 전체, BaseResponse, status code 전체, global exception 전체, global config 전체, TosunsaengApplication, 전체 src/test, build.gradle을 읽기 전용으로 분석했다.
- 기존 cookie 처리 선례가 없고 Clock과 공개 BlogPost query, programmatic Mongo index, BaseResponse non-null failure result를 재사용할 수 있음을 확인했다.
- 이번 계획 수립에서는 Java, 테스트, Gradle, application 설정, API, Mongo Document를 구현하거나 수정하지 않았다.
- 문서 생성 후 필수 관리 문서와 이 계획서를 규정 순서로 다시 읽었다.
- PLANS.md 필수 heading, 사용자 지정 항목, 고정 rule 1~10, 테스트 1~73이 모두 존재함을 확인했다.
- 변경 문서 trailing whitespace가 없고 git diff --check가 성공했다.
- 변경 파일은 이 계획서와 IMPLEMENTATION_STATUS.md뿐이며 Phase 03 외 단계 상태는 변경하지 않았다.
- 계획 전용 작업이므로 Gradle test와 build는 실행하지 않았다.
- 조건부 승인 후 계획 상태 `APPROVED`, Phase 03 `IN_PROGRESS`를 반영하고 기존 Session Log 아래에 승인 기록을 추가했다.
- 익명 방문자 HMAC/cookie, deterministic-test 가능한 SecureRandom profile, 외부 noun-image catalog, URL resolver, 두 Mongo Document/Repository/index, validation, Service/Controller/advice와 승인 테스트를 구현했다.
- `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'` 최종 결과는 댓글 테스트 95개, failures/errors/skipped 0으로 성공했다.
- 관련 테스트의 첫 보강 실행은 93개 중 한국어 조사 뒤 domain 경계 1개가 실패했으며 원인을 수정하고 재실행해 성공했다. 실패 이력을 삭제하거나 성공으로 바꾸지 않았다.
- `./gradlew clean test bootJar` 최종 결과는 `BUILD SUCCESSFUL`이며 전체 158개 테스트, failures/errors/skipped 0이다.
- 기존 게시글 Phase 02 테스트 61개, `TosunsaengApplicationTests`, `ExamsRepositoryScanTest`가 전체 검증에 포함돼 성공했다.
- `git diff --check`와 신규 파일 trailing whitespace 검사가 성공했다.
- 최종 mapping 검색 결과는 `GET /api/posts/{slug}/comments`, `POST /api/posts/{slug}/comments`, `POST /api/comments/nickname/regenerate` 세 개뿐이다.
- comment main/test source에서 AWS SDK/S3 client/presigner, RedisTemplate, Scheduling, PUT/PATCH/DELETE, 내부 운영 mapping을 찾지 못했다.
- 응답 DTO와 MockMvc 검증에서 raw token, tokenHash, avatarImageKey, anonymousVisitorId, 상태와 숨김 필드가 JSON에 노출되지 않음을 확인했다. 원본 token은 필요한 경우에만 HttpOnly cookie header로 설정된다.
- 실제 secret, 실제 CloudFront 주소, 실제 S3 object filename을 추가하지 않았고 test 전용 dummy 값만 test profile에 존재한다.
- `build.gradle`, `SecurityConfig`, 기존 BlogPost/exams, `BaseResponse`, `GlobalExceptionAdvice`, 기존 S3/Redis/Clock 설정은 변경하지 않았다.
- 실제 MongoDB index/query 및 비공개 S3/CloudFront OAC 연동 검증은 승인대로 Phase 08 과제로 유지한다.
- 모든 완료 조건이 충족돼 계획을 `EXECUTED`, Phase 03을 `DONE`으로 전환한다.
