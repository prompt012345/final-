package com.bubblesms.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bubblesms.app.data.ChatMessage
import com.bubblesms.app.data.Friend
import com.bubblesms.app.data.PrefsManager
import android.provider.Telephony

const val ACTION_NEW_MESSAGE = "com.bubblesms.app.NEW_MESSAGE"
const val EXTRA_PHONE = "extra_phone"

private const val PREFIX_REQ = "BUB::REQ::"
private const val PREFIX_ACC = "BUB::ACC::"
private const val PREFIX_MSG = "BUB::MSG::"
private const val CHANNEL_ID = "bubble_sms_channel"

/**
 * Intercepte les SMS entrants. Ne touche pas à la boite SMS par défaut du téléphone :
 * on lit juste une copie de chaque SMS reçu, comme n'importe quelle app tierce.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Nouveau transport : SMS de données, livrés uniquement à BubbleSMS
            Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                for (m in messages) {
                    val sender = m.originatingAddress ?: continue
                    val data = m.userData ?: continue
                    val body = BubbleSms.receiveChunk(context, sender, data) ?: continue
                    dispatch(context, sender, body)
                }
            }
            // Ancien transport (SMS texte) : gardé pour les téléphones pas encore mis à jour
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                if (messages.isEmpty()) return
                val sender = messages[0].originatingAddress ?: return
                val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
                dispatch(context, sender, fullBody)
            }
        }
    }

    private fun dispatch(context: Context, sender: String, fullBody: String) {
        when {
            fullBody.startsWith(PREFIX_REQ) -> handleRequest(context, sender, fullBody)
            fullBody.startsWith(PREFIX_ACC) -> handleAccept(context, sender, fullBody)
            fullBody.startsWith("BUB::GNEW::") -> handleGroupNew(context, sender, fullBody)
            fullBody.startsWith("BUB::GRP::") -> handleGroupMessage(context, sender, fullBody)
            fullBody.startsWith(PREFIX_MSG) -> handleMessage(context, sender, fullBody)
            else -> { /* SMS classique hors app : on ne s'en occupe pas */ }
        }
    }

    private fun handleRequest(context: Context, sender: String, body: String) {
        if (PrefsManager.isBlocked(context, sender)) return
        val theirPseudo = body.removePrefix(PREFIX_REQ)
        val existing = PrefsManager.findFriend(context, sender)
        if (existing != null) {
            // Demande croisée (les deux se sont ajoutés) ou ami déjà connu : avant, la demande
            // était ignorée et les deux restaient "en attente" pour toujours. On confirme et
            // on répond tout de suite.
            PrefsManager.confirmFriend(context, sender, existing.pseudo.ifBlank { theirPseudo })
            BubbleSms.send(context, sender, "$PREFIX_ACC${PrefsManager.getMyPseudo(context) ?: "Moi"}")
            notify(context, "Ami confirmé", "${existing.pseudo.ifBlank { theirPseudo }} est maintenant ton ami")
            return
        }
        val friend = Friend(sender, theirPseudo, confirmed = false)
        PrefsManager.addPendingRequest(context, friend)
        notify(context, "Nouvelle demande d'ami", "$theirPseudo veut t'ajouter")
    }

    private fun handleAccept(context: Context, sender: String, body: String) {
        if (PrefsManager.isBlocked(context, sender)) return
        val theirPseudo = body.removePrefix(PREFIX_ACC)
        // Avant : n'importe quel numéro pouvait s'ajouter comme ami confirmé en envoyant
        // "BUB::ACC::...". On ne confirme plus que quelqu'un à qui on avait envoyé une demande.
        val known = PrefsManager.findFriend(context, sender) ?: return
        PrefsManager.confirmFriend(context, sender, known.pseudo.ifBlank { theirPseudo })
        notify(context, "Ami confirmé", "${known.pseudo.ifBlank { theirPseudo }} a accepté ta demande")
    }

    private fun handleGroupNew(context: Context, sender: String, body: String) {
        if (PrefsManager.isBlocked(context, sender)) return
        val parts = body.split("::", limit = 6)
        if (parts.size < 6) return
        val id = parts[2]; val name = parts[3]; val creator = parts[4]; val rawMembers = parts[5]
        // Seul un ami confirmé peut nous ajouter à un groupe, et seul le créateur d'un groupe
        // existant peut le modifier (avant, n'importe quel numéro pouvait écraser un groupe).
        if (PrefsManager.findFriend(context, sender)?.confirmed != true) return
        val old = PrefsManager.getGroup(context, id)
        if (old != null && PrefsManager.normalizePhone(old.creatorPhone) != PrefsManager.normalizePhone(sender)) return
        val members = mutableListOf(Friend(sender, creator, true))
        if (rawMembers.isNotBlank()) rawMembers.split("|").forEach { item ->
            val x=item.split("~", limit=2); if(x.size==2 && members.none{PrefsManager.normalizePhone(it.phone)==PrefsManager.normalizePhone(x[0])}) members.add(Friend(x[0],x[1],true))
        }
        PrefsManager.saveGroup(context, com.bubblesms.app.data.Group(id,name,members,sender))
        notify(context, "Nouveau groupe", "Tu as été ajouté à $name", true, id)
    }

    private fun handleGroupMessage(context: Context, sender: String, body: String) {
        if (PrefsManager.isBlocked(context, sender)) return
        val parts = body.split("::", limit = 6)
        if (parts.size < 6) return
        val id=parts[2]; val senderPseudo=parts[4]; val text=parts[5]
        val group=PrefsManager.getGroup(context,id) ?: return
        if (group.members.none { PrefsManager.normalizePhone(it.phone)==PrefsManager.normalizePhone(sender) }) return
        PrefsManager.addGroupMessage(context, com.bubblesms.app.data.GroupMessage(id,text,senderPseudo,false,System.currentTimeMillis()))
        if (!PrefsManager.isMuted(context, id)) notify(context, group.name, "$senderPseudo : $text", true, id)
    }

    private fun handleMessage(context: Context, sender: String, body: String) {
        if (PrefsManager.isBlocked(context, sender)) return

        val parts = body.split("::", limit = 4)
        if (parts.size < 4) return
        val theirPseudo = parts[2]
        val text = parts[3]

        val friend = PrefsManager.findFriend(context, sender)
        if (friend == null) {
            // Message d'un numéro inconnu : on ignore (pas d'ami correspondant)
            return
        }

        PrefsManager.addMessage(context, sender, ChatMessage(text, isSent = false, timestamp = System.currentTimeMillis()))

        val localIntent = Intent(ACTION_NEW_MESSAGE).putExtra(EXTRA_PHONE, sender)
        LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)

        if (!PrefsManager.isMuted(context, sender)) {
            val title = friend.pseudo.ifBlank { theirPseudo }
            // Conversation verrouillée par PIN : on ne montre pas le texte dans la notification
            val shown = if (PrefsManager.isChatLocked(context, sender)) "Nouveau message" else text
            notify(context, title, shown, phone = if (PrefsManager.isChatLocked(context, sender)) null else friend.phone, chatPseudo = title)
        }
    }

    private fun notify(context: Context, title: String, text: String, isGroup: Boolean = false, groupId: String = "", phone: String? = null, chatPseudo: String = "") {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "BubbleSMS", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val openIntent = when {
            isGroup -> Intent(context, GroupChatActivity::class.java).putExtra("groupId", groupId)
            phone != null -> Intent(context, ChatActivity::class.java).putExtra("phone", phone).putExtra("pseudo", chatPseudo)
            else -> Intent(context, ContactsActivity::class.java)
        }
        // Code de requête unique par conversation (groupe ou ami) : sans ça, toutes les
        // notifications partagent le même PendingIntent et appuyer sur une ancienne
        // notification pouvait ouvrir la mauvaise conversation (la plus récente).
        val requestCode = (if (isGroup) "g_$groupId" else "c_${phone ?: title}").hashCode()
        val pending = PendingIntent.getActivity(
            context, requestCode, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(requestCode, notification)
    }
}
