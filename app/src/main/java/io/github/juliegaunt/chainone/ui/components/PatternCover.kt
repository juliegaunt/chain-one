package io.github.juliegaunt.chainone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import coil3.compose.AsyncImage
import io.github.juliegaunt.chainone.data.files.FileStore
import java.io.File

/**
 * Shows a pattern's cover photo, or the first letter of its title on a tinted
 * background when there is no photo. [model] can be a [File] or a content Uri.
 */
@Composable
fun PatternCover(
    model: Any?,
    title: String,
    modifier: Modifier = Modifier,
    letterStyle: TextStyle = MaterialTheme.typography.displaySmall,
) {
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = letterStyle,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

fun FileStore.coverFile(path: String?): File? = path?.let { resolve(it) }
