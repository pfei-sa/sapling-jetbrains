# CLAUDE.md

Guidance for AI agents (and humans) working in this repository. Read this before making changes. For user-facing product docs see [`README.md`](README.md); for the full design + implementation history see [`docs/superpowers/`](docs/superpowers/).

## What this is

A JetBrains IDE plugin — **Sapling SCM (`sl`) integration** — with two pillars:
1. **Native VCS provider** for `.sl`-mode repos (`AbstractVcs` + change/diff/history/annotation/log/merge/rollback providers; **no** checkin — see "No commit UI" below).
2. **Embedded ISL** (Interactive Smartlog) in a JCEF tool window, bridged to the IDE.

Everything is done by **shelling out to the `sl` CLI** and translating its `-Tjson` output into IntelliJ VCS SPI objects. The plugin embeds no Sapling source.

- **Coordinates:** package / Gradle `group` / plugin `<id>` = `io.github.pfeisa.sapling`; `<vendor>` display name = `Peng Fei` (GitHub `pfei-sa`, `url="https://github.com/pfei-sa"`).
- **Platform baseline:** IntelliJ Platform **2024.2+** (`sinceBuild = "242"`, open `untilBuild`). `verifyPlugin` is **Compatible across IC-242 / 243 / 251 / 252 and IU-253 (2025.3)**. (Build-253 unified the IDEA distribution — no standalone `ideaIC-2025.3` installer — so 2025.3 is verified against the superset `IU` build.)
- **Language/build:** Kotlin **2.1.21**, JVM target **21**; **Gradle 9.6.1 + IntelliJ Platform Gradle Plugin 2.18.1**. NOTE: plugin ≥ 2.12 requires Gradle 9; Kotlin stays on **2.1.x** deliberately — Kotlin 2.2 codegen references `kotlin.coroutines.jvm.internal.SpillingKt`, which is absent from 242/243's bundled Kotlin (→ runtime `NoSuchClassError`). `pluginVerification.failureLevel` fails only on `COMPATIBILITY_PROBLEMS` / `INVALID_PLUGIN`.

## Build / test / run

**Use the `Makefile`** — it wraps the common commands and sets `JAVA_HOME` for you (see JDK note below). Run `make help` to list targets:

```bash
make build      # → ./gradlew buildPlugin   — assemble the plugin zip → build/distributions/
make test       # → ./gradlew test          — hermetic unit / light-platform tests
make integration-test  # → ./gradlew integrationTest — real-repo tests (needs sl + git)
make check      # → ./gradlew build         — full compile + test + assemble (warning-free)
make verify     # → ./gradlew verifyPlugin  — Plugin Verifier (IC 242/243/251/252 + IU 253)
make run        # → ./gradlew runIde         — sandbox IDE (2024.2) for manual/GUI testing
make run-253    # → ./gradlew runIde2025_3   — 2025.3 (IU) sandbox
make clean      # → ./gradlew clean
```

- **A JDK 21 is required.** Gradle finds it via `JAVA_HOME` or `org.gradle.java.home`. On this machine JDK 21 is a keg-only Homebrew install at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` and is pinned in `~/.gradle/gradle.properties`. The **gradlew launcher** additionally needs `JAVA_HOME` set to bootstrap the JVM; the `Makefile` exports it, so prefer `make`. If a bare `./gradlew` command reports "Unable to locate a Java Runtime", `export JAVA_HOME=…` first.
- **A warning-free build is a hard requirement.** Kotlin compiler warnings (esp. deprecations) are treated as defects here — fix or narrowly `@Suppress` them, don't leave them.
- `sl` must be installed for anything that actually runs commands; unit tests do not need it (they test pure parsers / mapping).

## Architecture map

```
src/main/kotlin/io/github/pfeisa/sapling/
├── cli/SaplingCli              off-EDT `sl` execution (the ONLY place that runs sl)
├── command/SaplingCommandRunner  one cancellable bg task per mutating command; refresh + notify
├── status/                     `sl status -Tjson` model + parser
├── detection/                  `.sl` + dotgit detection; VcsRootChecker (auto-claims ONLY `.sl`)
├── changes/                    ChangeProvider, SaplingRevisionNumber, SaplingContentRevision, mapping
├── diff/                       DiffProvider
├── history/                    per-file VcsHistoryProvider + SaplingFileRevision
├── blame/                      annotate parser, FileAnnotation, AnnotationProvider
├── log/                        VcsLogProvider + SaplingRefManager (repo-wide Log tab)
├── merge/                      SaplingMergeProvider (IDE 3-way merge dialog for `sl` conflicts)
├── rollback/                   SaplingRollbackEnvironment + SaplingRevert (IDE Revert → `sl revert`)
├── actions/                    thin AnActions: goto/pull/push/uncommit/shelve/unshelve, bookmarks, copy-hash
├── widget/                     status-bar bookmark/commit widget (SaplingWidgetFactory)
├── conflict/ startup/ settings/ util/   conflicts action, dotgit hint + auto-open-ISL, settings, notifications+paths
├── isl/                        IslServerManager, IslToolWindowFactory, IslBrowserPanel, IdeBridge, ThemeSync
└── SaplingVcs.kt               AbstractVcs — wires the change/diff/history/annotation read providers plus the rollback + merge environments
```

**No commit UI:** the plugin deliberately provides **no `CheckinEnvironment`** — committing/amending is done through the embedded ISL, not the IDE commit tool window (`getCheckinEnvironment()` is left at the `null` default). This also avoids implementing the experimental `AmendCommitAware` interface, which changed incompatibly across platform builds. **ISL availability** (`IslToolWindowFactory` / `SaplingAutoOpenIsl`) uses `SaplingRepoDetector.findWorkingCopyRoot` (matches `.sl` **or** `.git`, so ISL works in dotgit mode); VCS root ownership still uses `.sl`-only detection, so Git4Idea keeps owning `.git` repos.

## Hard rules (non-negotiable conventions)

- **Threading:** every `sl` call runs **off the EDT** via `SaplingCli` (`@RequiresBackgroundThread`). **Never swallow `ProcessCanceledException`** — always rethrow / let it propagate. Every *mutating* command goes through `SaplingCommandRunner` (background `Task.Backgroundable`, then `VfsUtil.markDirtyAndRefresh` + `VcsDirtyScopeManager.markEverythingDirty()`, then a `SaplingNotifications` result).
- **Actions are thin:** `actionPerformed` only gathers input (project, root, dialogs) and delegates to the runner. No `sl` logic inline. Destructive actions (uncommit, delete-bookmark) confirm first; text-input actions guard `isBlank()` (dialogs return `""`, not null, on blank OK).
- **Always parse `sl` output; never hardcode** ports/paths/revisions. Reuse the shared pieces — content-at-revision **always** via `SaplingContentRevision`, revision identity **always** via `SaplingRevisionNumber`, path resolution via `SaplingPaths`/`SaplingRepoDetector`. Do not re-implement `sl cat`/parsing inline.
- **No static mutable state.** Light services (`@Service`) where services are needed; never parent `Disposable`s to `Application`/`Project`.
- **JCEF hygiene:** gate on `JBCefApp.isSupported()` (with a fallback panel); wrap JCEF objects in a plugin-owned `Disposable`; register `JBCefJSQuery` as a child of the browser; guard late JS callbacks with a `@Volatile disposed` flag.
- **Security:** never log the ISL auth token or the token-bearing URL, and never interpolate raw `sl web` output into a user-facing message. Treat every `JBCefJSQuery` payload as untrusted (the open-file bridge validates the path stays under the repo root). Build command args as **`List<String>`** (via `GeneralCommandLine`) — never shell strings.
- **Ownership model:** `.sl`-mode roots auto-detect as Sapling (`VcsRootChecker.isVcsDir` matches ONLY `.sl`). dotgit-mode roots default to Git4Idea; a one-time startup hint offers manual mapping. Don't make Sapling auto-claim `.git`.
- **License / trademark:** the plugin's own code is **non-GPL** and original. Never copy from GPL reference material, and never commit reference trees (the `.gitignore` excludes `selvejj-0.4.0/` and `sapling/`, which have since been removed). Keep the non-affiliation notice ("Not affiliated with Meta"); "Sapling" is a Meta trademark used descriptively. Never reintroduce the old `church.mgc` coordinates.

## Platform / SDK gotchas (learned building this)

- **Deprecated APIs to avoid (they break the warning-free bar):**
  - `Disposer.isDisposed(this)` → use a `@Volatile private var disposed` flag set in `dispose()`.
  - `VcsRootChecker.isRoot(String)` is deprecated → override `isRoot(VirtualFile)` and use `file.toNioPath()` (also Windows-correct; `Paths.get(file.path)` is not).
  - `VcsLogDataKeys.VCS_LOG.selectedCommits` → `VCS_LOG_COMMIT_SELECTION.commits`.
  - Any `AnAction` that overrides `update()` must also override `getActionUpdateThread()` (return `BGT` unless it touches EDT-only APIs).
  - If a forced-abstract member is itself deprecated (e.g. `VcsFileRevision.getContent()`), keep it with a narrow `@Suppress("OVERRIDE_DEPRECATION")`.
- **`bundledModule` needs (not on the default IC compile classpath), declared in `build.gradle.kts`:** `intellij.platform.vcs.impl` (for `ShowDiffAction.showDiffForChange`, used to intercept ISL's native diff) and `intellij.platform.vcs.log.impl` (for `LogDataImpl`/`SimpleRefType`/`SimpleRefGroup`).
- **Tests:** `BasePlatformTestCase` requires `opentest4j` on the test runtime classpath (already added as `testRuntimeOnly`). The light fixture's in-memory `TempFileSystem` throws on `VirtualFile.toNioPath()`; when a test needs real files, use `Files.createTempDirectory` + `LocalFileSystem.refreshAndFindFileByNioFile(...)` (see `SaplingVcsRootCheckerVfsTest`). `kotlinx.serialization` IS available at test runtime via `testFramework(Platform)` even though it's `compileOnly`.
- **`sl -Tjson` quirks (verified against real `sl`):**
  - `sl log` `date` is `[epochInt, tzOffset]` → parse as `List<Long>`.
  - `sl annotate` `date` is `[epochFloat, tzOffset]` → parse as `List<Double>` (a `List<Long>` will throw). Annotate has **no** `line_number` field — derive the line number from the array index. Fields are `line`/`node`/`user`/`date` (no extra flags needed beyond `-Tjson`).
  - `sl web` supports (hidden) `--platform androidStudio --json --no-open --cwd <root>`; the JSON is `{url, port, token, pid, wasServerReused, ...}`. Do not pass `--persist` (rely on ISL's idle auto-shutdown).
  - `sl log -Tjson` fields: `node`, `user` (not "author"), `desc` (not "description"), `date`, `parents`, `bookmarks`.

## Known limitations (intentional in v0.1 — don't "fix" as bugs without a reason)

- Change provider runs a full-repo `sl status` (ignores `dirtyScope`). Ignored files ARE reported (so they grey out) via a separate best-effort `sl status -i --terse=i` call that collapses fully-ignored directories to one entry each (`--terse` is a hidden `sl` flag; if it fails, greying is skipped without breaking change reporting).
- `DiffProvider.getCurrentRevision` = working-copy parent; `getLatestCommittedRevision` = null.
- `VcsLogProvider.readFullDetails` returns empty per-commit changes (Log "Changes" sub-panel is empty); `readAllHashes` buffers full output before streaming.
- ISL webview interactions and live VCS operations are best validated with `./gradlew runIde` (not covered by headless tests).

## Where things live

- **Design spec + the 4 implementation plans:** `docs/superpowers/specs/` and `docs/superpowers/plans/`.
- **Execution audit trail** (per-task reviews, fixes, deviations, triaged limitations): `.superpowers/sdd/progress.md` — this is git-ignored scratch, not part of the plugin.

## Repository status

Not yet a git repository (initial commit pending). When initialized, `.gitignore` already excludes build output, `.idea/`, the IntelliJ Platform sandbox/caches (`.intellijPlatform/`, `.kotlin/`), per-user `.claude/`, and the (removed) GPL reference trees. The **`LICENSE` file is present — MIT** (non-GPL, per the arms-length-subprocess rationale below); keep it in sync with the README license section. The plugin display `<name>` is **"Sapling SCM Integration"** (not bare `Sapling`): Marketplace Approval Guideline 1.2.h bans including "JetBrains"/a JetBrains product name (so no "… for JetBrains"), bare `Sapling` risks impersonating Meta's product (1.2.a "original and unique"), and the "Integration" suffix frames it as an unofficial third-party add-on. Keep the `<id>` `io.github.pfeisa.sapling` unchanged (renaming it orphans existing installs) and retain the "Not affiliated with Meta" notice.
