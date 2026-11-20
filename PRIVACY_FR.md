[🇬🇧 English](PRIVACY.md) · 🇫🇷 Français

# Politique de confidentialité de MyBuoy

*Dernière mise à jour : 19 novembre 2026.*

MyBuoy est conçu pour fonctionner hors ligne, et ce qu'il sait de vous reste sur
votre téléphone. Il n'y a ni compte, ni publicité, ni statistiques d'utilisation,
ni rapport de plantage. MyBuoy ne collecte aucune donnée personnelle, ne la vend
pas et ne la transmet à personne.

## Ce qui reste sur votre téléphone

Votre position, votre cap et votre vitesse, vos marques personnelles, vos
alarmes, vos tables de déviation et vos réglages sont enregistrés uniquement sur
l'appareil, dans le stockage propre à l'application. Rien de tout cela n'est
envoyé à Golden-Block ni à qui que ce soit. Désinstaller l'application, ou
effacer ses données, les supprime.

L'application écrit des messages techniques dans le journal système d'Android
pour le débogage. Ils restent sur le téléphone et ne sont jamais envoyés.

## Quand l'application se connecte

Certaines fonctions ont besoin du réseau. Dans chaque cas, l'application n'envoie
que ce dont la fonction a besoin, et le serveur voit aussi votre adresse IP,
comme pour toute connexion à internet.

**Données de carte.** Quand une zone n'est pas disponible hors ligne,
l'application demande à un serveur de données OpenStreetMap (l'API Overpass, ou
l'API OpenStreetMap en mode en ligne) les objets de cette zone. La requête
contient les limites de la zone affichée : le serveur peut donc en déduire
approximativement l'endroit que vous regardez. Le serveur utilisé se change dans
les paramètres développeur.

**Recherche de lieu.** Quand vous cherchez un lieu par son nom, le texte saisi
est envoyé au service de recherche Nominatim d'OpenStreetMap.

**Cartes hors ligne et données de marée.** Le téléchargement des cartes hors
ligne ou du modèle de marée récupère des fichiers sur golden-block.fr et sur
GitHub. Ces requêtes ne contiennent aucune position.

**Partage d'une marque.** Quand vous choisissez de partager une marque,
l'application construit un lien vers golden-block.fr qui contient le nom, les
coordonnées, l'altitude et l'icône de la marque, et le confie à
l'application que vous choisissez. Le serveur de Golden-Block ne reçoit ces
informations que si quelqu'un ouvre le lien. La page web n'utilise aucun cookie.

Chacun de ces services applique sa propre politique de confidentialité :
[OpenStreetMap Foundation](https://osmfoundation.org/wiki/Privacy_Policy) pour
l'API OpenStreetMap et Nominatim, l'exploitant du serveur Overpass utilisé, et
[GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

## Autorisations

**La localisation** sert à afficher votre position, suivre votre trajet et
déclencher vos alarmes. Elle n'est jamais envoyée, sauf indirectement à travers
les zones de carte décrites plus haut.

**Les notifications** servent aux alarmes et au téléchargement des cartes hors
ligne, qui tourne en service de premier plan.

**Le stockage** sert à lire et écrire les cartes hors ligne et les fichiers de
marée, qui peuvent se trouver sur une carte mémoire.

**Internet** ne sert qu'aux fonctions listées ci-dessus.

## Enfants

MyBuoy ne collecte de donnée personnelle de personne, enfants compris.

## Modifications

Si MyBuoy devait un jour traiter des données personnelles (par exemple pour un
futur mode multi-utilisateur), cette politique serait mise à jour avant la sortie
de la fonction, et le changement annoncé dans les notes de version.

## Contact

Pour toute question sur cette politique : privacy@golden-block.fr
