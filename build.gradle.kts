import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "io.github.pfeisa.sapling"
// Overridable by the release workflow via `-PpluginVersion=0.1.<datetime>`; drives both the
// zip filename and the injected `plugin.xml <version>`. Local builds fall back to the base.
version = (findProperty("pluginVersion") as String?) ?: "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    // Shipped with the IDE at runtime, so compileOnly.
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    intellijPlatform {
        create("IC", "2024.2")
        // intellij.platform.vcs.log.impl → LogDataImpl / SimpleRefType / SimpleRefGroup for the Log tab.
        bundledModule("intellij.platform.vcs.log.impl")
        // intellij.platform.vcs.impl → ShowDiffAction.showDiffForChange for ISL native-diff interception.
        bundledModule("intellij.platform.vcs.impl")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        // The Marketplace ZIP Signer tool `signPlugin` shells out to — resolved from defaultRepositories()
        // (Maven Central). Without it, `signPlugin` fails with "Cannot resolve 'Marketplace ZIP Signer'".
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    signing {
        // Values come from GitHub Actions secrets (env vars) at publish time; unset locally, so a plain
        // `buildPlugin` stays unsigned. Generated with openssl (RSA-4096, self-signed) — see release docs.
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        // Marketplace permanent token (stored as the JB_TOKEN secret, mapped to PUBLISH_TOKEN in CI).
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // channels default to "default" = the Stable channel. Use listOf("eap")/listOf("beta") for a
        // pre-release line that only opted-in users receive.
    }
    pluginVerification {
        // The 2.18 verifier is far stricter than older ones. Fail only on genuinely-fatal categories
        // (real binary-compat breaks + an invalid plugin) — NOT on advisory deprecated/experimental/
        // internal API *usages*, which are still reported. Implementing ToolWindowFactory unavoidably
        // touches 6 internal-API usages (its getAnchor/getIcon/manage defaults are @ApiStatus.Internal).
        // COMPATIBILITY_PROBLEMS stays fatal — that's the category that catches real breaks.
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )
        ides {
            // Full supported range: sinceBuild floor (242) through the latest 2026.2 EAP. 2025.1 removed
            // the implicit write-intent lock from Swing invokeLater / Dispatchers.Main; 2025.2 made lock
            // acquisition cancellation-aware; 2025.3 added Dispatchers.UI (unused here). Build-253 unified
            // the IDEA distribution (no standalone `ideaIC` installer from 253 on), so 253/261 are
            // verified against the unified `IU` build (262 pending EAP resolution — see below) — a sound
            // superset proxy, since this plugin uses only platform/VCS APIs present in both IC and IU.
            create("IC", "2024.2")
            create("IC", "2024.3")
            create("IC", "2025.1")
            create("IC", "2025.2")
            create("IU", "2025.3")
            create("IU", "2026.1.4")
            // 2026.2 is still EAP/RC; track the latest 262 EAP from the snapshots repo (resolved by
            // defaultRepositories()). Moving target — swap to a stable `create("IU", "2026.2")` at GA.
            // To pin the exact build from a Marketplace report instead, use "262.8665.176-EAP-SNAPSHOT".
            // Both "262-EAP-SNAPSHOT" and the exact pin "262.8665.176-EAP-SNAPSHOT" fail to resolve
            // locally ("Couldn't resolve IntellijIdea download URL") even with intellijPlatform {
            // snapshots() } added to repositories{} — the version->download-URL lookup fails before any
            // network/repository fetch is attempted. IU 2026.1.4 alone reproduces all 3 known
            // compatibility problems identically, so it remains the reliable gate.
            // TODO: add at 2026.2 GA (create("IU", "2026.2")).
            // create("IU", "262-EAP-SNAPSHOT")
        }
    }
}

// Dev-only harness: launch a build-253 sandbox to manually validate threading (ISL open-file, status
// refresh) on the newer lock model, without changing the 2024.2 compile target. Does not affect the
// shipped artifact. Run: `./gradlew runIde2025_3`. Uses the unified IU distribution because build-253
// has no standalone IC installer (see the pluginVerification note).
intellijPlatformTesting {
    runIde {
        register("runIde2025_3") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate
            version = "2025.3"
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    // Real-repo integration tests (io...sapling.realrepo) require the `sl`/`git` binaries.
    // Excluded from the default hermetic `test` run; opt in with `-Pintegration` or the
    // `integrationTest` task. Both reuse the platform-configured `test` task (a custom Test
    // task cannot inherit the plugin's JVM args cleanly — see the spec's build-wiring note).
    // Note: this is build-wide. Co-invoking e.g. `./gradlew check integrationTest` flips the
    // whole build to integration mode (the `test` run inside `check` would then include realrepo).
    // That is the intended "you opted in" semantics; plain `test`/`check`/`build` stay hermetic.
    val runIntegration = project.hasProperty("integration") ||
        gradle.startParameter.taskNames.any { it == "integrationTest" || it.endsWith(":integrationTest") }
    named<Test>("test") {
        if (!runIntegration) exclude("**/realrepo/**")
        systemProperty("sapling.integrationTest", runIntegration.toString())
    }
    register("integrationTest") {
        group = "verification"
        description = "Runs real-repo integration tests against real sl/git (requires both installed)."
        dependsOn(named("test"))
    }
}
