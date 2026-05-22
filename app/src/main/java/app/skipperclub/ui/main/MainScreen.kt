package app.skipperclub.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var current by rememberSaveable { mutableStateOf(MainDestination.MAP) }
    MainScreenContent(
        current = current,
        onSelect = { current = it },
        modifier = modifier,
    )
}

@Composable
private fun MainScreenContent(
    current: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            SkipperBottomBar(selected = current, onSelect = onSelect)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (current) {
                MainDestination.POSTS -> PostsScreen()
                MainDestination.CRUISES -> CruisesScreen()
                MainDestination.MAP -> MapScreen()
                MainDestination.MESSAGES -> MessagesScreen()
                MainDestination.MENU -> MenuScreen()
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun MainScreenPreviewMap() {
    SkipperClubTheme {
        MainScreenContent(current = MainDestination.MAP, onSelect = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun MainScreenPreviewPosts() {
    SkipperClubTheme {
        MainScreenContent(current = MainDestination.POSTS, onSelect = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun MainScreenPreviewPl() {
    SkipperClubTheme {
        MainScreenContent(current = MainDestination.MESSAGES, onSelect = {})
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MainScreenPreviewDark() {
    SkipperClubTheme {
        MainScreenContent(current = MainDestination.MAP, onSelect = {})
    }
}
