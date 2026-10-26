package com.bubblesms.app

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import com.bubblesms.app.data.PrefsManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/** Port sur lequel BubbleSMS envoie/reçoit ses SMS de données (doit être identique au manifest). */
const val BUBBLE_SMS_PORT: Short = 6392

/**
 * Transport des messages BubbleSMS.
 *
 * Avant : les messages partaient en SMS texte ("BUB::MSG::pseudo::texte"), donc Android les
 * rangeait dans la boîte de l'appli de messages du système, avec le préfixe visible.
 *
 * Maintenant : on envoie des SMS "de données" adressés à un port propre à BubbleSMS.
 * Ils sont livrés uniquement à BubbleSMS, jamais à l'appli de messages du système.
 *
 * Un SMS de données porte 140 octets max. Un message plus long est découpé en morceaux :
 *   [0] 0x42 (magic)  [1] id du message  [2] nombre total de morceaux  [3] index du morceau
 *   [4..] octets UTF-8 du texte
 */
object BubbleSms {
    private const val MAGIC: Byte = 0x42
    private const val HEADER = 4
    private const val MAX_PAYLOAD = 140 - HEADER
    private const val CHUNK_PREFS = "bubble_sms_chunks"
    private const val CHUNK_TTL_MS = 60L * 60L * 1000L
    private val counter = AtomicInteger((System.nanoTime() and 0xFF).toInt())

    private fun smsManager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    /** Retire les séparateurs du protocole d'un pseudo / nom de groupe. */
    fun clean(value: String): String = value.replace("::", " ").replace("|", " ").trim()

    /** Envoie [text] (déjà au format "BUB::...") à [phone]. Retourne false si l'envoi a échoué. */
    fun send(ctx: Context, phone: String, text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val chunks = ((bytes.size + MAX_PAYLOAD - 1) / MAX_PAYLOAD).coerceAtLeast(1)
        if (chunks > 255) return false
        val dest = phone.filter { it.isDigit() || it == '+' }
        if (dest.isEmpty()) return false
        val id = counter.incrementAndGet() and 0xFF
        return try {
            val sm = smsManager(ctx)
            for (i in 0 until chunks) {
                val from = i * MAX_PAYLOAD
                val to = minOf(bytes.size, from + MAX_PAYLOAD)
                val packet = ByteArray(HEADER + (to - from))
                packet[0] = MAGIC
                packet[1] = id.toByte()
                packet[2] = chunks.toByte()
                packet[3] = i.toByte()
                System.arraycopy(bytes, from, packet, HEADER, to - from)
                sm.sendDataMessage(dest, null, BUBBLE_SMS_PORT, packet, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Traite un SMS de données reçu. Retourne le message complet (texte) quand tous les
     * morceaux sont arrivés, sinon null (morceau mis de côté en attendant les autres).
     */
    fun receiveChunk(ctx: Context, sender: String, data: ByteArray): String? {
        if (data.size < HEADER || data[0] != MAGIC) return null
        val id = data[1].toInt() and 0xFF
        val total = data[2].toInt() and 0xFF
        val idx = data[3].toInt() and 0xFF
        if (total == 0 || idx >= total) return null
        val payload = data.copyOfRange(HEADER, data.size)
        if (total == 1) return String(payload, Charsets.UTF_8)

        val store = ctx.getSharedPreferences(CHUNK_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val edit = store.edit()

        // Nettoyage des messages incomplets trop vieux
        for ((k, v) in store.all) {
            val stale = try {
                now - JSONObject(v as String).getLong("t") > CHUNK_TTL_MS
            } catch (_: Exception) { true }
            if (stale) edit.remove(k)
        }

        val key = "${PrefsManager.normalizePhone(sender)}_$id"
        var obj = try { JSONObject(store.getString(key, null) ?: "{}") } catch (_: Exception) { JSONObject() }
        if (!obj.has("p") || obj.optInt("n", -1) != total) {
            obj = JSONObject().put("n", total).put("p", JSONObject())
        }
        obj.put("t", now)
        val parts = obj.getJSONObject("p")
        parts.put(idx.toString(), Base64.encodeToString(payload, Base64.NO_WRAP))

        if (parts.length() >= total) {
            val out = ByteArrayOutputStream()
            for (i in 0 until total) {
                if (!parts.has(i.toString())) {
                    edit.putString(key, obj.toString()).commit()
                    return null
                }
                out.write(Base64.decode(parts.getString(i.toString()), Base64.NO_WRAP))
            }
            edit.remove(key).commit()
            return String(out.toByteArray(), Charsets.UTF_8)
        }
        edit.putString(key, obj.toString()).commit()
        return null
    }
}
