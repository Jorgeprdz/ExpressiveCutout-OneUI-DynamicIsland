package com.ekoehler.expressivecutout.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ekoehler.expressivecutout.core.DynamicTile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Backing store for which dynamic tiles the user has enabled. */
private val Context.dynamicTileDataStore: DataStore<Preferences> by preferencesDataStore(name = "dynamic_tile_prefs")

/**
 * Resolves a persisted dynamic-tile switch. Assistant is intentionally opt-in on a fresh install;
 * every other tile keeps the historical enabled-by-default behaviour. An explicit stored/imported
 * value always wins so existing users are never silently changed.
 */
internal fun resolveDynamicTileEnabled(tile: DynamicTile, persisted: Boolean?): Boolean =
    persisted ?: (tile != DynamicTile.ASSISTANT)

/**
 * Persists whether each dynamic tile is allowed to appear on the cutout. Assistant is the sole
 * opt-in tile; every other absent preference remains enabled, matching the historical defaults.
 */
class DynamicTilePreferences(private val context: Context) : JsonSerializable {

    val enabled: Flow<Map<DynamicTile, Boolean>> = context.dynamicTileDataStore.data.map { prefs ->
        DynamicTile.entries.associateWith { tile ->
            resolveDynamicTileEnabled(tile, prefs[tile.key])
        }
    }

    suspend fun setEnabled(tile: DynamicTile, enabled: Boolean) = context.dynamicTileDataStore.edit {
        it[tile.key] = enabled
    }

    /**
     * Exports the per-tile enabled flags as JSON { enabled: { TILE_NAME: true, ... } }. The map is
     * keyed by [DynamicTile], so build the object by hand with each tile's name as the key — a
     * JSONObject key must be a String.
     */
    override suspend fun toJson(): String {
        val e = enabled.first()
        return JSONObject().apply {
            put("enabled", JSONObject().apply { e.forEach { (tile, on) -> put(tile.name, on) } })
        }.toString()
    }

    /**
     * Applies { enabled: { TILE_NAME: bool, ... } } exported by [toJson]. Explicit imported values
     * win; a missing entry falls back to that tile's real default (Assistant off, the others on).
     */
    override suspend fun fromJson(json: String) {
        val enabledObj = JSONObject(json).optJSONObject("enabled") ?: return
        context.dynamicTileDataStore.edit { prefs ->
            DynamicTile.entries.forEach { tile ->
                val imported = if (enabledObj.has(tile.name)) enabledObj.getBoolean(tile.name) else null
                prefs[tile.key] = resolveDynamicTileEnabled(tile, imported)
            }
        }
    }

    private val DynamicTile.key: Preferences.Key<Boolean>
        get() = booleanPreferencesKey("tile_enabled_$name")
}
