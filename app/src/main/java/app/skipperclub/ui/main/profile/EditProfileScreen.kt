package app.skipperclub.ui.main.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import java.nio.ByteBuffer
import kotlinx.coroutines.launch

/** Full-screen "Edit profile" view launched from the read-only profile screen. */
@Composable
fun EditProfileScreen(
    profile: UserProfile,
    onClose: () -> Unit,
    onSaved: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val controller = remember(scope, profile) {
        EditProfileController(
            source = profile,
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.profile_error_network)
    val errorAuth = stringResource(R.string.profile_error_auth)
    val errorValidation = stringResource(R.string.edit_profile_error_validation)
    val errorGeneric = stringResource(R.string.profile_error_generic)
    val avatarRejected = stringResource(R.string.edit_profile_avatar_rejected)

    fun errorMessage(error: Exception): String = when (error) {
        is ProfileError.Network -> errorNetwork
        is ProfileError.AuthenticationRequired -> errorAuth
        is ProfileError.Validation -> error.message ?: errorValidation
        else -> errorGeneric
    }

    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is EditProfileEvent.Saved -> onSaved(event.profile)
                is EditProfileEvent.SaveFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                EditProfileEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)

                EditProfileEvent.AvatarRejected ->
                    notificationHostState.show(avatarRejected, InAppNotificationType.Error)
            }
        }
    }

    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = readPickedAvatar(context, uri)
            if (picked == null) {
                controller.onAvatarRejected()
            } else {
                controller.onAvatarPicked(
                    fileName = picked.fileName,
                    mimeType = picked.mimeType,
                    bytes = picked.bytes,
                    width = picked.width,
                    height = picked.height,
                )
            }
        }
    }

    BackHandler(enabled = !state.isSaving, onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("edit_profile_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            EditProfileScreenContent(
                state = state,
                onClose = onClose,
                onPickAvatar = {
                    pickAvatar.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onName = controller::onName,
                onBio = controller::onBio,
                onCity = controller::onCity,
                onCountry = controller::onCountry,
                onSailingExperience = controller::onSailingExperience,
                onYearsOfExperience = controller::onYearsOfExperience,
                onSailingLicenses = controller::onSailingLicenses,
                onLanguagesSpoken = controller::onLanguagesSpoken,
                onPreferredVoyageStyles = controller::onPreferredVoyageStyles,
                onFacebookUrl = controller::onFacebookUrl,
                onInstagramUsername = controller::onInstagramUsername,
                onTiktokUsername = controller::onTiktokUsername,
                onWhatsappNumber = controller::onWhatsappNumber,
                onSave = controller::save,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
internal fun EditProfileScreenContent(
    state: EditProfileUiState,
    onClose: () -> Unit,
    onPickAvatar: () -> Unit,
    onName: (String) -> Unit,
    onBio: (String) -> Unit,
    onCity: (String) -> Unit,
    onCountry: (String) -> Unit,
    onSailingExperience: (SailingExperience?) -> Unit,
    onYearsOfExperience: (String) -> Unit,
    onSailingLicenses: (String) -> Unit,
    onLanguagesSpoken: (String) -> Unit,
    onPreferredVoyageStyles: (String) -> Unit,
    onFacebookUrl: (String) -> Unit,
    onInstagramUsername: (String) -> Unit,
    onTiktokUsername: (String) -> Unit,
    onWhatsappNumber: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                enabled = !state.isSaving,
                modifier = Modifier.testTag("edit_profile_close"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.profile_back),
                )
            }
            Text(
                text = stringResource(R.string.edit_profile_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier.testTag("edit_profile_save"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.edit_profile_save),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EditAvatar(
                name = state.name,
                currentAvatarUrl = state.currentAvatarUrl,
                pendingAvatar = state.pendingAvatar,
                enabled = !state.isSaving,
                onPick = onPickAvatar,
            )

            EditSection(title = stringResource(R.string.edit_profile_section_about)) {
                EditField(
                    value = state.name,
                    onValueChange = onName,
                    label = stringResource(R.string.edit_profile_label_name),
                    isError = state.nameInvalid,
                    supportingText = if (state.nameInvalid) {
                        stringResource(R.string.edit_profile_name_required)
                    } else {
                        null
                    },
                    enabled = !state.isSaving,
                    capitalization = KeyboardCapitalization.Words,
                    testTag = "edit_profile_name",
                )
                EditField(
                    value = state.bio,
                    onValueChange = onBio,
                    label = stringResource(R.string.edit_profile_label_bio),
                    enabled = !state.isSaving,
                    singleLine = false,
                    capitalization = KeyboardCapitalization.Sentences,
                    testTag = "edit_profile_bio",
                )
                EditField(
                    value = state.city,
                    onValueChange = onCity,
                    label = stringResource(R.string.edit_profile_label_city),
                    enabled = !state.isSaving,
                    capitalization = KeyboardCapitalization.Words,
                )
                EditField(
                    value = state.country,
                    onValueChange = onCountry,
                    label = stringResource(R.string.edit_profile_label_country),
                    supportingText = stringResource(R.string.edit_profile_hint_country),
                    enabled = !state.isSaving,
                    capitalization = KeyboardCapitalization.Characters,
                )
            }

            EditSection(title = stringResource(R.string.edit_profile_section_sailing)) {
                SailingExperienceField(
                    selected = state.sailingExperience,
                    enabled = !state.isSaving,
                    onSelected = onSailingExperience,
                )
                EditField(
                    value = state.yearsOfExperience,
                    onValueChange = onYearsOfExperience,
                    label = stringResource(R.string.edit_profile_label_years),
                    enabled = !state.isSaving,
                    keyboardType = KeyboardType.Number,
                )
                EditField(
                    value = state.sailingLicenses,
                    onValueChange = onSailingLicenses,
                    label = stringResource(R.string.edit_profile_label_licenses),
                    enabled = !state.isSaving,
                    capitalization = KeyboardCapitalization.Sentences,
                )
                EditField(
                    value = state.languagesSpoken,
                    onValueChange = onLanguagesSpoken,
                    label = stringResource(R.string.edit_profile_label_languages),
                    supportingText = stringResource(R.string.edit_profile_hint_languages),
                    enabled = !state.isSaving,
                )
                EditField(
                    value = state.preferredVoyageStyles,
                    onValueChange = onPreferredVoyageStyles,
                    label = stringResource(R.string.edit_profile_label_voyage_styles),
                    supportingText = stringResource(R.string.edit_profile_hint_voyage_styles),
                    enabled = !state.isSaving,
                )
            }

            EditSection(title = stringResource(R.string.edit_profile_section_social)) {
                EditField(
                    value = state.facebookUrl,
                    onValueChange = onFacebookUrl,
                    label = stringResource(R.string.profile_social_facebook),
                    supportingText = stringResource(R.string.edit_profile_hint_facebook),
                    enabled = !state.isSaving,
                    keyboardType = KeyboardType.Uri,
                )
                EditField(
                    value = state.instagramUsername,
                    onValueChange = onInstagramUsername,
                    label = stringResource(R.string.profile_social_instagram),
                    supportingText = stringResource(R.string.edit_profile_hint_handle),
                    enabled = !state.isSaving,
                )
                EditField(
                    value = state.tiktokUsername,
                    onValueChange = onTiktokUsername,
                    label = stringResource(R.string.profile_social_tiktok),
                    supportingText = stringResource(R.string.edit_profile_hint_handle),
                    enabled = !state.isSaving,
                )
                EditField(
                    value = state.whatsappNumber,
                    onValueChange = onWhatsappNumber,
                    label = stringResource(R.string.profile_social_whatsapp),
                    supportingText = stringResource(R.string.edit_profile_hint_whatsapp),
                    enabled = !state.isSaving,
                    keyboardType = KeyboardType.Phone,
                )
            }
        }
    }
}

@Composable
private fun EditAvatar(
    name: String,
    currentAvatarUrl: String?,
    pendingAvatar: PendingAvatar?,
    enabled: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(vertical = 16.dp)
            .size(112.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onPick)
            .testTag("edit_profile_avatar"),
        contentAlignment = Alignment.Center,
    ) {
        val model: Any? = pendingAvatar?.let { ByteBuffer.wrap(it.bytes) }
            ?: currentAvatarUrl?.takeIf { it.isNotEmpty() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .crossfade(enable = true)
                        .build(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name.ifBlank { "?" }.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Camera badge overlay.
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.edit_profile_change_avatar),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(7.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SailingExperienceField(
    selected: SailingExperience?,
    enabled: Boolean,
    onSelected: (SailingExperience?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { SailingExperience.entries.filter { it != SailingExperience.Unknown } }
    val noneLabel = stringResource(R.string.edit_profile_experience_none)
    val selectedLabel = selected?.labelRes()?.let { stringResource(it) } ?: noneLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.edit_profile_label_experience)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("edit_profile_experience"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { option.labelRes()?.let { Text(stringResource(it)) } },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EditSection(
    title: String,
    content: @Composable () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    testTag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it },
    )
}

// --- Previews ---

private val previewProfile = UserProfile(
    id = "preview-user",
    name = "Anna Nowak",
    email = "anna.nowak@example.com",
    role = "user",
    avatarUrl = null,
    bio = "Żeglarka od dziecka, zakochana w Bałtyku.",
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

@Composable
private fun PreviewContent(state: EditProfileUiState) {
    EditProfileScreenContent(
        state = state,
        onClose = {},
        onPickAvatar = {},
        onName = {},
        onBio = {},
        onCity = {},
        onCountry = {},
        onSailingExperience = {},
        onYearsOfExperience = {},
        onSailingLicenses = {},
        onLanguagesSpoken = {},
        onPreferredVoyageStyles = {},
        onFacebookUrl = {},
        onInstagramUsername = {},
        onTiktokUsername = {},
        onWhatsappNumber = {},
        onSave = {},
    )
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "en")
@Composable
private fun EditProfilePreview() {
    SkipperClubTheme { PreviewContent(EditProfileUiState.from(previewProfile)) }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun EditProfilePreviewDark() {
    SkipperClubTheme { PreviewContent(EditProfileUiState.from(previewProfile)) }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "pl")
@Composable
private fun EditProfilePreviewPl() {
    SkipperClubTheme {
        PreviewContent(
            EditProfileUiState.from(previewProfile).copy(name = "", nameInvalid = true, isSaving = true),
        )
    }
}
