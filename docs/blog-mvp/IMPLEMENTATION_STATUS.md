# 블로그 MVP 구현 상태

- 전체 상태: `IN_PROGRESS`
- 현재 단계: `Phase 06 — 뉴스레터 15분 자동 발송`
- 현재 브랜치: `feat/blog-mvp`
- 마지막 수정 시각: `2026-07-31 11:02:45 KST (+09:00)`

## 단계별 상태

| Phase | 이름 | 상태 |
|---|---|---|
| 00 | Agent guardrails 및 저장소 분석 | `DONE` |
| 01 | Java 21, 테스트, Mongo 스캔, Scheduling, 로컬 환경 | `DONE` |
| 02 | 게시글 목록·상세·제목 검색 | `DONE` |
| 03 | 익명 댓글과 번호 기반 validation | `DONE` |
| 04 | 댓글 rate limit과 숨김·복원 | `DONE` |
| 05 | 뉴스레터 구독과 구독 해지 | `DONE` |
| 06 | 뉴스레터 15분 자동 발송 | `TODO` |
| 07 | 내부 운영 API와 보안 | `TODO` |
| 08 | 전체 회귀 테스트와 API 문서 | `TODO` |

## 현재 단계의 목표

- Phase 01은 승인 범위 구현과 필수 검증을 완료해 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-01-infrastructure.md`는 `EXECUTED`다.
- Phase 02는 승인 범위 구현, 필수 테스트, 전체 build와 Codex review를 완료해 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-02-blog-read-search.md`는 `EXECUTED`다.
- Phase 03은 승인 범위 구현, 관련 테스트 95개, 전체 158개 테스트, bootJar와 Codex review를 완료해 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-03-anonymous-comments.md`는 실제 구현 차이와 검증 결과를 기록한 `EXECUTED`다.
- Phase 04는 승인 범위 구현, 댓글 관련 152개 테스트, 전체 215개 테스트와 bootJar 검증을 완료해 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-04-comment-abuse-moderation.md`는 실제 구현 차이와 검증 결과를 기록한 `EXECUTED`다.
- Phase 05는 승인 구현과 필수 검증을 완료해 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-05-newsletter-subscription.md`는 실제 구현 차이와 검증 결과를 기록한 `EXECUTED`다.
- Current phase는 Phase 06이며 상태는 `TODO`다.
- Phase 05는 즉시 ACTIVE 구독, 정규화 email unique, 조건부 재구독, stateless HMAC 해지 token과 newsletter 전용 IP rate limit을 승인 범위로 구현한다.
- 공개 API는 subscribe POST와 JSON body 기반 unsubscribe POST 두 개뿐이며 GET unsubscribe, verify, email 발송, Scheduler와 internal API는 구현하지 않는다.
- 저장소에는 Redis Cluster·Sentinel·다중 primary 설정이 없고 단일 `REDIS_HOST`/`REDIS_PORT`만 있어 Phase 04는 standalone/single-primary를 전제로 한다.
- 여러 visitor/IP/duplicate key를 한 Lua에서 처리하므로 Redis Cluster 전환 시 `CROSSSLOT`을 피하도록 key와 원자성 전략을 재설계해야 한다.
- client IP는 `request.getRemoteAddr()`만 사용하고 임의 `X-Forwarded-For`는 신뢰하지 않는다.
- duplicate reservation은 Mongo save 전에 owner token으로 만들고 Mongo 실패 시 owner 일치 Lua로만 해제하며 rate counter는 rollback하지 않는다.

## 변경 파일

Phase 05 계획 수립에서 변경한 파일:

- `docs/blog-mvp/plans/PHASE-05-newsletter-subscription.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 05 계획 수립에서 수정하지 않는 파일:

- Java 소스와 기존 테스트 전체
- `build.gradle`, main/test application 설정과 `.env.example`
- BaseResponse, GlobalExceptionAdvice, SecurityConfig와 기존 RedisConfig
- 기존 게시글·댓글·exams 비즈니스 로직

Phase 04 계획 수립에서 변경한 파일:

- `docs/blog-mvp/plans/PHASE-04-comment-abuse-moderation.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 04 계획 수립에서 수정하지 않는 파일:

- Java 소스와 기존 테스트 전체
- `build.gradle`, main/test application 설정
- 기존 RedisConfig, SecurityConfig, GlobalExceptionAdvice
- 기존 댓글·게시글·exams 비즈니스 로직

Phase 04 구현에서 생성한 파일:

- `src/main/java/web/tosunsaeng/domain/blog/comment/api/support/ClientIpResolver.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentModerationService.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentModerationServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/CommentAbusePreventionService.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/CommentAbusePreventionServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentAbuseConfig.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentAbuseProperties.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/converter/BlogCommentModerationConverter.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/CommentLimitScope.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/HiddenReason.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitHasher.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitKeyFactory.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/RedisFailureClassifier.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/CommentRateLimitRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/RedisCommentRateLimitRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentModerationDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/exception/CommentRateLimitException.java`
- `src/main/resources/redis/blog-comment-admission.lua`
- `src/main/resources/redis/blog-comment-duplicate-release.lua`
- `src/test/java/web/tosunsaeng/domain/blog/comment/api/support/ClientIpResolverTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentModerationServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/application/CommentAbusePreventionServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentAbusePropertiesTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/entity/BlogCommentStateTransitionTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitHasherTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentRateLimitKeyFactoryTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/RedisFailureClassifierTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/repository/RedisCommentRateLimitRepositoryTest.java`

Phase 04 구현에서 수정한 파일:

- `.env.example`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `src/main/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestController.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorService.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentService.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializer.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/entity/BlogComment.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentResponseDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/exception/BlogCommentExceptionAdvice.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostRepository.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestControllerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImplTest.java`
- `docs/blog-mvp/plans/PHASE-04-comment-abuse-moderation.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 04 구현에서 변경하지 않은 보호 파일:

- `build.gradle`
- `src/main/java/web/tosunsaeng/global/config/RedisConfig.java`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- `src/main/java/web/tosunsaeng/global/exception/GlobalExceptionAdvice.java`
- `src/main/java/web/tosunsaeng/domain/exams/**`

Phase 03 구현에서 생성한 파일:

- `src/main/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestController.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/api/support/AnonymousCookieFactory.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorService.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentService.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousProfileConfig.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousProfileProperties.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousSessionConfig.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/AnonymousSessionProperties.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializer.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/converter/BlogCommentConverter.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/entity/AnonymousVisitor.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/entity/BlogComment.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/CommentRule.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/enums/CommentStatus.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousAvatarImageCatalog.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousProfileGenerator.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousTokenManager.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/AvatarImageUrlResolver.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentSpamPatternPolicy.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentValidator.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/AnonymousVisitorRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentRequestDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/dto/BlogCommentResponseDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/exception/BlogCommentException.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/exception/BlogCommentExceptionAdvice.java`
- `src/main/java/web/tosunsaeng/domain/blog/comment/exception/CommentValidationException.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/api/BlogCommentRestControllerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/application/AnonymousVisitorServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/application/BlogCommentServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/config/BlogCommentMongoIndexInitializerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousAvatarImageCatalogTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousProfileGeneratorTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AnonymousTokenManagerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/AvatarImageUrlResolverTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/policy/CommentValidatorTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/comment/domain/repository/BlogCommentQueryRepositoryImplTest.java`

Phase 03 구현에서 수정한 파일:

- `.env.example`
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `docs/blog-mvp/plans/PHASE-03-anonymous-comments.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 03 구현에서 변경하지 않은 보호 파일:

- `build.gradle`, `SecurityConfig`, 기존 게시글·exams 비즈니스 코드와 기존 테스트
- 기존 `S3Config`, `RedisConfig`, `ClockConfig`, `BaseResponse`, `GlobalExceptionAdvice`

Phase 03 계획 수립에서 변경한 파일:

- `docs/blog-mvp/plans/PHASE-03-anonymous-comments.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 03 계획 수립에서 수정하지 않는 파일:

- Java 소스와 기존 테스트 전체
- `build.gradle`, main/test application 설정
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- 기존 게시글·exams 비즈니스 로직

Phase 02 계획 수립에서 변경한 파일:

- `docs/blog-mvp/plans/PHASE-02-blog-read-search.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 02 계획 수립에서 수정하지 않는 파일:

- Java 소스와 기존 테스트 전체
- `build.gradle`, `src/main/resources/application.yml`, `src/test/resources/application-test.yml`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`

Phase 02 구현에서 생성한 파일:

- `src/main/java/web/tosunsaeng/domain/blog/api/BlogPostRestController.java`
- `src/main/java/web/tosunsaeng/domain/blog/application/BlogPostService.java`
- `src/main/java/web/tosunsaeng/domain/blog/application/BlogPostServiceImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/config/BlogPostMongoIndexInitializer.java`
- `src/main/java/web/tosunsaeng/domain/blog/converter/BlogPostConverter.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/entity/BlogPost.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/enums/BlogPostStatus.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/policy/BlogPostSlugPolicy.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepository.java`
- `src/main/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImpl.java`
- `src/main/java/web/tosunsaeng/domain/blog/dto/BlogPostResponseDTO.java`
- `src/main/java/web/tosunsaeng/domain/blog/exception/BlogPostException.java`
- `src/main/java/web/tosunsaeng/global/config/ClockConfig.java`
- `src/test/java/web/tosunsaeng/domain/blog/api/BlogPostRestControllerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/application/BlogPostServiceImplTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/config/BlogPostMongoIndexInitializerTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/domain/policy/BlogPostSlugPolicyTest.java`
- `src/test/java/web/tosunsaeng/domain/blog/domain/repository/BlogPostQueryRepositoryImplTest.java`

Phase 02 구현에서 수정한 파일:

- `src/main/java/web/tosunsaeng/global/error/code/status/SuccessStatus.java`
- `src/main/java/web/tosunsaeng/global/error/code/status/ErrorStatus.java`
- `docs/blog-mvp/plans/PHASE-02-blog-read-search.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 02 구현에서 변경하지 않은 보호 파일:

- `build.gradle`, 운영/test application 설정, `SecurityConfig`
- `src/main/java/web/tosunsaeng/TosunsaengApplication.java`
- 기존 `src/main/java/web/tosunsaeng/domain/exams/**`와 기존 테스트

Phase 01에서 변경한 파일:

- `src/main/java/web/tosunsaeng/TosunsaengApplication.java`
- `src/main/java/web/tosunsaeng/global/config/SchedulingConfig.java`
- `.github/workflows/deploy.yml`
- `src/test/resources/application-test.yml`
- `src/test/java/web/tosunsaeng/TosunsaengApplicationTests.java`
- `src/test/java/web/tosunsaeng/domain/exams/domain/repository/ExamsRepositoryScanTest.java`
- `compose.local.yml`
- `.env.example`
- `.gitignore`
- `docs/blog-mvp/plans/PHASE-01-infrastructure.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`

Phase 01에서 수정하지 않은 확인 대상:

- `build.gradle`, `src/main/resources/application.yml`
- `src/main/java/web/tosunsaeng/global/config/SecurityConfig.java`
- 기존 `src/main/java/web/tosunsaeng/domain/exams/**` 비즈니스 코드
- `Dockerfile`, `Dokerfile`

Phase 00에서 생성한 파일:

- `AGENTS.md`
- `PLANS.md`
- `docs/blog-mvp/REQUIREMENTS.md`
- `docs/blog-mvp/WORKFLOW.md`
- `docs/blog-mvp/IMPLEMENTATION_STATUS.md`
- `docs/blog-mvp/plans/.gitkeep`
- `.codex/config.toml`
- `.codex/rules/default.rules`
- `scripts/codex-preflight.sh`
- `scripts/codex-status.sh`
- `scripts/codex-verify.sh`

Phase 00 시작 전부터 존재한 사용자 변경이며 수정하지 않는 파일:

- `.github/workflows/deploy.yml`
- `build.gradle`

## 실행한 명령

- `git branch --show-current`
- `git status --short`
- `find . -maxdepth 1 -mindepth 1 -print`
- `sed -n '1,260p' build.gradle`
- `sed -n '1,260p' .github/workflows/deploy.yml`
- `sed -n '1,260p' Dockerfile`
- `find src/main/java/web/tosunsaeng -maxdepth 6 -print`
- 기존 `AGENTS.md`와 `.codex` 경로 검색
- `git diff -- build.gradle .github/workflows/deploy.yml`
- `git diff --check`
- `git diff --stat`
- `git log -5 --oneline`
- `git branch --list feat/blog-mvp`
- `codex --version`
- `node .../openai-docs/scripts/fetch-codex-manual.mjs`로 공식 Codex 매뉴얼 조회
- 공식 매뉴얼의 rules/config 문법 구간 검색 및 확인
- `mkdir -p docs/blog-mvp/plans .codex/rules scripts`
- `chmod +x scripts/codex-preflight.sh scripts/codex-status.sh scripts/codex-verify.sh`
- 세 스크립트에 대한 `bash -n`
- `codex execpolicy check`를 이용한 전체 forbidden/prompt 규칙 확인
- `./scripts/codex-verify.sh`
- `bash ./gradlew clean test bootJar`
- `ls -l gradlew` 및 `git ls-files --stage gradlew`
- Gradle test result에서 placeholder 실패 원인 검색
- `src/main/resources`의 환경 placeholder 이름 검색
- 생성 파일 목록, script mode, trailing whitespace, 최종 `git status --short --untracked-files=all` 확인

### 2026-07-29 16:08:12 KST — Phase 01 계획 수립 명령

- 필수 문서 생성 전 확인: `sed -n`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md` 순서대로 읽음
- 현재 계획서 확인: `rg --files --hidden docs/blog-mvp/plans`
- 시작 조건: `git branch --show-current`, `git status --short`, `scripts/codex-preflight.sh`
- 대상 파일 탐색: `rg --files`로 build, application, config, test, workflow, Docker/Compose, `.gitignore` 파일 확인
- 기반 설정 읽기: `sed -n`으로 `build.gradle`, `TosunsaengApplication.java`, `application.yml`, `application.properties`, `gradle-wrapper.properties` 확인
- config 분석: `awk`로 `src/main/java/web/tosunsaeng/global/config/**` 전체 확인
- test 분석: `rg --files src/main/resources src/test`, `sed -n`으로 `TosunsaengApplicationTests.java` 확인
- CI 분석: `sed -n`으로 `.github/workflows/deploy.yml`, `rg --files .github/workflows` 확인
- Docker 분석: `awk`로 `Dockerfile`, `Dokerfile` 확인, `rg`로 참조 검색, `git ls-files`로 추적 및 Compose 파일 부재 확인
- Compose 탐색: `rg --files --hidden --no-ignore`의 compose glob으로 ignored 파일까지 확인
- 비밀 파일 정책 분석: `sed -n`으로 `.gitignore`, `rg --files`로 README와 env 관련 파일 확인
- exams 분석: `rg --files src/main/java/web/tosunsaeng/domain`, `awk`로 기존 Repository interface 전체 확인
- 외부 호출 분석: `rg`로 S3/AWS 참조 검색, `sed -n`으로 `ExamServiceImpl.java` 확인
- Scheduling 분석: `rg`로 `@Scheduled`, `@EnableScheduling`, `SchedulingConfigurer`, `TaskScheduler` 참조 검색
- 기타 확인: `sed -n`으로 `README.md`, `git ls-files --stage gradlew`, `date` 실행
- 관리 문서 수정: `apply_patch`로 Phase 01 `PLANNING` 전환과 Session Log 추가
- 계획서 작성: `apply_patch`로 `docs/blog-mvp/plans/PHASE-01-infrastructure.md` 생성
- 계획서 생성 직후 필수 재확인: `sed -n`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 01 계획서 순서대로 다시 읽음
- 문서 검증: `git status --short`, 필수 heading 검색, trailing whitespace 검색, `git diff --check`, 상태 문서 diff 확인

### 2026-07-29 16:30:56 KST — Phase 01 구현 및 검증 명령

- 필수 문서 확인: `sed -n`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 01 계획서 순서대로 읽음
- 시작 조건: `git branch --show-current`, `git status --short`
- 환경값 존재 여부만 확인: `MONGODB_URI`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_S3_BUCKET_NAME`, `REDIS_HOST`, `REDIS_PORT`의 SET/UNSET 확인, 값은 출력하지 않음
- 상태 반영: `apply_patch`로 계획 `APPROVED`, Phase 01 `IN_PROGRESS`, 승인 Session Log 반영
- 승인 상태 확인: `rg`로 계획 `APPROVED`와 Phase 01 `IN_PROGRESS` 확인
- test directory 준비: `mkdir -p src/test/resources src/test/java/web/tosunsaeng/domain/exams/domain/repository`
- 구현: `apply_patch`로 Mongo 탐색, Scheduling, CI, test profile/test, Compose, env 예시, `.gitignore` 반영
- 구현 범위 확인: `git status --short`, 대상 tracked diff, 신규 파일 전체 내용 확인
- 검증 상태 반영: `apply_patch`로 Phase 01 `VERIFYING`과 Session Log 반영
- 필수 정적 검사: `git diff --check`
- 필수 빌드 첫 실행: `./gradlew clean test bootJar` — 샌드박스의 `~/.gradle` lock 파일 접근 제한으로 task 실행 전 실패
- 필수 빌드 재실행: 승인된 샌드박스 외부 `./gradlew clean test bootJar` — 성공
- Compose: `docker compose -f compose.local.yml config`
- 테스트 결과 확인: `rg`로 Gradle XML의 testsuite/testcase와 Mongo Repository 5개 탐색 로그 확인
- CI/Java 확인: `rg`와 `sed -n`으로 workflow 순서, main trigger, Java 21, 기존 Docker/SSH 배포 단계 확인
- 제외 범위 확인: `git diff --quiet`로 `build.gradle`, 운영 `application.yml`, `SecurityConfig`, `Dockerfile`, `Dokerfile`, exams 비즈니스 코드 미변경 확인
- Dokerfile 확인: docs/build/.git 제외 참조 검색
- 제외 기능 확인: `@Scheduled`, `NewsletterScheduler`, BlogPost, NewsletterSubscriber, Testcontainers 검색
- 비밀값 확인: AWS access key ID와 private key 패턴 검색
- 테스트 외부 호출 확인: test source의 S3/HTTP/Redis/Mongo operation 참조 검색
- workflow YAML 첫 parser 명령은 시스템 Ruby 2.6이 `aliases` keyword를 지원하지 않아 실패, 호환 명령으로 재실행해 성공
- env ignore 확인: `git check-ignore`로 `.env`, `.env.local` 제외와 `.env.example` 추적 가능 상태 확인
- 신규 파일 whitespace 확인: `rg` trailing whitespace 검색
- 최종 범위 확인: `git status --short --untracked-files=all`, `git diff --stat`, `git diff --check`

### 2026-07-29 17:12:33 KST — Phase 02 계획 수립 명령

- 계획서 생성 전 필수 문서 확인: `cat`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md` 순서대로 읽음
- 시작 조건: `git branch --show-current`, `git status --short`, `scripts/codex-preflight.sh`
- 대상 파일 목록: `rg --files`, `find`, `wc -l`로 exams, 공통 응답, 실제 status package, 전역 예외, 전체 test, 전체 main source를 확인
- exams 구조 분석: `awk`와 `cat`으로 Controller, Service interface/impl, Converter, Request/Response DTO, 전체 Document, enum, Repository, 도메인 예외를 읽음
- 공통 구조 분석: `awk`와 `cat`으로 `BaseResponse`, `global/error/code/status/**`, `global/exception/**`를 읽고 `rg`로 ErrorStatus 참조 위치를 확인
- 애플리케이션·테스트 분석: `cat`과 `awk`로 `TosunsaengApplication`, 기존 전체 test와 test profile, `build.gradle`을 읽음
- 저장소 선례 검색: `rg`로 API mapping, Pageable/Page, page metadata, 날짜·시간, Mongo index, MongoTemplate/Criteria/Query 사용을 검색
- 보조 설정 확인: `cat`으로 `SecurityConfig`, `application.yml`, `.github/workflows/deploy.yml`, `compose.local.yml`, `RestTemplateConfig`, `SchedulingConfig`, `RedisConfig`를 읽음
- 시각 확인: `date '+%Y-%m-%d %H:%M:%S %Z (%z)'`
- 문서 작성: `apply_patch`로 Phase 02 DRAFT 계획서 생성과 Phase 02 `PLANNING` 상태 및 Session Log 반영
- 계획서 생성 직후 필수 재확인: `cat`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 02 계획서 순서대로 다시 읽음
- 문서 검증: heading `rg`, `git status --short --untracked-files=all`, trailing whitespace `rg`, `git diff --check`, 상태 문서 `git diff`, 최종 시각 확인

### 2026-07-29 17:38:50 KST — Phase 02 구현 및 검증 명령

- 필수 문서 확인: `cat`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 02 계획서 순서대로 읽음
- 시작 조건: `git branch --show-current`, `git status --short --untracked-files=all`
- 승인 시각 확인과 상태 반영: `date`, `apply_patch`, `rg`로 계획 `APPROVED`, Phase 02 `IN_PROGRESS`, 현재 단계 Phase 02 확인
- 구현 directory 준비: 승인된 main/test blog package에 한정한 `mkdir -p`
- 구현: `apply_patch`로 BlogPost/상태/slug 정책, Clock, Repository/custom query, index initializer, DTO/Converter, Service, Controller, success/error status 추가
- 테스트 구현: `apply_patch`로 slug policy, index, Repository Query BSON, Service, MockMvc Controller 테스트 추가
- 검증 상태 반영: `apply_patch`로 Phase 02 `VERIFYING`과 Session Log 추가
- 초기 범위 확인: `git status --short --untracked-files=all`, `git diff --check`, 금지 mapping·기능 `rg`
- 첫 Phase 02 테스트: 샌드박스 내부 Gradle cache lock 접근 제한으로 task 실행 전 실패; 승인된 외부 `./gradlew test --tests 'web.tosunsaeng.domain.blog.*'` 재실행 성공
- 첫 테스트 결과 확인: Gradle XML에서 Phase 02 57개, failures/errors/skipped 0 확인
- 테스트 보강: 상세·검색 공통 공개 query, 안정 정렬/count, null 검색, 검색 pagination 회귀 test 추가
- 보강 테스트 첫 실행: Phase 02 61개 중 검색 content/count Query BSON 동등성 1건 실패
- 실패 분석: test source line 확인 후 동일 검색 Criteria를 content/count에서 재사용하도록 Repository 구현 수정
- 보강 테스트 재실행: `./gradlew test --tests 'web.tosunsaeng.domain.blog.*'` 성공
- 필수 정적 검사: `git diff --check` 성공
- 필수 전체 검증: 승인된 외부 `./gradlew clean test bootJar` 성공
- 전체 결과 확인: Gradle XML에서 Phase 02 61개와 기존 2개, 총 63개 failures/errors/skipped 0 확인
- Codex review: status, 보호 파일 diff, API mapping, Newsletter/Redis/Scheduling/`Instant.now` 부재, 비밀값 패턴, trailing whitespace를 병렬 정적 검사

### 2026-07-30 11:11:50 KST — Phase 03 계획 수립 명령

- 계획서 생성 전 필수 문서 재확인: `cat`으로 `AGENTS.md`, `PLANS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, Phase 02 계획서를 읽음
- 시작 조건: `git branch --show-current`, `git status --short`, `scripts/codex-preflight.sh`
- 대상 목록: `rg --files`로 blog, exams, 공통 응답, 전역 예외, config, status code, 전체 test 파일 확인
- blog 분석: `awk`와 `cat`으로 Controller, Service, Converter, BlogPost, policy, Repository/custom query, DTO, 예외, index initializer 전체를 읽음
- blog test 분석: `awk`와 `cat`으로 Controller, Service, Repository BSON, index, slug policy 테스트 전체를 읽음
- exams 분석: `awk`, `cat`, `sed`, `wc -l`로 Controller, Service interface/impl, Converter, DTO, Document, enum, Repository, 예외 전체를 읽음
- 공통 계층 분석: `awk`로 BaseResponse, BaseCode, BaseErrorCode, SuccessStatus, ErrorStatus, GeneralException, GlobalExceptionAdvice 전체를 읽음
- config 분석: `awk`로 Clock, CORS, Redis, RestTemplate, S3, Scheduling, Security, Swagger, JWT 관련 config 전체를 읽음
- 테스트·기반 분석: application context/exams scan test, test profile, `build.gradle`, `TosunsaengApplication.java`를 읽음
- 선례 검색: `rg`로 cookie/header, JSON parsing, validation, pagination, MongoTemplate/index, configuration property, COMMENT code와 API 부재를 검색함
- 설정 파일 확인: `rg --files`, `rg`, `wc -l`로 application/env 파일 존재와 익명 cookie/HMAC 설정 부재를 확인함
- 계획서 존재 확인: `rg --files docs/blog-mvp/plans`
- 시각 확인: `date '+%Y-%m-%d %H:%M:%S %Z (%z)'`
- 문서 작성: `apply_patch`로 Phase 03 DRAFT 계획서 생성과 Phase 03 `PLANNING` 상태 반영
- 계획서 생성 직후 재독: `cat`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 03 계획서를 규정 순서로 읽음
- 전체 계획서 재확인: `sed -n` 세 구간으로 Phase 03 계획서 EOF까지 읽음
- 문서 검증: `rg`로 필수 heading, 고정 rule, 테스트 1~63, trailing whitespace를 확인함
- 변경 범위 검증: `git status --short --untracked-files=all`, 상태 문서 `git diff`, `git diff --check`
- 최종 기록: `date`와 `apply_patch`로 Phase 03 계획 검증 결과와 append-only Session Log를 추가함

### 2026-07-30 13:17:39 KST — Phase 03 구현 및 검증 명령

- 필수 문서 재확인: `sed -n`으로 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 03 계획서를 규정 순서와 EOF까지 읽음
- 재개 상태 확인: `git branch --show-current`, `git status --short --untracked-files=all`, `scripts/codex-preflight.sh`; 진행 중 변경 때문에 preflight의 clean-tree 검사만 실패했고 모두 직전 Codex Phase 03 변경임을 대조함
- 구현 재확인: `rg --files`, `awk`, `sed`, `git diff`로 comment main/test, status enum, env/application binding을 읽음
- 기존 테스트 선례 확인: `awk`로 Phase 02 Controller, Service, Repository query, index 테스트를 읽음
- 구현 및 테스트 작성: `apply_patch`로 승인된 comment main/test 파일, status enum, env/application binding과 관리 문서를 작성·보완함
- 첫 sandbox 댓글 테스트: `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'`가 사용자 Gradle cache lock 권한으로 task 실행 전 실패함
- 승인된 외부 댓글 테스트: 기존 4개 profile/token 테스트가 성공함
- 테스트 보강 후 첫 실행: 댓글 테스트 93개 중 `example.com을` domain 경계 1개 실패
- 실패 분석: Gradle XML을 `rg`로 확인하고 domain ASCII 경계를 수정함
- 보강 재실행: 댓글 테스트 93개 성공
- cookie overflow와 SecureRandom Bean 테스트 추가 후 재실행: 댓글 테스트 95개 성공
- 검증 상태 반영: `date`, `apply_patch`로 Phase 03 `VERIFYING`과 append-only Session Log를 반영함
- 필수 검사: `git diff --check` 성공
- 지정 관련 테스트: `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'` 성공
- 첫 전체 검증: `./gradlew clean test bootJar` 성공
- 정적 review: `rg`로 API mapping, AWS SDK/S3Presigner, Redis/Scheduling, token log, avatarImageKey 응답, PUT/PATCH/DELETE/internal/newsletter, TODO/FIXME, actual secret pattern을 검색함
- 보호 파일 review: `git diff --name-only`과 `git status`로 Gradle, SecurityConfig, 기존 S3/Redis/Clock, BaseResponse, GlobalExceptionAdvice, BlogPost/exams 미변경을 확인함
- 정규식 review 보완: 일반 `condition=value` 허용과 한국어 뒤 `http://` 차단 경계를 `apply_patch`로 보완하고 댓글 테스트를 재실행해 성공함
- 최종 전체 검증: `./gradlew clean test bootJar` 성공
- 결과 집계: Gradle XML을 `awk`로 집계해 댓글 95개, 전체 158개, failures/errors/skipped 0 확인
- 최종 정적 검사: `git diff --check`, 신규 파일 trailing whitespace, 승인된 세 mapping, 금지 의존성·endpoint·비밀값·로그·보호 파일을 재확인함
- 완료 상태 `rg` 확인 1회는 double-quoted shell pattern의 backtick을 명령 치환으로 해석해 `DONE`, `EXECUTED` command-not-found를 출력했으며 파일 영향은 없었다. single-quoted pattern으로 즉시 재실행해 상태를 확인함
- 금지 Git 명령, branch 변경, 운영/외부 접근, AWS API 호출은 실행하지 않음

## 테스트 결과

### 초기 검증 결과

- 저장소 초기 `git diff --check`: 성공
- `bash -n scripts/codex-preflight.sh`: 성공
- `bash -n scripts/codex-status.sh`: 성공
- `bash -n scripts/codex-verify.sh`: 성공
- `.codex/rules/default.rules`: 공식 execpolicy 로더 구문 및 inline `match`/`not_match` 검증 성공
- 금지 명령 21개 사례: 모두 `forbidden` 확인
- `mongosh`: `prompt` 확인
- `git status --short`: rules 미적용 확인
- `git diff --check`: 성공
- `./scripts/codex-verify.sh`: 실패. 저장소의 `gradlew`가 mode `100644`라 `./gradlew` 실행 시 `Permission denied`
- `bash ./gradlew clean test bootJar`: 실패. 컴파일과 `bootJar`는 성공했으나 `contextLoads()` 1건 실패
- 테스트 실패 원인: `S3Config` 생성 중 `${spring.cloud.aws.credentials.access-key}`가 참조한 `AWS_ACCESS_KEY` placeholder를 해석하지 못함
- 결론: 테스트가 실패했으므로 Phase 00을 `DONE`으로 변경하지 않음

### 환경변수 설정 후 재검증 결과

- 이전 테스트 실패 원인은 테스트 환경에 `AWS_ACCESS_KEY`와 `AWS_SECRET_KEY` 환경변수가 없었기 때문임
- 두 환경변수를 설정한 뒤 `./gradlew clean test bootJar`: `BUILD SUCCESSFUL`
- `gradlew`: 실행 가능
- `git diff --check`: 성공
- 테스트 환경, 애플리케이션 코드, 테스트 코드, `build.gradle`, `application.yml`, CI 파일 수정은 필요하지 않았음
- 재검증이 성공해 Phase 00을 `DONE`으로 변경함

### Phase 01 계획 수립 검증 결과

- `scripts/codex-preflight.sh`: 성공
- 계획서의 사용자 요구 heading과 `PLANS.md` 필수 heading: 모두 존재
- 변경 문서 trailing whitespace 검색: 없음
- `git diff --check`: 성공
- 변경 파일: 계획서와 상태 문서 두 개뿐이며 Java, 테스트, Gradle, CI, Compose 파일은 수정하지 않음
- 구현 테스트와 Docker 실행은 계획 전용 작업이므로 수행하지 않음

### Phase 01 구현 검증 결과

- `git diff --check`: 성공
- `./gradlew clean test bootJar`: 샌드박스 내부 첫 실행은 Gradle cache lock 접근 제한으로 task 실행 전 실패, 승인된 동일 명령 재실행은 `BUILD SUCCESSFUL`
- `TosunsaengApplicationTests.contextLoads()`: 1건 성공, failures 0, errors 0, skipped 0
- `ExamsRepositoryScanTest.registersExistingExamsMongoRepositories()`: 1건 성공, failures 0, errors 0, skipped 0
- Spring Data Mongo Repository 탐색: 기존 interface 5개 확인
- 테스트 source의 Mongo/Redis/S3/AI client 및 Repository method 호출: 없음
- AWS/S3 외부 호출: 없음. `S3Presigner` Bean mock도 추가하지 않음
- `docker compose -f compose.local.yml config`: 성공
- GitHub Actions YAML parse와 순서 검토: 성공
- Java 21: `build.gradle`, `.github/workflows/deploy.yml`, `Dockerfile`에서 확인
- `main` push trigger와 기존 Docker image build/push 및 SSH 배포 단계: 유지
- 실제 AWS access key ID/private key 패턴: 없음
- `.env`, `.env.local`: ignore됨, `.env.example`: 추적 가능
- 승인 제외 파일과 exams 비즈니스 코드: 변경 없음
- Codex review: 고정 요구사항, 제외 범위, 승인 계획, 실제 변경 파일 사이에 미승인 차이 없음

### Phase 02 계획 수립 검증 결과

- `scripts/codex-preflight.sh`: 성공
- 필수 관리 문서와 새 Phase 02 계획서의 생성 직후 재독: 완료
- `PLANS.md` 필수 heading과 사용자 지정 계획 항목: 모두 존재
- 변경 문서 trailing whitespace 검색: 없음
- `git diff --check`: 성공
- 변경 파일: Phase 02 계획서와 상태 문서 두 개뿐임
- Phase 상태: Phase 02만 `TODO`에서 `PLANNING`으로 변경, Phase 00·01 및 Phase 03~08 상태 유지
- Java, 기존 테스트, Gradle, application 설정, API, Mongo Document: 변경 없음
- 구현 테스트와 Gradle build: 계획 전용 작업이므로 실행하지 않음

### Phase 02 구현 검증 결과

- 첫 Phase 02 테스트: 57개 성공, failures/errors/skipped 0
- 보강 테스트 첫 실행: 61개 중 1개 실패; 검색 content/count Criteria가 동일 의미의 별도 Pattern 객체를 가져 BSON 객체 동등성 assertion 실패
- 수정 후 Phase 02 테스트: 61개 성공, failures/errors/skipped 0
- `./gradlew clean test bootJar`: `BUILD SUCCESSFUL`
- 전체 테스트: Phase 02 61개 + 기존 context/exams 2개 = 총 63개, failures/errors/skipped 0
- `git diff --check`: 성공
- 변경 문서와 신규 Java/test trailing whitespace: 없음
- API mapping: `GET /api/posts`, `GET /api/posts/search`, `GET /api/posts/{slug}` 세 개만 존재
- 쓰기 API와 댓글·뉴스레터 API 부정 MockMvc 테스트: 성공
- 보호 파일: `build.gradle`, application 설정, SecurityConfig, TosunsaengApplication, exams 비즈니스 코드 변경 없음
- 비밀값 패턴, Newsletter 필드, Redis, Scheduling 작업, Testcontainers: 추가 없음
- 실제 MongoDB 통합 테스트: 승인 범위에서 제외, Phase 08 전체 검수 과제로 유지
- Codex review: 고정 요구사항, 승인 계획, 제외 범위와 실제 변경 사이에 미승인 차이 없음

### Phase 03 계획 수립 검증 결과

- `scripts/codex-preflight.sh`: 성공
- Phase 02 `DONE`, 계획 `EXECUTED`, 현재 단계 Phase 03을 확인함
- 계획서 생성 직후 필수 관리 문서와 새 Phase 03 계획서를 규정 순서로 다시 읽음
- `PLANS.md` 필수 heading과 사용자 지정 계획 항목이 모두 존재함
- 고정 CommentRule 1~10과 요청된 최소 테스트 1~63이 계획서에 존재함
- 변경 문서 trailing whitespace 검색: 없음
- `git diff --check`: 성공
- 변경 파일은 Phase 03 계획서와 상태 문서 두 개뿐임
- Phase 상태는 Phase 03만 `TODO`에서 `PLANNING`으로 변경하고 다른 Phase는 유지함
- Java, 기존 테스트, Gradle, main/test application 설정, API, Mongo Document는 변경하지 않음
- 계획 전용 작업이므로 Gradle test와 build는 실행하지 않음

### Phase 03 구현 검증 결과

- `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'`: `BUILD SUCCESSFUL`
- Phase 03 댓글 테스트: 95개, failures 0, errors 0, skipped 0
- `./gradlew clean test bootJar`: `BUILD SUCCESSFUL`
- 전체 테스트: 158개, failures 0, errors 0, skipped 0
- 기존 게시글 Phase 02 테스트 61개, `TosunsaengApplicationTests`, `ExamsRepositoryScanTest`: 모두 성공
- `git diff --check`: 성공
- 신규 main/test/docs/config trailing whitespace: 없음
- 공개 mapping: 승인된 GET 1개와 POST 2개만 존재
- 댓글 PATCH/DELETE, 게시글 쓰기, newsletter, internal 운영 mapping 부정 테스트 및 정적 검색: 추가 없음
- validation: Unicode 2..500 code point, rule 3 비노출, rule 1~10 전체 수집·중복 제거·정렬, HTML/Markdown/URL·email·반복 경계 테스트 성공
- 저장 차단: validation violation이 있으면 AnonymousVisitor와 BlogComment Repository save 미호출 테스트 성공
- 익명 profile: HMAC-SHA256, raw token 비저장, SecureRandom, noun-image 1:1, 16-byte seed, 재생성, 기존 comment snapshot 불변 테스트 성공
- cookie: HttpOnly, SameSite=Lax, Path=/, Max-Age, 환경별 Secure, JSON raw token 미노출 테스트 성공
- Mongo query/index: VISIBLE, createdAt/_id DESC, pagination/count, tokenHash unique 및 두 comment index의 mock 정의·반복 실행 테스트 성공
- 응답 review: avatarImageUrl만 노출하고 avatarImageKey, anonymousVisitorId, tokenHash, status/hidden 내부 필드가 없음
- 금지 의존성 review: comment source에 AWS SDK/S3 API, Redis, Scheduling, IP 추출 없음
- 비밀값 review: 실제 HMAC secret, 실제 CloudFront 주소, 실제 S3 object filename 없음; test dummy만 존재
- 보호 범위: `build.gradle`, `SecurityConfig`, 기존 BlogPost/exams, BaseResponse/GlobalExceptionAdvice와 기존 S3/Redis/Clock 설정 변경 없음
- 실제 MongoDB query/index와 비공개 S3/CloudFront OAC 통합은 실행하지 않았고 Phase 08 과제로 유지
- Codex review: 승인 계획, 고정 요구사항, 제외 범위와 실제 변경 사이에 미승인 차이 없음

## 열린 문제

- Java 17 기반 `Dokerfile`은 참조되지 않는 것으로 확인됐지만 이번 Phase에서는 삭제하지 않는다.
- 기존 exams의 기능 수준 회귀 테스트가 없어 Phase 01 계획의 보장 범위는 context 기동과 Repository Bean 등록까지다.
- test context가 localhost MongoClient를 생성해 background monitor의 연결 거부 로그가 남지만 Repository method를 호출하거나 연결 성공을 요구하지 않으며 테스트는 통과한다.
- GitHub Actions의 실제 main 배포는 금지된 push·배포를 실행하지 않았으므로 repository 내 정적 순서와 YAML까지만 검증했다.
- `/api/posts/search` 때문에 직접 MongoDB에 게시글을 등록할 때 `search` slug 예약 규칙을 계속 지켜야 한다.
- 실제 Mongo index/query 동작은 Query BSON과 mock index 정의까지만 검증했으며 실제 MongoDB 통합 검증은 Phase 08 전체 검수 과제로 남긴다.
- case-insensitive unanchored title regex는 데이터 증가 시 검색 성능 위험이 있다.
- 비-test 시작 시 index 생성 실패를 명시적으로 전파하므로 Mongo 장애나 기존 중복 slug가 있으면 애플리케이션 시작이 실패한다.
- SameSite=Lax cookie는 프론트와 API가 서로 다른 site이면 전달되지 않을 수 있어 실제 배포 domain 구조 확인이 필요하다.
- 실제 CloudFront domain과 운영 avatar option 목록은 외부 배포 설정에서 제공해야 하며 실제 비공개 S3/CloudFront OAC 연동 검증은 Phase 08 과제다.
- HMAC secret을 회전하면 기존 anon_session과 방문자 연결이 끊기지만 기존 댓글 snapshot은 유지된다. secret rotation 절차는 운영 설계가 필요하다.
- 방문자 저장 뒤 댓글 저장이 실패하면 방문자만 남을 수 있다. 실제 Mongo multi-document transaction 도입 여부는 Phase 08 통합 환경에서 검토한다.
- 이미 댓글에 사용한 avatarImageKey object는 삭제하거나 덮어쓰지 않고 변경 시 versioned 새 key를 사용해야 한다.
- 기존 SecurityConfig의 CSRF 비활성화는 Phase 03 제외 범위라 변경하지 않았다. SameSite=Lax와 실제 frontend/API site 구성을 배포 전에 확인해야 한다.

## 다음 작업

1. 사용자가 Phase 05 DRAFT의 BOUNCED 응답, token rotation key ring, 별도 rate limit secret과 GET 해지 위험을 검토하고 명시적으로 승인한다.
2. 승인 전에는 Phase 05를 `IN_PROGRESS`로 변경하거나 Java, test, Gradle, application 설정과 API를 수정하지 않는다.
3. 승인 후 예상 파일 범위 안에서 newsletter 구독·해지와 IP rate limit을 구현하고 관련 테스트와 전체 build를 수행한다.
4. Phase 08 전체 검수에서 실제 Mongo unique/upsert, Redis Lua·TTL·동시성·topology, Sentry/web server query redaction과 email client scanner 동작을 검증한다.

## Session Log

> 이 절은 append-only다. 이전 기록을 삭제, 수정, 재정렬하지 않고 가장 아래에 새 기록만 추가한다.

### 2026-07-29 14:50:08 KST — Phase 00 시작

- 상태: Phase 00 `IN_PROGRESS`
- 브랜치: `main`
- 시작 시 작업 트리: `.github/workflows/deploy.yml`, `build.gradle` 수정 상태
- 확인 사항: Java toolchain 21, Spring Boot 3.4.2, MongoDB/Redis/Security 의존성, 배포 workflow, Dockerfile, Java 패키지 구조를 읽기 전용으로 확인함
- 기존 설정: 저장소에 `AGENTS.md`와 `.codex` 설정이 없었음
- 범위 결정: 사용자의 직접 요청에 따른 Phase 00 관리 체계 부트스트랩만 수행하고 기존 사용자 변경 및 애플리케이션 파일은 수정하지 않음
- 검증 상태: 관리 파일 작성 및 검증 진행 중

### 2026-07-29 15:00:00 KST — Phase 00 정적 검증

- 상태: Phase 00 `VERIFYING`
- 변경 파일: 요청된 관리 문서 6개, Codex 설정/rules 2개, scripts 3개 생성
- 스크립트 검증: 세 파일 모두 `bash -n` 성공
- rules 검증: 공식 `codex execpolicy check` 로더가 inline test를 포함해 성공적으로 로드함
- 정책 결과: 지정된 Git/GitHub/원격/클라우드/Docker/삭제 명령은 모두 `forbidden`, `mongosh`는 `prompt`
- whitespace 검증: `git diff --check` 성공, 신규 파일 trailing whitespace 없음

### 2026-07-29 15:03:04 KST — Phase 00 전체 검증 실패

- 상태: Phase 00 `BLOCKED`
- 첫 실행: `./scripts/codex-verify.sh`가 `gradlew` 실행 권한 부족으로 테스트 전에 실패함
- 권한 확인: `gradlew`는 파일 시스템과 Git index 모두 mode `100644`이며 Phase 00 범위 밖이라 수정하지 않음
- 대체 확인: `bash ./gradlew clean test bootJar`로 wrapper를 실행함
- 결과: `compileJava`, `bootJar`, test class 생성 성공; `TosunsaengApplicationTests.contextLoads()` 1건 실패
- 실패 원인: `S3Config`가 요구하는 `AWS_ACCESS_KEY` placeholder 미해결
- 보존 사항: 실패를 성공으로 바꾸지 않았고 Phase 00을 `DONE`으로 표시하지 않음
- 파일 영향: Gradle이 ignored `build/` 산출물을 재생성했으며, 기존 애플리케이션 소스·테스트·빌드·배포 파일은 수정하지 않음

### 2026-07-29 15:56:40 KST — Phase 00 재검증 완료

- 상태: 전체 `IN_PROGRESS`, Phase 00 `DONE`, 현재 단계 Phase 01 `TODO`
- 브랜치: `feat/blog-mvp`
- 이전 실패 원인: 테스트 환경에 `AWS_ACCESS_KEY`와 `AWS_SECRET_KEY` 환경변수가 없었음
- 재검증: 두 환경변수를 설정한 뒤 `./gradlew clean test bootJar`가 `BUILD SUCCESSFUL`로 완료됨
- 추가 검증: `gradlew` 실행 가능, `git diff --check` 성공
- 판단: 테스트 환경이나 애플리케이션 코드를 수정할 필요가 없었으며 Phase 00 완료 조건을 충족함
- preflight 상태: Phase 00의 초기 관리 파일 11개가 미추적 상태여서 `scripts/codex-preflight.sh`는 작업 트리 검사에서 실패함
- 변경 범위: `docs/blog-mvp/IMPLEMENTATION_STATUS.md`만 수정했으며, 소스 코드, 테스트 코드, `build.gradle`, `application.yml`, CI 파일은 수정하지 않음
- 다음 단계: Phase 01 계획은 작성하지 않았고 `TODO`로 유지함

### 2026-07-29 16:08:12 KST — Phase 01 계획 수립 시작

- 상태: Phase 01 `PLANNING`
- 브랜치: `feat/blog-mvp`
- 시작 시 작업 트리: 깨끗함
- 사전 점검: `scripts/codex-preflight.sh` 성공
- 작업 범위: 지정된 저장소 파일의 읽기 전용 분석, `DRAFT` 계획서 작성, 상태 문서 갱신만 수행
- 금지 범위: Java 소스, 테스트, Gradle, CI, Docker/Compose 설정 및 애플리케이션 파일 수정 금지

### 2026-07-29 16:20:00 KST — Phase 01 DRAFT 계획서 작성 완료

- 상태: Phase 01 `PLANNING`, 계획 `DRAFT`
- 분석 결과: Mongo 탐색이 exams Repository 패키지로 제한됨, CI가 checkout/Java 설정 전 Gradle을 실행함, 기존 Scheduling과 Compose 설정이 없음
- Docker 확인: CI는 Java 21 `Dockerfile`을 참조하며 Java 17 `Dokerfile`은 참조되지 않음
- 권고안: 명시적 Mongo domain 범위 확장, 별도 `SchedulingConfig`, test profile의 더미 환경값, 로컬 MongoDB/Redis compose
- 변경 파일: `docs/blog-mvp/plans/PHASE-01-infrastructure.md`, `docs/blog-mvp/IMPLEMENTATION_STATUS.md`
- 미변경 범위: Java 소스, 테스트, Gradle, CI, application 설정, Docker/Compose, `.gitignore`
- 검증: preflight, 필수 heading, trailing whitespace, `git diff --check` 성공
- 다음 단계: 사용자 명시적 승인 전 구현 금지

### 2026-07-29 16:30:56 KST — Phase 01 조건부 승인 및 구현 시작

- 상태: Phase 01 `IN_PROGRESS`, 계획 `APPROVED`
- 승인 근거: 사용자가 Mongo 명시적 domain 탐색, 별도 Scheduling 설정, CI 순서, 최소 test profile, exams Repository Bean 테스트, 로컬 Compose 범위를 명시적으로 승인함
- 상태 이력: 직전 실제 문서 상태는 이전 사용자 지시에 따른 `PLANNING`이었으며 `AWAITING_APPROVAL`은 표에 반영되지 않았음. 이전 Session Log는 수정하지 않고 이 차이를 현재 기록에 남김
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: 직전 Codex가 만든 계획서와 상태 문서 변경만 존재함
- 제외 범위: BlogPost, Comment, NewsletterSubscriber, 제품 API, 이메일 발송, 내부 인증, SecurityConfig, exams 비즈니스 로직, Testcontainers, 관련 없는 리팩터링
- 구현 원칙: 승인된 예상 변경 파일 밖의 수정이 필요하면 즉시 중단하고 보고함

### 2026-07-29 16:34:42 KST — Phase 01 구현 완료 및 검증 시작

- 상태: Phase 01 `VERIFYING`, 계획 `APPROVED`
- 구현 범위: Mongo domain 탐색, 별도 Scheduling 설정, CI 순서 정리, 최소 test profile, exams Repository Bean 테스트, MongoDB/Redis 로컬 Compose, env 보호 설정
- 미구현 범위: 제품 도메인/API, 이메일, 실제 scheduler 작업, 내부 인증, Testcontainers, exams 비즈니스 로직 변경
- 보존 파일: `SecurityConfig`, 운영 `application.yml`, `build.gradle`, `Dockerfile`, `Dokerfile`
- 다음 단계: 필수 검증과 Codex review가 모두 성공하기 전에는 `DONE`으로 변경하지 않음

### 2026-07-29 16:41:05 KST — Phase 01 검증 완료

- 상태: Phase 01 `DONE`, 계획 `EXECUTED`, 현재 단계 Phase 02 `TODO`
- 필수 검사: `git diff --check` 성공
- 필수 빌드: 샌드박스 내부 첫 `./gradlew clean test bootJar`는 Gradle cache lock 접근 제한으로 task 실행 전 실패했으나, 승인된 동일 명령 재실행은 `BUILD SUCCESSFUL`
- 테스트: `contextLoads()` 1건과 exams Repository Bean 등록 1건 모두 성공, failures/errors/skipped 0
- Repository 결과: 기존 exams Mongo Repository interface 5개 등록 확인
- Compose: `docker compose -f compose.local.yml config` 성공, container는 시작하지 않음
- CI review: main push trigger와 기존 배포 동작 유지, checkout → Java 21 → gradlew 권한 → test → bootJar → Docker/SSH 순서 확인
- 비밀값 review: 실제 AWS key/private key 패턴 없음, test dummy만 사용
- 제외 범위 review: BlogPost, Comment, NewsletterSubscriber, 제품 API, 이메일, 내부 인증, SecurityConfig, exams 비즈니스 로직, Testcontainers 변경 없음
- 승인 계획 차이: 조건부 승인에서 확정한 최소 test profile과 정적 Compose 검증을 반영했으며 미승인 차이 없음
- 남은 위험: Java 17 `Dokerfile` 잔존, exams 기능 통합 테스트 부재, localhost Mongo monitor 연결 거부 로그, 실제 GitHub Actions 배포 미실행
- 다음 단계: 사용자 검토 전 Phase 02 작업 시작 금지

### 2026-07-29 17:06:32 KST — Phase 02 계획 수립 시작

- 상태: Phase 02 `PLANNING`, 계획 `DRAFT`
- 브랜치: `feat/blog-mvp`
- 시작 시 작업 트리: 깨끗함
- 사전 점검: `scripts/codex-preflight.sh` 성공
- 작업 범위: 지정된 코드와 관리 문서의 읽기 전용 분석, Phase 02 DRAFT 계획서 작성, 상태 문서 갱신만 수행
- 금지 범위: Java 소스, 테스트, Gradle, application 설정, API, Mongo Document 수정·생성 및 모든 구현 작업

### 2026-07-29 17:12:33 KST — Phase 02 DRAFT 계획서 작성 완료

- 상태: Phase 02 `PLANNING`, 계획 `DRAFT`
- 변경 파일: `docs/blog-mvp/plans/PHASE-02-blog-read-search.md`, `docs/blog-mvp/IMPLEMENTATION_STATUS.md`
- 분석 결과: exams에 페이지네이션 선례와 영속 시간 선례가 없고, Mongo auto-index 및 test Mongo 의존성이 없으며, 공통 status 실제 경로는 `global/error/code/status`임
- 권고안: `Instant`와 UTC Clock, MongoTemplate custom query의 단일 공개 Criteria, 최대 두 번의 batch 관련 글 조회, programmatic blog index 초기화, 최대 size 100
- 테스트 전략: 기존 test dependency로 Repository Query BSON, Service, MockMvc Controller, index definition을 단위 검증하고 전체 기존 테스트를 회귀 실행
- 재독·정적 검증: 계획서 생성 직후 필수 문서 재독, 필수 heading, whitespace, `git diff --check`, 변경 범위 확인 성공
- 미변경 범위: Java, 기존 테스트, Gradle, application 설정, SecurityConfig, exams 및 모든 API 구현
- 다음 단계: 사용자 명시적 승인 전 계획 `APPROVED` 및 Phase 02 `IN_PROGRESS` 전환과 구현 금지

### 2026-07-29 17:19:07 KST — Phase 02 계획 승인 및 구현 시작

- 상태: Phase 02 `IN_PROGRESS`, 계획 `APPROVED`, 현재 단계 Phase 02 유지
- 승인 근거: 사용자가 `PHASE-02-blog-read-search.md`를 명시적으로 승인하고 구현 범위와 확정 결정 사항을 제시함
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: 이전 Codex가 작성한 Phase 02 계획서와 상태 문서 변경만 존재함
- 승인 시 확정: 최대 size 100, `Instant`/UTC Clock, 발행 경계 포함, publishedAt/createdAt/id DESC 안정 정렬, `search` 예약 slug, programmatic index, 세분화된 pagination 오류
- 테스트 제한: 실제 Mongo 통합 테스트와 Testcontainers는 제외하고 Query BSON 단위 테스트를 수행하며 실제 Mongo index/query 통합 검증은 Phase 08 과제로 기록함
- 제외 범위: 쓰기·관리자·댓글·뉴스레터 API, Redis, Scheduling 작업, 내부 인증, SecurityConfig, build/application 설정, exams 비즈니스 로직, 관련 없는 리팩터링
- 구현 원칙: 승인된 예상 파일과 확정 계약 밖의 변경이 필요하면 즉시 중단하고 보고함

### 2026-07-29 17:30:54 KST — Phase 02 구현 완료 및 검증 시작

- 상태: Phase 02 `VERIFYING`, 계획 `APPROVED`, 현재 단계 Phase 02 유지
- 구현 범위: BlogPost/상태/slug 정책, UTC Clock, custom Mongo query, programmatic index, 목록·상세·검색·관련 글, BaseResponse status와 승인 테스트
- 공개 조건: PUBLISHED, publishedAt exists/non-null/`$lte`를 Repository helper 한 곳에 적용
- 안정 정렬: publishedAt DESC, createdAt DESC, `_id` DESC를 목록·검색·최신 관련 글 보충에 적용
- 미구현 범위: 쓰기·관리자·댓글·뉴스레터 API, 등록 스크립트, Redis, Scheduling 작업, 내부 인증, SecurityConfig, Gradle/application 설정, Testcontainers
- 다음 단계: 정적 검사, 관련 테스트, `./gradlew clean test bootJar`, 승인 범위와 실제 diff review

### 2026-07-29 17:38:50 KST — Phase 02 검증 완료

- 상태: Phase 02 `DONE`, 계획 `EXECUTED`, 현재 단계 Phase 03 `TODO`
- 필수 검사: `git diff --check` 성공
- 필수 빌드: `./gradlew clean test bootJar` `BUILD SUCCESSFUL`
- 테스트: Phase 02 61개와 기존 context/exams 2개, 총 63개 failures/errors/skipped 0
- 실패 이력: 보강 테스트 첫 실행에서 검색 content/count Query BSON 동등성 1건 실패 후 Criteria 재사용으로 수정하고 전체 재검증 성공
- API review: 승인된 GET 세 개만 존재하고 게시글 쓰기·댓글·뉴스레터 API 없음
- query review: 공개 Criteria 단일 helper, `$lte` 경계 포함, 안정 정렬, 동일 count filter, regex quote/case-insensitive, 관련 글 batch 조회 확인
- index review: idempotent `ensureIndex`, slug unique, status/publishedAt compound, 예외 미흡수, test profile 비활성화 확인
- 보존 review: exams, BaseResponse, 전역 advice, SecurityConfig, Gradle/application 설정 미변경
- 비밀값 review: 새 실제 비밀값 패턴 없음
- 승인 계획 차이: 승인 메시지에서 확정한 slug 정책 파일과 Criteria 재사용 수정 외 미승인 차이 없음
- 남은 위험: 실제 Mongo index/query 통합 검증은 Phase 08 과제, regex 성능, 직접 DB 등록 시 slug 규칙, index 생성 실패 시 시작 차단
- 다음 단계: 사용자 검토 전 Phase 03 계획이나 구현 시작 금지

### 2026-07-30 11:21:10 KST — Phase 03 DRAFT 계획서 작성 완료

- 상태: Phase 03 `PLANNING`, 계획 `DRAFT`, Current phase Phase 03 유지
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: 사용자가 Phase 02 변경을 검토·커밋한 뒤 깨끗함
- 사전 점검: `scripts/codex-preflight.sh` 성공
- 선행 조건: Phase 02 `DONE`, Phase 02 계획 `EXECUTED`
- 분석 범위: 기존 blog/exams 전체, 공통 응답·status·예외, global config, 전체 test, build와 application 진입점
- 주요 권고: 기존 공개 BlogPost query와 UTC Clock 재사용, MongoTemplate 댓글 query, programmatic index, HMAC-SHA256 token hash, 180일 HttpOnly SameSite=Lax cookie, 최대 size 100
- validation 권고: JsonNode 타입 판정, rule 3 비노출, rule 1~10 전체 수집·정렬, HTML entity·이메일 차단, 동일 문자 8회·짧은 구문 4회 반복 차단
- Phase 04 이관: website 허니팟 실행, Redis rate limit, IP/최근 댓글 기반 제한, 숨김·복원
- 변경 파일: `docs/blog-mvp/plans/PHASE-03-anonymous-comments.md`, `docs/blog-mvp/IMPLEMENTATION_STATUS.md`
- 미변경 범위: Java, 테스트, Gradle, application 설정, 기존 게시글·exams, SecurityConfig, RedisConfig
- 검증: 생성 후 필수 문서 재독, 필수 heading, rule/test 목록, trailing whitespace, `git diff --check`, 변경 범위 확인 성공
- 다음 단계: 사용자가 계획의 승인 필요 사항을 명시적으로 승인하기 전 구현 금지

### 2026-07-30 11:39:28 KST — Phase 03 랜덤 프로필 이미지 계획 보완

- 상태: Phase 03 `PLANNING`, 계획 `DRAFT`, Current phase Phase 03 유지
- 사용자 추가 요구: immutable 형용사/명사 후보, SecureRandom Bean, 별도 16-byte avatarSeed, 제한 재시도와 생성 실패 정책, 프로필 이미지 URL 응답
- 저장·응답 권고: AnonymousVisitor와 BlogComment에는 avatarImageKey를 snapshot 저장하고 목록·작성·재생성 응답에는 avatarImageUrl을 제공
- S3 범위: to-teacher-web-blog bucket의 character-image/ 정적 object만 참조하며 upload, delete, list, HEAD, presigned URL, AWS network 호출은 제외
- 설정 권고: BLOG_AVATAR_IMAGE_BASE_URL을 .env.example에만 안내하고 main/test application 설정과 기존 S3Config/exams S3 코드는 변경하지 않음
- 테스트 보강: 후보 목록, deterministic random, 16-byte seed, key 검증, URL 조립, 재시도 실패, comment snapshot, 세 API 응답, S3 무호출 검증을 64~73으로 추가
- 미확정 사항: 실제 object filename·확장자, direct public S3와 CloudFront 중 제공 방식, object 접근 정책, nickname 명사와 이미지 mapping
- 변경 범위: 계획서와 IMPLEMENTATION_STATUS.md만 변경하고 Java, 테스트, Gradle, application 설정은 수정하지 않음
- 검증: 필수 heading과 DRAFT/PLANNING 상태, 테스트 64~73, trailing whitespace, `git diff --check`, branch와 변경 파일 범위를 확인함
- 다음 단계: 위 미확정 사항과 계획 전체를 사용자가 명시적으로 승인하기 전 구현 금지

### 2026-07-30 11:49:03 KST — Phase 03 조건부 승인 및 구현 시작

- 상태: Phase 03 `IN_PROGRESS`, 계획 `APPROVED`, Current phase Phase 03 유지
- 승인 근거: 사용자가 `PHASE-03-anonymous-comments.md`를 조건부 승인하고 API, 설정, profile image, validation, 테스트와 제외 범위를 확정함
- 조건 변경: HMAC 환경변수는 BLOG_ANONYMOUS_TOKEN_SECRET, cookie는 BLOG_ANONYMOUS_COOKIE_*, avatar base는 BLOG_ANONYMOUS_AVATAR_BASE_URL을 사용함
- profile 확정: CloudFront OAC와 비공개 S3, 외부 noun-image option 1:1 mapping, 두 option 이상, 재생성 시 adjective와 option 모두 변경
- validation 확정: rule 1~10 유지, rule 3 비노출, 동일 code point 10회와 2~20 code point 구문 5회 반복 차단
- 설정 범위: .env.example 변수 안내, main application.yml 최소 properties binding, application-test.yml 안전한 더미 secret/base URL/options만 수정
- 제외 범위: 댓글 수정·삭제·운영 기능, Redis/IP/허니팟 차단, AWS SDK/API, CloudFront 설정, 실제 Mongo/S3 통합 테스트, SecurityConfig, exams 리팩터링
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: 이전 Codex가 작성한 Phase 03 계획서와 상태 문서 변경만 존재함
- 구현 원칙: 승인된 예상 파일 밖 변경이나 계약 차이가 필요하면 즉시 중단하고 사용자에게 보고함

### 2026-07-30 13:10:51 KST — Phase 03 구현 완료 및 검증 시작

- 상태: Phase 03 `VERIFYING`, 계획 `APPROVED`, Current phase Phase 03 유지
- 구현 범위: 익명 방문자 cookie/HMAC, 랜덤 nickname·avatar, 댓글 snapshot Document, MongoTemplate 조회, programmatic index, rule 1~10 validation, 승인된 세 공개 API와 전용 validation 응답
- 설정 범위: BLOG_ANONYMOUS_* 최소 binding, test dummy secret/CDN/options, `.env.example` 변수 안내만 반영함
- 관련 테스트: 댓글 domain의 policy, Repository query, index, visitor/service, Controller 테스트 95개 성공
- 실패 이력: 첫 보강 실행에서 한국어 조사와 붙은 `example.com을` 경계 1건이 실패해 ASCII domain boundary로 수정한 뒤 전체 댓글 테스트를 재실행해 성공함
- 정적 사전 검사: `git diff --check` 성공, comment source에 AWS SDK/S3Presigner, RedisTemplate, Scheduling, PATCH/DELETE/internal/newsletter mapping 없음
- 미구현 범위: 댓글 수정·삭제·운영 기능, Redis/IP/허니팟 차단, AWS API, 실제 Mongo/S3/CloudFront 통합, SecurityConfig와 exams 리팩터링
- 다음 단계: 필수 전체 `clean test bootJar`, 기존 게시글·exams 회귀, 비밀값·응답 노출·금지 API 정적 review

### 2026-07-30 13:17:39 KST — Phase 03 검증 완료

- 상태: Phase 03 `DONE`, 계획 `EXECUTED`, Current phase Phase 04 `TODO`
- 필수 검사: `git diff --check` 성공, 신규 파일 trailing whitespace 없음
- 관련 테스트: `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'` 성공, 댓글 95개 failures/errors/skipped 0
- 필수 전체 빌드: `./gradlew clean test bootJar` `BUILD SUCCESSFUL`
- 전체 회귀: 총 158개 failures/errors/skipped 0; 기존 게시글 61개, application context와 exams Repository scan 포함
- API review: 승인된 댓글 GET 1개와 POST 2개만 추가하고 댓글 PATCH/DELETE, 게시글 쓰기, newsletter, internal 운영 API는 추가하지 않음
- validation review: 고정 rule 1~10과 rule 3 비노출, 전체 violation 수집·정렬, Unicode/HTML/Markdown/URL/email/spam 경계, save 금지 확인
- 익명 보안 review: raw token은 Mongo/JSON/log에 없고 HMAC hash만 저장하며 HttpOnly/SameSite=Lax/Path/Max-Age/Secure cookie 정책 확인
- profile review: SecureRandom, immutable 형용사와 외부 noun-image option, 별도 16-byte seed, 재생성, comment snapshot 불변, avatarImageUrl 공개와 key 비공개 확인
- Mongo review: VISIBLE 조회와 stable sort/pagination/count, tokenHash unique 및 두 comment index 정의·idempotent 초기화 확인
- 제외 범위 review: AWS SDK/API, Redis rate limit, IP, 허니팟 차단, Scheduling, SecurityConfig, 실제 Mongo/S3/CloudFront 통합, 관련 없는 exams 변경 없음
- 승인 계획 차이: 한국어에 붙은 domain/HTTP 경계와 HTML event handler 오탐 보완, cookie Max-Age overflow fail-fast는 승인 의미 안의 정확도·안전성 구현이며 미승인 범위 차이 없음
- 남은 위험: 실제 Mongo index/query와 CloudFront OAC/private S3 object 연동, 배포 site-cookie 구조, HMAC secret rotation, visitor/comment 다중 저장 원자성은 Phase 08 또는 후속 운영 검토 대상
- 다음 단계: 사용자의 코드 검토 전 Phase 04 계획이나 구현을 시작하지 않음

### 2026-07-30 13:20:10 KST — Phase 03 완료 상태 최종 확인

- 상태: 계획 `EXECUTED`, Phase 03 `DONE`, Current phase Phase 04 `TODO`를 재확인함
- 브랜치: `feat/blog-mvp` 유지
- 완료 문서 반영 후 `git diff --check`와 신규 파일 trailing whitespace 검사가 성공함
- 상태 검색의 shell quoting 실패 1회는 read-only였고 파일 또는 Git 상태에 영향이 없으며 수정된 명령으로 재확인함
- 사용자 검토를 기다리며 Phase 04 계획이나 구현은 시작하지 않음

### 2026-07-30 13:41:22 KST — Phase 04 DRAFT 계획서 작성

- 상태: Phase 04 `PLANNING`, 계획 `DRAFT`, Current phase Phase 04 유지
- 상태값은 이번 계획 수립 요청의 명시적 지시에 따라 `PLANNING`으로 유지하며 `IN_PROGRESS`나 `DONE`으로 변경하지 않음
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: clean
- 사전 점검: `scripts/codex-preflight.sh` 성공
- 선행 조건: Phase 03 `DONE`, Phase 03 계획 `EXECUTED`
- 분석 범위: 기존 댓글 작성·visitor 저장 흐름, comment Document/query/index, RedisConfig와 exams Redis 사용 전체, SecurityConfig, 공통·댓글 예외, main/test 설정과 기존 test 기반
- 주요 권고: visitor/IP 5개 counter와 duplicate reservation을 단일 Lua admission으로 처리, 생성 시점 TTL window, owner-checked duplicate release, 연결·timeout만 fail-open
- IP·보안 권고: `getRemoteAddr()`만 사용, 별도 `BLOG_COMMENT_RATE_LIMIT_SECRET` HMAC, raw token/IP/content key·log·응답 금지
- 허니팟 권고: 값이 있으면 visitor/comment/Redis 미호출, generic HTTP 202와 result·cookie 없음
- 운영 권고: Controller 없이 조회·숨김·복원 Service, conditional Mongo findAndModify, 삭제 상태·API 없음
- 검증 이관: 실제 Redis Lua·TTL·동시성·topology와 실제 Mongo query는 Phase 08 과제로 명시
- 변경 파일: Phase 04 계획서와 이 상태 문서만 변경
- 미변경 범위: Java, 테스트, Redis 구현, application 설정, Gradle, SecurityConfig, 기존 댓글·게시글·exams
- 다음 단계: 사용자가 DRAFT 계획과 결정 항목을 명시적으로 승인하기 전 구현 금지

### 2026-07-30 13:45:10 KST — Phase 04 계획 수립 검증

- 필수 문서: `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, `IMPLEMENTATION_STATUS.md`, `PLANS.md`, Phase 03 계획서를 계획서 생성 전에 읽음
- 시작 명령: `git branch --show-current`, `git status --short`, `scripts/codex-preflight.sh` 실행 및 성공
- 코드 분석 명령: `rg --files`, `rg -n`, `sed -n`으로 blog comment 전체 구조, BlogPost 공개 query, RedisConfig와 exams Redis 전체 참조, SecurityConfig, BaseResponse·status·exception, application main/test 설정, build와 관련 tests를 확인
- 문서 작성: `apply_patch`로 Phase 04 계획서를 생성하고 이 상태 문서의 Phase 04 `PLANNING`, 현재 목표, 변경 파일, 다음 작업과 append-only Session Log를 반영
- 생성 직후 재독: 필수 문서와 새 Phase 04 계획서를 규정 순서로 EOF까지 다시 읽음
- 계획 검증: 필수 heading, Redis/HMAC/허니팟/중복/상태 전이 항목, 최소 test 1~68, DRAFT·PLANNING 상태를 `rg`로 확인
- 범위 검증: `git status --short --untracked-files=all` 결과는 Phase 04 계획서와 상태 문서 두 개뿐이며 Java, test, Gradle, env/application 설정 변경 없음
- 정적 검증: 변경 문서 trailing whitespace 없음, `git diff --check` 성공
- 계획 전용 작업이므로 Gradle test와 build, Redis·Mongo 연결은 실행하지 않음
- 결과: 계획 `DRAFT`, Phase 04 `PLANNING`, Current phase 04를 유지하고 사용자 승인을 기다림

### 2026-07-30 13:55:19 KST — Phase 04 계획 승인 및 구현 시작

- 상태: Phase 04 `IN_PROGRESS`, 계획 `APPROVED`, Current phase Phase 04 유지
- 승인 근거: 사용자가 Phase 04 계획을 명시적으로 승인하고 Redis topology, Lua blocker 처리, HMAC/IP, 허니팟, duplicate reservation, 상태 전이와 테스트 범위를 확정함
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: 직전 Codex가 만든 Phase 04 계획서와 상태 문서 변경만 존재함
- preflight: 브랜치는 통과했고 known Phase 04 문서 두 개가 미커밋 상태라 clean-tree 검사만 실패함
- topology 확인: Redis Cluster·Sentinel·다중 primary 설정 없음, main 설정은 단일 host/port, local compose는 단일 Redis container
- standalone 제한: 여러 key Lua는 Cluster에서 `CROSSSLOT` 가능성이 있어 Cluster 지원을 구현하지 않고 전환 시 재설계 대상으로 기록함
- 확정 정책: blocker 최대 TTL, 승인된 tie 우선순위, website trim 후 허니팟, pre-save owner reservation, Mongo 실패 owner-checked cleanup, counter 비rollback
- IP 정책: `request.getRemoteAddr()`만 사용하고 forwarding header와 SecurityConfig는 변경하지 않음
- 제외 범위: 운영 Controller·내부 인증, 댓글 수정·삭제, CAPTCHA, newsletter, 실제 Redis/Mongo 접근, Cluster, Testcontainers, 관련 없는 리팩터링
- 구현 원칙: 승인 계획의 예상 파일 밖 변경이나 계약 차이가 필요하면 소스 수정을 중단하고 보고함

### 2026-07-30 16:47:49 KST — Phase 04 구현 완료 및 검증 시작

- 상태: Phase 04 `VERIFYING`, 계획 `APPROVED`, Current phase Phase 04 유지
- Redis: standalone/single-primary 전제의 단일 Lua admission으로 visitor 3개 창, IP 2개 창과 duplicate owner reservation을 함께 판정함
- 댓글 작성: 허니팟 조기 202, content validation 선행, 공개 글 확인, visitor prepare, Redis admission, visitor commit, comment save와 owner-checked cleanup 순서로 구현함
- 장애 정책: Redis connection/timeout만 sanitized warning 후 fail-open하고 Lua 계약·key·decode·application 오류는 generic 500으로 처리함
- 운영 기능: Controller 없이 status/post/slug/[from,to) 조회와 조건부 findAndModify 숨김·복원 Service를 구현하고 삭제 상태·API는 추가하지 않음
- 인덱스: 기존 postId/status/createdAt을 재사용하고 status/createdAt 운영 조회 index만 추가함
- 설정: 별도 32-byte HMAC secret, enabled, duplicate TTL만 환경변수로 노출하고 test profile은 안전한 dummy secret과 disabled를 사용함
- 예비 검증: 댓글 관련 test와 전체 `./gradlew test`가 성공했으며 전체 필수 clean build와 정적 범위 검증을 이어서 수행함
- 알려진 제한: fixed-window 경계 burst, Redis-Mongo 분산 transaction 부재, fail-open 중 남용 가능성, Cluster `CROSSSLOT` 비호환과 실제 Redis/Mongo 통합 미검증은 Phase 08 대상으로 유지함

### 2026-07-30 16:50:52 KST — Phase 04 검증 완료

- 상태: Phase 04 `DONE`, 계획 `EXECUTED`, Current phase Phase 05 `TODO`
- 필수 검사: `git diff --check` 성공
- 관련 테스트: `./gradlew test --tests 'web.tosunsaeng.domain.blog.comment.*'` 성공, 댓글 관련 152개 failures/errors/skipped 0
- 필수 전체 빌드: `./gradlew clean test bootJar` `BUILD SUCCESSFUL`, 전체 215개 failures/errors/skipped 0, bootJar 생성 성공
- Redis review: standalone 단일 Lua admission, 5개 fixed-window counter, duplicate owner reservation, 최대 TTL·동률 scope 선택, counter TTL 비연장과 no-partial-mutation 계약 확인
- 장애 review: connection/timeout만 fail-open, 구현 오류 generic 500, Mongo 실패 시 owner-checked duplicate cleanup과 rate counter 비rollback 확인
- IP·개인정보 review: `getRemoteAddr()`만 사용하고 raw IP/token/content·digest·key·secret을 응답과 로그에 노출하지 않으며 X-Forwarded-For를 직접 신뢰하지 않음
- 허니팟 review: trim 후 값이 있으면 202/result·commentId·cookie 없음, visitor/comment/Redis 미호출 확인
- moderation review: 외부 Controller 없이 조회·숨김·복원 Service만 추가하고 조건부 상태 전이, half-open 기간, 민감정보 제외와 추가 index 확인
- 제외 범위 review: 댓글 수정·삭제, `/internal/comments`, API Key, SecurityConfig, CAPTCHA, newsletter, AWS/S3, Cluster, Testcontainers와 실제 Redis/Mongo 접근 없음
- 실패 이력: 구현 중 record static factory 이름 충돌 컴파일 두 건과 index test 기대 호출 수 한 건을 수정한 뒤 모든 필수 검증을 재실행해 성공함
- 승인 계획 차이: 외부 기능 차이는 없고 lazy IP Supplier와 최소 환경변수 노출은 승인 처리 순서·설정 범위 안의 구현 세부사항임
- Phase 08 이관: 실제 Redis Lua syntax·TTL·동시성, standalone topology 장애, Cluster 전환 재설계, 실제 Mongo index/query와 신뢰 proxy client IP 검증
- 다음 단계: 사용자 검토와 커밋 전 Phase 05 계획 또는 구현을 시작하지 않음

### 2026-07-30 17:43:47 KST — Phase 05 DRAFT 계획서 작성

- 상태: Phase 05 `PLANNING`, 계획 `DRAFT`, Current phase Phase 05 유지
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: clean
- 사전 점검: `scripts/codex-preflight.sh` 성공
- 선행 조건: Phase 04 `DONE`, Phase 04 계획 `EXECUTED`
- 분석 범위: blog·comment·exams, 공통 BaseResponse/status/exception/config, Redis/Lua/index/test 패턴, main/test application 설정과 Sentry query 노출 위험
- 주요 권고: normalize-first Jakarta email validation, email unique + 조건부 findAndModify/duplicate 재조회, stateless HMAC token과 subscriber tokenVersion
- token 권고: payload에 formatVersion·keyId·subscriberId·subscriberTokenVersion·issuedAt만 포함하고 active + previous verification key ring으로 rotation 준비
- rate limit 권고: 별도 newsletter HMAC secret, getRemoteAddr, 10회/600초와 30회/86400초의 원자적 2-key Lua, connection/timeout만 fail-open
- 개인정보 권고: email/token 원문 응답·로그 금지, unsubscribe query를 Sentry event/transaction에서 제거하고 실제 access log 검증은 Phase 08로 이관
- 변경 파일: Phase 05 계획서와 이 상태 문서만 변경
- 미변경 범위: Java, test, Gradle, application/env 설정, API, Mongo Document, SecurityConfig, GlobalExceptionAdvice와 기존 blog/comment/exams
- 다음 단계: 계획 생성 후 필수 문서 재독과 정적 검증을 완료하고 사용자 명시적 승인을 기다림

### 2026-07-30 17:53:16 KST — Phase 05 계획 수립 검증

- 필수 문서: 계획서 생성 전 지정 문서와 Phase 04 계획서를 읽고, 생성 후 `AGENTS.md`, `REQUIREMENTS.md`, `WORKFLOW.md`, 이 상태 문서, `PLANS.md`, Phase 05 계획서를 규정 순서와 EOF까지 다시 읽음
- 시작 명령: `git branch --show-current`, `git status --short`, `scripts/codex-preflight.sh`를 실행했고 branch, clean tree와 PASS를 확인함
- 코드 분석: `rg --files`, `rg -n`, `sed -n`으로 blog/comment/exams, 공통 응답·status·exception/config, Redis/Lua/index/test와 main/test 설정을 읽기 전용으로 확인함
- 문서 작성: `apply_patch`로 Phase 05 DRAFT 계획서를 생성하고 Phase 05만 `PLANNING`으로 변경했으며 Session Log는 append-only로 추가함
- 계획 검증: `PLANS.md`와 사용자 필수 heading, 테스트 1~74의 연속성, DRAFT·PLANNING과 Current phase 05를 확인함
- 범위 검증: `git status --short --untracked-files=all` 결과는 Phase 05 계획서와 상태 문서 두 개뿐이며 Java, test, Gradle, application/env 설정 변경 없음
- 정적 검증: 두 변경 문서의 trailing whitespace 없음, `git diff --check` 성공
- 계획 전용 작업이므로 Gradle test/build, 실제 MongoDB·Redis·Sentry와 이메일 발송은 실행하지 않음
- 결과: 계획 `DRAFT`, Phase 05 `PLANNING`, Current phase 05를 유지하고 사용자 명시적 승인을 기다림

### 2026-07-31 09:46:17 KST — Phase 05 unsubscribe API 계획 보정

- 상태 유지: Phase 05 `PLANNING`, 계획 `DRAFT`, Current phase Phase 05 유지
- 사용자 보정: backend 해지 계약을 query token GET에서 JSON body `POST /api/newsletter/unsubscribe`로 변경함
- GET 정책: `GET /api/newsletter/unsubscribe` mapping을 제공하지 않고 GET 요청은 Newsletter Service·Repository를 호출하거나 Subscriber 상태를 변경하지 않음
- frontend 흐름: Phase 06 email link는 frontend 확인 page를 가리키고, 사용자가 확인한 뒤 frontend가 backend POST를 호출하도록 계획함; frontend 화면은 Phase 05 제외 범위
- token 정책: backend query parameter fallback 없이 POST body로만 받고 응답·일반 log·예외·Sentry request data·Subscriber Document에 원문 또는 hash를 노출·저장하지 않음
- rate limit 보정: 역직렬화 가능한 모든 subscribe 요청을 email·consent validation보다 먼저 IP rate limit에 집계하는 기존 확정 결정을 계획 전체에 반영함
- 테스트 보강: GET mapping 부재와 상태 불변, POST body token만 허용, query-only POST 거절, token 응답·log 미노출 MockMvc·보안 테스트 75~79를 추가함
- Phase 08 이관: frontend URL query의 browser history, Referer, frontend hosting/CDN access log와 email scanner 동작 검증
- 변경 범위: Phase 05 DRAFT 계획서 보정과 이 append-only Session Log 추가만 수행하고 Java, test, Gradle, application/env 설정은 수정하지 않음
- 다음 단계: 보정된 Phase 05 DRAFT 전체에 대한 사용자 명시적 승인 전 구현 금지

### 2026-07-31 10:06:03 KST — Phase 05 계획 승인 및 구현 시작

- 상태: Phase 05 `IN_PROGRESS`, 계획 `APPROVED`, Current phase Phase 05 유지
- 승인 근거: 사용자가 보정된 Phase 05 계획과 `web.tosunsaeng.domain.blog.newsletter` 구현 package를 명시적으로 승인함
- 테스트 선택자: `web.tosunsaeng.domain.blog.newsletter.*`로 정정 승인됐고 `.blog`가 빠진 선택자는 사용하지 않음
- 시작 브랜치: `feat/blog-mvp`
- 시작 작업 트리: 사용자가 Phase 05 계획 문서를 검토·커밋한 뒤 clean
- preflight: `scripts/codex-preflight.sh` 성공
- 선행 조건: Phase 04 `DONE`, Phase 04 계획 `EXECUTED`
- 공개 계약: subscribe POST와 JSON body unsubscribe POST만 구현하고 GET unsubscribe, verify와 internal newsletter API는 추가하지 않음
- 보호 범위: SecurityConfig, 기존 blog/comment/exams, 실제 email Sender·Scheduler·Delivery·Campaign과 운영 외부 접근은 변경·구현하지 않음
- 구현 원칙: 승인된 예상 파일과 계약 밖 변경이 필요하면 소스 수정을 중단하고 사용자에게 차이를 보고함

### 2026-07-31 10:44:59 KST — Phase 05 package 구조 추가 승인

- 상태 유지: Phase 05 `IN_PROGRESS`, 계획 `APPROVED`, Current phase Phase 05 유지
- 사용자 추가 승인: 댓글은 `web.tosunsaeng.domain.comment`, 뉴스레터는 `web.tosunsaeng.domain.newsletter`를 정식 main/test package와 물리 경로로 사용함
- 중복 제거: `.domain.blog.comment`와 `.domain.blog.newsletter` 호환 package를 함께 유지하지 않음
- 댓글 범위: Phase 03·04 구현은 package 선언, import와 경로만 이동하고 API, validation, Redis 제한, 숨김·복원 동작은 변경하지 않음
- 테스트 선택자: 뉴스레터는 `web.tosunsaeng.domain.newsletter.*`, 댓글은 `web.tosunsaeng.domain.comment.*`로 변경함
- 생성자 정책: 단순한 `final` 의존성 주입에는 Lombok `@RequiredArgsConstructor`를 사용하고 검증·변환·도메인 불변식 생성자는 명시적으로 유지함
- Git 상태: 기존 staged 삭제를 unstage하거나 index를 조작하지 않고 `git add`, `restore`, `reset`, branch 변경을 실행하지 않음

### 2026-07-31 10:56:26 KST — Phase 05 구현 완료 및 검증 시작

- 상태: Phase 05 `VERIFYING`, 계획 `APPROVED`, Current phase Phase 05 유지
- package: comment/newsletter main·test를 각각 `web.tosunsaeng.domain.comment`, `web.tosunsaeng.domain.newsletter`로 통일하고 옛 `.domain.blog.*` 중복 package를 유지하지 않음
- 생성자: 단순한 `final` 의존성 주입 생성자를 Lombok `@RequiredArgsConstructor`로 전환하고 qualifier·파생값·domain 검증이 있는 생성자는 명시적으로 유지함
- 구현: POST subscribe/unsubscribe, 즉시 ACTIVE, 상태별 멱등 처리, stateless HMAC token과 key rotation, Mongo 조건부 update/index, newsletter 전용 Redis IP 제한을 추가함
- 개인정보: backend unsubscribe token은 JSON body만 사용하고 email·token·IP를 응답·일반 log에서 제외하며 newsletter POST의 Sentry request data/query/cookie/header/env/user PII를 전송 전에 제거함
- 예비 검증: `compileJava`, root newsletter test 68개, root comment test 전체가 성공함
- 검증 중 발견·수정: 누락된 Sentry privacy config와 불필요한 checked 설정 검증 signature를 보완하고 nullable exception message의 민감정보 assertion을 안전하게 수정함
- 다음 검증: `git diff --check`, 전체 clean test/bootJar와 API·제외 범위·민감정보 정적 검색을 수행함

### 2026-07-31 11:02:45 KST — Phase 05 검증 완료

- 상태: Phase 05 `DONE`, 계획 `EXECUTED`, Current phase Phase 06 `TODO`
- 필수 검사: `git diff --check` 성공
- 관련 테스트: `bash ./gradlew test --tests 'web.tosunsaeng.domain.newsletter.*'` 성공, newsletter 68개 failures/errors/skipped 0
- package 회귀: `bash ./gradlew test --tests 'web.tosunsaeng.domain.comment.*'` 성공, comment 152개 failures/errors/skipped 0
- 전체 검증: `bash ./gradlew clean test bootJar` `BUILD SUCCESSFUL`, 전체 283개 failures/errors/skipped 0, bootJar 생성 성공
- API review: `POST /api/newsletter/subscribe`, JSON body 기반 `POST /api/newsletter/unsubscribe`만 추가하고 GET unsubscribe, verify, internal newsletter API를 추가하지 않음
- 상태 review: 유효 email·consent는 즉시 ACTIVE, ACTIVE timestamp 불변 멱등, UNSUBSCRIBED 재구독 tokenVersion 증가, BOUNCED 재구독 generic 409와 해지 허용, PENDING 상태 없음
- token review: 만료 없는 HMAC-SHA256 stateless token, active/previous key rotation, raw token/hash Mongo 미저장, query fallback·응답·일반 log 노출 없음
- 동시성 review: email unique, 조건부 재구독·해지 findAndModify, duplicate insert 재조회로 하나의 Subscriber와 멱등 결과에 수렴함
- rate limit review: 별도 HMAC secret, getRemoteAddr, 10분/하루 standalone Lua fixed window, 최대 TTL Retry-After, connection/timeout만 fail-open함
- 개인정보 review: newsletter POST의 Sentry event/transaction에서 body/query/cookie/인증·IP header/env/user email·IP를 제거하고 raw email/IP/token/secret을 일반 log와 응답에 포함하지 않음
- package review: comment/newsletter main·test는 root package만 유지하며 comment HEAD 대비 변경은 package/import와 승인된 `@RequiredArgsConstructor` 전환뿐이고 기존 comment test 전체가 통과함
- 제외 범위 review: 실제 email Sender·Scheduler·Delivery·Campaign, SecurityConfig, AWS/SMTP/SES, Testcontainers와 실제 MongoDB·Redis·Sentry 접근 없음
- 승인 계획 차이: 사용자가 후속 승인한 root comment/newsletter package와 `@RequiredArgsConstructor` 기준을 반영했으며 제품 API·상태·보안 계약 차이는 없음
- Phase 08 이관: 실제 Mongo unique/동시 update, Redis Lua·TTL·원자성·standalone topology, Sentry outbound와 web/frontend/CDN query log, frontend 확인 page와 email scanner 동작 검증
- 다음 단계: Phase 06은 `TODO`로 유지하며 사용자 코드 검토와 커밋 전 계획 또는 구현을 시작하지 않음
