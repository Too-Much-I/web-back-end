#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(git -C "${script_dir}/.." rev-parse --show-toplevel)"
cd "${repo_root}"

branch="$(git symbolic-ref --quiet --short HEAD || true)"
if [[ -z "${branch}" ]]; then
  printf 'Current branch: (detached HEAD)\n' >&2
  printf 'ERROR: detached HEAD에서는 작업을 시작할 수 없습니다.\n' >&2
  exit 1
fi

printf 'Current branch: %s\n' "${branch}"

case "${branch}" in
  main|master)
    printf 'ERROR: %s 브랜치에서는 파일을 수정할 수 없습니다.\n' "${branch}" >&2
    exit 1
    ;;
esac

required_files=(
  "AGENTS.md"
  "PLANS.md"
  "docs/blog-mvp/REQUIREMENTS.md"
  "docs/blog-mvp/WORKFLOW.md"
  "docs/blog-mvp/IMPLEMENTATION_STATUS.md"
  "docs/blog-mvp/plans/.gitkeep"
  ".codex/config.toml"
  ".codex/rules/default.rules"
  "scripts/codex-preflight.sh"
  "scripts/codex-status.sh"
  "scripts/codex-verify.sh"
)

missing=0
for required_file in "${required_files[@]}"; do
  if [[ ! -e "${required_file}" ]]; then
    printf 'ERROR: required file missing: %s\n' "${required_file}" >&2
    missing=1
  fi
done

if (( missing != 0 )); then
  exit 1
fi

worktree_status="$(git status --short)"
if [[ -n "${worktree_status}" ]]; then
  printf 'ERROR: 작업 트리가 깨끗하지 않습니다.\n' >&2
  printf '%s\n' "${worktree_status}" >&2
  exit 1
fi

if [[ "${branch}" != "feat/blog-mvp" ]]; then
  printf 'ERROR: 블로그 MVP 구현은 feat/blog-mvp 브랜치에서만 가능합니다.\n' >&2
  exit 1
fi

printf 'Preflight: PASS\n'
