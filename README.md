# BubbleSMS

BubbleSMS reste **gratuit et sans serveur central**.

## Modes de communication
- 💬 SMS : messages texte à distance via la carte SIM.
- 📎 Mode local Wi‑Fi : images, vidéos et fichiers sont transférés directement entre téléphones présents sur le même réseau Wi‑Fi.
- 🔎 Découverte locale : les appareils BubbleSMS visibles sur le réseau sont détectés par broadcast UDP, sans compte ni cloud.
- 🎨 Fonds personnalisés par conversation.
- 😀 Stickers/emoji en message texte.
- 📞 Appel audio : raccourci vers l'appel téléphonique classique.
- 📹 Interface d'appel vidéo local et appel de groupe local.

## Important
Le mode Wi‑Fi utilise la permission Android INTERNET uniquement pour ouvrir des **sockets locaux** entre les téléphones. L'application ne contacte aucun serveur BubbleSMS et ne nécessite aucun abonnement. Les téléphones doivent être sur le même réseau Wi‑Fi pour le partage local.

La vraie transmission vidéo/audio temps réel de type WebRTC demanderait une pile média dédiée ; cette V2 prépare l'expérience locale sans prétendre qu'un appel vidéo fonctionne à distance avec des SMS.
