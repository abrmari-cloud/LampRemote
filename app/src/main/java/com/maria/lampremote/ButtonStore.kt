package com.maria.lampremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LampButton(val name: String, val payloads: List<String>)

/** A named sequence of saved buttons, referenced by button name. */
data class Scene(val name: String, val steps: List<String>)

data class LampData(val buttons: List<LampButton>, val scenes: List<Scene>)

class ButtonStore(context: Context) {
    private val prefs = context.getSharedPreferences("lamp", Context.MODE_PRIVATE)

    fun load(): LampData {
        val buttons = try {
            parseButtons(JSONArray(prefs.getString(KEY_BUTTONS, "[]") ?: "[]"))
        } catch (e: Exception) {
            emptyList()
        }
        val scenes = try {
            parseScenes(JSONArray(prefs.getString(KEY_SCENES, "[]") ?: "[]"))
        } catch (e: Exception) {
            emptyList()
        }
        return LampData(buttons, scenes)
    }

    fun save(data: LampData) {
        prefs.edit()
            .putString(KEY_BUTTONS, buttonsJson(data.buttons).toString())
            .putString(KEY_SCENES, scenesJson(data.scenes).toString())
            .apply()
    }

    fun toJson(data: LampData): String =
        JSONObject()
            .put("buttons", buttonsJson(data.buttons))
            .put("scenes", scenesJson(data.scenes))
            .toString(2)

    /** Accepts the current format and the old buttons-only array. Throws if the text is invalid. */
    fun fromJson(text: String): LampData {
        val t = text.trim()
        if (t.startsWith("[")) return LampData(parseButtons(JSONArray(t)), emptyList())
        val o = JSONObject(t)
        val buttons = parseButtons(o.getJSONArray("buttons"))
        val scenes = o.optJSONArray("scenes")?.let { parseScenes(it) } ?: emptyList()
        return LampData(buttons, scenes)
    }

    private fun buttonsJson(list: List<LampButton>) = JSONArray().apply {
        list.forEach { b -> put(JSONObject().put("name", b.name).put("payloads", JSONArray(b.payloads))) }
    }

    private fun scenesJson(list: List<Scene>) = JSONArray().apply {
        list.forEach { s -> put(JSONObject().put("name", s.name).put("steps", JSONArray(s.steps))) }
    }

    private fun parseButtons(arr: JSONArray): List<LampButton> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val p = o.getJSONArray("payloads")
        val payloads = (0 until p.length()).map { j ->
            val hex = p.getString(j).uppercase()
            hex.hexToBytes() // validate
            hex
        }
        LampButton(o.getString("name"), payloads)
    }

    private fun parseScenes(arr: JSONArray): List<Scene> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val s = o.getJSONArray("steps")
        Scene(o.getString("name"), (0 until s.length()).map { s.getString(it) })
    }

    private companion object {
        const val KEY_BUTTONS = "buttons"
        const val KEY_SCENES = "scenes"
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray {
    val s = filter { !it.isWhitespace() }
    require(s.length % 2 == 0) { "нечётная длина hex" }
    return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
