# Appel Prédictions (APK)

Tous les fichiers sont à la racine du dépôt, sauf le workflow : GitHub exige `.github/workflows/build.yml`.
Le workflow range lui-même les fichiers dans la structure Android (dossier `proj`) au moment de compiler.
Fichiers : *.java, AndroidManifest.xml, app.gradle, build.gradle, settings.gradle, gradle.properties.

## Version 1.1 : comptes et abonnement
- Connexion par email + PIN (4 à 8 chiffres) ou empreinte digitale ; création de compte à la première utilisation.
- Le premier compte créé devient l'administrateur. Les autres doivent être validés par l'administrateur (24 h d'essai à la validation).
- Compte à rebours en haut de l'écran ; à la fin, écran bloqué avec « Payer » et « J'ai déjà payé » (capture d'écran).
- L'administrateur voit les inscriptions, les captures, et accorde +24 h / +48 h / +72 h (les durées s'additionnent).
- La clé de la base n'est visible que dans « Administration » → « Clé de la base ».
- L'historique affiche les 5 derniers appels.

## Version 1.2 : canaux par utilisateur
- À la validation d'un compte, l'administrateur **coche le ou les canaux** (la liste vient des canaux accordés sur le site, bouton « Appel APK »). Cette liste n'est visible que par l'administrateur.
- Le bouton « 📡 Canaux » permet ensuite de modifier les canaux d'un compte déjà validé.
- Un utilisateur ne voit que le **nom** de ses canaux et son téléphone ne sonne que pour les prédictions lancées dans ces canaux. L'administrateur reçoit tous les canaux (y compris les appels de test du site).
- L'historique affiche « ● Connecté » si le serveur répond, « ● Hors ligne » sinon ; les utilisateurs ne voient aucune mention de la base de données.
- Le serveur (comptes, canaux, appels) est décrit dans `serveur-comptes.sql`, à exécuter une fois dans Supabase (projet tv-kouame) > SQL Editor. Le script est rejouable.
- La lecture directe de la table `apk_calls` avec la clé publique est fermée : tout passe par les fonctions `app_calls` / `app_history`.
- L'adresse et la clé par défaut pointent vers le projet tv-kouame (`hvjgqaxejqnvgvefjhnj`). Ce projet doit être la base **principale** du site : c'est elle qui reçoit les appels (table `apk_calls`).

## Version 1.2.1
- Le message « Hors ligne » distingue maintenant un vrai problème de connexion (« vérifie ta connexion internet ») d'un serveur indisponible (« serveur indisponible, réessaie plus tard (code : …) »).
- `serveur-comptes.sql` met à niveau automatiquement une base restée en v1.1 (colonne `fails`, statut `approved`, anciennes sessions) ; il reste rejouable sans effet sur une base déjà en v1.2.

## Version 1.3 : sonnerie téléphone verrouillé + sessions gardées
- L'écran d'appel s'affiche par-dessus l'écran de verrouillage (sans demander de le déverrouiller), l'écran s'allume, la sonnerie joue en boucle.
- La surveillance garde le processeur et le Wi-Fi actifs écran éteint, se relance toute seule si le système l'arrête (réveil de secours toutes les 5 min, au redémarrage et après une mise à jour).
- Le bouton « permissions » ouvre, un par un, les réglages qui manquent : batterie « sans restriction », notifications plein écran (Android 14+), « Afficher par-dessus les autres applis ».
- Session gardée : le PIN est vérifié aussi sur le téléphone (haché), donc l'application se rouvre sans internet ; l'empreinte ouvre aussi hors ligne. Sur le serveur, la session dure 180 jours après la dernière utilisation (et non après la connexion).
