# Configuration ownership

The existing `config/signing/` material is the documented Jingdu current-stage GitHub release exception. `android-debug.keystore` is intentionally public test/debug signing material and is not a shared production credential.

Future products must establish product-scoped signing/release configuration and must not reuse Jingdu signing identity by convenience. Production/upload signing material remains outside the repository unless a separately reviewed policy explicitly says otherwise.
