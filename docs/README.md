# Root documentation ownership

The existing documents in this directory are the stable Jingdu product, architecture, quality, performance, testing and release contracts retained at their current paths for source-history and immutable-release continuity.

This directory is not a generic place for future product-specific documentation. New products should keep product-owned documentation under `apps/<product>/docs/` unless a document is genuinely repository-wide. Repository-wide governance lives in the root `README.md`, `CONTRIBUTING.md` and `.github/REPOSITORY_POLICY.md`.

Do not duplicate Jingdu contracts into a second documentation tree merely to rename them. Move a document only when its ownership changes for a real engineering reason and update every checked contract in the same change.
