#!/bin/bash
# Regenerates the throwaway git-backed Sapling repo that `make run` opens the sandbox IDE on.
# Usage: scripts/dummy-repo.sh [target-dir]   (default: build/dummy-repo)
#
# The repo is rebuilt from scratch on every invocation so manual-testing sessions always start
# from the same known state: three commits, a bookmark, and a working tree that exercises every
# status the plugin maps (M / A / R / ! / ? / ignored).
set -euo pipefail

TARGET="${1:-build/dummy-repo}"

rm -rf "$TARGET"
mkdir -p "$TARGET"
cd "$TARGET"

sl init --git .
# Repo-local identity, so commits work regardless of the machine's global sl/git config.
printf '\n[ui]\nusername = Sapling Sandbox <sandbox@example.com>\n' >> .sl/config

# --- commit 1: initial project ---------------------------------------------------------------
printf '# Dummy project\n\nFixture repo for manual plugin testing.\n' > README.md
printf '*.log\nout/\n' > .gitignore
mkdir -p src
printf 'fun main() {\n    println("hello")\n}\n' > src/main.kt
printf 'fun helper() = 1\n' > src/helper.kt
printf 'fun doomed() = 0\n' > src/doomed.kt
printf 'fun missing() = 2\n' > src/missing.kt
sl add README.md .gitignore src/main.kt src/helper.kt src/doomed.kt src/missing.kt
sl commit -m 'Initial project skeleton'

# --- commit 2: edit + rename (exercises copy/rename tracking) --------------------------------
printf 'fun main() {\n    println("hello, sapling")\n}\n' > src/main.kt
sl mv src/helper.kt src/util.kt
sl commit -m 'Greet sapling; move helper to util'

# --- commit 3 (the dot commit the IDE opens on) -----------------------------------------------
printf 'fun util() = 42\n' > src/util.kt
printf 'val version = "0.1"\n' > src/version.kt
sl add src/version.kt
sl commit -m 'Add version constant and answer everything'
sl bookmark feature

# --- dirty working tree: one file per status --------------------------------------------------
printf 'fun main() {\n    println("hello, dirty working tree")\n}\n' > src/main.kt   # M
printf 'fun fresh() = "added"\n' > src/fresh.kt
sl add src/fresh.kt                                                                  # A
sl rm src/doomed.kt                                                                  # R
rm src/missing.kt                                                                    # ! (missing)
printf 'scratch notes\n' > notes.txt                                                 # ? (untracked)
printf 'noise\n' > debug.log                                                         # ignored (*.log)
mkdir -p out && printf 'junk\n' > out/junk.txt                                       # ignored (out/)

echo
echo "Dummy repo ready at: $(pwd)"
sl status
