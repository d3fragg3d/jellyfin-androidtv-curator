package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import java.util.UUID
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PublicUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

@Serializable
private data class CuratorUserInfo(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
)

class CuratorUserPickerFragment : Fragment() {

	companion object {
		const val ARG_SERVER_ID = "server_id"
		private val json = Json { ignoreUnknownKeys = true }
	}

	private val startupViewModel: StartupViewModel by activityViewModel()
	private val sessionRepository: SessionRepository by inject()
	private val api: ApiClient by inject()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val server = remember {
			arguments?.getString(ARG_SERVER_ID)
				?.toUUIDOrNull()
				?.let { startupViewModel.getServer(it) }
		}

		var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }

		LaunchedEffect(server) {
			if (server != null) {
				val fetched = withContext(Dispatchers.IO) {
					runCatching {
						val resp = api.request(
							method = HttpMethod.GET,
							pathTemplate = "/Curator/users",
						)
						json.decodeFromString<List<CuratorUserInfo>>(resp.body.decodeToString())
							.mapNotNull { info ->
								val h = info.id.replace("-", "")
								val id = runCatching {
									val formatted = "${h.substring(0,8)}-${h.substring(8,12)}-${h.substring(12,16)}-${h.substring(16,20)}-${h.substring(20)}"
									formatted.toUUIDOrNull()
								}.getOrNull() ?: return@mapNotNull null
								PublicUser(
									id = id,
									name = info.name,
									serverId = server.id,
									accessToken = null,
									imageTag = null,
								)
							}
					}.getOrDefault(emptyList())
				}
				allUsers = fetched.sortedWith(compareBy({ curatorUserProfile(it.name).order }, { it.name.lowercase() }))
			}
		}

		JellyfinTheme {
			UserPickerScreen(
				users = allUsers,
				currentUserId = sessionRepository.currentSession.value?.userId,
				getImageUrl = { user ->
					api.imageApi.getUserImageUrl(userId = user.id, tag = null)
				},
				onUserClick = { user -> if (server != null) authenticateUser(server, user) },
				onManageClick = { if (server != null) (requireActivity() as StartupActivity).showServerManagement(server.id) },
			)
		}
	}

	private fun authenticateUser(server: Server, user: User) {
		// switchCurrentSession returns false when the session user hasn't changed, which
		// authenticateToken treats as failure → RequireSignInState. Skip re-auth for same user.
		val current = sessionRepository.currentSession.value
		if (current?.userId == user.id && current.serverId == server.id) {
			(requireActivity() as StartupActivity).proceedToHome()
			return
		}

		startupViewModel.authenticate(server, user)
			.onEach { state ->
				when (state) {
					AuthenticatingState -> Unit
					AuthenticatedState -> (requireActivity() as StartupActivity).proceedToHome()
					RequireSignInState -> requireActivity().supportFragmentManager.commit {
						replace<CuratorUserLoginFragment>(
							R.id.content_view, null, bundleOf(
								CuratorUserLoginFragment.ARG_SERVER_ID to server.id.toString(),
								CuratorUserLoginFragment.ARG_USERNAME to user.name,
							)
						)
						addToBackStack(null)
					}
					ServerUnavailableState,
					is ApiClientErrorLoginState,
					is ServerVersionNotSupported -> Toast.makeText(
						requireContext(),
						R.string.server_connection_failed,
						Toast.LENGTH_LONG,
					).show()
				}
			}.launchIn(lifecycleScope)
	}
}


@Composable
private fun UserPickerScreen(
	users: List<User>,
	currentUserId: UUID?,
	getImageUrl: (User) -> String?,
	onUserClick: (User) -> Unit,
	onManageClick: () -> Unit,
) {
	val initialFocusRequester = remember { FocusRequester() }
	val initialFocusIndex = users.indexOfFirst { it.id == currentUserId }.coerceAtLeast(0)

	// Request focus after composition, not during layout (LaunchedEffect is safe; onPlaced is not).
	// delay(50) gives Compose one frame to lay out the cards before requestFocus() runs.
	LaunchedEffect(users) {
		if (users.isNotEmpty()) {
			delay(50)
			runCatching { initialFocusRequester.requestFocus() }
		}
	}

	CuratorStartupBackground {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.fillMaxSize(),
		) {
			CuratorScreenTitle("Who's watching?")

			Spacer(Modifier.height(48.dp))

			if (users.isEmpty()) {
				Box(Modifier.height(170.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator(
						color = Color.White,
						modifier = Modifier.size(48.dp),
					)
				}
			} else {
				Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
					users.forEachIndexed { index, user ->
						val profile = curatorUserProfile(user.name)
						UserPickerCard(
							profile = profile,
							imageUrl = if (profile.avatar == null) getImageUrl(user) else null,
							onClick = { onUserClick(user) },
							modifier = if (index == initialFocusIndex) Modifier.focusRequester(initialFocusRequester) else Modifier,
						)
					}
				}
			}

			Spacer(Modifier.height(56.dp))

			CuratorPillButton(text = "Manage server", icon = R.drawable.ic_settings, onClick = onManageClick)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserPickerCard(
	profile: CuratorUserProfile,
	imageUrl: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "scale")
	val dim by animateFloatAsState(if (focused) 1f else 0.7f, label = "dim")
	val glow by animateFloatAsState(if (focused) 1f else 0f, label = "glow")

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier
			.width(112.dp)
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
	) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.size(112.dp)
				.scale(scale)
				.drawBehind {
					// Soft halo in the profile's own colour behind the focused avatar
					drawCircle(
						brush = Brush.radialGradient(
							colors = listOf(profile.gradient.last().copy(alpha = 0.55f * glow), Color.Transparent),
							radius = size.minDimension * 0.75f,
						),
						radius = size.minDimension * 0.75f,
					)
				},
		) {
			CuratorAvatar(
				profile = profile,
				imageUrl = imageUrl,
				modifier = Modifier
					.fillMaxSize()
					.border(3.dp, Color.White.copy(alpha = glow), CircleShape)
					.padding(6.dp)
					.alpha(dim),
			)
		}

		Spacer(Modifier.height(18.dp))

		Text(
			text = profile.displayName,
			color = if (focused) Color.White else CuratorMutedText,
			fontSize = 18.sp,
			fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
			maxLines = 1,
		)
	}
}
