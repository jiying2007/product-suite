# HarmonyOS Runner Contract

The real HarmonyOS build gate runs on a self-hosted GitHub Actions runner labeled `self-hosted,harmonyos`. This is deliberate: the repository only accepts Huawei's official DevEco/Command Line Tools toolchain and does not download SDK/tool binaries from third-party mirrors.

Hosted `harmony-contract` remains the automatic source contract on ordinary pull requests and `main` pushes. The real HAP build is an explicit qualification workflow: it is launched with `workflow_dispatch` only when a matching runner is online. This avoids leaving ordinary source changes permanently queued merely because optional device infrastructure is offline.

## Supported toolchain

Use a current Huawei HarmonyOS Command Line Tools release that supports this project's SDK/model version. Command Line Tools includes `hvigorw`, `ohpm`, code-linter tooling and the HarmonyOS SDK. Obtain it from the Huawei Developer download center and verify the package checksum published there before installation.

The runner must provide:

- official HarmonyOS Command Line Tools / SDK;
- `hvigorw` executable;
- Node.js 22 or a newer version supported by that Harmony toolchain;
- a current GitHub Actions self-hosted runner release that supports Node 24 JavaScript actions;
- Git and standard Unix shell utilities used by GitHub Actions;
- enough free disk for a clean native + ArkTS build and HAP/APP artifacts.

The GitHub workflow pins `actions/checkout` and `actions/upload-artifact` to immutable commit SHAs. Artifact upload uses the current Node-24 action line; do not keep an outdated runner merely to preserve a historical action runtime.

Do not cache or mirror Huawei SDK binaries inside this Git repository.

## Runner labels

Register the repository/organization runner with the additional label:

```text
harmonyos
```

The workflow intentionally selects:

```yaml
runs-on: [self-hosted, harmonyos]
```

A generic self-hosted runner without the HarmonyOS toolchain must not carry this label.

## Tool discovery

`scripts/check-harmony.sh` resolves Hvigor in this order:

1. `HARMONY_HVIGORW` — absolute path to the official `hvigorw` executable;
2. `hvigorw` on `PATH`;
3. `${DEVECO_TOOLS_HOME}/bin/hvigorw`.

Configure one of those routes in the runner service environment. No repository secret is required merely to build an unsigned/debug source gate.

Before adding the `harmonyos` label, validate interactively:

```bash
hvigorw --version
node --version
git --version
```

Also verify the GitHub Actions runner service is on the current supported release before bringing it online.

Then, from a clean checkout, run:

```bash
./scripts/check-harmony.sh
```

The command must exit zero and produce at least one `.hap` under `apps/jingdu/harmony`.

## CI evidence

`.github/workflows/harmony-device.yml` is the canonical real-HAP source qualification workflow. It is manually dispatched with an exact `source_ref`, checks out that ref, records the resolved commit SHA, builds with the configured official self-hosted toolchain, requires a generated HAP and uploads HAP/APP/native `.so` outputs as evidence.

A dispatched workflow that is queued means no matching `self-hosted,harmonyos` runner is online. It is not build success and must never be reported as such. Ordinary PR/main CI does not dispatch this workflow automatically and therefore remains terminal even when the optional runner is offline.

## Device evidence

A successful HAP build is still not the device gate. Before release, install the built package on the HarmonyOS device matrix in `DEVICE_MATRIX.md` and record the required import/encoding, reader, search/chapter, repair/export, lifecycle, TTS and 10/100/300 MiB results.

## Upgrade rule

When upgrading DevEco/Command Line Tools, the HarmonyOS SDK or the GitHub Actions runner:

- use only official vendor packages and published integrity data;
- run the complete HAP workflow before accepting the upgrade;
- update `build-profile.json5`, Hvigor model settings and this document in the same PR when their contract changes;
- keep workflow actions pinned to immutable SHAs and let Dependabot propose reviewed updates;
- never add compatibility scripts for multiple historical toolchains. The repository tracks one supported terminal toolchain contract at a time.
