#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(git -C "${script_dir}/.." rev-parse --show-toplevel)"
cd "${repo_root}"

branch="$(git symbolic-ref --quiet --short HEAD || true)"
if [[ -z "${branch}" ]]; then
  branch="(detached HEAD)"
fi

printf 'Current branch\n%s\n\n' "${branch}"
printf 'IMPLEMENTATION_STATUS.md\n'
sed -n '1,$p' docs/blog-mvp/IMPLEMENTATION_STATUS.md
printf '\ngit status --short\n'
git status --short
printf '\ngit diff --stat\n'
git diff --stat
printf '\nRecent commits (5)\n'
git log -5 --oneline
