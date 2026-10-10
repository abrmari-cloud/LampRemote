package com.maria.lampremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LampButton(val name: String, val payloads: List<String>)

class ButtonStore(context: Context) {
    private val prefs = context.getSharedPreferences("lamp", Context.MODE_PRIVATE)

    fun load(): List<LampButton> = try {
        fromJson(prefs.getString(KEY, "[]") ?: "[]")
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<LampButton>) {
        prefs.edit().putString(KEY, toJson(list)).apply()
    }

    fun toJson(list: List<LampButton>): String {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().put("name", b.name).put("payloads", JSONArray(b.payloads)))
        }
        return arr.toString(2)
    }

    /** Throws if the text is not a valid button list. */
    fun fromJson(text: String): List<LampButton> {
        val arr = JSONArray(text.trim())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.getJSONArray("payloads")
            val payloads = (0 until p.length()).map { j ->
                val hex = p.getString(j).uppercase()
                hex.hexToBytes() // validate
                hex
            }
            LampButton(o.getString("name"), payloads)
        }
    }

    private companion object {
        const val KEY = "buttons"
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray {
    val s = filter { !it.isWhitespace() }
    require(s.length % 2 == 0) { "нечётная длина hex" }
    return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
