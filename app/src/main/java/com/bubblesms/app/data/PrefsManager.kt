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
    private const val KEY_BLOCKED = "blocked_users"
    private const val KEY_GROUPS = "groups"
    private const val KEY_GROUP_MSG_PREFIX = "group_messages_"
    private const val KEY_STATUS = "status"
    private const val KEY_MUTES = "muted_chats"
    private const val KEY_PIN = "app_pin"
    private const val KEY_PRIVACY = "privacy_status"
    private const val KEY_MY_ID = "my_stable_id"

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

    /**
     * Identifiant stable et unique généré une seule fois sur cet appareil.
     * Contrairement au pseudo (modifiable dans les réglages), il ne change jamais :
     * on s'en sert pour savoir si on est bien l'admin d'un groupe qu'on a créé,
     * même après avoir changé de pseudo.
     */
    fun getMyId(ctx: Context): String {
        val existing = prefs(ctx).getString(KEY_MY_ID, null)
        if (existing != null) return existing
        val id = java.util.UUID.randomUUID().toString()
        prefs(ctx).edit().putString(KEY_MY_ID, id).apply()
        return id
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
            list.add(ChatMessage(o.getString("body"), o.getBoolean("sent"), o.getLong("time"), o.optString("reaction", ""), o.optBoolean("pinned", false)))
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
            o.put("time", m.timestamp).put("reaction", m.reaction).put("pinned", m.pinned)
            arr.put(o)
        }
        prefs(ctx).edit().putString(key, arr.toString()).apply()
    }
    // ---- Utilisateurs bloqués ----
    fun getBlockedUsers(ctx: Context): MutableSet<String> =
        prefs(ctx).getStringSet(KEY_BLOCKED, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun isBlocked(ctx: Context, phone: String): Boolean =
        getBlockedUsers(ctx).contains(normalizePhone(phone))

    fun setBlocked(ctx: Context, phone: String, blocked: Boolean) {
        val set = getBlockedUsers(ctx)
        val n = normalizePhone(phone)
        if (blocked) set.add(n) else set.remove(n)
        prefs(ctx).edit().putStringSet(KEY_BLOCKED, set).apply()
    }

    // ---- Groupes ----
    fun getGroups(ctx: Context): MutableList<Group> {
        val raw = prefs(ctx).getString(KEY_GROUPS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Group>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val members = mutableListOf<Friend>()
            val ma = o.optJSONArray("members") ?: JSONArray()
            for (j in 0 until ma.length()) {
                val m = ma.getJSONObject(j)
                members.add(Friend(m.getString("phone"), m.getString("pseudo"), true))
            }
            list.add(Group(o.getString("id"), o.getString("name"), members, o.optString("creatorPhone", "")))
        }
        return list
    }

    fun getGroup(ctx: Context, id: String): Group? = getGroups(ctx).find { it.id == id }

    fun saveGroup(ctx: Context, group: Group) {
        val groups = getGroups(ctx)
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) groups[idx] = group else groups.add(group)
        val arr = JSONArray()
        groups.forEach { g ->
            val o = JSONObject().put("id", g.id).put("name", g.name).put("creatorPhone", g.creatorPhone)
            val ma = JSONArray()
            g.members.forEach { m -> ma.put(JSONObject().put("phone", m.phone).put("pseudo", m.pseudo)) }
            o.put("members", ma)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    fun removeGroupMember(ctx: Context, groupId: String, phone: String) {
        getGroup(ctx, groupId)?.let { g ->
            g.members.removeAll { normalizePhone(it.phone) == normalizePhone(phone) }
            saveGroup(ctx, g)
        }
    }

    fun addGroupMember(ctx: Context, groupId: String, friend: Friend) {
        getGroup(ctx, groupId)?.let { g ->
            if (g.members.none { normalizePhone(it.phone) == normalizePhone(friend.phone) }) g.members.add(friend.copy(confirmed = true))
            saveGroup(ctx, g)
        }
    }

    fun getGroupMessages(ctx: Context, groupId: String): MutableList<GroupMessage> {
        val raw = prefs(ctx).getString(KEY_GROUP_MSG_PREFIX + groupId, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<GroupMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(GroupMessage(groupId, o.getString("body"), o.getString("sender"), o.getBoolean("sent"), o.getLong("time"), o.optString("reaction", ""), o.optBoolean("pinned", false)))
        }
        return list
    }

    fun addGroupMessage(ctx: Context, message: GroupMessage) {
        val list = getGroupMessages(ctx, message.groupId)
        list.add(message)
        val arr = JSONArray()
        list.forEach { m -> arr.put(JSONObject().put("body", m.body).put("sender", m.senderPseudo).put("sent", m.isSent).put("time", m.timestamp).put("reaction", m.reaction).put("pinned", m.pinned)) }
        prefs(ctx).edit().putString(KEY_GROUP_MSG_PREFIX + message.groupId, arr.toString()).apply()
    }

    // ---- Réglages premium ----
    fun getStatus(ctx: Context): String = prefs(ctx).getString(KEY_STATUS, "En ligne") ?: "En ligne"
    fun setStatus(ctx: Context, value: String) { prefs(ctx).edit().putString(KEY_STATUS, value).apply() }
    fun isMuted(ctx: Context, key: String): Boolean = prefs(ctx).getStringSet(KEY_MUTES, emptySet())?.contains(key) == true
    fun setMuted(ctx: Context, key: String, muted: Boolean) {
        val set = prefs(ctx).getStringSet(KEY_MUTES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (muted) set.add(key) else set.remove(key)
        prefs(ctx).edit().putStringSet(KEY_MUTES, set).apply()
    }
    fun getAppPin(ctx: Context): String? = prefs(ctx).getString(KEY_PIN, null)
    fun setAppPin(ctx: Context, pin: String?) { prefs(ctx).edit().putString(KEY_PIN, pin).apply() }
    fun setPrivateStatus(ctx: Context, enabled: Boolean) { prefs(ctx).edit().putBoolean(KEY_PRIVACY, enabled).apply() }
    fun isPrivateStatus(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PRIVACY, false)

    fun updateMessage(ctx: Context, phone: String, index: Int, message: ChatMessage) {
        val list = getMessages(ctx, phone)
        if (index !in list.indices) return
        list[index] = message
        val arr = JSONArray()
        list.forEach { m -> arr.put(JSONObject().put("body",m.body).put("sent",m.isSent).put("time",m.timestamp).put("reaction",m.reaction).put("pinned",m.pinned)) }
        prefs(ctx).edit().putString(KEY_MSG_PREFIX + normalizePhone(phone), arr.toString()).apply()
    }
    fun deleteMessage(ctx: Context, phone: String, index: Int) {
        val list = getMessages(ctx, phone); if (index !in list.indices) return
        list.removeAt(index)
        val arr=JSONArray(); list.forEach { m -> arr.put(JSONObject().put("body",m.body).put("sent",m.isSent).put("time",m.timestamp).put("reaction",m.reaction).put("pinned",m.pinned)) }
        prefs(ctx).edit().putString(KEY_MSG_PREFIX + normalizePhone(phone), arr.toString()).apply()
    }

    fun deleteGroup(ctx: Context, groupId: String) {
        val groups=getGroups(ctx).filterNot{it.id==groupId}
        val arr=JSONArray(); groups.forEach{g->val o=JSONObject().put("id",g.id).put("name",g.name).put("creatorPhone",g.creatorPhone);val ma=JSONArray();g.members.forEach{m->ma.put(JSONObject().put("phone",m.phone).put("pseudo",m.pseudo))};o.put("members",ma);arr.put(o)}
        prefs(ctx).edit().putString(KEY_GROUPS,arr.toString()).remove(KEY_GROUP_MSG_PREFIX+groupId).apply()
    }
    fun updateGroupMessage(ctx: Context, groupId:String, index:Int, message:GroupMessage){
        val list=getGroupMessages(ctx,groupId);if(index !in list.indices)return;list[index]=message;val arr=JSONArray();list.forEach{m->arr.put(JSONObject().put("body",m.body).put("sender",m.senderPseudo).put("sent",m.isSent).put("time",m.timestamp).put("reaction",m.reaction).put("pinned",m.pinned))};prefs(ctx).edit().putString(KEY_GROUP_MSG_PREFIX+groupId,arr.toString()).apply()
    }
    fun deleteGroupMessage(ctx: Context, groupId:String,index:Int){val list=getGroupMessages(ctx,groupId);if(index !in list.indices)return;list.removeAt(index);val arr=JSONArray();list.forEach{m->arr.put(JSONObject().put("body",m.body).put("sender",m.senderPseudo).put("sent",m.isSent).put("time",m.timestamp).put("reaction",m.reaction).put("pinned",m.pinned))};prefs(ctx).edit().putString(KEY_GROUP_MSG_PREFIX+groupId,arr.toString()).apply()}

}
