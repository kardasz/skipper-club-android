package app.skipperclub.ui.main.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.PostComment

private const val COMMENT_MAX_LENGTH = 500

/**
 * Comments bottom sheet: paginated list (infinite scroll), composer with a
 * 500-character limit, swipe-free delete via trailing icon for own comments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    state: CommentsUiState,
    currentUserId: String?,
    canComment: Boolean,
    nowMillis: Long,
    onLoadMore: () -> Unit,
    onSend: (String) -> Unit,
    onEdit: (PostComment, String) -> Unit,
    onDelete: (PostComment) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetHeightFraction = when {
        state.isLoading || state.comments.size > 2 -> 0.78f
        state.comments.isEmpty() && !state.loadFailed -> 0.42f
        canComment -> 0.56f
        else -> 0.46f
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CommentsSheetContent(
            state = state,
            currentUserId = currentUserId,
            canComment = canComment,
            nowMillis = nowMillis,
            onLoadMore = onLoadMore,
            onSend = onSend,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = Modifier
                .fillMaxHeight(sheetHeightFraction)
                .navigationBarsPadding()
                .imePadding(),
        )
    }
}

@Composable
internal fun CommentsSheetContent(
    state: CommentsUiState,
    currentUserId: String?,
    canComment: Boolean,
    nowMillis: Long,
    onLoadMore: () -> Unit,
    onSend: (String) -> Unit,
    onEdit: (PostComment, String) -> Unit,
    onDelete: (PostComment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<PostComment?>(null) }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(state.hasMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        CommentsSheetHeader(
            count = state.comments.size,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.loadFailed -> {
                    Text(
                        text = stringResource(R.string.comments_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }

                state.comments.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.comments_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 20.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.comments, key = { it.id }) { comment ->
                            val isOwn = comment.user.id == currentUserId
                            CommentRow(
                                comment = comment,
                                canModify = isOwn && canComment,
                                nowMillis = nowMillis,
                                onEdit = {
                                    editing = comment
                                    draft = comment.text
                                },
                                onDelete = { onDelete(comment) },
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (canComment) {
            val isEditing = editing != null
            Column(modifier = Modifier.padding(top = 4.dp)) {
                if (isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.comments_editing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            editing = null
                            draft = ""
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.comments_edit_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CommentInputField(
                        draft = draft,
                        onDraftChange = { draft = it.take(COMMENT_MAX_LENGTH) },
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = {
                            val target = editing
                            if (target != null) {
                                onEdit(target, draft)
                                editing = null
                            } else {
                                onSend(draft)
                            }
                            draft = ""
                        },
                        enabled = draft.isNotBlank() && !state.isSending,
                        modifier = Modifier.testTag("comment_send"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isEditing) {
                                Icons.Filled.Check
                            } else {
                                Icons.AutoMirrored.Filled.Send
                            },
                            contentDescription = stringResource(
                                if (isEditing) R.string.comments_edit_save else R.string.comments_send,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsSheetHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count > 0) {
                "${stringResource(R.string.comments_title)} · $count"
            } else {
                stringResource(R.string.comments_title)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CommentInputField(
    draft: String,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        placeholder = {
            Text(
                text = stringResource(R.string.comments_input_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.testTag("comment_input"),
        maxLines = 3,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.64f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun CommentRow(
    comment: PostComment,
    canModify: Boolean,
    nowMillis: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PostUserAvatar(user = comment.user, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.user.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = relativeTime(comment.createdAt, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (comment.updatedAt != comment.createdAt) {
                    Text(
                        text = stringResource(R.string.comments_edited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        if (canModify) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("comment_menu"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.post_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.comments_edit)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("comment_edit"),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.comments_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
