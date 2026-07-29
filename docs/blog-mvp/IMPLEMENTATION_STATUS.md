# 블로그 MVP 구현 상태

- 전체 상태: `IN_PROGRESS`
- 현재 단계: `Phase 02 — 게시글 목록·상세·제목 검색`
- 현재 브랜치: `feat/blog-mvp`
- 마지막 수정 시각: `2026-07-29 16:41:05 KST (+09:00)`

## 단계별 상태

| Phase | 이름 | 상태 |
|---|---|---|
| 00 | Agent guardrails 및 저장소 분석 | `DONE` |
| 01 | Java 21, 테스트, Mongo 스캔, Scheduling, 로컬 환경 | `DONE` |
| 02 | 게시글 목록·상세·제목 검색 | `TODO` |
| 03 | 익명 댓글과 번호 기반 validation | `TODO` |
| 04 | 댓글 rate limit과 숨김·복원 | `TODO` |
| 05 | 뉴스레터 구독과 구독 해지 | `TODO` |
| 06 | 뉴스레터 15분 자동 발송 | `TODO` |
| 07 | 내부 운영 API와 보안 | `TODO` |
| 08 | 전체 회귀 테스트와 API 문서 | `TODO` |

## 현재 단계의 목표

- Phase 01은 승인 범위 구현과 필수 검증을 완료해 `DONE`이다.
- `docs/blog-mvp/plans/PHASE-01-infrastructure.md`는 `EXECUTED`다.
- 현재 단계 Phase 02는 `TODO`이며 계획 수립과 구현을 시작하지 않았다.

## 변경 파일

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

## 열린 문제

- Java 17 기반 `Dokerfile`은 참조되지 않는 것으로 확인됐지만 이번 Phase에서는 삭제하지 않는다.
- 기존 exams의 기능 수준 회귀 테스트가 없어 Phase 01 계획의 보장 범위는 context 기동과 Repository Bean 등록까지다.
- test context가 localhost MongoClient를 생성해 background monitor의 연결 거부 로그가 남지만 Repository method를 호출하거나 연결 성공을 요구하지 않으며 테스트는 통과한다.
- GitHub Actions의 실제 main 배포는 금지된 push·배포를 실행하지 않았으므로 repository 내 정적 순서와 YAML까지만 검증했다.

## 다음 작업

1. 사용자가 Phase 01 변경과 검증 결과를 검토한다.
2. 사용자 검토 전에는 Phase 02 계획이나 구현을 시작하지 않는다.
3. Phase 02 작업 요청을 받으면 필수 문서와 작업 트리를 다시 확인하고 계획서 작성만을 위한 `PLANNING`부터 시작한다.

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
