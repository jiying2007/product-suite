# Pose Studio privacy contract

Pose Studio v0.1 is local-only.

- No account.
- No analytics or telemetry SDK.
- No advertising SDK.
- No network client.
- No `INTERNET` permission.
- Projects live in app-private storage until the user explicitly exports them.
- PNG and JSON export use Android's Storage Access Framework and write only to a URI chosen by the user.
- Import reads only the document URI chosen by the user.

Future online capability, if ever justified, must be separately reviewed and cannot silently change the local-only behavior or make existing projects depend on a remote service.