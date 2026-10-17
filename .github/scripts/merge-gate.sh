#!/usr/bin/env bash
# Decides which pull requests the Claude merge agent should look at, and why,
# without spending a Claude run on PRs that are still waiting for something.
#
# Usage: merge-gate.sh [PR_NUMBER]   (no number = every open PR)
# Prints a JSON array like [{"pr":5,"mode":"resolve"}] for the job matrix.
#   resolve = the PR conflicts with its base branch
#   decide  = CI and the Claude review finished on the head commit; check
#             whether the review was addressed, then approve and merge
#
# Only PRs labelled $LABEL are touched, so nothing merges unless you opt in.
set -euo pipefail

LABEL="${LABEL:-automerge}"
SELF_WORKFLOW="${SELF_WORKFLOW:-Claude merge agent}"
REVIEW_WORKFLOW="${REVIEW_WORKFLOW:-Claude PR review}"

if [ -n "${1:-}" ]; then
  candidates="$1"
else
  candidates=$(gh pr list --state open --label "$LABEL" --json number --jq '.[].number' || true)
fi

out="[]"
for pr in $candidates; do
  info=$(gh pr view "$pr" --json state,isDraft,isCrossRepository,labels,mergeable)
  if [ "$(jq -r '.state' <<<"$info")" != "OPEN" ] ||
     [ "$(jq -r '.isDraft' <<<"$info")" = "true" ] ||
     [ "$(jq -r '.isCrossRepository' <<<"$info")" = "true" ] ||
     ! jq -e --arg l "$LABEL" 'any(.labels[]; .name == $l)' <<<"$info" >/dev/null; then
    echo "#$pr: skipped (not an open, non-draft, same-repo PR labelled $LABEL)" >&2
    continue
  fi

  # GitHub computes mergeability in the background; give it a moment.
  mergeable=$(jq -r '.mergeable' <<<"$info")
  for _ in 1 2 3 4 5; do
    [ "$mergeable" != "UNKNOWN" ] && break
    sleep 5
    mergeable=$(gh pr view "$pr" --json mergeable --jq '.mergeable')
  done

  if [ "$mergeable" = "CONFLICTING" ]; then
    echo "#$pr: conflicts with its base branch" >&2
    out=$(jq -c --argjson pr "$pr" '. + [{pr: $pr, mode: "resolve"}]' <<<"$out")
    continue
  fi

  checks=$(gh pr checks "$pr" --json workflow,bucket || true)
  checks=$(jq -c --arg self "$SELF_WORKFLOW" '[.[] | select(.workflow != $self)]' <<<"${checks:-[]}")
  if jq -e 'any(.[]; .bucket == "pending")' <<<"$checks" >/dev/null; then
    echo "#$pr: waiting for checks to finish" >&2
    continue
  fi
  if jq -e 'any(.[]; .bucket == "fail" or .bucket == "cancel")' <<<"$checks" >/dev/null; then
    echo "#$pr: a check failed; nothing to merge until it is fixed" >&2
    continue
  fi
  if ! jq -e --arg r "$REVIEW_WORKFLOW" 'any(.[]; .workflow == $r and .bucket == "pass")' <<<"$checks" >/dev/null; then
    echo "#$pr: no finished Claude review on the head commit yet" >&2
    continue
  fi

  echo "#$pr: ready for a merge decision" >&2
  out=$(jq -c --argjson pr "$pr" '. + [{pr: $pr, mode: "decide"}]' <<<"$out")
done

echo "$out"
