# Sapling for JetBrains

Sapling SCM (`sl`) integration for JetBrains IDEs — a native version-control provider for `.sl`-mode repositories **and** an embedded [Interactive Smartlog (ISL)](https://sapling-scm.com/docs/addons/isl) tool window, in one plugin.

> **Unofficial / independent project.** Not affiliated with, endorsed by, or sponsored by Meta.
> "Sapling" is a trademark of Meta Platforms, Inc.; it is used here only to describe the software this plugin integrates with. The plugin talks to the `sl` command-line tool as a subprocess and contains none of Sapling's source code.

- **Plugin ID:** `io.github.pfeisa.sapling`
- **Compatibility:** IntelliJ Platform **2024.2+** (Plugin Verifier runs against IC 2024.2 / 2024.3 / 2025.1 / 2025.2 and IU 2025.3)
- **Status:** early **v0.1**. Builds cleanly, unit tests pass, and the Plugin Verifier reports compatibility. The interactive surfaces (the ISL webview and live VCS operations) are implemented and build-verified; a hands-on pass in a running IDE against a real repository is the recommended next step before any Marketplace release.

---

## What it does

The plugin has two complementary pillars.

### 1. Native Sapling VCS provider
Registers Sapling as a first-class VCS so `.sl`-mode repositories (where Git4Idea can't operate) get real IDE version-control support:

- **Changes view** — modified / added / removed / untracked / missing files, via a single batched `sl status -Tjson` per refresh (off the EDT).
- **Diff** — native side-by-side diff of working-copy files against their committed content (`DiffProvider`, `sl cat`).
- **Revert** — roll back working-copy changes through the IDE's standard Revert action (`sl revert`).
- **File history** — per-file history backed by `sl log -Tjson`.
- **Blame / annotate** — per-line author, date, and revision via `sl annotate -Tjson`.
- **Repository Log tab** — a native `VcsLogProvider` fed by `sl log`, with bookmarks shown as refs.
- **Conflict resolution** — a 3-way `MergeProvider` opens the IDE's merge dialog for `sl` conflicts (base/local/other via `sl cat`, mark-resolved via `sl resolve`).
- **Status-bar widget** — shows the current commit / active bookmark.
- **Commands** — Goto, Pull, Push, Uncommit (with confirmation), Shelve/Unshelve, create/delete Bookmarks, Copy Commit Hash, and Resolve Conflicts — each a thin action over a shared cancellable background runner that refreshes the VFS and reports success/failure.

> **Committing and amending is done in the embedded ISL** (below), not the IDE's Commit tool window — the plugin deliberately ships no `CheckinEnvironment`, matching Sapling's no-staging-area model.

**Ownership model:** `.sl`-mode roots are auto-detected as Sapling. **dotgit-mode** roots (a Git-backed Sapling checkout) default to Git4Idea; the plugin shows a one-time hint that you can switch such a root to Sapling via *Settings → Version Control → Directory Mappings* if you prefer.

### 2. Embedded ISL deep bridge
Hosts Sapling's own web GUI inside a JetBrains tool window and wires it to the IDE:

- Launches the stock `sl web` server and loads it in a JCEF browser tool window (**Sapling ISL**, docked right).
- **Open-file bridge** — clicking a file in ISL opens it in the IDE editor (via a `window.__IdeBridge` host bridge; paths are validated against the repo root).
- **Clipboard bridge** — ISL "copy" actions use the IDE clipboard.
- **Theme sync** — the ISL theme follows the IDE's light/dark theme, live.
- Gracefully degrades to an explanatory message on IDE builds without a JCEF runtime.

---

## Requirements

- **A JetBrains IDE on the IntelliJ Platform 2024.2 or newer** (IDEA Community/Ultimate, and other IntelliJ-based IDEs). The ISL tool window additionally needs a JCEF-capable IDE build (most official builds qualify).
- **The Sapling CLI (`sl`) installed and on your `PATH`** — or point the plugin at it explicitly in *Settings → Tools → Sapling*. See [sapling-scm.com](https://sapling-scm.com/) for installation.
- To **build from source:** JDK **21**.

---

## Installing

No Marketplace release yet. To use it now, build the plugin zip and install it from disk:

1. Build it (see below) — the artifact lands at `build/distributions/sapling-jetbrains-0.1.0.zip`.
2. In your IDE: *Settings → Plugins → ⚙ → Install Plugin from Disk…* → select that zip → restart.
3. Open a `.sl`-mode Sapling repository. Accept the **Sapling** VCS mapping when prompted (or set it under *Settings → Version Control → Directory Mappings*). Open the **Sapling ISL** tool window on the right to use ISL.

---

## Building & developing

Standard IntelliJ Platform Gradle Plugin workflow:

```bash
./gradlew buildPlugin     # assemble the installable plugin zip (build/distributions/)
./gradlew runIde          # launch a sandbox IDE with the plugin loaded (manual testing)
./gradlew test            # run the unit / light-platform test suite
./gradlew verifyPlugin    # run the JetBrains Plugin Verifier (API compatibility)
```

**JDK note:** the build targets JVM 21 and needs a JDK 21 to run. If Gradle can't find it, either export `JAVA_HOME` pointing at a JDK 21, or set `org.gradle.java.home` in `~/.gradle/gradle.properties`.

**Tech stack:** Kotlin 2.1.x (JVM 21), Gradle 9.6.1, IntelliJ Platform Gradle Plugin 2.18.1, IntelliJ Platform 2024.2, `kotlinx.serialization` (provided by the IDE), coroutines on injected service scopes.

---

## How it works

Every Sapling operation is performed by **shelling out to the `sl` CLI** through a single wrapper (`SaplingCli`) that runs off the EDT, uses a console-style parent environment and UTF-8, and never swallows cancellation. Machine-readable output (`-Tjson`) is parsed into typed models; the results are translated into the corresponding IntelliJ VCS SPI objects. Because the plugin only invokes `sl` as a separate process (arms-length, over the CLI) and embeds none of its code, the plugin's own code is independent of Sapling's license.

Key building blocks:

- `cli/SaplingCli` — off-EDT process execution (`GeneralCommandLine` + `CapturingProcessHandler`).
- `command/SaplingCommandRunner` — one cancellable background task per mutating command; refreshes the VFS + dirty scope and notifies on completion. Every command action is a thin wrapper over it.
- `SaplingVcs` — the `AbstractVcs` that wires up the change / diff / history / annotation providers plus the rollback and merge environments.
- `isl/*` — `IslServerManager` (launches/parses `sl web --json`), `IslToolWindowFactory` + `IslBrowserPanel` (JCEF host, gated + with fallback), `IdeBridge` (`window.__IdeBridge`), `ThemeSync`.

---

## Project layout

```
src/main/kotlin/io/github/pfeisa/sapling/
├── cli/          # SaplingCli — off-EDT `sl` execution
├── status/       # `sl status -Tjson` model + parser
├── detection/    # .sl detection + VcsRootChecker (auto-claims only .sl)
├── changes/      # ChangeProvider, revision + content-at-revision, change mapping
├── diff/         # DiffProvider
├── history/      # per-file VcsHistoryProvider + file revision
├── blame/        # annotate parser, FileAnnotation, AnnotationProvider
├── log/          # VcsLogProvider + ref manager (the repo-wide Log tab)
├── merge/        # MergeProvider (IDE 3-way merge dialog)
├── rollback/     # RollbackEnvironment + revert (IDE Revert action)
├── command/      # SaplingCommandRunner
├── actions/      # goto/pull/push/uncommit/shelve/unshelve, bookmarks, copy-hash
├── widget/       # status-bar bookmark/commit widget
├── conflict/     # resolve-conflicts action
├── settings/     # persistent settings + Settings UI
├── startup/      # dotgit hint + auto-open-ISL
├── isl/          # ISL server manager, JCEF tool window, IDE bridge, theme sync
├── util/         # notifications, path helpers
└── SaplingVcs.kt # AbstractVcs registration
```

Every Sapling operation is a thin translation layer over the `sl` CLI; see [How it works](#how-it-works) below.

---

## Configuration

*Settings → Tools → Sapling*:

- **`sl` executable path** — defaults to `sl` (resolved on `PATH`); override if it lives elsewhere.
- **Auto-open ISL** — when enabled, the Sapling ISL tool window opens automatically on project open for Sapling repositories (default off).

---

## Testing

- **Hermetic unit / light-platform tests** (`./gradlew test`) covering the CLI wrapper, all `-Tjson` parsers, `.sl` detection, the status→`Change` mapping (including rename de-duplication), the diff/merge/rollback providers, bookmarks, settings, and the ISL launch/scrub/theme helpers. The `sl log`/`sl annotate` parsers were validated against real `sl` output. Real-repo integration tests (`./gradlew integrationTest`, requires `sl` + `git`) exercise the providers against live repositories.
- **JetBrains Plugin Verifier** (`./gradlew verifyPlugin`) reports **Compatible** across IC 2024.2 / 2024.3 / 2025.1 / 2025.2 and IU 2025.3.
- A GitHub Actions workflow (`.github/workflows/verify-plugin.yml`) runs the tests, builds the plugin, and runs the verifier on push.

Live UI behavior (ISL rendering, and diff/commit/history/blame against a real repository) is best exercised with `./gradlew runIde`.

---

## Known limitations (v0.1)

- The change provider runs a full-repo `sl status` per refresh rather than narrowing to the dirty scope. (`sl status` stays fast on large repos thanks to Sapling's watch/virtual-FS support.)
- Ignored files aren't listed in the Changes view (the status call doesn't request `-i`); untracked and missing files are shown.
- The diff provider resolves "current revision" as the working-copy parent; per-file "last committed revision" and "latest committed" precision are follow-ups.
- The repo Log tab's per-commit **Changes** sub-panel is empty (listing a commit's changed files is a follow-up); commit metadata, graph, and refs are populated.
- `readAllHashes` buffers the full `sl log` output before streaming (bounded by repo size).
- ISL webview interactions (file-click, theme, clipboard) rely on Sapling's `androidStudio` platform client and are best validated in a running IDE.

---

## License

**MIT** — see [`LICENSE`](LICENSE).

The plugin's own code is original and permissively licensed even though Sapling itself is GPLv2, because the plugin only invokes `sl` as a **separate, arm's-length subprocess** over its command-line interface and parses its JSON output — it embeds none of Sapling's source. Under the FSF's own interpretation of the GPL, "pipes, sockets and command-line arguments" between two programs are "mere aggregation," not a combined/derivative work, so the plugin is not bound by Sapling's copyleft. (This is the well-settled, least-contested case, not legal advice — the boundary only holds while the interface stays arm's-length; do not embed Sapling source or exchange intimate internal data structures.)

Contributions and any repackaging should preserve the non-affiliation and trademark notices above.

---

## Acknowledgements

- [Sapling SCM](https://sapling-scm.com/) and its Interactive Smartlog, created by Meta — the tools this plugin integrates with.
- Built on the [IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/welcome.html) VCS and JCEF APIs.
