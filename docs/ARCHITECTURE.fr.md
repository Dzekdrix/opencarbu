# Architecture

🇬🇧 [English version](ARCHITECTURE.md)

## Principes

- **Lisibilité d'abord.** Chaque classe et fonction publique a un commentaire KDoc ;
  les choix non évidents sont expliqués par un commentaire « pourquoi ».
- **Léger et fluide.** Les ~10 000 stations tiennent en mémoire (quelques Mo). Le calcul
  des couleurs prend ~0,3 à 2,5 ms (mesuré sur une JVM de bureau ; à re-mesurer sur
  téléphone). La carte sera dessinée par le GPU via une couche GeoJSON MapLibre, jamais
  sous forme de milliers de vues Android.
- **Cœur testable.** La logique métier est du Kotlin pur, sans import Android : ses tests
  s'exécutent en quelques secondes sur un ordinateur.

## Couches (package `fr.opencarbu`)

| Package | Contenu | Sans Android ? |
|---|---|---|
| `domain.model` | `Station`, `FuelPrice`, `FuelType`, `BoundingBox` | oui |
| `domain.pricing` | `NationalPriceScale` (quartiles nationaux → 4 couleurs), `PriceRange` (ratio fin), `PriceColors`, `ViewportPricing` (cœur de la fonctionnalité) | oui |
| `data` | `StationMapper` (ligne du flux → `Station`), `StationRepository` (logique de cache et d'actualisation), `FeedConfig`, `OkHttpFeedDownloader`, `MoshiFeedParser` | tous sauf les deux derniers : oui |
| `ui` | `MainViewModel`, `UiState`, `MapCamera` | non |
| `ui.map` | `MapScreen` (Compose), `StationMapView` / `StationMapController` / `StationLayers` (MapLibre), `StationGeoJson` + `MapDataFactory` (purs : stations → GeoJSON) | les deux derniers : oui |

## Flux de données

1. **Au démarrage** : `MainViewModel` lit le fichier en cache (instantané, fonctionne hors ligne) et l'affiche.
2. S'il n'y a pas de cache, ou s'il est plus ancien que l'intervalle choisi par l'utilisateur (réglage, 8 heures
   par défaut, de 1 minute à 48 heures ; `DataSettings.isStale`), le flux est téléchargé en arrière-plan. Le même
   contrôle a lieu à chaque retour de l'application à l'écran (`MainActivity.onStart` → `MainViewModel.onAppOpened`),
   car un processus gardé en arrière-plan ne redémarre pas le ViewModel.
   La requête ne demande que les colonnes lues par `StationMapper` (paramètre `select=`, construit à partir de
   `StationMapper.requestedColumns`), ce qui garde le téléchargement léger. La réponse est écrite en flux
   dans un fichier temporaire.
3. Le fichier est lu en flux (un enregistrement en mémoire à la fois) pour produire des `Station`.
   Il ne remplace le cache que s'il est valide : un téléchargement raté ou corrompu ne détruit jamais de bonnes données.
4. Toutes les stations restent en mémoire ; les couleurs de la carte sont calculées à partir de cette liste (voir ci-dessous).

**Pourquoi un fichier et pas une base de données (Room) ?** L'application a toujours besoin de *toutes* les
stations pour colorer la carte, donc des requêtes ne serviraient à rien. Un fichier évite une dépendance et une
étape de compilation lourdes, et le relire au démarrage est rapide. À reconsidérer seulement si une fonction
exige des requêtes filtrées (ex. historique des prix).

## La carte

- **Moteur** : MapLibre Native (`org.maplibre.gl:android-sdk`, licence BSD, sans clé API, rendu GPU),
  utilisé directement via un `AndroidView`. Le wrapper `maplibre-compose`, plus récent, a été écarté :
  il est en version 0.x, alors que l'API du SDK classique est stable et bien documentée.
- **Fond de carte** : style vectoriel OpenFreeMap « Liberty » (gratuit, sans clé). Une seule URL dans
  `StationMapController.STYLE_URL`. Le bouton d'attribution reste affiché (exigé par les licences des données).
- **Points** : une seule source GeoJSON, trois couches GPU (`StationLayers`) : points colorés, pastille rouge
  de rupture, texte du prix. Aucune vue Android par station : ~10 000 points restent fluides.
- **Selon le zoom** : petits points sans texte quand on est loin ; à partir du zoom 8, le prix s'écrit sur les
  points (les étiquettes qui se chevauchent sont masquées, la moins chère l'emporte) et les pastilles de rupture
  apparaissent. Toutes les tailles sont des « paliers de zoom » dans `StationLayers`, faciles à ajuster.
- **Facteur de taille** : `MapDisplaySettings.stationSizeFactor` (0,5 à 2,0, 1,0 par défaut, sauvegardé sous `station_size_factor`) multiplie le rayon des points et des pastilles ainsi que la taille des prix. À 1,0 l'aspect est exactement celui des paliers écrits dans `StationLayers` ; le futur écran de réglages modifiera ce facteur.
- **Déroulement** : la carte signale la zone visible quand elle s'arrête → `MainViewModel` recalcule les points
  sur un thread d'arrière-plan (`MapDataFactory`) → le nouveau texte GeoJSON est envoyé à la carte. Un calcul
  devenu obsolète est annulé quand un plus récent arrive.
- **Polices** : le texte des prix utilise « Noto Sans Bold », fournie par le style OpenFreeMap. Si le style
  change, vérifier que cette police y existe, sinon les étiquettes disparaissent.
- **Noms de lieux** : une cinquième couche réutilise les données `place` du fond de carte (source
  `openmaptiles`) pour écrire les noms de villes/villages au-dessus des points, à tous les niveaux de zoom, en texte
  sombre semi-transparent avec un halo blanc. Les étiquettes propres du fond de carte issues de ces données (sauf pays et régions) sont retirées au chargement du style (`removeBaseMapPlaceNames`), il ne reste donc que celles-ci. Opacité par défaut 0,8 (`MapDisplaySettings`, sauvegardée sous
  `place_name_opacity` ; un écran de réglages la proposera). `textIgnorePlacement` est actif : les noms ne
  masquent jamais un prix. Le fond de carte dessine aussi ses propres noms en dessous ; c'est accepté.
  Hypothèses à revérifier si le style change : source `openmaptiles`, couche `place`, police « Noto Sans Regular ».
- **Localisation** : `ui/location`. Utilise directement le `LocationManager` d'Android (pas de Google Play
  Services, donc compatible F-Droid) et la permission de position (l'approximative suffit ; la précise est facultative et permet d'utiliser le GPS sur les téléphones sans fournisseur réseau). Tous les fournisseurs disponibles sont essayés, le premier qui répond l'emporte, délai de 15 s. Une dernière position
  de moins d'1 heure est utilisée telle quelle, sinon une position réseau récente est demandée. Au démarrage
  (une fois par lancement, silencieusement) et sur le bouton « ma position », la carte est ajustée à
  `BoundingBox.around(position, rayon)` (15 km par défaut, réglable dans les Paramètres, réglage `start_radius_km`). Un marqueur bleu indique la position. Les messages d'échec ne
  s'affichent que pour un appui explicite sur le bouton.

## La fiche station

Un appui sur un point ouvre une fiche en bas d'écran (`ui/details/StationSheet`). L'appui est résolu dans
`StationMapController.handleTap` : tous les points à moins de 20 dp du doigt sont candidats, le plus proche
l'emporte. Le contenu est calculé par `StationDetailsBuilder` (fonction pure, testée), avec les mêmes règles
d'ancienneté de prix et de rupture que les points : la fiche et la carte ne se contredisent jamais.

- **Enseigne et nom** : **le flux officiel ne fournit ni nom, ni enseigne, ni logo** ; ils viennent d'un
  fichier de référence séparé (CSV construit à partir d'OpenStreetMap, ODbL, publié par Chiffrex sur
  data.gouv.fr ; URL dans `FeedConfig.NAMES_URL`). `StationNamesRepository` le garde dans `cacheDir` et ne le
  retélécharge qu'après un délai réglable (30 jours par défaut) ; un échec conserve l'ancienne copie et est signalé avec l'erreur réelle (toast, texte dans Paramètres et Infos ; la page Infos indique aussi combien de noms sont chargés).
  Le fichier publié est compressé en bzip2 (`.csv.bz2`, reconnu à ses premiers octets) ; Android ne sait pas lire ce format, donc `Bzip2InputStream` est un petit décodeur écrit pour l'app (sans dépendance), testé sur des fichiers produits par le compresseur de référence, avec vérification des CRC. `StationNamesParser` lit trois colonnes par leur nom d'en-tête : `ref:FR:prix-carburants` (l'identifiant
  officiel = l'`id` du flux ; **pas** `osm_id`, qui est le numéro de l'objet OSM), `brand` et `name`, plus `lon`/`lat` (position OSM, qui remplace les coordonnées du flux pour les points, la distance et l'épingle de l'appli de cartes : `StationPositions`), `addr:housenumber`, `addr:street`, `addr:postcode`, `addr:city` : la fiche préfère cette adresse à celle du flux, fausse pour certaines stations (repli sur celle du flux si le fichier n'en a pas). Il gère
  les champs entre guillemets (`CsvReader` maison) et répare le texte encodé deux fois.
  `StationIdentity.lines` affiche l'enseigne puis le nom, sans répétition. La fiche les place au-dessus de
  l'adresse. Les logos ne sont pas faits : les marques déposées demandent une vérification de licence, le choix
  sûr serait un disque coloré avec les initiales de l'enseigne.
- **Ligne de coût du trajet** : sous les distances, « A€ (trajet) + B€ (plein) = C€ », suivie d'une petite note en italique rappelant que la consommation et le volume du plein se règlent dans les paramètres et, quand le calcul par la route est désactivé, d'une seconde note invitant à l'activer pour un coût de trajet plus précis (`JourneyCost`,
  aussi derrière les montants de la liste des plus proches) : le trajet est la distance (par la route une fois que le
  serveur d'itinéraires a répondu, à vol d'oiseau sinon ou quand le calcul est désactivé) fois la consommation
  configurée, aller simple, au prix de la station pour le carburant affiché sur la carte ; le plein est le volume
  configuré fois ce prix ; le total additionne les deux montants arrondis au centime. Ligne masquée si la station ne
  vend pas ce carburant ou si la position de l'utilisateur est inconnue. L'ancien avertissement sur les adresses
  approximatives a été retiré : les adresses viennent désormais du fichier de référence croisé avec OpenStreetMap.
- **Oiseau de la ligne de distance** : la distance à vol d'oiseau est racontée « à dos de <oiseau> » (`DistanceByBird`
  dans la fiche). L'oiseau est tiré à l'ouverture de la fiche (nombre aléatoire dans `rememberSaveable` : un nouvel oiseau à
  chaque ouverture, le même après une rotation) dans `assets/birds.tsv`, lu par `BirdListParser` au démarrage.
  `BirdPhraseBuilder` choisit le nom dans la langue du téléphone (français, sinon anglais ; l'anglais se rabat sur le nom
  scientifique), le petit mot (« de »/« d’ » avec les exceptions du h aspiré, « a »/« an ») et la page Wikipédia (langue du
  téléphone d'abord, sinon l'autre, sinon pas de lien). La phrase traduite contient un repère à l'endroit de l'oiseau.
  La liste complète (tous les oiseaux ayant un nom français) est générée depuis Wikidata (CC0) par
  `tools/birds/build_birds.py` (requête dans `birds.sparql`) ; le fichier du dépôt est au départ une petite liste écrite à la main.
  La même ligne indique aussi le temps que mettrait cet oiseau (« · 5 min »). `BirdFlight` estime la vitesse de chaque
  oiseau à partir de sa masse avec la loi allométrique d'Alerstam et al. 2007 (PLoS Biology 5(8) : e197) :
  vitesse = 15,9 × masse^0,13 (m/s, kg), bornée à la plage observée 8–23 m/s ; un oiseau dont la masse est inconnue vole
  à 40 km/h (la liste livrée a une masse pour 4 353 de ses 11 334 oiseaux). Les masses forment la sixième colonne facultative (en grammes) de `birds.tsv`, récupérée sur Wikidata
  (propriété P2067) par une seconde requête (`birds_mass.sparql`) ; `build_birds.py --mass-input` accepte un export de
  cette requête. C'est une estimation d'une vitesse de croisière typique, pas une mesure propre à l'espèce.
- **Distance** : pour l'instant la distance à vol d'oiseau (haversine, `GeoPoint.straightLineKmTo`), indiquée
  comme telle. La distance par la route demande un moteur d'itinéraires. Pistes open source : (a) les serveurs
  de démonstration publics OSRM/Valhalla — gratuits, sans clé, mais leurs conditions interdisent un usage
  intensif ou de production ; (b) OpenRouteService — open source, offre gratuite avec clé d'API (la clé
  devrait être embarquée) ; (c) une instance Valhalla ou OSRM hébergée par le projet — meilleure qualité et
  confidentialité, mais coût et maintenance ; (d) un moteur embarqué (Valhalla/GraphHopper avec cartes
  téléchargées) — entièrement hors ligne, mais lourd (centaines de Mo, grosse dépendance). Quel que soit le
  choix, il se placera derrière une petite interface, la distance à vol d'oiseau restant le repli.
- **Carburants** : tous ceux vendus ou en rupture récente, avec prix, date de mise à jour relative et
  absolue ; les prix anciens et les ruptures sont affichés en couleur d'erreur.
- **Bouton carte** : un lien Android `geo:` (`MapAppLauncher`), donc l'application de cartes de l'utilisateur
  s'ouvre (OsmAnd, Organic Maps, Google Maps...) et il peut y lancer l'itinéraire. Aucune API propre à une
  application n'est utilisée.

## Distance par la route (optionnelle)

Désactivée par défaut (`RoutingSettings`, Paramètres > Distance par la route) car elle envoie la position de
l'utilisateur et celle de la station à un serveur. Activée, la fiche station interroge un serveur compatible
OSRM (`data/RouteProvider`, `OsrmRouteProvider` ; par défaut l'instance publique FOSSGIS
`https://routing.openstreetmap.de/routed-car`, modifiable) via `/route/v1/driving/lon,lat;lon,lat` et affiche
« x km par la route · y min » sous la ligne de l'oiseau. `MainViewModel.roadRoute` sérialise les requêtes, les
espace d'au moins 1,1 s (le serveur public en autorise une par seconde), garde les 50 dernières réponses en
mémoire (clé : serveur, position de l'utilisateur arrondie à ~100 m, position de la station) et la fiche attend
400 ms avant de demander. Les parties pures (`OsrmRequest`, `OsrmResponseParser`, validation d'URL) ont des
tests unitaires. Les échecs s'affichent sur la fiche ; rien d'autre ne dépend de cette fonction.
  `OsrmRouteProvider` partage un seul `OkHttpClient` (connexion gardée ouverte entre les requêtes, adresses IPv4 essayées en
  premier) : seule la première requête paie le DNS et le TLS ; la durée de chaque requête est affichée dans Info › Performances
  (`TimedFeed.ROUTING`).

## Stations les plus proches

Un bouton à flèche dirigée vers la droite (dessinée, sous la loupe ; le bouton de position est descendu d'un cran)
ouvre `ui/nearest/NearestStationsPage`. Il est grisé et inerte tant que `UiState.userLocation` est nul. La liste
vient de `NearestStations.candidates` (stations ayant un prix pour le carburant choisi, hors stations « référence
seule », dans `NearestSettings.radiusKm` — 30 km par défaut, 5 à 100 km dans les Paramètres ; pré-filtre par boîte
englobante puis haversine ; 300 au plus) et de `NearestStations.rank`. Chaque ligne : nom, ville et distance à gauche ; à
droite le prix puis trois lignes alignées à droite « Coût du plein : X€ », « Coût du trajet : Y€ » et « Coût total : Z€ » (`NearestEntry.totalCostEuro`, somme des deux montants arrondis au centime comme affichés). L'en-tête de la page rappelle le volume du plein et la consommation configurés. Le coût du plein : coût du plein = `litres du plein × prix` (taille du
plein réglable, 45 L par défaut, de 5 à 120 L) et coût du trajet = `distance × consommation / 100 × prix` (aller simple ;
la consommation est un réglage, 6 L/100 km par défaut). La distance a la même source que la distance par la route de la fiche : quand elle
est activée, des requêtes OSRM `table` donnent les distances routières, les plus proches à vol d'oiseau d'abord
(`NearestStations.roadBatches`) : par groupes de 50
stations par requête (`ROAD_BATCH_SIZE`) : jusqu'à 50 stations sont répondues par la seule première requête, une liste plus
longue demande une requête de plus par tranche de 50. Le serveur public met environ 10 s par requête quel que soit le nombre
de destinations (mesuré, voir Info > Performances), donc peu de grosses requêtes remplissent la liste bien plus vite que
beaucoup de petites. La page demande les groupes l'un après l'autre et `MainViewModel.roadDistances` espace toujours les
requêtes d'au moins 1,1 s (comme `roadRoute`). Les lignes se mettent à jour après chaque réponse (la liste est
retriée, un avis indique « n stations sur m »), ce qui permet de lire les plus proches pendant l'arrivée des autres ;
une ligne pas encore répondue affiche sa distance à vol d'oiseau, signalée comme telle. Si une requête ultérieure échoue, les lignes déjà répondues
gardent leur distance routière et un avis indique que le reste est à vol d'oiseau (`OsrmTableRequest` /
`OsrmTableParser`) ; la liste s'affiche d'abord avec les distances à vol d'oiseau. Les distances routières sont mises en cache par identifiant de station (`domain/nearest/RoadDistanceCache`, tenu par le ViewModel, donc conservé à la fermeture de la page) : rouvrir à moins de 100 m de la position du calcul (et avec le même serveur) les affiche aussitôt sans requête, et seules les stations encore inconnues sont demandées ; s'éloigner davantage ou changer de serveur les oublie. Si la distance par la
route est désactivée ou la requête échoue, les distances à vol d'oiseau sont conservées et un avis le signale.
Toucher une ligne ferme la page et ouvre la station (`onNearestChosen`). Les parties pures sont testées.

## Comportement de la carte autour de la fiche station, et chargement

- **Fiche et caméra** : à l'ouverture d'une fiche, `StationSheet` signale l'ordonnée de son bord haut
  (`onTopEdgeChanged`, moins les 48 dp de la poignée). Une fois qu'il ne bouge plus (150 ms), `MapScreen` construit
  un `StationFocus` et `StationMapController.focusStation` déplace la caméra pour que la station soit au milieu de la
  partie de carte située au-dessus de la fiche (centre de la caméra décalé vers le sud du bon nombre de pixels,
  converti en degrés de latitude avec une échelle mesurée sur la carte elle-même, donc juste quelle que soit la densité de l'écran ; zoom conservé, ou porté à 10 si la station vient de la liste des plus proches).
  La fermeture de la fiche ne déplace rien : la carte reste où elle est.
  Volontairement léger : le bord de la fiche est lu via `snapshotFlow` (jamais en composition, il change à chaque image
  pendant le glissement) ; un seul déplacement par station, 400 ms ; et quand le déplacement ne fait que glisser la carte
  (pas de changement de zoom, station déjà à l'écran, zone pas déjà retenue), le contrôleur redonne à l'application la zone précédente, donc les points ne sont pas
  recalculés pendant l'ouverture (la bande découverte est sous la fiche). `refreshArea` remet l'application à jour
  450 ms après la disparition de la fiche, et tout toucher ou interruption fait remonter aussitôt la vraie zone. Le réglage « Centrer la station... » de la section Carte coupe le déplacement.
- **Flèche de chargement** : `ui/map/LoadingArrow` s'affiche au centre de l'écran (80 dp, deux fois la largeur d'un
  bouton de la carte) tant qu'aucune station n'est connue et qu'un téléchargement est en cours, ou que les stations
  sont connues mais que la carte n'a pas encore signalé sa première zone ; elle abandonne après 20 s (hors ligne
  sans fond de carte). C'est une flèche noire en anneau, dessinée au trait sur un rectangle arrondi à l'allure des boutons, affichée en
  60 images (6° d'écart), la suivante dès que 16 ms se sont écoulées, synchronisée avec le rafraîchissement de l'écran
  (`withFrameNanos`) : environ un tour par seconde sur un écran 60 Hz. Elle n'intercepte pas les touchers.

## Historique des prix et actualisation quotidienne en arrière-plan (tous deux désactivés par défaut)

**Historique** (`HistorySettings`, Paramètres > Historique des prix). `PriceHistoryRules.changesToRecord` (pur, testé)
compare les prix d'un téléchargement aux derniers prix enregistrés et ne garde que les CHANGEMENTS : un prix est
enregistré quand il n'y en a pas encore, ou quand la date de mise à jour de la station est plus récente ET que le prix
diffère. Les prix sont des millièmes d'euro entiers. `data/PriceHistoryStore` est un petit fichier SQLite (intégré à
Android, sans bibliothèque) dans `noBackupFilesDir`, donc hors de la sauvegarde cloud, dont la limite de taille pourrait
être dépassée : table `changes` (station, carburant, instant, prix ; groupée sur cette clé, environ 15 octets par ligne,
quelques centaines de ko par jour estimés pour toute la France) et table `latest` (dernier prix par station et carburant,
lue d'un coup pour décider de ce qui a changé). La conservation est un réglage (3 mois, 6 mois, 1 an, 2 ans, illimitée) ;
les lignes plus anciennes sont supprimées, mais chaque série garde sa dernière ligne, le prix en vigueur. L'enregistrement a
lieu après chaque téléchargement réussi (`MainViewModel.recordHistory` ; une erreur de stockage est silencieuse, elle ne
ressemble jamais à un téléchargement raté) et à l'activation de l'historique (à partir des prix déjà présents). La fiche
station affiche `PriceHistorySection` : une courbe en escalier dessinée sur un Canvas, avec un rond plein sur chaque prix enregistré (pas sur les points de début et de fin de la fenêtre) ; toucher un rond ouvre une petite bulle (prix et date, couleur de thème `surfaceContainerHighest` avec une ombre ; l'infobulle système ne peut pas s'ancrer sur un point) et toucher ailleurs la ferme, pour le carburant sélectionné (30
jours, 3 mois, 1 an, tout), construite par `PriceHistoryRules.curve`. Les paramètres montrent l'espace utilisé et un
bouton d'effacement (« tout rétablir » garde les données, il ne fait que désactiver les réglages). Avec un téléchargement
par jour, un prix changé deux fois dans la journée n'est vu qu'une fois.

**Structure de la base** (`price-history.db`, SQLite, schéma version 1), deux tables :

| Table `changes` (l'historique) | Type | Signification |
|---|---|---|
| `station` | INTEGER | identifiant de la station dans le flux open data |
| `fuel` | INTEGER | position du carburant dans l'énumération `FuelType` : 0 DIESEL, 1 SP95, 2 E10, 3 SP98, 4 E85, 5 LPG (ne jamais réordonner cette énumération) |
| `at` | INTEGER | instant où la station a déclaré le prix, millisecondes epoch (UTC) |
| `price` | INTEGER | millièmes d'euro par litre (1789 = 1,789 €) |

Clé primaire `(station, fuel, at)`, `WITHOUT ROWID` (la table est son propre index) ; index supplémentaire
`changes_by_time (at)`. La table `latest` (`key` INTEGER clé primaire = station × 16 + carburant, `at`, `price`) n'est
qu'une copie de travail de la dernière ligne de chaque série, pour repérer vite les changements ; elle peut être
reconstruite depuis `changes` et n'est pas exportée.

**Export** (Paramètres > Historique des prix > Exporter l'historique) : CSV, UTF-8, en-tête
`station_id,fuel,changed_at,price_eur`, une ligne par changement triée par station, carburant et date
(`PriceHistoryCsv`) : `1000001,DIESEL,2026-10-15T08:30:00Z,1.789`. Carburant par son nom, dates ISO 8601 UTC et point
décimal quelle que soit la langue du téléphone. L'utilisateur choisit la destination avec le sélecteur de fichiers
d'Android (aucune permission de stockage) ; le fichier est écrit ligne par ligne, jamais entièrement en mémoire. Il contient tout l'historique (chaque station,
carburant et changement enregistré) ; le nom du fichier porte la date.

**Import** (Paramètres > Historique des prix > Importer l'historique) : lit un fichier de ce format (n'importe quel
fichier peut être choisi ; la première ligne doit être l'en-tête `station_id,fuel,changed_at,price_eur`, les colonnes
au-delà de la quatrième sont ignorées, donc un futur export plus riche resterait lisible) et AJOUTE ses lignes
(`PriceHistoryStore.importCsv`, une seule transaction, ligne par ligne, jamais entièrement en mémoire). Rien d'existant
n'est modifié ni effacé : une ligne dont (station, carburant, instant) existe déjà est ignorée. Sont aussi ignorées : les
lignes illisibles (`PriceHistoryCsv.parseLine` : carburant inconnu, date ou nombre invalide, prix hors de 0,100 à
10,000 €/L), les dates dans le futur et les lignes plus anciennes que la durée de conservation. Ensuite `latest` est
reconstruite à partir de la ligne la plus récente de chaque série, pour que les prochains téléchargements se comparent
aux prix importés. Le résultat est annoncé par un message (ajoutés / ignorés). Usage : sauvegarder l'historique, le
transférer sur un nouveau téléphone ou le donner à un ami. Fonctionne que l'historique soit activé ou non.

**Actualisation en arrière-plan** (`data/DailyRefreshJobService`, Paramètres > Actualisation en arrière-plan). Aucun
service ne tourne en permanence : une tâche périodique `JobScheduler` (période 24 h, souplesse 6 h, pour qu'Android la
regroupe avec d'autres réveils), exigeant un réseau non facturé à l'usage (Wi-Fi ; un réglage autorise tout réseau) et une
batterie pas faible, conservée après un redémarrage (`RECEIVE_BOOT_COMPLETED`, sans demande à l'utilisateur). Elle saute le
téléchargement si le cache a moins de 12 h (l'application a été ouverte), sinon télécharge le flux dans le même fichier de
cache et enregistre les changements si l'historique est activé. Une tâche qui échoue ou que le système arrête est
relancée plus tard. Android peut la retarder (économie d'énergie, application rarement utilisée) ; elle ne s'exécute pas
si l'application a été forcée à s'arrêter. Chaque exécution menée à son terme (prix téléchargés, ou saut car les données
étaient récentes) enregistre sa date dans les préférences `settings` (`DailyRefreshJobService.lastRefreshMillis`) ; les
paramètres l'affichent sous le titre de la section (« Dernière actualisation des prix en arrière-plan le … »), en rouge
quand l'option est activée et que la date a plus d'un jour, pour voir d'un coup d'œil si Android exécute vraiment la
tâche. Elle est relue à chaque ouverture de l'application ; rien n'est affiché avant la première exécution.

## Option de test : stations absentes du flux des prix

`Paramètres > Données > Test : afficher les stations absentes du flux` (désactivée par défaut)
ajoute, via `StationPositions.apply(..., includeReferenceOnly = true)`, chaque entrée du fichier de référence
ayant une position mais pas de station dans le flux. Ce sont des `Station(isReferenceOnly = true)` sans prix,
dessinées en points violets avec un « ? » (`PriceStatus.REFERENCE_ONLY`) quel que soit le carburant, hors de
l'échelle de prix. La page Infos les compte. Défaut : `DEFAULT_SHOW_REFERENCE_ONLY_STATIONS`.

## Recherche d'adresse

Un bouton loupe (dessinée, même taille que le bouton menu, juste en dessous) ouvre une page plein écran
(`ui/search/AddressSearchPage`). La saisie (3 caractères minimum, 400 ms d'attente ; la touche
« rechercher » du clavier lance la recherche aussitôt et utilise le premier résultat, ou celui déjà affiché) interroge le géocodeur IGN Géoplateforme
(`data/AddressSearcher`, OkHttp, sans clé d'API, seul le texte saisi est envoyé).
`domain/search/AddressResponseParser` transforme la réponse GeoJSON (déjà lue en Map/List) en
`AddressResult`, avec tests unitaires. Choisir un résultat renseigne `areaToShow` (un rectangle de 10 km
autour du point, quel que soit le type de lieu), que la carte cadre comme
après une localisation.

**Identifiant de station (aide au débogage, volontairement non annoncée dans le texte d'aide du champ).** Un texte de 6 à 9 chiffres seulement (`StationIdQuery`, un zéro initial est accepté) est d'abord cherché parmi les stations connues (`MainViewModel.searchAddress`) : en cas de correspondance, l'unique résultat est « Station 1120005 · nom · code postal ville » (`AddressResult.stationId`) et aucune requête n'est envoyée ; le choisir ouvre la fiche de la station et la carte s'y rend comme pour une station choisie dans la liste des plus proches (zoom 10 au moins). Sans correspondance, ou pour un nombre plus court (un code postal) : recherche d'adresse normale.

## Le menu (Infos et Paramètres)

Un bouton « burger » (trois barres dessinées, en haut à droite sous le sélecteur de carburant) ouvre un petit
menu à deux pages (`ui/menu`), affichées en plein écran par-dessus la carte ; le bouton Retour les ferme.

- **Infos** : nombre de stations chargées, date de téléchargement, sources et licences (data.economie.gouv.fr —
  Licence Ouverte 2.0 ; OpenFreeMap / OpenMapTiles ; OpenStreetMap — ODbL ; MapLibre — BSD ; OpenCarbu —
  AGPLv3). Un appui sur une source ouvre son site (les sources incluent le canard de l'icône : SVG Repo, CC0). Sous les sources, une section **Performances** : pour le flux des
  prix et celui des noms, la durée du dernier téléchargement réussi (`FeedTiming` : temps de réponse du serveur = requête
  envoyée jusqu'aux en-têtes reçus, et durée totale jusqu'au dernier octet écrit ; mesurées par
  `OkHttpFeedDownloader.onTimed`, conservées dans les préférences `settings` par `FeedTimingPreference`, donc gardées après
  un redémarrage ; la tâche de fond enregistre aussi celle du flux des prix), et la mémoire utilisée par l'application en Mo
  (PSS du processus via `ActivityManager.getProcessMemoryInfo`, relue toutes les 2 s sur un thread d'arrière-plan,
  seulement tant que la page est affichée).
- **Icône de l'application** : une icône adaptative (`res/mipmap-anydpi-v26/ic_launcher*.xml`) : une image de 512 px (`res/drawable-nodpi/ic_launcher_foreground.png`, pompe et canard) sur un fond uni bleu ciel (`ic_launcher_background`). Le lanceur la découpe selon sa propre forme et peut la réduire, donc seuls les deux tiers centraux sont toujours visibles ; le dessin reste dans cette zone. minSdk 29 n'exige aucun fichier d'icône à l'ancienne.
- **Bord à bord** : `MainActivity` appelle `enableEdgeToEdge`, donc la carte est dessinée derrière les barres système sur toutes les versions d'Android (Android 15 l'impose de toute façon). Les deux barres sont transparentes (icônes sombres) ; `MapScreen` dessine la teinte de la barre d'état par-dessus la carte : un gris clair mélangé à 18 % de la couleur principale du thème (donc l'accent Material You sur Android 12+), à 45 % d'opacité (`STATUS_BAR_GREY`, `STATUS_BAR_TINT`, `STATUS_BAR_OPACITY`). Chaque élément superposé se décale déjà avec `statusBarsPadding` / `navigationBarsPadding`, les éléments superposés et les pages utilisent les modificateurs de `ui/Insets.kt` (`insetsAllSides`, `insetsTopAndSides`, `insetsBottomAndSides` : barres système ET encoche de caméra, où qu'elles soient ; en paysage la barre de navigation à trois boutons et l'encoche sont sur les côtés, et changent selon le sens du téléphone), et `StationMapController.setBarInsets` écarte le bouton d'attribution de MapLibre de la barre de navigation.
- **Thème** : `OpenCarbuTheme` suit les couleurs d'accentuation du système (Material You) sur Android 12+, et la palette Material standard avant ; toujours en mode clair, car le fond de carte est clair. Les couleurs de prix sur la carte sont fixes volontairement.
- **Paramètres** (sections : carburant par défaut, fraîcheur des prix, stations les plus proches, carte, données, historique des prix, actualisation en arrière-plan, distance par la route) : seuils de prix ancien et de rupture ancienne, facteur de taille des points et des prix, facteur de taille et opacité des noms de lieux, intervalle d'actualisation,
  opacité des noms de lieux, rayon de départ, interrupteur (section Carte) qui coupe le glissement de la carte vers une station touchée (`MapDisplaySettings.centerOnStation`, activé par défaut ; une station choisie dans la liste des plus proches reste affichée), carburant par défaut (« dernier utilisé » ou un carburant donné)
  et bouton d'actualisation manuelle. Chaque valeur est sauvegardée dans les préférences de l'application
  (voir `data/*Preference`) et appliquée immédiatement. Les curseurs appliquent leur valeur quand on lève le
  doigt, pour que la carte ne soit pas recalculée pendant le glissement.
- Les pages ne parlent à l'application que via `MenuActions` (quelques fonctions de rappel) et lisent `UiState`.

## Comment un point obtient sa couleur

1. La carte indique la zone visible (`BoundingBox`) quand la caméra s'arrête.
2. `ViewportPricing.compute` garde les stations visibles concernant le carburant choisi
   et attribue à chacune un **statut** (`PriceStatus`) :
   - `FRESH` : prix mis à jour depuis moins de 90 jours → couleur sur l'échelle vert→rouge ;
   - `STALE` : prix plus ancien (ou date inconnue) → point **gris**, prix toujours affiché. Le seuil par défaut
     est volontairement large : beaucoup de stations gardent un prix stable pendant des semaines (E85 surtout) ;
   - `TEMPORARILY_OUT` : la station signale une rupture temporaire de ce carburant débutée il y a
     moins de 30 jours → point **gris** avec une **pastille rouge**. Les ruptures « temporaires »
     plus anciennes sont ignorées : une station sans prix non plus n'est alors pas dessinée du tout.
3. `NationalPriceScale` est construite à partir des prix FRESH de TOUTES les stations du pays pour ce carburant
   (`ViewportPricing.nationalScale`), pas seulement des visibles : un point garde sa couleur quand on déplace ou
   zoome la carte. Les stations grises sont exclues pour ne pas la fausser. `MainViewModel` la construit une fois
   et la réutilise pendant les déplacements (reconstruite si les stations, le carburant ou les seuils changent, et
   toutes les heures).
4. La couleur est l'une des quatre `PriceBand` : **rouge** au-dessus du 3e quartile, **orange** au-dessus de la
   médiane, **jaune** au-dessus du 1er quartile, **vert** sinon (un prix égal à un quartile appartient à la
   tranche inférieure ; si tous les prix sont égaux, tout est vert). `PriceRange` (percentiles 5–95 %) fournit
   encore un `ratio` fin de 0 à 1, utilisé seulement pour départager deux étiquettes qui se chevauchent.

Les deux seuils (90 jours pour les prix, 30 pour les ruptures) sont regroupés dans `PricingSettings`, mémorisés
avec `SharedPreferences` (`PricingSettingsPreference`) et transmis de `MainViewModel` jusqu'à `ViewportPricing`.
`MainViewModel.setPricingSettings` est prêt pour le futur écran de réglages ; le modifier redessine la carte.

Le carburant sélectionné est mémorisé de la même façon (`FuelPreference`) et restauré au lancement.

## Format du flux (vérifié le 08/10/2026)

`StationMapper` a été vérifié sur deux enregistrements réels du jeu v2. L'essentiel :

- Un enregistrement par station (≈ 9 800 au total), chaque carburant dans ses propres colonnes :
  `gazole_prix` / `gazole_maj`, `sp95_…`, `e85_…`, `gplc_…`, `e10_…`, `sp98_…`.
  Un carburant non vendu ou en rupture vaut `null` dans les deux colonnes.
- Position : `geom` = `{lat, lon}` en degrés. Les colonnes `latitude` / `longitude` sont des
  **chaînes en 1e-5 degré** (`"4377300"` = 43,773°) ; le mapper gère les deux.
- `horaires_automate_24_24` vaut `"Oui"` / `"Non"`.
- Les dates de mise à jour sont en ISO-8601 (`2026-10-07T08:54:39+00:00`). Certains prix sont
  anciens (8 à 10 jours dans les échantillons) : la date sera affichée à l'utilisateur.
- **Ruptures.** `<carburant>_rupture_type` vaut `"temporaire"` ou `"definitive"`, et
  `carburants_rupture_temporaire` liste les temporaires sous forme de texte (`"Gazole;E85;E10;SP98"`).
  Un carburant en rupture n'a **aucun prix** (null) : une station peut donc avoir des ruptures et aucun prix.
  Environ 2 200 des ~9 800 stations signalent au moins une rupture temporaire, et certaines depuis plus
  d'un an : « temporaire » ne veut pas toujours dire « de courte durée ». La date de début est dans
  `<carburant>_rupture_debut` et sert à la règle des 30 jours.
- D'autres colonnes existent (horaires, services…) et sont ignorées pour l'instant.

Pour revérifier avec un téléchargement récent :

```bash
curl -sSL "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records?limit=2" -o sample.json
```

## Feuille de route

1. ~~Télécharger le flux complet et le mettre en cache.~~ Fait (à valider sur un appareil).
2. ~~Écran de carte (MapLibre, tuiles OpenFreeMap) avec points colorés + sélecteur de carburant.~~ Fait (à valider sur un appareil).
3. Détail d'une station (tous les prix, dernière mise à jour, ouverture 24h/24), position, recherche.
