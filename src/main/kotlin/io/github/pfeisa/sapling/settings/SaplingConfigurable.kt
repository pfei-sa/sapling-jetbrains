package io.github.pfeisa.sapling.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class SaplingConfigurable : BoundConfigurable("Sapling") {
    private val settings = SaplingSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row("Path to sl executable:") {
            textField().bindText(settings::executablePath)
        }
        row {
            checkBox("Open ISL automatically when a Sapling project opens")
                .bindSelected(settings::autoOpenIsl)
        }
        row {
            checkBox("Hide IDE commit UI (commit in ISL)")
                .bindSelected(settings::hideCommitUi)
                .comment(
                    "Sapling commits are made in the ISL tool window. This hides the IDE's inline " +
                        "commit message box and grays the Commit action. Applies to the commit box " +
                        "after reopening the project; the grayed action updates immediately.",
                )
        }
    }
}
