package app.skipperclub.ui.main.cruises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.PostMedia
import app.skipperclub.ui.main.posts.VideoPlayerDialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
internal fun CruiseMediaCover(
    media: List<PostMedia>,
    modifier: Modifier = Modifier,
) {
    val cover = media.firstOrNull() ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("cruise_media_cover"),
    ) {
        CruiseMediaImage(media = cover, modifier = Modifier.fillMaxSize())
        if (cover.isVideo) {
            CruiseVideoOverlay(modifier = Modifier.align(Alignment.Center))
        }
        if (media.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ) {
                Text(
                    text = stringResource(R.string.cruise_media_count, media.size),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun CruiseMediaGallery(
    media: List<PostMedia>,
    modifier: Modifier = Modifier,
) {
    if (media.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { media.size })
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("cruise_media_gallery"),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = media[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (item.isVideo) {
                            Modifier
                                .clickable { playingVideoUrl = item.url }
                                .testTag("cruise_video_play")
                        } else {
                            Modifier
                        },
                    ),
            ) {
                CruiseMediaImage(media = item, modifier = Modifier.fillMaxSize())
                if (item.isVideo) {
                    CruiseVideoOverlay(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        if (media.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(media.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.56f)),
                    )
                }
            }
        }
    }

    playingVideoUrl?.let { url ->
        VideoPlayerDialog(url = url, onDismiss = { playingVideoUrl = null })
    }
}

@Composable
private fun CruiseMediaImage(
    media: PostMedia,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(media.url)
            .crossfade(enable = true)
            .build(),
        contentDescription = stringResource(R.string.cruise_media_content_description),
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun CruiseVideoOverlay(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.PlayCircle,
        contentDescription = stringResource(R.string.post_video_play),
        tint = Color.White.copy(alpha = 0.92f),
        modifier = modifier.size(56.dp),
    )
}
