package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.ui.preference.screen.AuthPreferencesScreen
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Curator replacement for the upstream ServerFragment, reached from "Manage server" on the
 * "Who's watching?" screen. Users are picked on that screen, so this one only holds server-level actions.
 */
class CuratorServerManagementFragment : Fragment() {

	companion object {
		const val ARG_SERVER_ID = "server_id"
	}

	private val startupViewModel: StartupViewModel by activityViewModel()

	private val server: Server?
		get() = arguments?.getString(ARG_SERVER_ID)?.toUUIDOrNull()?.let(startupViewModel::getServer)

	private val startupActivity get() = requireActivity() as StartupActivity

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		requireActivity().onBackPressedDispatcher.addCallback(this) { backToUserPicker() }
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val server = remember { server }

		JellyfinTheme {
			ServerManagementScreen(
				server = server,
				warning = server?.let(::serverWarning),
				onBack = ::backToUserPicker,
				onSignIn = { if (server != null) signIn(server) },
				onChangeServer = ::changeServer,
				onSettings = ::openSettings,
				onHelp = ::openHelp,
			)
		}
	}

	private fun backToUserPicker() {
		val server = server
		if (server != null) startupActivity.showUserPicker(server)
		else changeServer()
	}

	private fun signIn(server: Server) = parentFragmentManager.commit {
		replace<CuratorUserLoginFragment>(
			R.id.content_view, null, bundleOf(
				CuratorUserLoginFragment.ARG_SERVER_ID to server.id.toString(),
				CuratorUserLoginFragment.ARG_USERNAME to null,
			)
		)
		addToBackStack(null)
	}

	private fun changeServer() = parentFragmentManager.commit {
		replace<StartupToolbarFragment>(R.id.content_view)
		add<SelectServerFragment>(R.id.content_view)
		addToBackStack(null)
	}

	private fun openSettings() {
		val intent = Intent(requireContext(), PreferencesActivity::class.java)
		intent.putExtra(PreferencesActivity.EXTRA_SCREEN, AuthPreferencesScreen::class.qualifiedName)
		intent.putExtra(
			PreferencesActivity.EXTRA_SCREEN_ARGS, bundleOf(
				AuthPreferencesScreen.ARG_SHOW_ABOUT to true
			)
		)
		startActivity(intent)
	}

	private fun openHelp() = parentFragmentManager.commit {
		replace<ConnectHelpAlertFragment>(R.id.content_view)
		addToBackStack(null)
	}

	private fun serverWarning(server: Server): String? = when {
		!server.versionSupported -> getString(
			R.string.server_unsupported_notification,
			server.version,
			ServerRepository.recommendedServerVersion.toString(),
		)

		!server.setupCompleted -> getString(R.string.server_setup_incomplete)
		else -> null
	}
}

@Composable
private fun ServerManagementScreen(
	server: Server?,
	warning: String?,
	onBack: () -> Unit,
	onSignIn: () -> Unit,
	onChangeServer: () -> Unit,
	onSettings: () -> Unit,
	onHelp: () -> Unit,
) {
	val backFocusRequester = remember { FocusRequester() }

	// delay(50) gives Compose one frame to lay out the buttons before requestFocus() runs
	LaunchedEffect(Unit) {
		delay(50)
		runCatching { backFocusRequester.requestFocus() }
	}

	CuratorStartupBackground {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.fillMaxSize(),
		) {
			CuratorScreenTitle("Manage server")

			Spacer(Modifier.height(40.dp))

			if (server != null) ServerCard(server)

			if (warning != null) {
				Spacer(Modifier.height(16.dp))
				Text(
					text = warning,
					color = Color(0xFFFFB74D),
					fontSize = 14.sp,
					textAlign = TextAlign.Center,
					modifier = Modifier.width(560.dp),
				)
			}

			Spacer(Modifier.height(48.dp))

			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				CuratorPillButton(
					text = "Who's watching",
					icon = R.drawable.ic_users,
					onClick = onBack,
					modifier = Modifier.focusRequester(backFocusRequester),
				)
				CuratorPillButton(text = "Sign in with password", icon = R.drawable.ic_user_add, onClick = onSignIn)
				CuratorPillButton(text = "Change server", icon = R.drawable.ic_house_edit, onClick = onChangeServer)
				CuratorPillButton(text = "Settings", icon = R.drawable.ic_settings, onClick = onSettings)
				CuratorPillButton(text = "Help", icon = R.drawable.ic_help, onClick = onHelp)
			}
		}
	}
}

@Composable
private fun ServerCard(server: Server) {
	val shape = RoundedCornerShape(16.dp)

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.width(560.dp)
			.clip(shape)
			.background(CuratorSurface)
			.border(1.dp, CuratorOutline, shape)
			.padding(horizontal = 24.dp, vertical = 20.dp),
	) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.size(52.dp)
				.clip(CircleShape)
				.background(Brush.linearGradient(listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC)))),
		) {
			Image(
				painter = painterResource(R.drawable.ic_house),
				contentDescription = null,
				colorFilter = ColorFilter.tint(Color.White),
				modifier = Modifier.size(26.dp),
			)
		}

		Spacer(Modifier.width(20.dp))

		Column(Modifier.weight(1f)) {
			Text(
				text = server.name,
				color = Color.White,
				fontSize = 22.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
			)
			Spacer(Modifier.height(4.dp))
			Text(
				text = server.address,
				color = CuratorMutedText,
				fontSize = 15.sp,
				maxLines = 1,
			)
		}

		if (server.version != null) {
			Text(
				text = "v${server.version}",
				color = CuratorMutedText,
				fontSize = 15.sp,
			)
		}
	}
}
