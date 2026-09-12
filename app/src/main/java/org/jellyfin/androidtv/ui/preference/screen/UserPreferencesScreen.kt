package org.jellyfin.androidtv.ui.preference.screen

import android.content.Intent
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.preference.category.aboutCategory
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.koin.android.ext.android.inject

class UserPreferencesScreen : OptionsFragment() {
	private val navigationRepository by inject<NavigationRepository>()

	override val screen by optionsScreen {
		setTitle(R.string.settings_title)

		category {
			action {
				setTitle(R.string.pref_change_users)
				setContent(R.string.pref_change_users_description)
				icon = R.drawable.ic_users
				onActivate = {
					val context = requireContext()
					requireActivity().finish()
					context.startActivity(
						Intent(context, StartupActivity::class.java).apply {
							addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
							putExtra(StartupActivity.EXTRA_HIDE_SPLASH, true)
						}
					)
				}
			}

			action {
				setTitle(R.string.pref_genre_preferences)
				setContent(R.string.pref_genre_preferences_description)
				icon = R.drawable.ic_star
				onActivate = {
					requireActivity().finish()
					navigationRepository.navigate(Destinations.genrePreferences)
				}
			}

			link {
				setTitle(R.string.pref_login)
				setContent(R.string.pref_login_description)
				icon = R.drawable.ic_users
				withFragment<AuthPreferencesScreen>()
			}

			link {
				setTitle(R.string.pref_customization)
				setContent(R.string.pref_customization_description)
				icon = R.drawable.ic_adjust
				withFragment<CustomizationPreferencesScreen>()
			}

			link {
				setTitle(R.string.pref_playback)
				setContent(R.string.pref_playback_description)
				icon = R.drawable.ic_next
				withFragment<PlaybackPreferencesScreen>()
			}

			link {
				setTitle(R.string.pref_telemetry_category)
				setContent(R.string.pref_telemetry_description)
				icon = R.drawable.ic_error
				withFragment<CrashReportingPreferencesScreen>()
			}

			link {
				setTitle(R.string.pref_developer_link)
				setContent(R.string.pref_developer_link_description)
				icon = R.drawable.ic_flask
				withFragment<DeveloperPreferencesScreen>()
			}
		}

		aboutCategory()
	}
}
