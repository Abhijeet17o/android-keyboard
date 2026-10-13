package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.VOICE_AUTO_CAPITALIZE
import org.futo.inputmethod.latin.uix.VOICE_DEBUG_LOG
import org.futo.inputmethod.latin.uix.VOICE_FILENAME_RECOGNITION
import org.futo.inputmethod.latin.uix.VOICE_FORMAT_LISTS
import org.futo.inputmethod.latin.uix.VOICE_POST_PROCESSING
import org.futo.inputmethod.latin.uix.VOICE_REMOVE_FILLERS
import org.futo.inputmethod.latin.uix.VOICE_REMOVE_VERBAL_TICS
import org.futo.inputmethod.latin.uix.VOICE_SMART_PARAGRAPHS
import org.futo.inputmethod.latin.uix.VOICE_SPOKEN_PUNCTUATION
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.utils.VoiceDictationLog

val VoicePostProcessingMenu = UserSettingsMenu(
    title = R.string.voice_pp_settings_title,
    navPath = "voicePostProcessing",
    registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_post_processing,
            subtitle = R.string.voice_input_settings_post_processing_subtitle,
            setting = VOICE_POST_PROCESSING
        ),
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_remove_fillers,
            subtitle = R.string.voice_input_settings_remove_fillers_subtitle,
            setting = VOICE_REMOVE_FILLERS
        ),
        userSettingToggleDataStore(
            title = R.string.voice_pp_remove_verbal_tics,
            subtitle = R.string.voice_pp_remove_verbal_tics_subtitle,
            setting = VOICE_REMOVE_VERBAL_TICS
        ),
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_spoken_punctuation,
            subtitle = R.string.voice_input_settings_spoken_punctuation_subtitle,
            setting = VOICE_SPOKEN_PUNCTUATION
        ),
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_auto_capitalize,
            subtitle = R.string.voice_input_settings_auto_capitalize_subtitle,
            setting = VOICE_AUTO_CAPITALIZE
        ),
        userSettingToggleDataStore(
            title = R.string.voice_pp_format_lists,
            subtitle = R.string.voice_pp_format_lists_subtitle,
            setting = VOICE_FORMAT_LISTS
        ),
        userSettingToggleDataStore(
            title = R.string.voice_pp_filename_recognition,
            subtitle = R.string.voice_pp_filename_recognition_subtitle,
            setting = VOICE_FILENAME_RECOGNITION
        ),
        userSettingToggleDataStore(
            title = R.string.voice_pp_smart_paragraphs,
            subtitle = R.string.voice_pp_smart_paragraphs_subtitle,
            setting = VOICE_SMART_PARAGRAPHS
        ),
        userSettingToggleDataStore(
            title = R.string.voice_pp_debug_log,
            subtitle = R.string.voice_pp_debug_log_subtitle,
            setting = VOICE_DEBUG_LOG
        ),
        userSettingAction(
            title = R.string.voice_pp_debug_log_share,
            subtitle = R.string.voice_pp_debug_log_share_subtitle,
            action = VoiceDictationLog::share
        ),
        userSettingAction(
            title = R.string.voice_pp_debug_log_clear,
            action = VoiceDictationLog::clear
        ),
    )
)

private fun userSettingAction(
    @StringRes title: Int,
    @StringRes subtitle: Int? = null,
    action: (Context) -> Unit
) = UserSetting(
    name = title,
    subtitle = subtitle,
    component = {
        val context = LocalContext.current
        NavigationItem(
            title = stringResource(title),
            subtitle = subtitle?.let { stringResource(it) },
            style = NavigationItemStyle.MiscNoArrow,
            navigate = { action(context) }
        )
    }
)
