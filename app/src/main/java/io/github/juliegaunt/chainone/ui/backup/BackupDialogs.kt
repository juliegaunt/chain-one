package io.github.juliegaunt.chainone.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.ui.components.ProgressRow
import java.text.DateFormat
import java.util.Date

/** The progress message while a backup task runs, and the question before a restore. */
@Composable
fun BackupDialogs(phase: BackupPhase, onConfirmRestore: () -> Unit, onCancelRestore: () -> Unit) {
    when (phase) {
        BackupPhase.Idle -> Unit
        is BackupPhase.Working -> AlertDialog(
            // Stays up until the task is done: leaving halfway would only confuse.
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            text = {
                ProgressRow(
                    stringResource(
                        when (phase.task) {
                            BackupTask.SAVING -> R.string.backup_saving
                            BackupTask.READING -> R.string.backup_reading
                            BackupTask.RESTORING -> R.string.backup_restoring
                        },
                    ),
                )
            },
        )
        is BackupPhase.ConfirmRestore -> {
            val summary = phase.summary
            val date = remember(summary.createdAt) { DateFormat.getDateInstance(DateFormat.LONG).format(Date(summary.createdAt)) }
            AlertDialog(
                onDismissRequest = onCancelRestore,
                title = { Text(stringResource(R.string.restore_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(
                                R.string.restore_contents,
                                date,
                                pluralStringResource(R.plurals.restore_patterns, summary.patternCount, summary.patternCount),
                                pluralStringResource(R.plurals.restore_pdfs, summary.pdfCount, summary.pdfCount),
                            ),
                        )
                        if (phase.currentPatterns > 0) {
                            Text(
                                pluralStringResource(R.plurals.restore_replaces, phase.currentPatterns, phase.currentPatterns),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirmRestore) {
                        Text(stringResource(if (phase.currentPatterns > 0) R.string.restore_replace else R.string.restore_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancelRestore) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

val BackupMessage.textRes: Int
    get() = when (this) {
        BackupMessage.SAVED -> R.string.backup_saved
        BackupMessage.RESTORED -> R.string.restore_done
        BackupMessage.SAVE_FAILED -> R.string.backup_save_failed
        BackupMessage.READ_FAILED -> R.string.restore_read_failed
        BackupMessage.NOT_A_BACKUP -> R.string.restore_not_a_backup
        BackupMessage.NEWER_VERSION -> R.string.restore_newer_version
        BackupMessage.DAMAGED -> R.string.restore_damaged
        BackupMessage.NO_SPACE -> R.string.restore_no_space
    }
