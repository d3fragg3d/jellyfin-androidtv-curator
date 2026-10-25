package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.startup.fragment.CuratorAvatar
import org.jellyfin.androidtv.ui.startup.fragment.curatorUserProfile
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

enum class MainToolbarActiveButton {
	User,
	Home,
	Search,

	None,
}

@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	MainToolbar(
		userImage = userImage,
		userName = currentUser?.name,
		activeButton = activeButton,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	userName: String? = null,
	activeButton: MainToolbarActiveButton,
) {
	val focusRequester = remember { FocusRequester() }
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val activity = LocalActivity.current
	val activeButtonColors = ButtonDefaults.colors(
		containerColor = JellyfinTheme.colorScheme.buttonActive,
		contentColor = JellyfinTheme.colorScheme.onButtonActive,
	)

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		start = {
			ToolbarButtons {
				val userImagePainter = rememberAsyncImagePainter(userImage)
				val userImageState by userImagePainter.state.collectAsState()
				val userImageVisible = userImageState is AsyncImagePainter.State.Success
				// Curator: family profiles show their Curator avatar instead of the Jellyfin image
				val curatorProfile = remember(userName) { userName?.let(::curatorUserProfile)?.takeIf { it.avatar != null } }

				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.User) {
							mediaManager.clearAudioQueue()
							// Curator: keep the session so StartupActivity shows the "Who's watching?" picker
							// (it needs an authenticated client) instead of the server/sign-in screen

							// Open login activity
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.User) activeButtonColors else ButtonDefaults.colors(),
					contentPadding = if (userImageVisible || curatorProfile != null) PaddingValues(3.dp) else IconButtonDefaults.ContentPadding,
				) {
					if (curatorProfile != null) {
						CuratorAvatar(
							profile = curatorProfile,
							imageUrl = null,
							modifier = Modifier.aspectRatio(1f),
							initialFontSize = 14,
						)
					} else if (!userImageVisible) {
						Icon(
							imageVector = ImageVector.vectorResource(R.drawable.ic_user),
							contentDescription = stringResource(R.string.lbl_switch_user),
						)
					} else {
						Image(
							painter = userImagePainter,
							contentDescription = stringResource(R.string.lbl_switch_user),
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.aspectRatio(1f)
								.clip(IconButtonDefaults.Shape)
						)
					}
				}

				NowPlayingComposable(
					onFocusableChange = {},
				)
			}
		},
		center = {
			ToolbarButtons(
				modifier = Modifier
					.focusRequester(focusRequester)
			) {
				ProvideTextStyle(JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold)) {
					Button(
						onClick = {
							if (activeButton != MainToolbarActiveButton.Home) {
								navigationRepository.navigate(
									Destinations.home,
									replace = true,
								)
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else ButtonDefaults.colors(),
						content = { Text(stringResource(R.string.lbl_home)) }
					)
					Button(
						onClick = {
							if (activeButton != MainToolbarActiveButton.Search) {
								navigationRepository.navigate(Destinations.search())
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else ButtonDefaults.colors(),
						content = { Text(stringResource(R.string.lbl_search)) }
					)
				}
			}
		},
		end = {
			ToolbarButtons {
				IconButton(
					onClick = {
						activity?.startActivity(ActivityDestinations.userPreferences(activity))
					},
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				ToolbarClock()
			}
		}
	)
}
