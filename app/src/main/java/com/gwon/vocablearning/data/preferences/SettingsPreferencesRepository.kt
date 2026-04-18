package com.gwon.vocablearning.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gwon.vocablearning.domain.model.SchoolGrade
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "app_settings")

data class SyncState(
    val manifestVersion: Int = 0,
    val fileVersions: Map<String, Int> = emptyMap(),
)

class SettingsPreferencesRepository(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSelectedGrade(): SchoolGrade =
        context.dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { preferences ->
                SchoolGrade.fromCode(preferences[SELECTED_GRADE] ?: SchoolGrade.HIGH_3.code)
            }
            .first()

    suspend fun getNickname(): String =
        context.dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { preferences -> preferences[NICKNAME].orEmpty() }
            .first()

    suspend fun setNickname(nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[NICKNAME] = nickname.trim()
        }
    }

    suspend fun setSelectedGrade(grade: SchoolGrade) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_GRADE] = grade.code
        }
    }

    suspend fun getLearningCount(defaultValue: Int = 20): Int =
        context.dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { preferences -> preferences[LEARNING_COUNT] ?: defaultValue }
            .first()

    suspend fun setLearningCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[LEARNING_COUNT] = count.coerceAtLeast(1)
        }
    }

    suspend fun hasCompletedOnboarding(): Boolean =
        context.dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { preferences -> preferences[HAS_COMPLETED_ONBOARDING] ?: false }
            .first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun getRemoteBaseUrl(defaultUrl: String): String =
        context.dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { preferences ->
                preferences[REMOTE_BASE_URL]?.takeIf { it.isNotBlank() } ?: defaultUrl
            }
            .first()

    suspend fun setRemoteBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[REMOTE_BASE_URL] = url
        }
    }

    suspend fun getSyncState(): SyncState =
        context.dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map(::preferencesToSyncState)
            .first()

    suspend fun updateSyncState(
        manifestVersion: Int,
        fileVersions: Map<String, Int>,
    ) {
        context.dataStore.edit { preferences ->
            preferences[MANIFEST_VERSION] = manifestVersion
            preferences[FILE_VERSIONS] = json.encodeToString(fileVersions)
        }
    }

    private fun preferencesToSyncState(preferences: Preferences): SyncState {
        val fileVersionsJson = preferences[FILE_VERSIONS]
        val decoded = fileVersionsJson
            ?.let { stored ->
                runCatching {
                    json.decodeFromString<Map<String, Int>>(stored)
                }.getOrDefault(emptyMap())
            }
            ?: emptyMap()

        return SyncState(
            manifestVersion = preferences[MANIFEST_VERSION] ?: 0,
            fileVersions = decoded,
        )
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")
        val SELECTED_GRADE = stringPreferencesKey("selected_grade")
        val LEARNING_COUNT = intPreferencesKey("learning_count")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val REMOTE_BASE_URL = stringPreferencesKey("remote_base_url")
        val MANIFEST_VERSION = intPreferencesKey("manifest_version")
        val FILE_VERSIONS = stringPreferencesKey("file_versions")
    }
}
