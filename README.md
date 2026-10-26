# BubbleSMS

BubbleSMS reste **gratuit et sans serveur central**.

## Modes de communication
- 💬 SMS : messages texte à distance via la carte SIM, envoyés en **SMS de données** (port 6392) : ils sont livrés uniquement à BubbleSMS et n'apparaissent pas dans l'appli de messages du système.
- 📎 Mode local Wi‑Fi : images, vidéos et fichiers sont transférés directement entre téléphones présents sur le même réseau Wi‑Fi.
- 🔎 Découverte locale : les appareils BubbleSMS visibles sur le réseau sont détectés par broadcast UDP, sans compte ni cloud.
- 🎨 Fonds personnalisés par conversation.
- 😀 Stickers/emoji en message texte.
- 📞 Appel audio : raccourci vers l'appel téléphonique classique.
- 📹 Interface d'appel vidéo local et appel de groupe local.

## Important
Le mode Wi‑Fi utilise la permission Android INTERNET uniquement pour ouvrir des **sockets locaux** entre les téléphones. L'application ne contacte aucun serveur BubbleSMS et ne nécessite aucun abonnement. Les téléphones doivent être sur le même réseau Wi‑Fi pour le partage local.

La vraie transmission vidéo/audio temps réel de type WebRTC demanderait une pile média dédiée ; cette V2 prépare l'expérience locale sans prétendre qu'un appel vidéo fonctionne à distance avec des SMS.

## Transport SMS de données (important)
- Les messages BubbleSMS ne passent plus par la boîte SMS texte du téléphone : ils partent en SMS de données vers le port `6392` (voir `BubbleSms.kt` et le `intent-filter` `DATA_SMS_RECEIVED` du manifest).
- **Les deux téléphones doivent avoir cette version** : une ancienne version ne reçoit pas les SMS de données.
- Certains opérateurs/MVNO bloquent les SMS de données : si un message n'arrive jamais, tester avec une autre SIM/opérateur.
- L'ancien format texte `BUB::...` est toujours lu à la réception (compatibilité), mais n'est plus envoyé.
