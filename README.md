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
- Le serveur (comptes, canaux, appels) est décrit dans `serveur-comptes.sql`, déjà appliqué sur le projet Supabase « site-production-baccara ». Le script est rejouable.
- La lecture directe de la table `apk_calls` avec la clé publique est fermée : tout passe par les fonctions `app_calls` / `app_history`.
- L'adresse et la clé par défaut pointent maintenant vers le projet du site (`kmvbsntmzjkpwwqgkqhz`).
