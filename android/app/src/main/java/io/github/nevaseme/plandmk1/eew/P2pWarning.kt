package io.github.nevaseme.plandmk1.eew

import kotlinx.serialization.json.*
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** P2P codes are intensity classes, not instrumental intensity multiplied by ten. */
data class P2pArea(val name: String, val pref: String, val scaleFrom: Int, val scaleTo: Int, val kindCode: String?)

@ConsistentCopyVisibility
data class EewWarning private constructor(
    val messageId: String, val eventId: String, val serial: Int,
    val issuedAt: Long, val test: Boolean, val cancelled: Boolean,
    val areas: List<P2pArea>, val raw: String, val source: String = "p2pquake",
) {
    fun warningFor(region: String): P2pArea? =
        if (test || cancelled) null else areas.firstOrNull { it.name == region && it.kindCode in setOf("10", "11", "19") }

    companion object {
        private val scales = setOf(-1, 0, 10, 20, 30, 40, 45, 50, 55, 60, 70)

        /** Non-EEW traffic and invalid packets cannot become actionable warnings. */
        fun parse(raw: String, source: String = "p2pquake"): EewWarning? {
            if (source == "wolfx") return parseWolfx(raw)
            if (source != "p2pquake") return null
            return parseP2p(raw)
        }

        private fun parseP2p(raw: String): EewWarning? = try {
            val root = Json.parseToJsonElement(raw).jsonObject
            require(root["code"]?.jsonPrimitive?.int == 556)
            val id = root.string("id").also { require(it.isNotBlank()) }
            val issue = root.getValue("issue").jsonObject
            val event = issue.string("eventId").also { require(it.matches(Regex("[0-9]{14}"))) }
            val serial = issue.string("serial").toInt().also { require(it > 0) }
            val at = parseJst(issue.string("time"))
            val cancelled = root.getValue("cancelled").jsonPrimitive.boolean
            val test = root["test"]?.jsonPrimitive?.boolean ?: false
            val areas = root["areas"]?.jsonArray?.map { element ->
                val area = element.jsonObject
                val from = area.scale("scaleFrom").also { require(it in scales) }
                val to = area.scale("scaleTo").also { require(it in scales || it == 99) }
                require(from == -1 || to == -1 || to == 99 || from <= to)
                P2pArea(area.string("name"), area.string("pref"), from, to,
                    area["kindCode"]?.jsonPrimitive?.contentOrNull)
            }.orEmpty()
            if (!cancelled) require(root["earthquake"] is JsonObject && root["areas"] is JsonArray)
            EewWarning(id, event, serial, at, test, cancelled, areas, raw)
        } catch (_: IllegalArgumentException) { null }
          catch (_: NoSuchElementException) { null }

        private fun parseWolfx(raw: String): EewWarning? = try {
            val root = Json.parseToJsonElement(raw).jsonObject
            require(root.string("type") == "jma_eew")
            val event = root.string("EventID").also { require(it.matches(Regex("[0-9]{14}"))) }
            val serial = root.getValue("Serial").jsonPrimitive.int.also { require(it > 0) }
            val at = parseJst(root.string("AnnouncedTime"))
            val cancelled = root.getValue("isCancel").jsonPrimitive.boolean
            val training = root.getValue("isTraining").jsonPrimitive.boolean
            val isWarn = root.getValue("isWarn").jsonPrimitive.boolean
            val status = root.getValue("Issue").jsonObject.string("Status")
            require(status in setOf("通常", "訓練", "試験"))
            val areas = root["WarnArea"]?.jsonArray?.map { element ->
                val area = element.jsonObject
                var from = wolfxScale(area["Shindo2"])
                var to = wolfxScale(area["Shindo1"])
                if (from != -1 && to != -1 && from > to) { from = -1; to = -1 }
                P2pArea(area.string("Chiiki"), "", from, to,
                    if (isWarn && area["Type"]?.jsonPrimitive?.contentOrNull == "警報") "19" else null)
            }.orEmpty()
            EewWarning("wolfx:$event:$serial:$at:${if (cancelled) 1 else 0}", event, serial, at,
                training || status != "通常", cancelled, areas, raw, "wolfx")
        } catch (_: IllegalArgumentException) { null }
          catch (_: NoSuchElementException) { null }

        private fun wolfxScale(value: JsonElement?): Int = mapOf(
            "0" to 0, "1" to 10, "2" to 20, "3" to 30, "4" to 40,
            "5弱" to 45, "5-" to 45, "5強" to 50, "5+" to 50,
            "6弱" to 55, "6-" to 55, "6強" to 60, "6+" to 60, "7" to 70,
        )[(value as? JsonPrimitive)?.contentOrNull] ?: -1

        private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.let {
            require(it.isString); it.content
        }
        private fun JsonObject.scale(key: String): Int {
            val number = getValue(key).jsonPrimitive.double
            require(number.isFinite() && number == number.toInt().toDouble())
            return number.toInt()
        }
        private fun parseJst(value: String): Long {
            require(value.matches(Regex("[0-9]{4}/[0-9]{2}/[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")))
            val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT).apply {
                isLenient = false; timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            }
            val position = ParsePosition(0)
            val parsed = format.parse(value, position)
            require(parsed != null && position.index == value.length)
            return parsed.time
        }
    }
}

/** Compatibility for existing P2P history and callers. */
typealias P2pWarning = EewWarning
