# 블로그 MVP 고정 요구사항

- 기준 상태: 최종 합의본
- 변경 정책: Codex 임의 변경 금지

이 문서는 블로그 MVP 구현의 변경 불가능한 기준이다. 코드, 테스트, 계획 또는 다른 문서와 내용이 다르면 Codex는 임의로 해석하거나 이 문서를 코드에 맞춰 바꾸지 않는다. 작업을 중단하고 차이를 사용자에게 보고한다. 변경은 사용자의 명시적 재합의가 문서에 반영된 뒤에만 가능하다.

## 게시글

- 공개 게시글 목록 조회를 제공한다.
- slug 기반 공개 게시글 상세 조회를 제공한다.
- 제목 부분 검색을 제공한다.
- 게시글 상태는 `DRAFT`, `PUBLISHED`, `ARCHIVED`를 사용한다.
- 상태가 `PUBLISHED`이고 `publishedAt`이 현재 시각보다 이전인 글만 공개한다.
- 관리자 글쓰기 UI를 만들지 않는다.
- 게시글 생성·수정·삭제 공개 API를 만들지 않는다.
- 운영자가 DB 또는 내부 스크립트로 게시글을 작성한다.

## 댓글

- 로그인 없는 익명 댓글을 제공한다.
- 작성자에게 랜덤 닉네임과 아바타를 부여한다.
- 댓글 조회와 작성만 제공한다.
- 일반 사용자 댓글 수정 기능과 API를 만들지 않는다.
- 일반 사용자 댓글 삭제 기능과 API를 만들지 않는다.
- 운영자도 댓글 삭제 기능이나 제품 API를 제공하지 않는다.
- 운영자는 댓글 숨김과 복원만 할 수 있다.
- 댓글은 일반 텍스트만 허용한다.
- URL, HTML, Markdown을 차단한다.
- 공개 조건을 충족한 게시글에만 댓글을 작성할 수 있다.

### 댓글 고정 규칙

규칙 번호, 이름, 순서는 변경하지 않는다.

1. `COMMENT_MIN_LENGTH`
2. `COMMENT_MAX_LENGTH`
3. `COMMENT_TRIM`
4. `COMMENT_PLAIN_TEXT_ONLY`
5. `COMMENT_HTML_NOT_ALLOWED`
6. `COMMENT_MARKDOWN_NOT_ALLOWED`
7. `COMMENT_URL_NOT_ALLOWED`
8. `COMMENT_EMPTY`
9. `COMMENT_SPAM_PATTERN`
10. `COMMENT_POST_NOT_PUBLIC`

### 댓글 세부 규칙

- 최소 길이는 2자다.
- 최대 길이는 500자다.
- 길이는 UTF-16 code unit이나 byte 수가 아니라 Unicode code point 기준으로 계산한다.
- 입력을 trim한 값을 후속 검증과 저장에 사용한다.
- rule 3 `COMMENT_TRIM`은 정규화 단계이며 violation으로 반환하지 않는다.
- 여러 규칙을 위반하면 해당되는 모든 violation을 반환한다.
- violation은 항상 `ruleNumber` 오름차순으로 반환한다.
- violation이 하나라도 발생하면 댓글을 DB에 저장하지 않는다.

## 뉴스레터

- 이메일 인증 기능을 만들지 않는다.
- verification endpoint를 만들지 않는다.
- `NewsletterSubscriber`에 `PENDING` 상태를 만들지 않는다.
- 이메일과 수신 동의가 유효하면 즉시 `ACTIVE` 처리한다.
- 이메일을 정규화하고 unique 제약을 적용한다.
- 이미 `ACTIVE`인 이메일의 중복 구독 요청은 멱등 성공으로 처리한다.
- `UNSUBSCRIBED` 상태의 재구독은 `ACTIVE`로 전환한다.
- 구독 해지를 제공한다.
- 새 글 공개 후 기본 15분 뒤 자동 발송한다.
- `newsletterEnabled=true`인 글만 발송한다.
- `ACTIVE` 구독자에게만 발송한다.
- 동일한 post/subscriber 조합의 중복 발송을 방지한다.
- 실패 발송은 최대 3회 재시도한다.
- 뉴스레터 발송 기능의 기본값은 비활성화한다.
- kill switch 기본값은 `false`다.
- 테스트 발송, 예약 취소, 실패 재시도는 인증된 내부 운영 기능으로만 제공한다.

## 운영

- 운영자는 댓글을 숨기고 복원할 수 있다.
- 뉴스레터 테스트 발송을 제공한다.
- 뉴스레터 예약 취소를 제공한다.
- 실패한 뉴스레터 발송 재시도를 제공한다.
- 모든 운영 API는 `/internal/**` 아래에 두고 API Key로 인증한다.
- Codex는 운영 서버, 운영 DB 또는 실제 구독자에게 직접 접근하지 않는다.
- Codex는 배포, 실제 이메일 발송, 운영 데이터 변경을 수행하지 않는다.

## 명시적 제외 범위

- 게시글 생성·수정·삭제 공개 API
- 관리자 글쓰기 UI
- 댓글 수정
- 댓글 삭제
- 이메일 인증
- newsletter verification endpoint
- `PENDING` 구독 상태

위 제외 범위를 우회하는 숨은 endpoint, 임시 API, 관리자 전용 제품 API도 만들지 않는다.
