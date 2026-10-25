# Journal des modifications

Les plus récentes d'abord. Les numéros suivent `versionName` ; chaque journal F-Droid est dans
`fastlane/metadata/android/<langue>/changelogs/<versionCode>.txt`.

## 0.21.4 (versionCode 55)
- Stations les plus proches : les distances par la route sont demandées par groupes de 50 stations (une requête pour
  jusqu'à 50 stations, une de plus par tranche de 50), les plus proches d'abord. Le serveur public met environ 10 s par
  requête quelle que soit sa taille.

## 0.21.3 (54)
- Version de test : une station par requête d'itinéraire.

## 0.21.2 (53)
- Client d'itinéraires partagé entre les requêtes (connexion gardée, adresses IPv4 d'abord).
- Info > Performances : durée de la dernière requête au serveur d'itinéraires.

## 0.21.1 (52)
- Source de l'icône créditée dans Info ; comportement des distances par la route de la page des stations proches.

## Avant 0.21.1
Historique des prix (facultatif, export/import CSV), actualisation en arrière-plan, section Performances, affichage bord à
bord, renommage en OpenCarbu, stations proches avec coût du plein et du trajet, distance par la route facultative,
recherche d'adresse, enseigne et nom des stations.
