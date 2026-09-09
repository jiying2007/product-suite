# Google Play Data Safety — Jingdu Android

This file is the source-of-truth worksheet for the Google Play **Data safety** form for `com.junchen.jingdu`.

It is intentionally conservative. The Play Console declaration must be verified against the exact production AAB, its final SDK dependency graph, and Google Play's current form wording before submission. Do not mark a row complete merely because source code intends the behavior.

## Product architecture facts

Current source contract:

- no Android `INTERNET` permission;
- no advertising SDK;
- no runtime analytics SDK;
- no Jingdu account or cloud backend;
- local TXT/source/normalized/Clean files stay on-device unless the user explicitly exports them;
- user-triggered diagnostic export is local and declares `containsBookText=false`;
- Google Play Billing is used only for `jingdu_pro_lifetime`;
- Google Play In-App Review may be invoked after local milestones;
- Android system TTS is an external platform/service boundary;
- optional `PROCESS_TEXT` / Look up is an explicit user-initiated transfer to another installed app.

Google Play defines data as "collected" when the app or its SDKs transmit it off-device, with documented exceptions. On-device-only processing is not collection. On-device transfer to another app can be "sharing", but Google documents exceptions for specific user-initiated transfers where the user reasonably expects the transfer.

## Candidate declaration by data family

### Local book/content data

TXT bytes, normalized content, Clean output, search queries, annotations, bookmarks, reading progress, Smart Clean candidate text, and local rules are processed locally by Jingdu.

**Candidate Play declaration:** not collected by Jingdu, provided final production inspection confirms no bundled SDK transmits these data off-device.

Important boundaries:

- `PROCESS_TEXT` is user-initiated and hands only the selected text to the app chosen by the user. Verify the current Play user-initiated sharing exception still applies when filing the form.
- Android TTS receives bounded text chunks through the system TTS API. A configured TTS engine is separately supplied software and may have its own network behavior. Do not claim that every third-party/system TTS engine is offline. Review the final Play form and the chosen disclosure strategy before submission.

### Files and documents

The app accesses user-selected TXT files/folders through Android SAF and stores private working copies locally.

**Candidate Play declaration:** not collected, because Jingdu does not transmit file contents or file metadata off-device through a Jingdu service.

### App activity / reading activity

Reading sessions, progress, favorites, tags, search state, and local usage milestones are stored locally. Review eligibility is decided locally.

**Candidate Play declaration:** not collected by Jingdu, subject to final SDK inspection.

### Diagnostics

Jingdu keeps only a bounded local ring of stable error code, operation name, and timestamp. It does not automatically transmit diagnostics.

**Candidate Play declaration:** not collected by Jingdu. A user may explicitly export a diagnostic JSON to a destination they choose; that export is not background collection.

### Purchases / financial information

Google Play Billing processes payment information directly under Google's terms. Jingdu does not receive card/bank details. The app does receive purchase state/product identity/token information required to unlock and restore the one-time Pro entitlement.

Google Play's Data safety guidance states that financial information collected directly by an external payment service such as Google Play Billing does not need to be declared by the app when the app never accesses that financial information and the payment service collects it directly under its own terms.

Before submission, review whether any purchase-history or purchase-token data surfaced to the app must be declared under the current form. Jingdu does not send purchase tokens to a developer backend because no entitlement backend exists.

### Device or other identifiers

Jingdu does not intentionally create an advertising identifier, cross-app identifier, or developer analytics identifier.

A user-triggered privacy/diagnostic export may contain bounded build/device/storage-class facts for support. It is not automatically uploaded.

**Candidate Play declaration:** no developer collection, subject to final AAB/SDK inspection.

### Crash / performance telemetry

Jingdu does not include a runtime crash analytics SDK or custom telemetry upload channel.

Google Play / Android platform services may provide aggregate Android vitals to the Play developer independently of a Jingdu telemetry SDK. Do not conflate Play platform vitals with an in-app analytics integration.

## Sharing review

The source contract has no developer-controlled server sharing path.

Explicit user/platform transfers that require final Play-form review:

1. exported TXT/rules/backup/diagnostic files written to a user-selected Android destination;
2. selected text sent to a user-chosen `PROCESS_TEXT` app;
3. bounded text sent to the configured Android TTS engine;
4. optional Google Play Billing / In-App Review flows.

For each, verify whether the current Google Play documented user-initiated, service-provider, or payment-service exception applies to the exact integration before submitting the form.

## Security practices

Source/build evidence supports the following statements:

- ordinary reader content is held in app-private storage;
- no direct app network permission exists;
- cleartext traffic is disabled in the manifest;
- private source/normalized/Clean publication is immutable/fail-safe;
- backups are validated before apply and exclude book payload;
- production signing material is not committed to the repository.

Do not claim "data encrypted in transit" as a blanket answer unless every data type that the final Play form treats as collected/shared meets Play's definition for that answer.

## Required pre-submission evidence

- [ ] exact production AAB dependency/permission inspection completed;
- [ ] privacy policy URL from `store/play/PRIVACY_POLICY_URL.txt` is active, public, non-geofenced, non-PDF and accepted by Play;
- [ ] privacy policy is accessible from within the production-installed app;
- [ ] Billing 9.1.0 and Review 2.0.2 SDK data-safety guidance reviewed for the exact version;
- [ ] system/default TTS behavior reviewed and privacy wording matches actual UI behavior;
- [ ] `PROCESS_TEXT` transfer is clearly user initiated;
- [ ] Play Console Data safety answers match this file and `docs/PRIVACY_POLICY.md`;
- [ ] final screenshots/listing do not make stronger privacy claims than the policy supports.

## Change rule

Any future addition of `INTERNET`, analytics, ads, remote AI, cloud sync, account services, crash-upload SDKs, or server-side entitlement verification is a privacy-contract change. It must update this worksheet, `docs/PRIVACY_POLICY.md`, Play Data safety, product UX copy, tests, and release evidence before distribution.
