# 블로그 MVP 구현 상태

- 전체 상태: `IN_PROGRESS`
- 현재 단계: `Phase 01 — Java 21, 테스트, Mongo 스캔, Scheduling, 로컬 환경`
- 현재 브랜치: `feat/blog-mvp`
- 마지막 수정 시각: `2026-07-29 15:56:40 KST (+09:00)`

## 단계별 상태

| Phase | 이름 | 상태 |
|---|---|---|
| 00 | Agent guardrails 및 저장소 분석 | `DONE` |
| 01 | Java 21, 테스트, Mongo 스캔, Scheduling, 로컬 환경 | `TODO` |
| 02 | 게시글 목록·상세·제목 검색 | `TODO` |
| 03 | 익명 댓글과 번호 기반 validation | `TODO` |
| 04 | 댓글 rate limit과 숨김·복원 | `TODO` |
| 05 | 뉴스레터 구독과 구독 해지 | `TODO` |
| 06 | 뉴스레터 15분 자동 발송 | `TODO` |
| 07 | 내부 운영 API와 보안 | `TODO` |
| 08 | 전체 회귀 테스트와 API 문서 | `TODO` |

## 현재 단계의 목표

- Phase 01은 아직 `TODO`이며 계획서를 작성하지 않았다.
- Phase 01의 읽기 전용 분석, 계획 작성, 구현은 아직 시작하지 않았다.
- 초기 관리 파일의 작업 트리 상태가 정리된 뒤 별도 `PLANNING` 작업으로 계획서만 작성한다.

## 변경 파일

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

## 열린 문제

- Phase 00에서 만든 초기 관리 파일 11개가 아직 미추적 상태이므로 `scripts/codex-preflight.sh`는 작업 트리 검사에서 실패한다.
- 원본 애플리케이션 소스 작업 트리는 변경하지 않았으며, Phase 01 계획은 아직 작성하지 않았다.

## 다음 작업

1. 사용자가 Phase 00의 초기 관리 파일 11개를 검토하고 작업 트리 처리 방식을 결정한다.
2. 작업 트리가 깨끗해진 뒤 `./scripts/codex-preflight.sh`를 다시 실행한다.
3. preflight가 성공하면 Phase 01을 `PLANNING`으로 변경하고 읽기 전용 분석과 `DRAFT` 계획서 작성만 수행한다.
4. Phase 01 계획에 대한 사용자 명시적 승인 전에는 소스 코드를 수정하지 않는다.

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
