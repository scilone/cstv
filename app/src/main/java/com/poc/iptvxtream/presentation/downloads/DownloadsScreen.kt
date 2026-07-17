package com.poc.iptvxtream.presentation.downloads

import com.poc.iptvxtream.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.poc.iptvxtream.domain.model.DownloadStatus
import com.poc.iptvxtream.domain.model.DownloadedItem
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface3

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    isTv: Boolean,
    onPlayMovie: (DownloadedItem) -> Unit,
    onPlayEpisode: (DownloadedItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Surface1)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.downloads_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Text(
                text = stringResource(R.string.downloads_used_space, formatBytes(state.usedBytes)),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            if (state.downloads.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.downloads_empty),
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().focusGroup()
                ) {
                    items(state.downloads, key = { it.contentId }) { item ->
                        DownloadRow(
                            item = item,
                            onPlay = {
                                if (item.type == DownloadedItem.TYPE_EPISODE) onPlayEpisode(item) else onPlayMovie(item)
                            },
                            onRemove = { viewModel.remove(item.contentId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadedItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val playable = item.status == DownloadStatus.COMPLETED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Surface3)
            .clickable(enabled = playable) { onPlay() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            DownloadStatusLine(item)
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.download_remove),
                tint = Color(0xFFCF6679)
            )
        }
    }
}

@Composable
private fun DownloadStatusLine(item: DownloadedItem) {
    when (item.status) {
        DownloadStatus.COMPLETED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.download_status_completed), color = Color(0xFF4CAF50), fontSize = 11.sp)
        }
        DownloadStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.download_status_failed), color = Color(0xFFCF6679), fontSize = 11.sp)
        }
        DownloadStatus.QUEUED -> Text(stringResource(R.string.download_status_queued), color = Color.Gray, fontSize = 11.sp)
        DownloadStatus.PAUSED -> Text(stringResource(R.string.download_status_paused), color = Color.Gray, fontSize = 11.sp)
        DownloadStatus.REMOVING -> Text(stringResource(R.string.download_status_queued), color = Color.Gray, fontSize = 11.sp)
        DownloadStatus.DOWNLOADING -> Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Downloading, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.download_status_downloading, item.percent), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Surface1
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Mo"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(java.util.Locale.FRANCE, "%.1f Go", mb / 1024)
    } else {
        String.format(java.util.Locale.FRANCE, "%.0f Mo", mb)
    }
}
