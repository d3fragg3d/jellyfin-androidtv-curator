package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.LoginState
import org.jellyfin.androidtv.auth.model.PendingQuickConnectState
import org.jellyfin.androidtv.auth.model.QuickConnectState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.UnavailableQuickConnectState
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Curator replacement for the upstream UserLoginFragment: Quick Connect and password side by side,
 * styled like the "Who's watching?" screen. Signing in goes straight to Home.
 */
class CuratorUserLoginFragment : Fragment() {

	companion object {
		const val ARG_SERVER_ID = "server_id"
		const val ARG_USERNAME = "user_name"
	}

	private val userLoginViewModel: UserLoginViewModel by activityViewModel()

	private val usernameArgument get() = arguments?.getString(ARG_USERNAME)?.ifBlank { null }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		userLoginViewModel.forcedUsername = usernameArgument
		userLoginViewModel.setServer(arguments?.getString(ARG_SERVER_ID)?.toUUIDOrNull())
		userLoginViewModel.clearLoginState()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val server by userLoginViewModel.server.collectAsState()
		val loginState by userLoginViewModel.loginState.collectAsState()
		val quickConnectState by userLoginViewModel.quickConnectState.collectAsState()

		LaunchedEffect(server) {
			if (server != null) userLoginViewModel.initiateQuickconnect()
		}

		LaunchedEffect(loginState) {
			if (loginState == AuthenticatedState) (requireActivity() as StartupActivity).proceedToHome()
		}

		JellyfinTheme {
			UserLoginScreen(
				username = usernameArgument,
				serverName = server?.name ?: "Curator",
				status = loginStatus(loginState),
				quickConnectState = quickConnectState,
				onLogin = { username, password -> userLoginViewModel.login(username, password) },
				onBack = { parentFragmentManager.popBackStack() },
			)
		}
	}

	@Composable
	private fun loginStatus(state: LoginState?): String? = when (state) {
		is ServerVersionNotSupported -> stringResource(
			R.string.server_issue_outdated_version,
			state.server.version.orEmpty(),
			ServerRepository.recommendedServerVersion.toString(),
		)

		AuthenticatingState -> stringResource(R.string.login_authenticating)
		RequireSignInState -> stringResource(R.string.login_invalid_credentials)
		ServerUnavailableState,
		is ApiClientErrorLoginState -> stringResource(R.string.login_server_unavailable)

		AuthenticatedState, null -> null
	}
}

@Composable
private fun UserLoginScreen(
	username: String?,
	serverName: String,
	status: String?,
	quickConnectState: QuickConnectState,
	onLogin: (username: String, password: String) -> Unit,
	onBack: () -> Unit,
) {
	val profile = remember(username) { username?.let(::curatorUserProfile) }
	val quickConnectAvailable = quickConnectState != UnavailableQuickConnectState
	var showPassword by remember { mutableStateOf(false) }
	val passwordToggleFocusRequester = remember { FocusRequester() }

	// delay(50) gives Compose one frame to lay out the buttons before requestFocus() runs
	LaunchedEffect(Unit) {
		delay(50)
		runCatching { passwordToggleFocusRequester.requestFocus() }
	}

	CuratorStartupBackground {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.fillMaxSize(),
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (profile != null) {
					CuratorAvatar(profile = profile, imageUrl = null, modifier = Modifier.size(72.dp), initialFontSize = 30)
					Spacer(Modifier.width(20.dp))
				}

				Column(horizontalAlignment = if (profile != null) Alignment.Start else Alignment.CenterHorizontally) {
					CuratorScreenTitle(if (profile != null) "Sign in as ${profile.displayName}" else "Sign in")
					Spacer(Modifier.height(4.dp))
					Text(
						text = stringResource(R.string.login_connect_to, serverName),
						color = CuratorMutedText,
						fontSize = 16.sp,
					)
				}
			}

			Spacer(Modifier.height(36.dp))

			Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
				if (quickConnectAvailable) {
					LoginPanel(title = "Use Quick Connect") {
						Text(
							text = "On your phone or computer, open Jellyfin, go to Quick Connect in the user menu and enter:",
							color = CuratorMutedText,
							fontSize = 15.sp,
							lineHeight = 22.sp,
						)
						Spacer(Modifier.height(20.dp))
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier
								.fillMaxWidth()
								.height(72.dp),
						) {
							if (quickConnectState is PendingQuickConnectState) {
								Text(
									text = quickConnectState.code.chunked(3).joinToString(" "),
									color = Color.White,
									fontSize = 52.sp,
									fontWeight = FontWeight.SemiBold,
									letterSpacing = 4.sp,
								)
							} else {
								CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
							}
						}
					}
				}

				LoginPanel(title = "Use your password") {
					if (!showPassword) {
						Text(
							text = if (quickConnectAvailable) "Prefer typing it in? Sign in with your Jellyfin password instead."
							else "Sign in with your Jellyfin password.",
							color = CuratorMutedText,
							fontSize = 15.sp,
							lineHeight = 22.sp,
						)
						Spacer(Modifier.height(20.dp))
						CuratorPillButton(
							text = "Enter password",
							icon = R.drawable.ic_user,
							onClick = { showPassword = true },
							modifier = Modifier.focusRequester(passwordToggleFocusRequester),
						)
					} else {
						PasswordForm(forcedUsername = username, onLogin = onLogin)
					}
				}
			}

			Spacer(Modifier.height(24.dp))

			// Reserve the line so the layout doesn't jump when a status appears
			Text(
				text = status.orEmpty(),
				color = Color(0xFFFFB74D),
				fontSize = 15.sp,
				textAlign = TextAlign.Center,
				modifier = Modifier.height(22.dp),
			)

			Spacer(Modifier.height(20.dp))

			CuratorPillButton(text = "Back", icon = R.drawable.ic_users, onClick = onBack)
		}
	}
}

@Composable
private fun LoginPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
	val shape = RoundedCornerShape(16.dp)

	Column(
		modifier = Modifier
			.width(400.dp)
			.height(280.dp)
			.background(CuratorSurface, shape)
			.border(1.dp, CuratorOutline, shape)
			.padding(28.dp),
	) {
		Text(
			text = title,
			color = Color.White,
			fontSize = 20.sp,
			fontWeight = FontWeight.SemiBold,
		)
		Spacer(Modifier.height(12.dp))
		content()
	}
}

@Composable
private fun PasswordForm(
	forcedUsername: String?,
	onLogin: (username: String, password: String) -> Unit,
) {
	var username by remember { mutableStateOf(forcedUsername.orEmpty()) }
	var password by remember { mutableStateOf("") }
	val firstFieldFocusRequester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		delay(50)
		runCatching { firstFieldFocusRequester.requestFocus() }
	}

	if (forcedUsername == null) {
		CuratorTextField(
			value = username,
			onValueChange = { username = it },
			placeholder = stringResource(R.string.input_username),
			imeAction = ImeAction.Next,
			modifier = Modifier.focusRequester(firstFieldFocusRequester),
		)
		Spacer(Modifier.height(12.dp))
	}

	CuratorTextField(
		value = password,
		onValueChange = { password = it },
		placeholder = stringResource(R.string.input_password),
		password = true,
		imeAction = ImeAction.Done,
		onSubmit = { if (username.isNotBlank()) onLogin(username, password) },
		modifier = if (forcedUsername != null) Modifier.focusRequester(firstFieldFocusRequester) else Modifier,
	)

	Spacer(Modifier.height(16.dp))

	CuratorPillButton(
		text = stringResource(R.string.action_login),
		icon = R.drawable.ic_check,
		onClick = { if (username.isNotBlank()) onLogin(username, password) },
	)
}

@Composable
private fun CuratorTextField(
	value: String,
	onValueChange: (String) -> Unit,
	placeholder: String,
	modifier: Modifier = Modifier,
	password: Boolean = false,
	imeAction: ImeAction = ImeAction.Done,
	onSubmit: () -> Unit = {},
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val shape = RoundedCornerShape(12.dp)

	BasicTextField(
		value = value,
		onValueChange = onValueChange,
		singleLine = true,
		interactionSource = interactionSource,
		visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
		keyboardOptions = KeyboardOptions.Default.copy(
			keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
			imeAction = imeAction,
			autoCorrectEnabled = false,
			showKeyboardOnFocus = true,
		),
		keyboardActions = KeyboardActions { onSubmit() },
		textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
		cursorBrush = SolidColor(Color.White),
		modifier = modifier.fillMaxWidth(),
		decorationBox = { innerTextField ->
			Box(
				contentAlignment = Alignment.CenterStart,
				modifier = Modifier
					.background(Color(0xFF0E0B16), shape)
					.border(2.dp, if (focused) Color.White else CuratorOutline, shape)
					.padding(horizontal = 16.dp, vertical = 12.dp),
			) {
				if (value.isEmpty()) Text(text = placeholder, color = CuratorMutedText, fontSize = 17.sp)
				innerTextField()
			}
		},
	)
}
