[🇬🇧 English](ROADMAP.md) · 🇫🇷 Français

# ![MyBuoy](design/app-icon/logo48x48.svg) MyBuoy — Feuille de route

Tout ce qui est prévu, en cours et déjà fait, directement issu de la liste de travail. Dernière génération le 21 novembre 2026.

## 💰 Budget

0 € disponibles 😭 🙏

## 📋 Tâches

- [ ] **🚧 Revoir les alarmes radar + étendre aux personal marks**  
      *`feature №7` · [issue #1](https://github.com/BlackDuckyGB/MyBuoy/issues/1)*  
      L'application prévient quand on approche d'une marque, selon la distance et le temps restant avant de l'atteindre. Les marques personnelles s'en servent désormais aussi : chacune a son propre interrupteur d'alarme, et une marque armée se reconnaît d'un coup d'œil sur la carte comme dans les listes. Reste à étendre le procédé aux marques marines.
- [ ] **🚧 Finir les images des personal marks**  
      *`feature №30`*  
      Compléter le choix d'images proposé à la création d'une marque personnelle, aujourd'hui trop restreint.
- [ ] **🚧 Finir la page Support the development (#33)**  
      *`feature №37`*  
      Une page explique ce qu'est MyBuoy, pourquoi le projet est ouvert, comment y contribuer et à quoi sert l'argent reçu, avec un lien vers le canal Telegram. Reste à arrêter les moyens de paiement.
- [ ] **🚧 Template de création d'avatar**  
      *`feature №58`*  
      Une planche vierge et une marche à suivre pour dessiner un nouvel avatar sans repartir de zéro : formats, ordre des directions, repères de calage et étapes d'intégration.
- [ ] **🚧 Alarme marque : son coupé qui repart au reload de chunk + un clic = une seule action**  
      *`fix №59`*  
      Le son coupé sur une marque repartait dès que la carte se rechargeait, et un même appui pouvait déclencher plusieurs actions à la fois. Une marque rendue muette le reste maintenant toute la session, l'alerte visuelle continuant de signaler le danger.
- [ ] **🚧 Flèche de bord d'écran vers les marques en alarme hors champ**  
      *`feature №60`*  
      Une marque en alarme hors du champ n'était signalée par rien. Une flèche collée au bord de l'écran pointe désormais vers elle, accompagnée de la distance et, pour un radar, de la vitesse limite.
- [ ] **🚧 Nouvel algorithme des modes de déplacement (état mesuré vs mode appliqué)**  
      *`feature №61`*  
      L'avatar changeait dès que la vitesse variait : une voiture arrêtée à un feu redevenait un marcheur. Le mode de déplacement est désormais confirmé dans la durée avant d'être appliqué, et ne redescend plus au moindre ralentissement.
- [ ] **🚧 Publier la liste des tâches sur GitHub en Markdown lisible**  
      *`feature №62`*  
      La feuille de route ne vit aujourd'hui que dans un tableur, sur un seul ordinateur. L'objectif est de la publier sur GitHub sous une forme lisible par n'importe qui, avec pour chaque tâche son état, son type et une description.
- [ ] **🚧 Recherche de lieux et de POI (bouton loupe)**  
      *`feature №67` · [issue #2](https://github.com/BlackDuckyGB/MyBuoy/issues/2)*  
      Un bouton loupe pour chercher un lieu par son nom, le phare de Trézien ou un supermarché, et en obtenir les coordonnées qu'il ne reste plus qu'à enregistrer comme marque.
- [ ] **🚧 Documenter le contrat de l'API manifest des cartes offline**  
      *`feature №69`*  
      Décrire ce qu'un serveur doit répondre pour distribuer des cartes hors ligne, afin que chacun puisse héberger les siennes — la contrepartie de l'adresse devenue réglable.
- [ ] **🚧 Bouton balai : effacer les mesures sans quitter le mode pan**  
      *`feature №75`*  
      Un point de mesure pose par erreur pouvait etre efface, mais la carte sautait aussitot sur la position du bateau et la vue que l'on regardait etait perdue. Le balai efface desormais le dessin en laissant la carte exactement ou elle est, et le compte a rebours du recentrage automatique repart de cinq secondes. Les deux actions du bouton ont desormais chacune leur son, joue au clic uniquement : un balayage feutre pour l'effacement, un bloup pour le recentrage. Un recentrage automatique, lui, reste silencieux.
- [ ] **🚧 Écran d'attente au lancement (splash screen)**  
      *`feature №76`*  
      Au lancement, la carte met un moment à s'afficher. Un écran d'attente occupe ce temps : un bout de cordage se noue tout seul, tiré au hasard entre un nœud de chaise et un nœud de huit, pendant que s'affichent des astuces sur les gestes que l'application ne montre nulle part.
- [ ] **🚧 Paramètres des alarmes : son au choix et alarmes radar débrayables**  
      *`feature №81`*  
      Chacune des trois alarmes (radar, marque personnelle, zone de surveillance) reçoit son propre réglage de son : aucun son et signal visuel seul, un son de la bibliothèque livrée avec l'application, ou un fichier choisi sur le téléphone. Un second réglage permet de couper complètement les alarmes radar, pour qui ne veut être prévenu que de ses propres marques.
- [ ] **🚧 Enregistrer l’altitude d’une marque**  
      *`feature №82`*  
      Une marque ne retient aujourd’hui que sa latitude et sa longitude : l’altitude du lieu, pourtant connue du GPS au moment où la marque est posée, est perdue. La conserver donnerait du sens à l’application en montagne, où un point ne se décrit pas seulement par sa position au sol, et permettrait de la partager avec la position.
- [ ] **🚧 Distances et caps tenant compte de l’altitude**  
      *`feature №83`*  
      Toutes les distances affichées sont mesurées à plat, comme si le terrain était horizontal. En montagne, un point situé trois cents mètres plus haut est annoncé bien plus proche qu’il ne l’est réellement à la marche. Il s’agit de reprendre ce calcul pour qu’il tienne compte du dénivelé, et sans doute de l’afficher à part.
- [ ] **🚧 Partager la position d’une marque**  
      *`feature №84`*  
      Depuis le détail d’une marque, un bouton ouvre la feuille de partage du téléphone et envoie le lieu à qui l’on veut : le nom, les coordonnées et l’icône voyagent avec. Le destinataire qui a MyBuoy voit la marque s’ouvrir telle quelle ; les autres tombent sur une page web qui montre la position et propose de l’ouvrir dans leur application de carte. Sert aussi à partager sa position courante, en ouvrant une marque sans l’enregistrer.
- [ ] **🚧 Mode économie de ressources pour téléphones modestes**  
      *`feature №85`*  
      Sur un téléphone d'entrée de gamme, la page des paramètres et la carte deviennent saccadées. Un interrupteur unique, à activer soi-même dans les paramètres, coupe alors ce qui est purement décoratif sans toucher à ce qui sert à naviguer : les vignettes d'avatar cessent de s'animer, les sons d'agrément se taisent et la carte se redessine moins souvent. Les alarmes, la position et les mesures restent intactes.
- [ ] **🚧 Cache local des données Overpass**  
      *`feature №86`*  
      Chaque changement de chunk redemande à Overpass les données du secteur, même si l'application vient tout juste de les télécharger. Elles sont désormais conservées dans un dossier de cache dont le nom porte la zone couverte, et relues au lieu d'être retéléchargées : le passage d'un chunk au suivant devient plus vif, l'attente du démarrage raccourcit, et un forfait internet modeste s'use moins vite. Une nouvelle page Maintenance montre l'état du cache et offre plusieurs façons de le vider. Le double-clic sur la carte, qui force un rechargement, ignore volontairement le cache.
- [ ] **🚧 Double-clic : messages de rechargement trompeurs, et remise à zéro du mode de déplacement**  
      *`fix №89`*  
      Un double-clic sur la carte recharge les données autour de la position courante et l'annonce en deux temps, la demande puis la confirmation. Seulement la confirmation s'affiche aussi quand personne n'a rien demandé, notamment au lancement de l'application, ce qui laisse croire à un geste que l'on n'a pas fait. Elle ne paraîtra plus que pour un vrai double-clic, et pour la fin d'un téléchargement de carte hors ligne, où elle a du sens. Le même double-clic remet par ailleurs le mode de déplacement à l'indéterminé et réarme ses temporisations, pour repartir d'une page blanche quand l'avatar affiché ne correspond plus à ce que l'on fait.
- [ ] **🚧 Performances d'affichage des terres hors ligne**  
      *`fix №91`*  
      Avec la carte des terres hors ligne installée, l'application rame sur un téléphone modeste : elle plafonne vers 18 à 20 images par seconde au lieu des 25 habituelles, alors que sans cette carte elle tourne à pleine cadence. Les terres sont désormais dessinées d'un seul tenant et sans le contour qui les grossissait, ce qui supprime près des deux tiers de leur coût. Trois indicateurs de temps ont été ajoutés à la liste de mise au point de l'écran principal, pour pouvoir mesurer sans câble. En cours de test sur téléphone.
- [ ] **🚧 Horaires et hauteurs de marée hors ligne**  
      *`feature №92`*  
      L'application calcule désormais les marées partout dans le monde, sans réseau et sans abonnement à un service de marée. Une page dédiée, accessible par le menu, donne l'heure et la hauteur des deux prochaines marées, une courbe que l'on fait défiler au doigt sur trente jours, un pendule de marée, et une zone verte indiquant les meilleurs moments pour pêcher. La donnée nécessaire est un fichier de 20 Mo téléchargé séparément, à côté des cartes hors ligne.
- [ ] **🚧 Cap d'orientation du téléphone**  
      *`feature №94`*  
      Le téléphone sait dans quelle direction il est tenu grâce à son capteur d'orientation (gyroscope, accéléromètre et boussole combinés). L'application calcule désormais ce cap d'orientation, rafraîchi dix fois par seconde et stabilisé pour ne pas trembler quand on ne bouge pas, tout en réagissant aussitôt dès qu'on tourne. À l'arrêt, quand le GPS ne peut plus donner de direction, c'est lui qui oriente les flèches vers les marks et le cap affiché : il suffit de pivoter sur soi-même pour voir les flèches tourner. En déplacement, le cap reste celui du GPS. Une prochaine étape s'en servira aussi pour stabiliser le cap GPS à faible vitesse. Les capteurs de mouvement du téléphone (accéléromètre et gyroscope) rendent aussi la détection de l'arrêt plus rapide et plus fiable : quand le téléphone ne bouge plus, l'arrêt est reconnu tout de suite et la dérive du GPS ne fait plus croire à un déplacement ; dès qu'on repart, l'application le voit en un peu plus d'une seconde. L'état affiché devient « Stopped + Still » quand on est arrêté et parfaitement immobile.
- [ ] **🚧 Ordre d'affichage des marks perso**  
      *`feature №97`*  
      Aujourd'hui les marks sont dessinées dans l'ordre de la liste (alarme, sélection, distance, titre) : la mark sélectionnée ou en alarme est dessinée en premier et passe donc sous les autres. Nouvel ordre : du nord au sud, puis d'ouest en est à hauteur égale ; les marks en alarme ensuite, par-dessus ; la mark principale sélectionnée en tout dernier.
- [ ] **🚧 Mode streamer (anonymat à l'écran)**  
      *`feature №98`*  
      Pour tourner les vidéos YouTube sans montrer où l'on est : un interrupteur « Streamer mode » dans les paramètres développeur. Les coordonnées affichées sont décalées de 1000 à 5000 km (décalage tiré au hasard à chaque lancement), l'éditeur de mark montre ces coordonnées en lecture seule, et les noms de la carte (rues, routes, lieux, balises) sont remplacés par des noms inventés.
- [ ] **🚧 Page Soleil et Lune hors ligne**  
      *`feature №99`*  
      La page Soleil et Lune donne, sans réseau, où se trouvent le Soleil et la Lune au-dessus de soi. Une carte du ciel les montre à leur place, chacun sur la route qu'il suit, avec les heures de lever et de coucher, les trois crépuscules, la phase de la Lune dessinée telle qu'elle apparaît vraiment, sa distance et son âge. Une courbe de marée terrestre en bas de page se fait glisser sur un mois, et tout ce qui est au-dessus suit le repère : on voit le ciel de n'importe quel instant. La page se tient à jour toute seule quand on la laisse ouverte, et elle marche aussi bien chez soi que pour un lieu choisi ailleurs dans le monde, mémorisé séparément de celui des marées.
- [ ] **💰 Finir et gérer le zoom / changement d'échelle**  
      *`feature №4` · ⏳ en attente de financement*  
      Terminer le zoom de la carte pour passer proprement d'une vue large à une vue rapprochée, en gardant un affichage lisible à chaque échelle.
- [ ] **💰 Cartes mode offline**  
      *`feature №5` · ⏳ en attente de financement*  
      Naviguer sans réseau : les cartes sont stockées dans le téléphone, ce qui est indispensable en mer où la couverture mobile s'arrête vite.
- [ ] **💰 Chargement dynamique des chunks hors zone GPS**  
      *`feature №10` · ⏳ en attente de financement*  
      La carte n'est chargée qu'autour de sa propre position. Il faut aussi charger la zone que l'on va regarder en faisant glisser la carte, pour pouvoir explorer plus loin.
- [ ] **💰 Zoom/dézoom automatique selon la proximité d'une marque**  
      *`feature №14` · ⏳ en attente de financement*  
      La carte se rapproche d'elle-même à mesure que l'on approche d'une marque, et s'éloigne quand on s'en écarte. Un simple appui désactive ce comportement.
- [ ] **💰 Zoom par pincement à 2 doigts (pinch-to-zoom)**  
      *`feature №15` · ⏳ en attente de financement*  
      Changer l'échelle de la carte en écartant ou en rapprochant deux doigts, le geste que tout le monde attend sur un téléphone.
- [ ] **Mode d'orientation course-up / heading-up**  
      *`feature №16`*  
      Orienter la carte dans le sens de la marche, la direction suivie vers le haut de l'écran, au lieu du nord toujours en haut. Un bouton bascule d'un mode à l'autre.
- [ ] **💰 Mode multi-utilisateur avec serveur**  
      *`feature №18` · ⏳ en attente de financement*  
      Un serveur pour partager des marques entre plusieurs utilisateurs et distribuer les cartes hors ligne : chacun retrouve les marques des autres, par exemple entre bateaux d'une même sortie.
- [ ] **Ajouter un 'release number' basé sur l'UID de commit git**  
      *`feature №21`*  
      Compléter le numéro de version par l'identifiant exact de la version du code d'où provient l'application, pour savoir sans ambiguïté ce qui tourne sur un téléphone.
- [ ] **Remplir la feuille de réglages terrain Predict/Npts**  
      *`feature №23`*  
      Relever sur le terrain, vitesse par vitesse, les réglages qui donnent le meilleur affichage, afin que l'application les choisisse ensuite toute seule.
- [ ] **Afficher/comparer la vitesse hardware**  
      *`feature №24`*  
      Afficher la vitesse donnée directement par le récepteur GPS à côté de celle que calcule l'application, pour comparer les deux et retenir la plus juste.
- [ ] **Proposer les images seamarks à la création/modif d'une personal mark**  
      *`feature №31` · [issue #4](https://github.com/BlackDuckyGB/MyBuoy/issues/4)*  
      Pouvoir choisir, pour une marque personnelle, les icônes des sea marks — bouées, balises marines, feux — en plus des images déjà proposées.
- [ ] **💰 Télécharger les chunks voisins en bord de chunk lors du pan doigt**  
      *`feature №32` · ⏳ en attente de financement*  
      Quand on fait glisser la carte jusqu'au bord de la zone chargée, charger d'avance la zone voisine pour que le déplacement ne bute pas sur du vide.
- [ ] **Documentation en vidéo : chaîne YouTube et tutoriels**  
      *`feature №34`*  
      Montrer l'application en vidéo plutôt que de l'expliquer par écrit : première installation, autorisation d'une application hors magasin, téléchargement de la carte hors ligne, puis les gestes du quotidien. Des sous-titres permettent de suivre dans sa langue.
- [ ] **Téléporter le chibi2 au changement de chunk (bbox)**  
      *`fix №35`*  
      Au passage d'une zone de carte à la suivante, l'avatar doit rester au même endroit à l'écran au lieu de sauter.
- [ ] **Seamarks portées par des ways (navigation\_line / restricted\_area)**  
      *`feature №42`*  
      Certaines informations marines ne sont pas des objets ponctuels mais des lignes et des surfaces : alignements de navigation, zones réglementées. Elles ne sont pas affichées aujourd'hui et demandent un dessin propre.
- [ ] **Radars : reconnaître enforcement=\* (radars de feu rouge)**  
      *`fix №43` · [issue #5](https://github.com/BlackDuckyGB/MyBuoy/issues/5)*  
      Les radars de feu rouge ne sont pas décrits comme les radars de vitesse dans les données cartographiques, et l'application les ignore : environ un sur sept manque à l'appel.
- [ ] **Radars : tenir compte du tag direction (pas d'alarme à contresens)**  
      *`fix №44` · [issue #6](https://github.com/BlackDuckyGB/MyBuoy/issues/6)*  
      Un radar ne contrôle que dans un sens. L'application alarme aujourd'hui même lorsqu'on le croise à contresens, ce qui donne de fausses alertes ; il faut comparer son orientation au cap suivi.
- [ ] **💰 Requête large alarmes Overpass (seamarks + radars, recul avant écran)**  
      *`feature №46` · ⏳ en attente de financement*  
      Charger les objets susceptibles de déclencher une alarme, sea marks et radars, sur une zone bien plus large que l'écran, afin d'être prévenu avant même qu'ils apparaissent sur la carte.
- [ ] **Gestion de routes**  
      *`feature №56`*  
      Enchaîner plusieurs marques pour composer une route, puis la suivre : point de passage actif, cap à tenir, écart par rapport à la route, distance et heure d'arrivée. Le contenu exact reste à définir ensemble.
- [ ] **Mode sombre suivant le réglage du téléphone**  
      *`feature №73` · [issue #3](https://github.com/BlackDuckyGB/MyBuoy/issues/3)*  
      Une version sombre de l'application, qui suit le réglage du téléphone — lequel bascule lui-même selon l'heure ou le coucher du soleil — pour ne pas éblouir de nuit, en mer comme sur la route.
- [ ] **Journaliser les chemins de type highway non gérés (tracés en rouge)**  
      *`feature №78`*  
      Les chemins dont le type n'est pas connu de l'application, par exemple une aire de repos ou une voie de bus, sont dessinés en rouge faute de mieux. L'idée est d'enregistrer au passage leurs coordonnées et leurs caractéristiques dans un fichier, pour dresser la liste de ce qui manque et décider ensuite comment les représenter.
- [ ] **Stabilité du cap et de la vitesse à faible vitesse**  
      *`feature №79`*  
      À l'allure d'un marcheur, deux relevés GPS séparés d'une seconde sont si proches que l'imprécision de chacun suffit à fausser la direction et la vitesse calculées. Il s'agit de reprendre ce calcul pour qu'il reste juste quand on avance lentement, sans devenir mou quand on accélère.
- [ ] **Chevauchement des panneaux d'alarme à trois marques ou plus**  
      *`fix №80`*  
      Quand plusieurs marques signalent en même temps et au même endroit du bord de l'écran, leurs panneaux peuvent se recouvrir. Le cas de deux panneaux est traité, celui de trois ou plus reste à faire.
- [ ] **Chunk hors ligne quand le téléchargement ne passe plus**  
      *`feature №87`*  
      En mer ou en zone blanche, dès que l'on quitte la zone déjà chargée l'application devient inutilisable : la carte reste figée sur un secteur dont on est sorti, et au lancement elle ne va pas plus loin que l'écran d'attente. Elle bascule désormais sur une zone hors ligne, construite avec ce qui est disponible sur le téléphone, le trait de côte et les marques, au lieu de ne rien montrer du tout. Cette zone se sait incomplète : tant que le réseau ne revient pas, l'application retente de la remplacer par la vraie.
- [ ] **Option pour garder la barre de navigation à l'écran**  
      *`feature №88`*  
      Le mode immersif efface la barre de boutons d'Android sur l'écran principal, ce qui gêne qui s'en sert pour revenir en arrière ou changer d'application. Un réglage la garde affichée en permanence, comme elle l'est déjà sur les autres pages. La carte continue alors de se dessiner jusqu'au bas de l'écran, sous la barre, qui devient un voile blanc translucide au lieu d'une bande opaque ; les commandes, elles, se décalent pour ne pas se retrouver dessous, en portrait comme en paysage. La barre du haut reste cachée.
- [ ] **💰 Découper les polygones de terre en amont, à la génération de la base**  
      *`feature №90` · ⏳ en attente de financement*  
      Les terres de la carte hors ligne sont stockées en morceaux découpés sur une grille d'un degré, soit environ 111 km de côté, alors que l'écran en montre 200 m. Un morceau aussi grand n'est pas dessiné du tout : là où il porte le trait de côte, la terre n'apparaît pas. Il s'agit de redécouper ces morceaux en pièces de taille raisonnable au moment où la base est fabriquée, pour que l'application n'ait plus qu'à les afficher.
- [ ] **Exporter et réimporter les marks et les réglages**  
      *`feature №93`*  
      Quand on désinstalle l'application, ou qu'on efface ses données, les marks personnels et les réglages sont perdus définitivement : rien ne les stocke ailleurs que dans le dossier privé de l'application, qu'Android supprime avec elle. Il s'agit d'ajouter un bouton qui écrit tout cela dans un fichier que l'utilisateur range où il veut, et un second qui le relit.
- [ ] **Profondeur d'affichage : trier les objets du jeu par latitude**  
      *`feature №95`*  
      Aujourd'hui l'ordre d'affichage est fixe : carte et marks tout au fond, puis les chibis, le GPS, et les bullets par-dessus tout. Un chibi passe donc toujours derrière le marqueur, même s'il se trouve au sud, et une bullet toujours devant. Il faudrait peindre marks, chibis, bullets et GPS dans une passe commune triée par latitude décroissante (y croissant en coordonnées d'aire) pour obtenir l'effet de fausse 3D : ce qui est au nord passe derrière, ce qui est au sud passe devant.
- [ ] **Détecter « dans l'eau » sur les lacs, étangs et rivières**  
      *`feature №96`*  
      Aujourd'hui l'appli ne sait dire si on est dans l'eau qu'à partir du trait de côte (la base des terres hors ligne). Sur un lac ou un étang à l'intérieur des terres, elle répond donc « dans les terres » alors que la carte montre de l'eau. Il faudrait que la réponse corresponde toujours à ce qu'on voit à l'écran, îles comprises, et aussi quand une zone (habitations, par exemple) est dessinée par-dessus une grande étendue d'eau.
- [ ] **Identifier l'auteur d'une marque partagée**  
      *`feature №100`*  
      Pour le mode multi-utilisateur et le partage de marques, chaque marque doit pouvoir dire qui l'a créée, sans rien révéler de personnel : un identifiant propre à l'installation, tiré au hasard par l'application, et un pseudo choisi par l'utilisateur.
- [ ] **Fines lignes aux jointures des tuiles de polygones**  
      *`fix №101`*  
      Sur la carte, de fines lignes claires (parfois grises) apparaissent, droites, horizontales et verticales, là où deux morceaux d'un même polygone d'eau ou de terre se rejoignent. Elles viennent du découpage des grands polygones en tuiles, fait pour les performances d'affichage.
- [x] **Masquer la barre de navigation (mode immersif)**  
      *`feature №1`*  
      La carte occupe tout l'écran : la barre de boutons d'Android s'efface, et un glissement depuis le haut la fait revenir.
- [x] **Stabiliser position & direction**  
      *`feature №2`*  
      Le point GPS brut saute et tremble. L'application lisse la position et le cap affichés pour que le déplacement reste régulier, y compris à l'allure d'un marcheur.
- [x] **Cône d'incertitude pour la direction**  
      *`feature №3`*  
      La direction n'est jamais connue exactement. Plutôt qu'un trait unique, l'écran affiche un cône dont l'ouverture montre à quel point la direction est fiable à cet instant.
- [x] **Investiguer la disparition des sea marks**  
      *`feature №6`*  
      Certaines sea marks — balises et bouées marines — présentes dans les données cartographiques n'apparaissent pas à l'écran. Il s'agit de comprendre pourquoi avant de corriger.
- [x] **Fluidifier le déplacement du personnage**  
      *`feature №8`*  
      L'avatar glisse d'un mouvement continu au lieu de sauter d'un endroit à l'autre à chaque relevé GPS.
- [x] **Défilement inertiel/élastique (fling) au pan**  
      *`feature №9`*  
      Quand on lance la carte d'un geste du doigt, elle continue sur sa lancée et ralentit doucement au lieu de s'arrêter net.
- [x] **Afficher le cap actuel en degrés**  
      *`feature №11`*  
      Le cap suivi s'affiche en degrés en bas à droite. Un appui bascule entre nord vrai et nord magnétique, la déclinaison du lieu étant appliquée.
- [x] **Afficher le cap à prendre vers la marque sélectionnée**  
      *`feature №12`*  
      Un panneau en haut à gauche indique la direction à suivre pour rejoindre la marque sélectionnée ; un appui passe à la marque suivante, de la plus proche à la plus éloignée.
- [x] **Afficher distance + ETA vers la marque sélectionnée**  
      *`feature №13`*  
      Sous le cap, l'écran affiche la distance qui reste jusqu'à la marque sélectionnée et le temps qu'il faudra pour l'atteindre à l'allure actuelle.
- [x] **Ajout / modification de marque**  
      *`feature №17`*  
      Créer une marque et la modifier : saisie des coordonnées dans les formats courants, bouton pour reprendre la position actuelle, ouverture de l'éditeur par appui long, aussi bien depuis la liste de proximité que depuis la carte. Déplacer une marque se fait en modifiant ses coordonnées dans l'éditeur, ou d'un bouton pour la poser là où l'on se trouve.
- [x] **Finir l'indicateur de marque sélectionnée**  
      *`feature №19`*  
      Le nom et la distance de la marque sélectionnée s'affichent le long du trait qui y mène, et restent lisibles quand la marque sort de l'écran ; rien n'est écrit s'il n'y a pas la place.
- [x] **Gestion de version du logiciel**  
      *`feature №20`*  
      L'application porte un numéro de version au format Majeur.Mineur.build, les deux premiers fixés à la main et le dernier incrémenté automatiquement à chaque compilation.
- [x] **Configurer un remote git (GitHub) + push**  
      *`feature №22`*  
      Le code est publié sur GitHub sous licence AGPL-3.0, avec une licence commerciale en alternative : textes juridiques, accord de contribution (CLA) signé via un robot, dépôt neuf sans l'historique privé et mention des sources cartographiques.
- [x] **Zone de surveillance (mouillage / couloir de louvoyage)**  
      *`feature №25`*  
      Délimiter une zone autour de soi et de ses marques : tout ce qui est en dehors est grisé et une alarme sonne dès qu'on en sort, ce qui permet au mouillage de détecter une ancre qui dérape. La zone se règle à deux doigts, en orientation comme en taille.
- [x] **Bouton de recentrage manuel sur la position**  
      *`feature №26`*  
      Un bouton ramène l'affichage sur la position actuelle. Il n'apparaît que si l'on a déplacé la carte au doigt, donc au moment où il sert.
- [x] **Débit de téléchargement des maps offline (land\_polygons)**  
      *`fix №27`*  
      Le téléchargement d'une carte hors ligne était lent au regard de sa taille. La manière de récupérer le fichier a été revue pour tirer le meilleur parti de la connexion, la reprise après coupure restant possible.
- [x] **Téléchargement map interrompu si l'app n'est pas active**  
      *`fix №28`*  
      Le téléchargement d'une carte s'arrêtait dès que l'on quittait l'application. Il continue désormais en arrière-plan, avec une notification qui en montre l'avancement.
- [x] **DELETE map offline : ré-interroger le backend sans redémarrer**  
      *`fix №29`*  
      Après la suppression d'une carte hors ligne, il fallait quitter et relancer l'application pour pouvoir la retélécharger. La liste des cartes disponibles se rafraîchit maintenant toute seule.
- [x] **Burger menu à la place du bouton M (pages supplémentaires)**  
      *`feature №33`*  
      Un bouton en forme de trois traits ouvre un menu vers les quatre pages de l'application : marques personnelles, cartes hors ligne, paramètres et soutien au développement.
- [x] **Finir la page Parameters (#33)**  
      *`feature №36`*  
      Une page de réglages rassemble les paramètres jusque-là cachés derrière des boutons de mise au point : distances et délais d'alarme, format des coordonnées, tracé du parcours, choix de l'avatar, plus une section réservée aux réglages avancés.
- [x] **Flèche de direction sur les personal marks**  
      *`feature №38`*  
      Dans la liste des marques personnelles, une flèche indique vers où se trouve chaque marque depuis la position actuelle, parmi les huit directions de la rose des vents.
- [x] **Migrer le mode Online de l'API OSM 0.6 vers Overpass**  
      *`fix №39`*  
      En ligne, l'application puisait ses données cartographiques dans le service d'édition d'OpenStreetMap, qui n'est pas prévu pour cet usage et pourrait la bloquer une fois le projet public. Elle bascule vers le service prévu pour la consultation, sans rien perdre au passage.
- [x] **Alerte visuelle périphérique (en complément de l'alarme sonore)**  
      *`feature №40`*  
      En plus du son, le pourtour de l'écran pulse en rouge quand une alarme se déclenche. Couper le son ne l'éteint pas : l'alerte reste visible tant que la situation dure.
- [x] **Rotation écran : alarme relancée/empilée + fuite mémoire (GameSurface/SoundPool recréés)**  
      *`fix №41`*  
      Tourner le téléphone relançait les alarmes en cours et les empilait, tout en consommant de la mémoire à chaque rotation. L'application encaisse maintenant la rotation sans se reconstruire, en gardant la vue là où elle était.
- [x] **Indicateur de position hors-écran pendant le pan (dot rouge + distance)**  
      *`feature №45`*  
      Quand on explore la carte au doigt, on perd de vue où l'on se trouve. Un repère rouge collé au bord de l'écran montre la direction de la position réelle, avec la distance qui l'en sépare.
- [x] **Footer de notifications universel (erreur / info)**  
      *`feature №47`*  
      Un bandeau apparaît en bas de n'importe quel écran pour annoncer une erreur en rouge ou une information en bleu, puis s'efface tout seul au bout de quelques secondes.
- [x] **Fix NPE quand aucun chunk chargé (rendu null-safe)**  
      *`fix №48`*  
      Tant qu'aucune carte n'était chargée, l'application plantait en boucle au lieu d'afficher simplement un fond de mer en attendant.
- [x] **Fix « in the water » au relancement (course chunk vs maps)**  
      *`fix №49`*  
      Au lancement à terre, l'application se croyait dans l'eau pendant un moment : elle dessinait la carte avant d'avoir ouvert les données de côtes. Elle les attend désormais.
- [x] **Double-tap sur la carte = forcer un reload de chunk**  
      *`feature №50`*  
      Un double appui sur la carte force le rechargement de la zone en cours, avec un message de confirmation — utile quand l'affichage semble figé ou incomplet.
- [x] **Mettre en avant la marque principale sélectionnée**  
      *`feature №51`*  
      Quand plusieurs marques sont sélectionnées, celle dont le cap est affiché garde son aspect normal tandis que les autres sont estompées, pour la retrouver d'un coup d'œil.
- [x] **Viseurs de mesure & création de marques (mode pan)**  
      *`feature №52`*  
      En mode déplacement, des viseurs apparaissent sur la carte : un appui dessus crée une marque à l'endroit visé, un appui à côté mesure une distance de proche en proche, chaque segment portant sa longueur et son temps de parcours.
- [x] **Fix mode balai + retour d'appli : chunk perdu et blocage du changement de chunk**  
      *`fix №53`*  
      En revenant d'une autre application alors qu'une mesure était en cours, la carte disparaissait et ne se rechargeait plus jamais. Le lien avec l'affichage n'est désormais plus rompu, et la vue est restituée telle qu'on l'avait laissée.
- [x] **Cartes de déviation compas (remplacer la correction de déclinaison manuelle)**  
      *`feature №54`*  
      Un compas de bateau ne montre jamais le nord exact : chaque bord a ses erreurs propres, relevées cap par cap. L'application enregistre plusieurs compas avec leur courbe de déviation et corrige en conséquence tous les caps affichés.
- [x] **Bandeau d'identité : version, copyright et licence dans la page Support**  
      *`feature №55`*  
      La page Support the development fait aussi office de page « À propos » : elle affiche le nom de l'application, son numéro de version, le copyright Golden-Block et la licence sous laquelle elle est distribuée.
- [x] **Créer les avatars Middle (av\_ride\_) et Fast (av\_drive\_)**  
      *`feature №57`*  
      L'application choisit l'avatar selon l'allure : bateau sur l'eau, marcheur à pied, et deux catégories plus rapides qui n'ont encore aucun dessin. Il reste à les créer.
- [x] **Perte de GPS : vitesse figée sur la dernière valeur (No GPS)**  
      *`fix №63`*  
      En entrant dans un bâtiment, la vitesse restait figée sur sa dernière valeur au lieu de retomber à zéro, et la position continuait de dériver. L'application surveille maintenant l'âge du dernier point reçu.
- [x] **Mode 3 viseurs : le 1er clic choisit le viseur, les 2 autres disparaissent**  
      *`feature №64`*  
      Avec trois viseurs à l'écran, la mesure devenait confuse. Le premier appui retient le viseur le plus proche et les deux autres s'effacent, pour ne suivre qu'une seule chaîne de mesure.
- [x] **Ouvrir MyBuoy depuis une position partagée (URI geo:)**  
      *`feature №65`*  
      Quand une position est partagée depuis un message, un site ou une autre application, MyBuoy figure désormais dans la liste des applications capables de l'ouvrir, et l'éditeur de marque s'ouvre pré-rempli.
- [x] **Distances des marques tronquées dans la liste de proximité**  
      *`fix №66`*  
      Dans la liste des marques proches, une distance un peu longue était coupée à l'affichage. Le nombre de décimales s'adapte maintenant à la place disponible.
- [x] **Endpoints Overpass et Golden-Block en parametres (Developer)**  
      *`feature №68`*  
      Les adresses des serveurs interrogés par l'application étaient figées dans le code. Elles se modifient maintenant dans les réglages avancés, pour pouvoir en changer sans recompiler.
- [x] **Mode fenêtré : bande blanche en haut, pan inopérant et commandes sous les barres**  
      *`fix №70`*  
      En écran partagé, une bande blanche occupait le haut, la carte ne se déplaçait plus au doigt et les commandes se retrouvaient sous les barres du système. La carte occupe désormais tout le cadre, et seules les commandes s'écartent des barres.
- [x] **Glissement démarré sur une commande : rendre le geste à la carte**  
      *`fix №71`*  
      Poser le doigt sur un bouton puis glisser ne faisait rien du tout : le geste restait prisonnier du bouton. Il rend maintenant la main à la carte dès que le doigt se déplace.
- [x] **Redimensionnement en mode fenetre : echelle des objets, pan et chaines de mesure**  
      *`fix №72`*  
      En redimensionnant la fenêtre, la côte et les objets de la carte se décalaient par rapport au bateau jusqu'au chargement suivant. Tout est désormais remis à l'échelle au moment du redimensionnement, chaînes de mesure comprises.
- [x] **Bouton de partage de l'app (page Support)**  
      *`feature №74`*  
      Un bouton unique en haut de la page Support ouvre la feuille de partage d'Android et envoie le lien du site. Une phrase et une fleche animee invitent a l'utiliser.
- [x] **Zone de surveillance mal placée avec plusieurs marques : alarme immédiate**  
      *`fix №77`*  
      Quand la zone de surveillance est créée avec plusieurs marques sélectionnées, elle se place parfois de travers ou trop loin, au point que le bateau se retrouve en dehors dès sa création : l'alarme de sortie se déclenche aussitôt.
