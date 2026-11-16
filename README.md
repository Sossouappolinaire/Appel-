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
