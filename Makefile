# Sapling SCM (sl) JetBrains plugin — common commands. Run `make help` for the list.
#
# `java` is not on PATH on the dev machine, and the gradlew launcher needs JAVA_HOME to
# bootstrap the JVM (otherwise: "Unable to locate a Java Runtime"). We set it here.
# Override by exporting JAVA_HOME before running make, or edit the default below.
JAVA_HOME ?= /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export JAVA_HOME

GRADLE := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help build test integration-test verify check run run-253 clean

help: ## List available commands
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

build: ## Assemble the installable plugin zip -> build/distributions/
	$(GRADLE) buildPlugin

test: ## Run the hermetic unit / light-platform tests
	$(GRADLE) test

integration-test: ## Run real-repo integration tests (requires `sl` + `git` installed)
	$(GRADLE) integrationTest

verify: ## Plugin Verifier across IC 242/243/251/252 + IU 253 (2025.3)
	$(GRADLE) verifyPlugin

check: ## Full build: compile + test + assemble (must be warning-free)
	$(GRADLE) build

run: ## Launch a sandbox IDE (2024.2) with the plugin loaded
	$(GRADLE) runIde

run-253: ## Launch a 2025.3 (IU) sandbox for manual threading validation
	$(GRADLE) runIde2025_3

clean: ## Delete build outputs
	$(GRADLE) clean
