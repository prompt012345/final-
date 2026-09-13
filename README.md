# BubbleSMS

Appli Android de messagerie via SMS (carte SIM), sans aucun serveur.

## Comment ça marche

- Aucune permission Internet dans le Manifest : tout passe par `SmsManager`
  (envoi) et un `BroadcastReceiver` (réception) — donc via ton opérateur/SIM.
- Au premier lancement, tu choisis ton **pseudo** (stocké en local sur le
  téléphone, jamais envoyé à un serveur).
- **Ajouter un ami** : comme il n'existe pas d'annuaire central sans serveur,
  il faut forcément connaître son numéro une seule fois, au moment de
  l'ajouter. Tu entres son numéro + le pseudo que tu veux lui donner. L'appli
  lui envoie automatiquement un SMS caché du type `BUB::REQ::TonPseudo`.
  Quand il accepte dans son appli, elle renvoie `BUB::ACC::SonPseudo` et vous
  êtes amis. Ensuite, dans toute l'appli (liste, conversation), tu ne vois
  plus que les pseudos, jamais le numéro.
- Les messages échangés utilisent le préfixe `BUB::MSG::pseudo::texte`, donc
  ils n'apparaissent pas mélangés à tes vrais SMS dans l'appli SMS par
  défaut du téléphone (elle les recevra quand même, mais avec ce préfixe
  visible — c'est la seule limite technique sans être l'appli SMS par
  défaut du système).
- Design : bulles de discussion aux coins très arrondis (bleu à droite pour
  toi, gris clair à gauche pour ton ami), façon "fluid bubble".

## Pour l'ouvrir et le compiler

1. Installe **Android Studio** (gratuit).
2. `Ouvrir un projet existant` → sélectionne le dossier `BubbleSMS`.
3. Laisse Gradle synchroniser (ça télécharge les dépendances, connexion
   Internet nécessaire uniquement sur TON PC pour builder, pas sur le
   téléphone à l'usage).
4. Branche un téléphone Android (ou lance un émulateur avec support SMS)
   et clique sur ▶️ Run.
5. Accepte les permissions SMS/notifications au premier lancement.

## Limites à connaître

- Ce n'est pas l'appli SMS par défaut : les messages de BubbleSMS
  apparaîtront aussi (avec leur préfixe technique) dans l'appli SMS native
  du téléphone. Pour les masquer complètement il faudrait faire de
  BubbleSMS l'appli SMS par défaut du système (faisable, mais plus lourd
  à mettre en place — dis-moi si tu veux que je l'ajoute).
- Fonctionne uniquement entre deux téléphones qui ont l'appli installée.
- Les tarifs SMS de ton opérateur s'appliquent normalement.
