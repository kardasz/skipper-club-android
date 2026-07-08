package app.skipperclub.ui.main.posts.wizard

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import app.skipperclub.data.FriendUser
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.SessionUser
import app.skipperclub.ui.main.posts.previewPosts
import app.skipperclub.ui.theme.SkipperClubTheme

private val previewUser = SessionUser(
    id = "preview-user",
    email = "anna.nowak@example.com",
    name = "Anna Nowak",
)

@Composable
private fun previewWizardState(populated: Boolean): PostWizardState {
    val scope = rememberCoroutineScope()
    return remember {
        PostWizardState(scope = scope, accessToken = { null }).apply {
            if (populated) {
                updateText(
                    "Zakończyliśmy weekendowy rejs po Zatoce! Wiatr 4°B, słońce " +
                        "i idealna załoga. Hel jak zawsze nie zawodzi ⛵",
                )
                selectLocation(
                    GeocodedLocation(
                        name = "Zatoka Pucka",
                        formattedAddress = "Zatoka Pucka, Polska",
                        coordinates = PostCoordinates(54.66, 18.48),
                    ),
                )
                addStop(
                    GeocodedLocation(
                        name = "Gdynia",
                        formattedAddress = "Gdynia, Polska",
                        coordinates = PostCoordinates(54.52, 18.55),
                    ),
                )
                addStop(
                    GeocodedLocation(
                        name = "Hel",
                        formattedAddress = "Hel, Polska",
                        coordinates = PostCoordinates(54.61, 18.80),
                    ),
                )
                updateDurationDays("2")
                updateLengthNm("42")
                updateRouteEnabled(true)
                taggedUsers.add(FriendUser(id = "u1", name = "Anna Nowak"))
                taggedUsers.add(FriendUser(id = "u2", name = "Marek Wilk"))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun PostWizardPreviewEmpty() {
    SkipperClubTheme {
        PostWizard(
            state = previewWizardState(populated = false),
            onClose = {},
            user = previewUser,
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostWizardPreviewFilledDark() {
    SkipperClubTheme {
        PostWizard(
            state = previewWizardState(populated = true),
            onClose = {},
            user = previewUser,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PostWizardPreviewAlertEditPl() {
    val scope = rememberCoroutineScope()
    val state = remember {
        PostWizardState(
            scope = scope,
            accessToken = { null },
            editingPost = previewPosts.first { it.content.alert != null },
        ).apply {
            // Busy state: alert badge + a visible validation error on location.
            clearLocation()
            publish()
        }
    }
    SkipperClubTheme {
        PostWizard(
            state = state,
            onClose = {},
            user = previewUser,
        )
    }
}
