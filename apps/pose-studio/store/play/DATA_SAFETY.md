# Pose Studio Google Play Data Safety source worksheet

This worksheet must be re-checked against the exact production AAB before Play submission.

Current source contract:

- app requests no INTERNET or ACCESS_NETWORK_STATE permission;
- no advertising, analytics, account, crash-reporting or telemetry SDK is included;
- local pose/project data is not collected by or shared with the developer;
- user-selected JSON import and JSON/PNG export are device-local Storage Access Framework operations;
- autosave recovery data remains app-private on-device;
- no mandatory cloud processing is part of core creation.

Therefore repository-side source inspection currently indicates no developer collection or sharing of user project data. Play Console answers must still be validated against the exact dependency graph, final production manifest and any platform/SDK disclosures at submission time.
