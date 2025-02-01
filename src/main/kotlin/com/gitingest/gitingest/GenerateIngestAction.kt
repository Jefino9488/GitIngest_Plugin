package com.gitingest.gitingest

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.application.ApplicationManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GenerateIngestAction : AnAction() {
    private var isGitingestInstalled: Boolean? = null

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            showErrorDialog("No project is open!")
            return
        }

        // Get the currently open file
        val file = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        if (file == null) {
            showErrorDialog("No file is open!")
            return
        }

        val filePath = file.path
        val directoryPath = File(filePath).parent

        if (directoryPath.isNullOrEmpty()) {
            showErrorDialog("Could not determine the directory of the open file!")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running gitingest", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Check if gitingest is installed
                    if (isGitingestInstalled == null || isGitingestInstalled == false) {
                        indicator.text = "Checking gitingest installation..."
                        isGitingestInstalled = checkGitingestInstalled()
                    }

                    if (isGitingestInstalled == false) {
                        showInfoMessage("Gitingest module is not installed. Attempting to install...")
                        indicator.text = "Installing gitingest..."
                        val installed = installGitingest()
                        if (!installed) {
                            showErrorDialog(
                                """
                                Failed to install gitingest. Please install it manually using:
                                pip install gitingest
                                """.trimIndent()
                            )
                            return
                        }
                        isGitingestInstalled = true
                    }

                    // Run gitingest command on the directory of the open file
                    indicator.text = "Running gitingest..."
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
                            showInfoMessage("Ingest generated successfully: ${digestFile.absolutePath}")
                        } else {
                            showErrorDialog("gitingest executed but digest.txt not found!")
                        }
                    } else {
                        showErrorDialog("Error during execution:\n$output")
                    }
                } catch (ex: Exception) {
                    showErrorDialog("Execution failed: ${ex.message}")
                }
            }
        })
    }

    private fun checkGitingestInstalled(): Boolean {
        return try {
            val checkProcess = ProcessBuilder("python", "-m", "pip", "show", "gitingest").start()
            checkProcess.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun installGitingest(): Boolean {
        return try {
            val installProcess = ProcessBuilder("python", "-m", "pip", "install", "gitingest").start()

            // Capture installation output
            val reader = BufferedReader(InputStreamReader(installProcess.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = installProcess.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun showErrorDialog(message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(message, "Error")
        }
    }

    private fun showInfoMessage(message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showInfoMessage(message, "Info")
        }
    }
}