# Security Policy

## Reporting a vulnerability

Please report security issues **privately** — do not open a public issue for anything
exploitable.

Use GitHub's **private vulnerability reporting**: go to this repository's **Security** tab →
**Report a vulnerability**. (Repository maintainers: enable this under *Settings → Code security →
Private vulnerability reporting*.)

Please include the affected version, reproduction steps, and impact. You can expect an initial
acknowledgement within a few days.

## Supported versions

This project is pre-1.0; only the latest released version receives security fixes.

## Trust boundary (important)

This plugin operates by running the Sapling CLI (`sl`) as a subprocess **in the directory of the
repository you open**. Like Git, Mercurial, and every other VCS integration, `sl` reads
**repository-local configuration** — which can define extensions, hooks, and aliases that execute
code as ordinary commands run. Some of those commands (e.g. a status refresh) run automatically
when a Sapling repository is opened.

**Only open Sapling repositories you trust.** Treat an untrusted repository the same way you would
treat untrusted code: opening it in the IDE can cause `sl` to run repository-defined code. This is
inherent to integrating with a VCS and is not specific to this plugin.

## What the plugin does to stay safe

- All `sl` invocations are built as an argument list via `GeneralCommandLine` (never a shell
  string) and terminate option parsing with `--` before file paths, so a maliciously-named file
  cannot inject `sl` options.
- The embedded ISL webview is gated on JCEF availability, and every payload crossing the
  JavaScript → IDE bridge is treated as untrusted: file-open requests are validated to stay within
  the repository root (lexically and via real-path resolution).
- The local `sl web` auth token and its token-bearing URL are never logged or shown in
  user-facing messages.
