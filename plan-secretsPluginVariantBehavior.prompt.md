## Plan: Harden Secrets Plugin Variant Behavior

<!-- ...existing content... -->

### Steps
1. [ ] Add focused diagnostics in `secrets-gradle-plugin/secrets-plugin/src/main/kotlin/aero/digitalhangar/secrets/Secrets.kt` around `Secrets.apply`, `onVariants`, and `variantSecretsMapping` resolution to print effective `defaultSecretsFileName`, merged map order, and matched patterns at `INFO`/`DEBUG` levels.
2. [ ] Refactor mapping assembly in `secrets-gradle-plugin/secrets-plugin/src/main/kotlin/aero/digitalhangar/secrets/Secrets.kt` so `".*"` is re-injected via `putIfAbsent` from `defaultSecretsFileName` after user config (`put` or `set`), then apply deterministic precedence (default first, specific overrides later).
3. [ ] Replace eager `.get()` usage paths with provider-safe reads in `secrets-gradle-plugin/secrets-plugin/src/main/kotlin/aero/digitalhangar/secrets/Secrets.kt` (`defaultSecretsFileName`, `ignoreList`, `variantSecretsMapping`) and explicitly document why `afterEvaluate` is avoided with AGP `AndroidComponentsExtension.onVariants`.
4. [x] Implement requested-variant gating in `secrets-gradle-plugin/secrets-plugin/src/main/kotlin/aero/digitalhangar/secrets/VariantMatcher.kt` by parsing `gradle.startParameter.taskNames` into candidate variant names and skipping non-requested variants; keep fallback to "all variants" for sync/ambiguous tasks. Regex-based, no hard-coded task prefixes.
5. [ ] Add/extend plugin tests under `secrets-gradle-plugin/secrets-plugin/src/test` (new) for: default file override behavior, `variantSecretsMapping.set(...)` retaining wildcard default, safe configuration-time access, and requested-task filtering; include logging usage notes in `secrets-gradle-plugin/secrets-plugin/README.md` (new) for `--info`, `--debug`, `-Dorg.gradle.logging.level=info`, and Android Studio Gradle run options.

<!-- ...existing content... -->

