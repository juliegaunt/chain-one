package io.github.juliegaunt.chainone.ui.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.db.rowMarker
import java.io.File

/** The pattern's PDF; with several, a menu above it switches between them. */
@Composable
fun PdfTab(
    pdfs: List<PatternAttachment>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    fileOf: (PatternAttachment) -> File,
    onPageChange: (pdfId: Long, page: Int) -> Unit,
    onMarkerChange: (pdfId: Long, RowMarker?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pdf = pdfs.firstOrNull { it.id == selectedId } ?: pdfs.first()
    Column(modifier.fillMaxWidth()) {
        if (pdfs.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { expanded = true }) {
                    Text(pdf.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.pdf_choose))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    pdfs.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            trailingIcon = if (option.id == pdf.id) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                expanded = false
                                onSelect(option.id)
                            },
                        )
                    }
                }
            }
        }
        // Keyed so switching PDFs starts the viewer afresh on that PDF's own page and marker.
        key(pdf.id) {
            PdfViewer(
                file = fileOf(pdf),
                initialPage = pdf.lastPage,
                initialMarker = pdf.rowMarker,
                onPageChange = { onPageChange(pdf.id, it) },
                onMarkerChange = { onMarkerChange(pdf.id, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
