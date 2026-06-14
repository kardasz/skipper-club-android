package app.skipperclub.ui.main.messages

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import app.skipperclub.R
import app.skipperclub.data.Chat
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatUser
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessagesScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatListRendersChatsAndHeaderActions() {
        val opened = mutableListOf<String>()
        val searches = mutableListOf<String>()
        var filtersClicked = false

        compose.setContent {
            SkipperClubTheme {
                ChatListScreenContent(
                    state = ChatListUiState(
                        chats = listOf(oneToOneChat, groupChat),
                        hasLoadedOnce = true,
                    ),
                    nowMillis = NOW,
                    currentUserId = "me",
                    onSearchChange = { searches += it },
                    onOpenFilters = { filtersClicked = true },
                    onOpenChat = { opened += it.id },
                    onNewChat = {},
                    onMarkRead = {},
                    onDeleteRequest = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Jan Kowalski").assertExists()
        compose.onNodeWithText("Summer Crew").assertExists()
        // Group preview is prefixed with the sender's name.
        compose.onNodeWithText("Anna Nowak: Ahoy crew!").assertExists()
        // Unread badge for the group chat.
        compose.onNodeWithText("3").assertExists()

        // The filter icon opens the filter sheet rather than inline chips.
        compose.onNodeWithTag("messages_filters").assertExists().performClick()
        assertEquals(true, filtersClicked)

        compose.onNodeWithTag("chat_item_c1").performClick()
        assertEquals(listOf("c1"), opened)
    }

    @Test
    fun searchIconOpensSearchBarSeparateFromFilters() {
        val searches = mutableListOf<String>()
        var filtersClicked = false

        compose.setContent {
            SkipperClubTheme {
                ChatListScreenContent(
                    state = ChatListUiState(
                        chats = listOf(oneToOneChat),
                        hasLoadedOnce = true,
                    ),
                    nowMillis = NOW,
                    currentUserId = "me",
                    onSearchChange = { searches += it },
                    onOpenFilters = { filtersClicked = true },
                    onOpenChat = {},
                    onNewChat = {},
                    onMarkRead = {},
                    onDeleteRequest = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        // Tapping the search icon reveals a dedicated search field, not the filter sheet.
        compose.onNodeWithTag("messages_search").performClick()
        compose.onNodeWithTag("messages_search_field").assertExists()
        compose.onNodeWithTag("messages_search_field").performTextInput("jan")
        assertEquals(listOf("jan"), searches)
        assertEquals(false, filtersClicked)

        // Back closes the search bar and restores the header.
        compose.onNodeWithTag("messages_search_back").performClick()
        compose.onNodeWithTag("messages_search_field").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.nav_messages)).assertExists()
    }

    @Test
    fun activeSearchShowsChipThatReopensSearch() {
        compose.setContent {
            SkipperClubTheme {
                ChatListScreenContent(
                    state = ChatListUiState(
                        chats = listOf(oneToOneChat),
                        searchQuery = "jan",
                        hasLoadedOnce = true,
                    ),
                    nowMillis = NOW,
                    currentUserId = "me",
                    onSearchChange = {},
                    onOpenFilters = {},
                    onOpenChat = {},
                    onNewChat = {},
                    onMarkRead = {},
                    onDeleteRequest = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithTag("messages_search_chip").assertExists().performClick()
        compose.onNodeWithTag("messages_search_field").assertExists()
    }

    @Test
    fun filterSheetSelectsTypeAndApplies() {
        val applied = mutableListOf<ChatType?>()

        compose.setContent {
            SkipperClubTheme {
                MessageFilterSheetContent(
                    selected = null,
                    onApply = { applied += it },
                )
            }
        }

        compose.onNodeWithTag("message_filter_type_group").performClick()
        compose.onNodeWithTag("message_filter_apply").performClick()
        assertEquals(listOf<ChatType?>(ChatType.Group), applied)
    }

    @Test
    fun chatListLongPressOpensMenuAndRequestsDelete() {
        val deleteRequests = mutableListOf<String>()

        compose.setContent {
            SkipperClubTheme {
                ChatListScreenContent(
                    state = ChatListUiState(chats = listOf(oneToOneChat), hasLoadedOnce = true),
                    nowMillis = NOW,
                    currentUserId = "me",
                    onSearchChange = {},
                    onOpenFilters = {},
                    onOpenChat = {},
                    onNewChat = {},
                    onMarkRead = {},
                    onDeleteRequest = { deleteRequests += it.id },
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithTag("chat_item_c1").performTouchInput { longClick() }
        compose.onNodeWithTag("chat_item_delete_c1").performClick()

        assertEquals(listOf("c1"), deleteRequests)
    }

    @Test
    fun chatListShowsEmptyState() {
        compose.setContent {
            SkipperClubTheme {
                ChatListScreenContent(
                    state = ChatListUiState(hasLoadedOnce = true),
                    nowMillis = NOW,
                    currentUserId = "me",
                    onSearchChange = {},
                    onOpenFilters = {},
                    onOpenChat = {},
                    onNewChat = {},
                    onMarkRead = {},
                    onDeleteRequest = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.messages_empty_title)).assertExists()
    }

    @Test
    fun conversationRendersMessagesAndSendsInput() {
        val sent = mutableListOf<Unit>()
        var input = ""

        compose.setContent {
            SkipperClubTheme {
                ChatConversationScreenContent(
                    state = ChatConversationUiState(
                        chat = groupChat,
                        messages = listOf(
                            message("m1", "What time should I arrive?", anna),
                            message("m2", "Around 9:00.", me),
                        ),
                        hasLoadedOnce = true,
                    ),
                    currentUserId = "me",
                    inputText = input,
                    nowMillis = NOW,
                    onInputChange = { input = it },
                    onSend = { sent += Unit },
                    onLoadMore = {},
                    onRetry = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("What time should I arrive?").assertExists()
        compose.onNodeWithText("Around 9:00.").assertExists()
        compose.onNodeWithText("Summer Crew").assertExists()

        // Empty input keeps send disabled.
        compose.onNodeWithTag("conversation_send").assertIsNotEnabled()

        compose.onNodeWithTag("conversation_input").performTextInput("Ahoy!")
        assertEquals("Ahoy!", input)
    }

    @Test
    fun conversationSendButtonEnabledWithText() {
        var sendCount = 0

        compose.setContent {
            SkipperClubTheme {
                ChatConversationScreenContent(
                    state = ChatConversationUiState(chat = oneToOneChat, hasLoadedOnce = true),
                    currentUserId = "me",
                    inputText = "Ahoy!",
                    nowMillis = NOW,
                    onInputChange = {},
                    onSend = { sendCount++ },
                    onLoadMore = {},
                    onRetry = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("conversation_send").assertIsEnabled().performClick()
        assertEquals(1, sendCount)
    }

    @Test
    fun newChatTogglesUsersAndRequiresGroupName() {
        compose.setContent {
            SkipperClubTheme {
                val state = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        NewChatUiState(results = listOf(jan, anna), hasSearchedOnce = true),
                    )
                }
                NewChatScreenContent(
                    state = state.value,
                    onSearchChange = {},
                    onToggleUser = { user ->
                        state.value = state.value.copy(
                            selected = if (state.value.selected.any { it.id == user.id }) {
                                state.value.selected.filterNot { it.id == user.id }
                            } else {
                                state.value.selected + user
                            },
                        )
                    },
                    onGroupNameChange = { state.value = state.value.copy(groupName = it) },
                    onCreate = {},
                    onClose = {},
                )
            }
        }

        // Nothing selected: create disabled, no group name field.
        compose.onNodeWithTag("new_chat_create").assertIsNotEnabled()
        compose.onNodeWithTag("new_chat_group_name").assertDoesNotExist()

        compose.onNodeWithTag("new_chat_user_u1").performClick()
        compose.onNodeWithTag("new_chat_create").assertIsEnabled()

        compose.onNodeWithTag("new_chat_user_u2").performClick()
        // Two selected: group, name required.
        compose.onNodeWithTag("new_chat_create").assertIsNotEnabled()
        compose.onNodeWithTag("new_chat_group_name").performTextInput("Crew")
        compose.onNodeWithTag("new_chat_create").assertIsEnabled()
    }

    private fun text(id: Int): String = compose.activity.getString(id)

    private companion object {
        const val NOW = 1_775_000_000_000

        val me = ChatUser("me", "Current User")
        val jan = ChatUser("u1", "Jan Kowalski")
        val anna = ChatUser("u2", "Anna Nowak")

        fun message(id: String, textValue: String, user: ChatUser) = ChatMessage(
            id = id,
            chatId = "c1",
            text = textValue,
            read = true,
            user = user,
            createdAt = "2026-06-12T10:00:00Z",
            updatedAt = "2026-06-12T10:00:00Z",
        )

        val oneToOneChat = Chat(
            id = "c1",
            type = ChatType.OneToOne,
            participants = listOf(me, jan),
            lastMessage = message("lm1", "See you!", jan),
            updatedAt = "2026-06-12T10:00:00Z",
        )

        val groupChat = Chat(
            id = "c2",
            type = ChatType.Group,
            name = "Summer Crew",
            participants = listOf(me, jan, anna),
            lastMessage = message("lm2", "Ahoy crew!", anna),
            unreadCount = 3,
            updatedAt = "2026-06-12T10:00:00Z",
        )
    }
}
