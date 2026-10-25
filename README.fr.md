# OpenCarbu

🇬🇧 [English version](README.md)

Application Android open source qui affiche les stations-service françaises sur une
carte interactive. Chaque station est un point portant le prix du carburant choisi,
coloré selon le rang de son prix parmi toutes les stations de France : **vert** (quart le plus bas), **jaune**, **orange**, **rouge** (quart le plus haut).

**État :** développement initial (v0.21.4). Fait : logique centrale (échelle de prix,
couleurs, lecture des données), téléchargement et cache local, écran de carte avec sélecteur de carburant, avec tests unitaires. Aussi : noms de lieux sur la carte, position de l'utilisateur (la carte s'ouvre sur un rayon de 15 km autour de vous, réglable). Fiche station au toucher (adresse, distance, carburants avec dates de mise à jour, ouverture dans une application de cartes). Menu avec pages Infos (données, sources, licences) et Paramètres (avec un bouton « rétablir les valeurs par défaut »). Enseigne et nom de la station sur la fiche (fichier de référence, actualisé tous les 30 jours par défaut). La distance est racontée « à dos de <oiseau> » (un oiseau différent à chaque fois, avec lien Wikipédia). Recherche d'adresse (bouton loupe sous le menu) qui centre la carte sur le lieu choisi (géocodeur IGN). Distance par la route optionnelle (désactivée par défaut, OSRM). Bouton « stations les plus proches » (flèche, sous la loupe ; grisé tant que la position est inconnue) : stations dans un rayon réglable (30 km par défaut), classées par distance, avec prix et coût en carburant du trajet.

## Données

Données ouvertes officielles « Prix des carburants en France – Flux instantané v2 »,
publiées par le ministère de l'Économie sur [data.economie.gouv.fr](https://www.data.economie.gouv.fr/explore/assets/prix-des-carburants-en-france-flux-instantane-v2/)
sous Licence Ouverte 2.0. Le flux est actualisé environ toutes les 10 minutes.

## Compilation

Fonctionne sur Android 10 (API 29) et plus récent.

Prérequis : [Android Studio](https://developer.android.com/studio) (il inclut le JDK et le SDK Android).

1. Ouvrir ce dossier dans Android Studio et laisser Gradle se synchroniser.
2. Lancer sur un émulateur ou un téléphone (débogage USB activé) : bouton **Run**, ou `./gradlew installDebug`.
3. Lancer les tests unitaires (sans appareil) : `./gradlew test`.
4. Essayer la version release minifiée sur un téléphone (pas encore de clé de publication : elle est signée avec la clé de debug, pour test uniquement) : `./gradlew assembleRelease -PdebugSignRelease`, puis installer `app/build/outputs/apk/release/app-release.apk`.

Code source : https://github.com/dzekdrix/opencarbu

## Contribuer

Voir [CONTRIBUTING.md](CONTRIBUTING.md) et le [journal des modifications](CHANGELOG.fr.md).

## Documentation

Voir [docs/ARCHITECTURE.fr.md](docs/ARCHITECTURE.fr.md).

## Licence

[GNU AGPL v3](LICENSE). Données cartographiques © contributeurs OpenStreetMap (attribution affichée dans l'application).

