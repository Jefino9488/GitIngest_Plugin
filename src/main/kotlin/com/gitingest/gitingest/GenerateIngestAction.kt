package com.gitingest.gitingest

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GenerateIngestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("No project is open!", "Error")
            return
        }

        // Get the currently open file
        val file = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        if (file == null) {
            Messages.showErrorDialog("No file is open!", "Error")
            return
        }

        val filePath = file.path
        val directoryPath = File(filePath).parent

        if (directoryPath.isNullOrEmpty()) {
            Messages.showErrorDialog("Could not determine the directory of the open file!", "Error")
            return
        }

        try {
            // Check if gitingest is installed
            if (!isGitingestInstalled) {
                Messages.showInfoMessage("Gitingest module is not installed. Attempting to install...", "Installing")

                // Attempt to install gitingest
                val installed = installGitingest()
                if (!installed) {
                    Messages.showErrorDialog(
                        """
                            Failed to install gitingest. Please install it manually using:
                            pip install gitingest
                            """.trimIndent(), "Installation Failed"
                    )
                    return
                }
            }

            // Run gitingest command on the directory of the open file
            val processBuilder = ProcessBuilder("gitingest", ".")
            processBuilder.directory(File(directoryPath)) // Set working directory to the directory of the open file
            val process = processBuilder.start()

            // Capture gitingest output (if any)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val digestFile = File(directoryPath, "digest.txt")
                if (digestFile.exists()) {
                    Messages.showInfoMessage("Ingest generated successfully: ${digestFile.absolutePath}", "Success")
                } else {
                    Messages.showErrorDialog("gitingest executed but digest.txt not found!", "Error")
                }
            } else {
                Messages.showErrorDialog("Error during execution:\n$output", "Execution Error")
            }
        } catch (ex: Exception) {
            Messages.showErrorDialog("Execution failed: ${ex.message}", "Error")
        }
    }

    private val isGitingestInstalled: Boolean
        get() {
            try {
                val checkProcess = ProcessBuilder("python", "-m", "pip", "show", "gitingest").start()
                return checkProcess.waitFor() == 0
            } catch (e: Exception) {
                return false
            }
        }

    private fun installGitingest(): Boolean {
        try {
            val installProcess = ProcessBuilder("python", "-m", "pip", "install", "gitingest").start()

            // Capture installation output
            val reader = BufferedReader(InputStreamReader(installProcess.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = installProcess.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            return false
        }
    }
}

