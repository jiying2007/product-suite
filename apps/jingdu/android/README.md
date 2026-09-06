# Android

Native Android shell for Jingdu TXT. All document semantics come from the shared C++ ABI v2 through JNI.

Android owns only Android-specific UI/lifecycle, system document access, charset normalization, preferences, TTS/audio focus and concurrency scheduling. Long import/search/chapter/repair/export operations run on a bounded worker pool; only bounded page/TTS reads remain synchronous.

Build:

```bash
./gradlew --no-daemon androidCheck
```

Release signing material stays local/release-infrastructure state and is ignored by Git.
