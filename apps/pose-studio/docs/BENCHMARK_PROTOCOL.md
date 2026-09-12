# Pose Studio frozen ten-pose benchmark protocol v1

Purpose: test the product's primary hypothesis — direct manipulation materially reduces time-to-reference-pose versus established Android pose tools.

## Participants

Use at least 8 practicing illustrators/comic/storyboard artists for directional evidence; 12+ is preferred before a v1 marketing claim. Record experience level and whether the participant previously used each compared tool.

## Comparators

Use Pose Studio plus at least two established Android pose/reference applications available at the time of the study. Do not change comparator settings mid-study to disadvantage them.

## Tasks

Freeze ten public-domain/original target references spanning:
1. neutral standing three-quarter;
2. contrapposto;
3. overhead reach;
4. running stride;
5. seated torso twist;
6. crouch;
7. asymmetric arm gesture;
8. strong foreshortening;
9. low camera angle;
10. high camera angle.

The target set must be stored with study evidence before timing begins and must not be replaced after seeing results.

## Procedure

- randomized tool order per participant;
- one short standardized tutorial per tool before timed tasks;
- timer begins from an opened editable scene and ends when participant says the reference is usable for drawing;
- no downloadable pose/scene may exactly match the target;
- allow normal built-in presets and manipulation accelerators;
- capture completion time, correction operations, undo count, obvious mis-selections, task abandonment and participant confidence (1–5);
- do not exclude slow/failed runs after observing results; predefine exclusion only for external interruption/device failure.

## Primary analysis

Report per-tool median and P95 completion time across all valid task runs plus paired participant-level median differences. Pose Studio passes the product gate only if the median advantage is material and consistent enough to matter to artists; the historical 40–50% target remains an aspiration, not a claim.

## Secondary analysis

Compare mis-selection rate, undo/correction count, abandonment, confidence and qualitative comments. Faster completion with substantially worse reference confidence is not a pass.

## Evidence

Bind study data to exact Pose Studio source SHA/version, device model/API and comparator versions. Preserve raw anonymized timing rows plus the frozen target-set provenance. Do not publish participant personal data.

Record the structured rows using `BENCHMARK_RESULTS_TEMPLATE.csv`. Before interpreting results, run:

```bash
python3 apps/pose-studio/scripts/analyze-artist-benchmark.py path/to/results.csv
```

The analyzer fails closed if there are fewer than eight participants, fewer than two comparators, missing participant/tool/task combinations, mixed Pose Studio source SHAs, duplicate task rows or malformed values. It reports the protocol statistics but deliberately does not convert them into an automatic marketing claim.
