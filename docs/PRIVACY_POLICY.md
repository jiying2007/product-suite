# Jingdu Privacy Policy

**Effective date:** September 12, 2026

Jingdu / 净读 ("Jingdu") is an offline-first TXT reader developed and published by **jiying2007**. This policy explains how the Android product handles user and device data.

Privacy inquiries may be submitted through the public project contact mechanism at <https://github.com/jiying2007/product-suite/issues>. Do not include private book text, file paths, purchase tokens, credentials, or other sensitive content in a public issue.

## 1. Summary

Jingdu is designed so that ordinary reading does not require an account, advertising identifier, analytics service, cloud library, or Jingdu-operated server.

- The Android package requests `INTERNET` because optional Google Play Billing and Google Play In-App Review flows can communicate with Google Play services.
- Jingdu does **not** contain an advertising SDK or runtime analytics SDK.
- Jingdu does **not** operate a content, analytics, account, or entitlement server.
- Jingdu does **not** upload TXT book contents, search queries, annotations, reading progress, or Smart Clean text to a Jingdu server.
- Imported TXT files and derived reading data are processed in app-private storage unless the user explicitly exports something or invokes an external platform action described below.
- The original external TXT selected by the user is never modified or deleted by Jingdu.

The `INTERNET` capability does not make the core reader cloud-dependent. Network-capable product flows are limited to optional Google Play platform services; ordinary reading, search, chapters, cleaning, annotations, local backup, and local diagnostics remain on-device.

## 2. Data Jingdu accesses locally

When the user explicitly selects files or folders through Android's Storage Access Framework, Jingdu may access:

- TXT file bytes selected by the user;
- document metadata supplied by the Android document provider, such as display name, document identifier, size, and last-modified time;
- user-selected folder roots for local library synchronization;
- locally created bookmarks, highlights, notes, reading progress, tags, favorites, reading preferences, Clean rules, Smart Clean feedback fingerprints, and reading-session statistics;
- system TTS voice metadata needed to present read-aloud options;
- limited device/build/storage-class facts used only in a user-triggered privacy/diagnostic export.

Jingdu does not request broad shared-storage permission. Access to external documents is based on explicit user selection and Android-granted URI permissions.

## 3. Local processing and storage

Selected TXT source bytes are copied into Jingdu's private application storage. Jingdu may create normalized UTF-8 revisions, sparse indexes, derived Clean revisions, and other local caches needed for reading, search, chapters, repair, and performance.

These private files remain on the device unless the user explicitly exports a file or backup. Content-derived caches can be deleted and rebuilt without changing the original source file.

Jingdu keeps source and normalized revisions immutable so a failed import, decode, repair, or low-storage operation cannot silently overwrite the last valid private copy.

## 4. Smart Clean and local text intelligence

TXT Doctor, Smart Layout, Smart TOC, Smart Clean, encoding detection, Simplified/Traditional display conversion, and the bundled candidate-only semantic classifier run locally in the Jingdu process or shared native library.

Smart Clean correction memory stores one-way SHA-256-derived candidate fingerprints and decision metadata such as KEEP, DELETE, or PROTECT. It does not retain candidate or book text in the feedback store.

## 5. Read-aloud and external text actions

Jingdu uses Android's system Text-to-Speech API for read-aloud. Text chunks are sent through Android IPC to the TTS engine selected or configured on the device. Jingdu does not operate a speech server, but a separately installed/system TTS engine is software supplied by another provider and may have its own network and privacy behavior. Users should review the privacy settings of their chosen TTS engine. When Jingdu offers an explicit Pro offline-voice selector, it only lists voices the Android TTS engine reports as not requiring a network connection.

If the user explicitly invokes Android `PROCESS_TEXT` / "Look up" on selected text, the selected text is intentionally handed to the external application chosen by the user. That external application's data practices are governed by its own policy.

## 6. Google Play Billing and review services

The Android package includes Google Play Billing for the optional one-time `jingdu_pro_lifetime` purchase and Google Play In-App Review after meaningful local usage milestones. These optional platform flows are why the final Android package has network capability.

Google Play may process purchase, account, payment, device, and review-related information under Google's own terms and privacy policies. Jingdu receives only the billing/review responses needed to provide those features. Jingdu does not send book text, search queries, annotations, file paths, reading progress, or Smart Clean content to Google Play Billing or Review.

Jingdu has no product account or developer-operated entitlement backend. The last Play-verified Pro entitlement may be cached locally for offline use.

## 7. Portable local-user backup

An optional Pro backup can export user-owned Reader state such as settings, global Clean rules, annotations, favorites/tags, revision-bound progress, reading sessions/pace, Smart Clean decision fingerprints, and supported pronunciation preferences.

The backup declares `containsBookText=false` and excludes source, normalized, and Clean book files. The user chooses where the exported backup is written. Jingdu does not upload it to a Jingdu service.

Storage Access Framework grants and unavailable imported font binaries are not treated as portable credentials and must be selected again when required on another installation or device.

## 8. Diagnostics and support

Jingdu does not send background telemetry to the developer. A bounded local diagnostic history may contain stable error code, operation name, and timestamp. It is designed not to contain exception messages, book text, book name, source path, content URI, search query, or purchase token.

The user may explicitly export a local privacy/diagnostic JSON. That export is user-controlled and is not automatically transmitted anywhere.

## 9. Data collection, sharing, and sale

Jingdu does not sell user data.

Jingdu does not operate a server that collects reading data, book text, annotations, search history, advertising identifiers, analytics events, or purchase tokens.

Data may leave the Jingdu application boundary only through an explicit user/platform action or the optional Google Play platform integrations described in this policy, including:

- exporting a TXT, rules file, diagnostic report, or local-user backup to a destination selected by the user;
- using Android system TTS, which passes text chunks to the configured TTS engine;
- using an explicitly selected external `PROCESS_TEXT` application;
- communicating with Google Play for optional Billing or In-App Review flows.

## 10. Retention and deletion

Jingdu retains private imported books, derived revisions, indexes, reading state, settings, and optional entitlement state on the device until one of the following occurs:

- the user removes the book/private copy or related user data through Jingdu;
- the user clears Jingdu's app data;
- the user uninstalls the application;
- Android removes cache/derived data that is designed to be rebuildable.

Jingdu has no server account, so there is no separate server-side account deletion process. Google Play purchase history and data held by external TTS/PROCESS_TEXT providers are controlled by those providers and their respective policies.

## 11. Security

Jingdu uses app-private Android storage for imported/private working data, disables cleartext network traffic in its manifest, avoids broad storage permission, and validates imported/backup structures before applying them. Production signing keys and credentials are not stored in the public source repository.

The package's network capability is not used as a general-purpose developer telemetry or content-upload channel. No software can guarantee absolute security, but Jingdu minimizes exposure by keeping the primary reading path local and avoiding a Jingdu-operated network service.

## 12. Children

Jingdu is a general-purpose TXT reader and is not designed to collect personal information from children. Because Jingdu does not operate accounts, advertising, analytics, or a content-upload service, it does not intentionally collect children's book text or reading activity on a Jingdu server.

## 13. Changes to this policy

If Jingdu's data practices materially change, this policy and the corresponding Google Play Data safety declarations must be updated before the changed behavior is distributed. Historical source releases remain immutable, while this governing policy may be revised for the currently distributed product.

## 14. Governing product

This policy applies to the Android application **Jingdu / 净读**, package name `com.junchen.jingdu`, published by **jiying2007**.
