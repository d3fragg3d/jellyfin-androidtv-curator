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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import kotlin.math.absoluteValue

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
				allUsers = fetched.sortedBy { it.name }
			}
		}

		JellyfinTheme {
			UserPickerScreen(
				users = allUsers,
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
						replace<UserLoginFragment>(
							R.id.content_view, null, bundleOf(
								UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
								UserLoginFragment.ARG_USERNAME to user.name,
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
	getImageUrl: (User) -> String?,
	onUserClick: (User) -> Unit,
	onManageClick: () -> Unit,
) {
	val firstCardFocusRequester = remember { FocusRequester() }

	// Request focus after composition, not during layout (LaunchedEffect is safe; onPlaced is not).
	// delay(50) gives Compose one frame to lay out the cards before requestFocus() runs.
	LaunchedEffect(users) {
		if (users.isNotEmpty()) {
			delay(50)
			runCatching { firstCardFocusRequester.requestFocus() }
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black),
		contentAlignment = Alignment.Center,
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Image(
				painter = painterResource(R.drawable.app_logo),
				contentDescription = null,
				modifier = Modifier.width(200.dp),
			)

			Spacer(Modifier.height(20.dp))

			Text(
				text = "Who's watching?",
				color = Color(0xFFCCCCCC),
				fontSize = 26.sp,
				fontWeight = FontWeight.Light,
			)

			Spacer(Modifier.height(40.dp))

			if (users.isEmpty()) {
				CircularProgressIndicator(
					color = Color.White,
					modifier = Modifier.size(48.dp),
				)
			} else {
				Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
					// "Manage server" card — always first in the row
					ManageServerCard(
						onClick = onManageClick,
						modifier = Modifier.width(110.dp),
					)
					users.forEachIndexed { index, user ->
						UserPickerCard(
							name = user.name,
							imageUrl = getImageUrl(user),
							onClick = { onUserClick(user) },
							modifier = Modifier
								.width(110.dp)
								.then(if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier),
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserPickerCard(
	name: String,
	imageUrl: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "scale")
	val borderColor = if (focused) Color(0xFF00A4DC) else Color(0xFF444444)

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier
			.scale(scale)
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
	) {
		Box(
			modifier = Modifier
				.aspectRatio(1f)
				.clip(CircleShape)
				.border(3.dp, borderColor, CircleShape)
		) {
			// Initials always rendered as background — shows through if image fails to load
			UserInitialsAvatar(name)
			if (imageUrl != null) {
				AsyncImage(
					modifier = Modifier.fillMaxSize(),
					scaleType = ImageView.ScaleType.CENTER_CROP,
					url = imageUrl,
				)
			}
		}

		Spacer(Modifier.height(8.dp))

		Text(
			text = name,
			color = if (focused) Color.White else Color(0xFFAAAAAA),
			fontSize = 16.sp,
			maxLines = 1,
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageServerCard(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "scale")
	val borderColor = if (focused) Color(0xFF00A4DC) else Color(0xFF444444)

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = modifier
			.scale(scale)
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
	) {
		Box(
			modifier = Modifier
				.aspectRatio(1f)
				.clip(CircleShape)
				.border(3.dp, borderColor, CircleShape)
				.background(Color(0xFF222222)),
			contentAlignment = Alignment.Center,
		) {
			Image(
				painter = painterResource(R.drawable.ic_users),
				contentDescription = null,
				modifier = Modifier.size(40.dp),
			)
		}

		Spacer(Modifier.height(8.dp))

		Text(
			text = "Manage server",
			color = if (focused) Color.White else Color(0xFFAAAAAA),
			fontSize = 14.sp,
			maxLines = 1,
		)
	}
}

private val avatarColors = listOf(
	Color(0xFF1565C0),
	Color(0xFF2E7D32),
	Color(0xFF6A1B9A),
	Color(0xFFC62828),
	Color(0xFFE65100),
	Color(0xFF00838F),
)

@Composable
private fun UserInitialsAvatar(name: String) {
	val initial = name.firstOrNull()?.uppercase() ?: "?"
	val color = avatarColors[name.hashCode().absoluteValue % avatarColors.size]

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(color),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = initial,
			color = Color.White,
			fontSize = 52.sp,
			fontWeight = FontWeight.Bold,
		)
	}
}

