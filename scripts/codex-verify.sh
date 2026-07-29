#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(git -C "${script_dir}/.." rev-parse --show-toplevel)"
cd "${repo_root}"

print_repository_state() {
  printf '\ngit status --short\n'
  git status --short
  printf '\ngit diff --stat\n'
  git diff --stat
}

printf 'Verification: git diff --check\n'
if ! git diff --check; then
  printf 'Verification: FAILED (git diff --check)\n' >&2
  print_repository_state
  exit 1
fi

printf '\nVerification: ./gradlew clean test bootJar\n'
if ! ./gradlew clean test bootJar; then
  printf 'Verification: FAILED (Gradle)\n' >&2
  print_repository_state
  exit 1
fi

print_repository_state
printf '\nVerification: PASS\n'
