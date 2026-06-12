package app.skipperclub.ui.main.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipperclub.R
import app.skipperclub.data.ReactionType

/**
 * Bottom sheet with the 20 curated reactions in two sections (standard +
 * sailing). The user's current reactions are highlighted; tapping toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionPickerSheet(
    userReactions: Set<ReactionType>,
    onSelect: (ReactionType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ReactionPickerContent(
            userReactions = userReactions,
            onSelect = onSelect,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

@Composable
internal fun ReactionPickerContent(
    userReactions: Set<ReactionType>,
    onSelect: (ReactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.reaction_picker_title),
            style = MaterialTheme.typography.titleMedium,
        )
        ReactionSection(
            title = stringResource(R.string.reaction_section_standard),
            reactions = ReactionType.entries.filterNot { it.isSailing },
            userReactions = userReactions,
            onSelect = onSelect,
        )
        ReactionSection(
            title = stringResource(R.string.reaction_section_sailing),
            reactions = ReactionType.entries.filter { it.isSailing },
            userReactions = userReactions,
            onSelect = onSelect,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionSection(
    title: String,
    reactions: List<ReactionType>,
    userReactions: Set<ReactionType>,
    onSelect: (ReactionType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reactions.forEach { reaction ->
                val selected = reaction in userReactions
                Surface(
                    onClick = { onSelect(reaction) },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reaction_${reaction.wireValue}"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = reaction.emoji, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
