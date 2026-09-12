# Pose Studio 0.2.1 commercial-beta candidate

version: pose-studio-v0.2.1
kind: pose-studio-commercial-beta-candidate
android_version_name: 0.2.1
android_version_code: 3
production_v1: false
production_signing_proven: false
google_play_production: false
qualification_issue: 83

This manifest freezes repository-side provenance for the Pose Studio 0.2.1 commercial-beta candidate. The candidate tag is created only after CI, Pose Studio, and CI Contracts all complete successfully on the same exact `main` SHA. The annotated tag message records that SHA and this file's SHA-256.

0.2.1 is a commercial-beta hardening release. It carries the repository-side commercial-readiness work merged through PR #96, including asynchronous project I/O, accessible non-canvas joint selection and adjustment, responsive inspector refinements, stronger large-font/UI automation, immutable beta privacy-link handling, benchmark evidence tooling, and release-topology cleanup. It intentionally does not broaden the product with cloud accounts, ads, mandatory telemetry, generative AI, subscription lock-in, or a large asset marketplace.

The candidate is not a v1 commercial-production declaration and is not evidence of a provisioned production signing key, physical-device qualification, Play Console validation, manual accessibility review, artist benchmarking, closed-track testing, or staged rollout. Those remain fail-closed in issue #83.
