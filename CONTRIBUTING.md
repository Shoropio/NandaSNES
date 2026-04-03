# Contributing

## Branching model

- `development`: integration branch for ongoing work
- `master`: stable branch for production-ready snapshots
- `feature/<short-name>`: feature or refactor branches created from `development`
- `fix/<short-name>`: targeted bugfix branches created from `development`

## Recommended workflow

1. Update your local `development` branch.
2. Create a dedicated branch for your change.
3. Keep commits focused and descriptive.
4. Open a pull request into `development`.
5. Merge `development` into `master` only when the state is considered stable.

Example:

```bash
git checkout development
git pull
git checkout -b feature/improve-audio-sync
```

## Before opening a pull request

- Verify the Android project builds:

```bat
cd android
.\gradlew.bat :app:assembleDebug
```

- If you touch native code, verify the NDK build still succeeds through the Android build.
- Do not commit generated files such as `.gradle`, `.idea`, `build`, `.cxx`, or `local.properties`.
- If you update `third_party/snes9x`, make sure the submodule pointer is intentional and note it in the PR.

## Coding expectations

- Preserve emulation behavior unless the change explicitly targets emulation accuracy or timing.
- Prefer small, reviewable refactors over broad rewrites.
- Keep UI work aligned with the current Compose-based architecture.
- Maintain separation between `presentation`, `domain`, `data`, and `runtime`.
- Treat performance regressions in audio, video, input, and save-state paths as high priority.

## Pull request notes

Include:

- what changed
- why it changed
- how it was verified
- any emulator-specific risk or compatibility concern

