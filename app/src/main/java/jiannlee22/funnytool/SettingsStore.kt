package jiannlee22.funnytool

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore("regions")

class FunnyToolApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store by lazy { SettingsStore(this, scope) }
}

data class StoredSettings(
    val config: AppConfig = AppConfig(),
    val loaded: Boolean = false,
    val error: String? = null,
)

class SettingsStore(context: Context, scope: CoroutineScope) {
    private val dataStore = context.settingsDataStore
    private val key = stringPreferencesKey("config_json_v1")
    val state = dataStore.data.map { preferences ->
        runCatching { decode(preferences[key]) }
            .fold({ StoredSettings(it, loaded = true) }, {
                StoredSettings(loaded = true, error = "配置无法读取，未覆盖原数据：${it.message}")
            })
    }.catch { emit(StoredSettings(loaded = true, error = "存储读取失败：${it.message}")) }
        .stateIn(scope, SharingStarted.Eagerly, StoredSettings())

    suspend fun update(transform: (AppConfig) -> AppConfig) {
        val committed = dataStore.edit { preferences ->
            val config = transform(decode(preferences[key]))
            require(config.error() == null) { config.error().orEmpty() }
            preferences[key] = encode(config)
        }
        // UI actions are re-enabled only after the observable source reflects the commit.
        val config = decode(committed[key])
        state.first { it.loaded && it.config == config }
    }

    private fun encode(config: AppConfig): String = JSONObject().apply {
        put("version", 1)
        config.screen?.let { s ->
            put("screen", JSONArray(listOf(s.widthPx, s.heightPx, s.leftPx, s.topPx, s.rightPx, s.bottomPx)))
        }
        put("regions", JSONArray().apply {
            config.regions.forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("name", r.name)
                put("centerX", r.centerX.toDouble()); put("centerY", r.centerY.toDouble())
                put("radiusPx", r.radiusPx.toDouble()); put("enabled", r.enabled)
                put("baseDelayMs", r.timing.baseDelayMs)
                put("randomDelayMinMs", r.timing.randomDelayMinMs)
                put("randomDelayMaxMs", r.timing.randomDelayMaxMs)
                r.sourceRegionId?.let { put("sourceRegionId", it) }
                put("repeatPolicy", r.repeatPolicy.name)
                put("pressDurationMs", r.pressDurationMs)
            }) }
        })
    }.toString()

    private fun decode(raw: String?): AppConfig {
        if (raw == null) return AppConfig()
        val root = JSONObject(raw)
        require(root.getInt("version") == 1) { "不支持的配置版本" }
        val screen = root.optJSONArray("screen")?.let {
            ScreenSpace(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5))
        }
        val array = root.getJSONArray("regions")
        val regions = List(array.length()) { index ->
            val r = array.getJSONObject(index)
            ClickRegion(
                id = r.getString("id"), name = r.getString("name"),
                centerX = r.getDouble("centerX").toFloat(), centerY = r.getDouble("centerY").toFloat(),
                radiusPx = r.getDouble("radiusPx").toFloat(), enabled = r.getBoolean("enabled"),
                timing = TimingRule(r.getLong("baseDelayMs"), r.getLong("randomDelayMinMs"), r.getLong("randomDelayMaxMs")),
                sourceRegionId = if (r.has("sourceRegionId")) r.getString("sourceRegionId") else null,
                repeatPolicy = RepeatPolicy.valueOf(r.getString("repeatPolicy")),
                pressDurationMs = r.getLong("pressDurationMs"),
            )
        }
        return AppConfig(regions, screen).also { require(it.error() == null) { it.error().orEmpty() } }
    }
}
