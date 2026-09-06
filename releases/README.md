# Release namespace ownership

`releases/source/` is the retained Jingdu source-manifest namespace paired with Jingdu's unprefixed `vX.Y.Z` tags. Existing manifests and tags are immutable provenance and must not be renamed, moved or reused by another product.

Jingdu permanently owns the unprefixed `v*` repository tag namespace. Other products must use product-prefixed tags and product-scoped manifests, for example:

```text
audiolab-v1.0.0
releases/audiolab/source/audiolab-v1.0.0.md
```

The same rule applies to Network Toolbox, Phone Doctor, Voice Cleaner and any future product. A product must establish its own release gates before creating its namespace; scaffolds alone are not release evidence.
