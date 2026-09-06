# Fastlane metadata ownership

The existing `fastlane/metadata/android/` tree is retained Jingdu Android store metadata. It remains at the historical root path so existing release/store tooling and provenance stay stable.

Future products must use product-scoped metadata/tooling and must not write another product's listing text into this unqualified Jingdu namespace.
