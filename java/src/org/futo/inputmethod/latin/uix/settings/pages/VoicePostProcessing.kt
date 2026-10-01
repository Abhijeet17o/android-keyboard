package org.futo.inputmethod.latin.uix.settings.pages

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.VOICE_AUTO_CAPITALIZE
import org.futo.inputmethod.latin.uix.VOICE_FILENAME_RECOGNITION
import org.futo.inputmethod.latin.uix.VOICE_POST_PROCESSING
import org.futo.inputmethod.latin.uix.VOICE_REMOVE_FILLERS
import org.futo.inputmethod.latin.uix.VOICE_SMART_PARAGRAPHS
import org.futo.inputmethod.latin.uix.VOICE_SPOKEN_PUNCTUATION
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore

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
            title = R.string.voice_pp_filename_recognition,
            subtitle = R.string.voice_pp_filename_recognition_subtitle,
            setting = VOICE_FILENAME_RECOGNITION
        ),
        userSettingToggleDataStore(
            title = R.string.voice_pp_smart_paragraphs,
            subtitle = R.string.voice_pp_smart_paragraphs_subtitle,
            setting = VOICE_SMART_PARAGRAPHS
        )
    )
)
