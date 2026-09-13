package com.bubblesms.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Toutes les données (pseudo, amis, demandes en attente, messages) sont stockées
 * uniquement en local sur le téléphone (SharedPreferences). Aucun serveur.
 */
object PrefsManager {

    private const val PREFS = "bubble_sms_prefs"
    private const val KEY_PSEUDO = "my_pseudo"
    private const val KEY_FRIENDS = "friends"
    private const val KEY_REQUESTS = "pending_requests"
    private const val KEY_MSG_PREFIX = "messages_"
    private const val KEY_PROFILE_PIC = "profile_picture_path"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Normalise un numéro pour comparaison/stockage : ne garde que les chiffres,
     * et convertit les formats internationaux français (+33 / 0033) vers le format
     * local (0X XX XX XX XX) pour que le numéro saisi manuellement (souvent en 0X...)
     * et celui reçu dans un SMS (souvent en +33...) soient reconnus comme identiques.
     * Sans ça, les messages/amis pouvaient ne jamais se faire correspondre.
     */
    fun normalizePhone(phone: String): String {
        val hadPlus = phone.trim().startsWith("+")
        val digits = phone.filter { it.isDigit() }
        return when {
            hadPlus && digits.startsWith("33") -> "0" + digits.substring(2)
            digits.startsWith("0033") -> "0" + digits.substring(4)
            else -> digits
        }
    }

    // ---- Photo de profil ----
    fun getProfilePicturePath(ctx: Context): String? = prefs(ctx).getString(KEY_PROFILE_PIC, null)

    /** Copie l'image choisie dans le stockage privé de l'appli et retourne son chemin. */
    fun saveProfilePicture(ctx: Context, uri: Uri): String? {
        return try {
            val file = File(ctx.filesDir, "profile.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            prefs(ctx).edit().putString(KEY_PROFILE_PIC, file.absolutePath).apply()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // ---- Pseudo perso ----
    fun getMyPseudo(ctx: Context): String? = prefs(ctx).getString(KEY_PSEUDO, null)

    fun setMyPseudo(ctx: Context, pseudo: String) {
        prefs(ctx).edit().putString(KEY_PSEUDO, pseudo).apply()
    }

    // ---- Amis ----
    fun getFriends(ctx: Context): MutableList<Friend> {
        val raw = prefs(ctx).getString(KEY_FRIENDS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Friend>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Friend(o.getString("phone"), o.getString("pseudo"), o.optBoolean("confirmed", false)))
        }
        return list
    }

    private fun saveFriends(ctx: Context, friends: List<Friend>) {
        val arr = JSONArray()
        for (f in friends) {
            val o = JSONObject()
            o.put("phone", f.phone)
            o.put("pseudo", f.pseudo)
            o.put("confirmed", f.confirmed)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_FRIENDS, arr.toString()).apply()
    }

    fun findFriend(ctx: Context, phone: String): Friend? {
        val n = normalizePhone(phone)
        return getFriends(ctx).find { normalizePhone(it.phone) == n }
    }

    fun upsertFriend(ctx: Context, friend: Friend) {
        val friends = getFriends(ctx)
        val n = normalizePhone(friend.phone)
        val idx = friends.indexOfFirst { normalizePhone(it.phone) == n }
        if (idx >= 0) {
            friends[idx] = friend
        } else {
            friends.add(friend)
        }
        saveFriends(ctx, friends)
    }

    fun confirmFriend(ctx: Context, phone: String, fallbackPseudo: String) {
        val friends = getFriends(ctx)
        val n = normalizePhone(phone)
        val idx = friends.indexOfFirst { normalizePhone(it.phone) == n }
        if (idx >= 0) {
            friends[idx] = friends[idx].copy(confirmed = true)
        } else {
            friends.add(Friend(phone, fallbackPseudo, true))
        }
        saveFriends(ctx, friends)
    }

    // ---- Demandes reçues en attente ----
    fun getPendingRequests(ctx: Context): MutableList<Friend> {
        val raw = prefs(ctx).getString(KEY_REQUESTS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Friend>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Friend(o.getString("phone"), o.getString("pseudo")))
        }
        return list
    }

    private fun saveRequests(ctx: Context, list: List<Friend>) {
        val arr = JSONArray()
        for (f in list) {
            val o = JSONObject()
            o.put("phone", f.phone)
            o.put("pseudo", f.pseudo)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_REQUESTS, arr.toString()).apply()
    }

    fun addPendingRequest(ctx: Context, friend: Friend) {
        val n = normalizePhone(friend.phone)
        val list = getPendingRequests(ctx)
        if (list.none { normalizePhone(it.phone) == n } && findFriend(ctx, friend.phone) == null) {
            list.add(friend)
            saveRequests(ctx, list)
        }
    }

    fun removePendingRequest(ctx: Context, phone: String) {
        val n = normalizePhone(phone)
        val list = getPendingRequests(ctx).filterNot { normalizePhone(it.phone) == n }
        saveRequests(ctx, list)
    }

    // ---- Messages ----
    fun getMessages(ctx: Context, phone: String): MutableList<ChatMessage> {
        val key = KEY_MSG_PREFIX + normalizePhone(phone)
        val raw = prefs(ctx).getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<ChatMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(ChatMessage(o.getString("body"), o.getBoolean("sent"), o.getLong("time")))
        }
        return list
    }

    fun addMessage(ctx: Context, phone: String, message: ChatMessage) {
        val key = KEY_MSG_PREFIX + normalizePhone(phone)
        val list = getMessages(ctx, phone)
        list.add(message)
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("body", m.body)
            o.put("sent", m.isSent)
            o.put("time", m.timestamp)
            arr.put(o)
        }
        prefs(ctx).edit().putString(key, arr.toString()).apply()
    }
}
