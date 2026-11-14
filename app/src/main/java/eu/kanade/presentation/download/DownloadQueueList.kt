package eu.kanade.presentation.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.download.DownloadQueueSection
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadQueueList(
    sections: List<DownloadQueueSection>,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    onReorder: (List<Download>) -> Unit,
    onMove: (Download, Boolean) -> Unit,
    onMoveSeries: (Download, Boolean) -> Unit,
    onCancel: (Download) -> Unit,
    onCancelSeries: (Download) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionsState = remember { sections.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState, contentPadding) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (!fromKey.startsWith(DOWNLOAD_KEY) || !toKey.startsWith(DOWNLOAD_KEY)) {
            return@rememberReorderableLazyListState
        }

        // Downloads can only be moved within their own source
        val sectionIndex = sectionsState.indexOfFirst { section -> section.downloads.any { it.key == fromKey } }
        if (sectionIndex == -1) return@rememberReorderableLazyListState
        val section = sectionsState[sectionIndex]
        val toIndex = section.downloads.indexOfFirst { it.key == toKey }
        if (toIndex == -1) return@rememberReorderableLazyListState

        val downloads = section.downloads.toMutableList()
        val fromIndex = downloads.indexOfFirst { it.key == fromKey }
        downloads.add(toIndex, downloads.removeAt(fromIndex))
        sectionsState[sectionIndex] = section.copy(downloads = downloads)
    }

    LaunchedEffect(sections) {
        if (!reorderableState.isAnyItemDragging) {
            sectionsState.clear()
            sectionsState.addAll(sections)
        }
    }

    val onDragStopped = { onReorder(sectionsState.flatMap { it.downloads }) }

    val firstDownload = sectionsState.firstOrNull()?.downloads?.firstOrNull()
    val lastDownload = sectionsState.lastOrNull()?.downloads?.lastOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = contentPadding,
    ) {
        sectionsState.forEach { section ->
            item(key = section.key) {
                ListGroupHeader(
                    text = "${section.name} (${section.downloads.size})",
                    modifier = Modifier.padding(top = MaterialTheme.padding.small),
                )
            }
            items(
                items = section.downloads,
                key = { it.key },
            ) { download ->
                ReorderableItem(reorderableState, download.key) { isDragging ->
                    DownloadQueueItem(
                        download = download,
                        isDragging = isDragging,
                        canMoveToTop = download != firstDownload,
                        canMoveToBottom = download != lastDownload,
                        onDragStopped = onDragStopped,
                        onMove = { toTop -> onMove(download, toTop) },
                        onMoveSeries = { toTop -> onMoveSeries(download, toTop) },
                        onCancel = { onCancel(download) },
                        onCancelSeries = { onCancelSeries(download) },
                    )
                }
            }
        }
    }
}

// Lazy list keys must fit in a Bundle
private const val SECTION_KEY = "section-"
private const val DOWNLOAD_KEY = "download-"
private val DownloadQueueSection.key get() = "$SECTION_KEY$id"
private val Download.key get() = "$DOWNLOAD_KEY${chapter.id}"

@Composable
private fun ReorderableCollectionItemScope.DownloadQueueItem(
    download: Download,
    isDragging: Boolean,
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    onDragStopped: () -> Unit,
    onMove: (toTop: Boolean) -> Unit,
    onMoveSeries: (toTop: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCancelSeries: () -> Unit,
) {
    val progress by download.progressFlow.collectAsState(0)
    val status by download.statusFlow.collectAsState()
    val animatedProgress by animateFloatAsState(progress.coerceIn(0, 100) / 100f, label = "progress")
    val pageCountText = remember(progress, status) {
        download.pages?.let { pages -> "${download.downloadedImages}/${pages.size}" }
    }

    Surface(
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.background
        },
        shadowElevation = if (isDragging) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = MaterialTheme.padding.small,
                end = MaterialTheme.padding.extraSmall,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.small)
                    .draggableHandle(onDragStopped = { onDragStopped() }),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MaterialTheme.padding.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    Text(
                        text = download.manga.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (pageCountText != null) {
                        Text(
                            text = pageCountText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    text = download.chapter.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.extraSmall),
                )
            }
            DownloadQueueItemMenu(
                canMoveToTop = canMoveToTop,
                canMoveToBottom = canMoveToBottom,
                onMove = onMove,
                onMoveSeries = onMoveSeries,
                onCancel = onCancel,
                onCancelSeries = onCancelSeries,
            )
        }
    }
}

@Composable
private fun DownloadQueueItemMenu(
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    onMove: (toTop: Boolean) -> Unit,
    onMoveSeries: (toTop: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCancelSeries: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val closeMenu = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(MR.strings.action_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
            if (canMoveToTop) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_top)) },
                    onClick = {
                        closeMenu()
                        onMove(true)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_move_to_top_all_for_series)) },
                onClick = {
                    closeMenu()
                    onMoveSeries(true)
                },
            )
            if (canMoveToBottom) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_move_to_bottom)) },
                    onClick = {
                        closeMenu()
                        onMove(false)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_move_to_bottom_all_for_series)) },
                onClick = {
                    closeMenu()
                    onMoveSeries(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_cancel)) },
                onClick = {
                    closeMenu()
                    onCancel()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.cancel_all_for_series)) },
                onClick = {
                    closeMenu()
                    onCancelSeries()
                },
            )
        }
    }
}
