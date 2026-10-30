package io.github.juliegaunt.chainone.ui.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R

/**
 * What's laid over a PDF: the row marker's on/off and up/down buttons, the hint shown when it's
 * turned on, and the page number.
 */
@Composable
internal fun PdfOverlay(
    markerOn: Boolean,
    showHint: Boolean,
    page: Int,
    pageCount: Int,
    onMarkerToggle: (Boolean) -> Unit,
    onMarkerStep: (rows: Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconToggleButton(
                checked = markerOn,
                onCheckedChange = onMarkerToggle,
            ) {
                Icon(
                    painterResource(R.drawable.ic_row_marker),
                    contentDescription = stringResource(if (markerOn) R.string.pdf_marker_hide else R.string.pdf_marker_show),
                )
            }
            if (markerOn) {
                FilledTonalIconButton(onClick = { onMarkerStep(-1) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.pdf_marker_up))
                }
                FilledTonalIconButton(onClick = { onMarkerStep(1) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.pdf_marker_down))
                }
            }
        }
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp, end = 64.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    stringResource(R.string.pdf_marker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Text(
                stringResource(R.string.pdf_page_of, page + 1, pageCount),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
