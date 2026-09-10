# Pose Studio Privacy Policy

Effective date: 2026-09-10

Pose Studio is designed as an offline-first drawing-reference application. The current product does not request Android INTERNET or ACCESS_NETWORK_STATE permissions and does not include advertising, analytics, account or telemetry SDKs.

## Data handled by the app

Pose Studio stores pose projects in app-private local storage. A project contains the user-provided project name, mannequin joint state, camera/light state and local modification timestamp. Unsaved edits may also be written to a local recovery journal so work can be restored after process death.

Pose Studio does not transmit these projects to the developer. The app has no network client in its current product boundary.

## User-directed import and export

JSON project import and PNG/JSON export use Android's Storage Access Framework. Pose Studio reads or writes only a document location explicitly selected by the user. Exported files may then be handled by other apps/services chosen by the user; their privacy practices are outside Pose Studio.

## Accounts, advertising and analytics

The current release has no account system, advertising SDK or analytics/telemetry SDK. Core creation, saving and export do not require a remote service.

## Retention and deletion

Local projects remain on the device until the user deletes them or removes the app's local data. Exported copies are controlled by the user. Recovery snapshots are removed when an explicit project save succeeds or when the user discards recovery.

## Future changes

Any future online capability must be additive and separately reviewed. Existing local projects will not become dependent on a remote entitlement merely to be opened or exported.

## Contact

Privacy questions may be raised through the public issue tracker for the `jiying2007/product-suite` repository on GitHub. Do not include private project files or sensitive personal information in a public issue.
