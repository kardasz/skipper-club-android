package app.skipperclub.ui.main.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.ProfileError
import app.skipperclub.data.SailingExperience
import app.skipperclub.data.SessionStore
import app.skipperclub.data.UserProfile
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Full-screen, read-only "My profile" view launched from the main menu. */
@Composable
fun ProfileScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        ProfileController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    var showEdit by rememberSaveable { mutableStateOf(false) }

    val errorNetwork = stringResource(R.string.profile_error_network)
    val errorAuth = stringResource(R.string.profile_error_auth)
    val errorGeneric = stringResource(R.string.profile_error_generic)

    fun errorMessage(error: Exception): String = when (error) {
        is ProfileError.Network -> errorNetwork
        is ProfileError.AuthenticationRequired -> errorAuth
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is ProfileEvent.LoadFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                ProfileEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("profile_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProfileScreenContent(
                state = state,
                title = stringResource(R.string.profile_title),
                onClose = onClose,
                onRefresh = controller::refresh,
                onRetry = controller::refresh,
                onEdit = state.profile?.let { { showEdit = true } },
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    val editableProfile = state.profile
    if (showEdit && editableProfile != null) {
        Dialog(
            onDismissRequest = { showEdit = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            EditProfileScreen(
                profile = editableProfile,
                onClose = { showEdit = false },
                onSaved = { updated ->
                    controller.applyProfile(updated)
                    showEdit = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreenContent(
    state: ProfileUiState,
    title: String,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("profile_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.profile_back),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("profile_edit")) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_profile_title),
                    )
                }
            }
            trailingContent()
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.profile == null && state.loadFailed -> ProfileMessage(
                    title = stringResource(R.string.profile_load_failed),
                    actionLabel = stringResource(R.string.profile_retry),
                    onAction = onRetry,
                )

                state.profile != null -> ProfileDetails(profile = state.profile)
            }
        }
    }
}

@Composable
private fun ProfileDetails(profile: UserProfile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
            .testTag("profile_details"),
    ) {
        ProfileHeader(profile = profile)
        ProfileStats(profile = profile)

        profile.bio?.let { bio ->
            ProfileSection(title = stringResource(R.string.profile_section_about)) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        val experienceLabel = profile.sailingExperience?.takeIf { it != SailingExperience.Unknown }
            ?.labelRes()?.let { stringResource(it) }
        val years = profile.yearsOfExperience
        val languages = profile.languagesSpoken.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { formatLanguage(it) }
        val voyageStyles = profile.preferredVoyageStyles.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { formatVoyageStyle(it) }
        val hasSailing = experienceLabel != null || years != null ||
            profile.sailingLicenses != null || languages != null || voyageStyles != null

        if (hasSailing) {
            ProfileSection(title = stringResource(R.string.profile_section_sailing)) {
                experienceLabel?.let {
                    ProfileInfoRow(Icons.Filled.Sailing, stringResource(R.string.profile_label_experience), it)
                }
                years?.let {
                    ProfileInfoRow(
                        Icons.Filled.CalendarMonth,
                        stringResource(R.string.profile_label_years),
                        it.toString(),
                    )
                }
                profile.sailingLicenses?.let {
                    ProfileInfoRow(Icons.Filled.WorkspacePremium, stringResource(R.string.profile_label_licenses), it)
                }
                languages?.let {
                    ProfileInfoRow(Icons.Filled.Language, stringResource(R.string.profile_label_languages), it)
                }
                voyageStyles?.let {
                    ProfileInfoRow(Icons.Filled.Sell, stringResource(R.string.profile_label_voyage_styles), it)
                }
            }
        }

        val social = buildList {
            profile.facebookUrl?.let { add(stringResource(R.string.profile_social_facebook) to it) }
            profile.instagramUsername?.let { add(stringResource(R.string.profile_social_instagram) to it) }
            profile.tiktokUsername?.let { add(stringResource(R.string.profile_social_tiktok) to it) }
            profile.whatsappNumber?.let { add(stringResource(R.string.profile_social_whatsapp) to it) }
        }
        if (social.isNotEmpty()) {
            ProfileSection(title = stringResource(R.string.profile_section_social)) {
                social.forEach { (label, value) ->
                    ProfileInfoRow(icon = null, label = label, value = value)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(
            name = profile.name,
            avatarUrl = profile.avatarUrl,
            modifier = Modifier.size(104.dp),
        )
        Text(
            text = profile.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (profile.email.isNotBlank()) {
            Text(
                text = profile.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (profile.isAdmin) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_role_admin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        formatLocation(profile.city, profile.country)?.let { location ->
            ProfileCaption(icon = Icons.Filled.LocationOn, text = location)
        }
        formatMemberSince(profile.createdAt)?.let { memberSince ->
            ProfileCaption(
                icon = Icons.Filled.CalendarMonth,
                text = stringResource(R.string.profile_member_since, memberSince),
            )
        }
    }
}

@Composable
private fun ProfileCaption(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ProfileStats(profile: UserProfile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ProfileStat(value = profile.cruisesCount, label = stringResource(R.string.profile_stat_cruises))
        ProfileStat(value = profile.friendsCount, label = stringResource(R.string.profile_stat_friends))
        ProfileStat(value = profile.postsCount, label = stringResource(R.string.profile_stat_posts))
    }
}

@Composable
private fun ProfileStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
    )
    content()
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector?,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
        }
        Column(modifier = Modifier.padding(start = if (icon != null) 12.dp else 0.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(enable = true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.profileInitials(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun String.profileInitials(): String =
    trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

@Composable
private fun ProfileMessage(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

// --- Previews ---

private val previewProfile = UserProfile(
    id = "preview-user",
    name = "Anna Nowak",
    email = "anna.nowak@example.com",
    role = "user",
    avatarUrl = null,
    bio = "Żeglarka od dziecka, zakochana w Bałtyku. Szukam załogi na weekendowe rejsy.",
    city = "Gdańsk",
    country = "PL",
    sailingExperience = SailingExperience.Advanced,
    yearsOfExperience = 10,
    sailingLicenses = "RYA Yachtmaster Offshore",
    languagesSpoken = listOf("pl", "en", "de"),
    preferredVoyageStyles = listOf("coastal", "racing"),
    facebookUrl = "https://facebook.com/anna.skipper",
    instagramUsername = "@anna_skipper",
    tiktokUsername = null,
    whatsappNumber = "+48123456789",
    cruisesCount = 15,
    friendsCount = 42,
    postsCount = 28,
    createdAt = "2025-01-15T10:00:00Z",
)

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "en")
@Composable
private fun ProfilePreview() {
    SkipperClubTheme {
        ProfileScreenContent(
            state = ProfileUiState(profile = previewProfile, hasLoadedOnce = true),
            title = "Profile",
            onClose = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfilePreviewDark() {
    SkipperClubTheme {
        ProfileScreenContent(
            state = ProfileUiState(profile = previewProfile, hasLoadedOnce = true),
            title = "Profile",
            onClose = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "pl")
@Composable
private fun ProfilePreviewPl() {
    SkipperClubTheme {
        ProfileScreenContent(
            state = ProfileUiState(
                profile = previewProfile.copy(role = "admin"),
                hasLoadedOnce = true,
            ),
            title = "Profile",
            onClose = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "pl")
@Composable
private fun ProfileFailedPreviewPl() {
    SkipperClubTheme {
        ProfileScreenContent(
            state = ProfileUiState(loadFailed = true, hasLoadedOnce = true),
            title = "Profile",
            onClose = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}
