/*
 * MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
 * Copyright (C) 2026 Golden-Block
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * For commercial licensing (use without AGPL obligations), contact:
 * licence@golden-block.fr
 */
package com.gb.mybuoy;

// Mode de deplacement : eau (bateau) ou terre lent / moyen / rapide.
//
// On distingue deux notions.
//
//   ETAT : ce que l'on MESURE a l'instant t. Le milieu (eau/terre, donne par les polygones de cote)
//          et, a terre, la bande de vitesse (< 8 km/h, 8-50, > 50). Il bouge en permanence.
//
//   MODE : ce que l'on APPLIQUE (le skin affiche). Il ne suit l'etat que sous condition de duree,
//          et il ne DESCEND jamais tout seul.
//
// Le mode ne peut donc que monter (pieton -> velo -> voiture) ; s'arreter a un feu ou entrer dans
// l'eau ne le fait pas retomber. La seule remise a zero est un arret prolonge, qui renvoie a l'etat
// indetermine (MODE_UNKNOWN), d'ou l'on se requalifie normalement.
//
// Trois temporisations, mutuellement exclusives (une seule tourne a la fois) :
//
//   UNKNOWN_CONFIRM_MS : indetermine ET en mouvement -> au bout de 10 s on adopte l'etat courant.
//                        Remise a zero si l'on s'arrete ou si le milieu change. Elle ne s'arme qu'en
//                        mouvement : c'est le garde-fou du lancement, ou un jump de chunk ou un GPS
//                        instable ne doit pas nous coller en mode voiture.
//
//   UPGRADE_CONFIRM_MS : mode terrestre etabli et vitesse au-dessus du cran courant -> au bout de 3 s
//                        on monte. Anti-pic GPS : voir upgrade() pour la regle exacte.
//
//   STOP_RESET_MS      : mode etabli ET a l'arret -> au bout de 60 s, retour en indetermine. Remise
//                        a zero des que l'on rebouge.
//
// La classe est volontairement pure (aucune dependance Android, le temps est passe en parametre) :
// elle est ainsi testable directement en JUnit, cf. TravelModeTest.
public class TravelMode {

    // Etat de base au lancement : on ne sait pas encore. Hors de l'echelle des CAT_* d'AvatarCatalog.
    public static final int MODE_UNKNOWN = -1;

    public static final long UNKNOWN_CONFIRM_MS = 10000;
    public static final long UPGRADE_CONFIRM_MS = 3000;
    public static final long STOP_RESET_MS      = 60000;

    // Mode applique. MODE_UNKNOWN tant que rien n'est confirme.
    private int _mode = MODE_UNKNOWN;

    // Etat mesure au dernier update. Sert de skin tant que l'on est indetermine.
    private int _state = AvatarCatalog.CAT_WALK;

    // Debut de la fenetre de confirmation en cours. 0 = tempo desarmee.
    private long    _unknownSinceMs = 0;
    private boolean _unknownInLand  = true;   // milieu au moment de l'armement, pour detecter un changement
    private long    _stopSinceMs    = 0;
    private long    _upgradeSinceMs = 0;
    private int     _upgradeMin     = MODE_UNKNOWN;  // plus petit cran vu depuis l'armement

    public int getMode() {
        return _mode;
    }

    // #89: back to undetermined ON DEMAND (double-tap on the map). Does exactly what the 60 s stop
    // branch of updateEstablished() does, no more and no less: the mode falls and both timers of an
    // established mode are disarmed. Once a mode is established the medium is no longer consulted --
    // crossing a bridge puts you "in the water" while keeping the land skin, which is intended -- so
    // the only way to requalify was to wait a minute at a standstill; this shortcut saves that wait
    // when the skin on screen no longer matches.
    // _state is NOT touched: it is the state measured right now, and it serves as the skin while we
    // are undetermined, so giving it an arbitrary value would make the avatar flicker.
    public void reset() {
        _mode = MODE_UNKNOWN;
        _stopSinceMs    = 0;
        _upgradeSinceMs = 0;
    }

    public int getState() {
        return _state;
    }

    // Categorie d'avatar a afficher : le mode etabli, ou l'etat instantane tant qu'on est indetermine.
    public int category() {
        return (_mode == MODE_UNKNOWN) ? _state : _mode;
    }

    // Un tick de la machine. nowMs doit etre une horloge monotone (elapsedRealtime), pas l'heure
    // murale. stopped vient du detecteur d'arret de l'AppManager, qui marche a toute vitesse.
    public void update(long nowMs, boolean inLand, double speedKm, boolean stopped) {
        // Etat instantane. A terre, l'hysteresis de landCategory evite que le skin clignote quand on
        // roule pile sur un seuil ; elle ne sert que la, l'affichage temps reel de l'indetermine.
        _state = inLand
                ? AvatarCatalog.landCategory(speedKm, _state)
                : AvatarCatalog.CAT_SEA;

        if( _mode == MODE_UNKNOWN )
            updateUnknown(nowMs, inLand, stopped);
        else
            updateEstablished(nowMs, speedKm, stopped);
    }

    // Indetermine : on cherche a confirmer un etat pour en faire un mode.
    private void updateUnknown(long nowMs, boolean inLand, boolean stopped) {
        // A l'arret rien ne se decide : la tempo ne s'arme qu'au demarrage.
        if( stopped ) {
            _unknownSinceMs = 0;
            return;
        }

        // Premier tick en mouvement, ou changement de milieu : la fenetre repart de zero.
        if( (_unknownSinceMs == 0) || (inLand != _unknownInLand) ) {
            _unknownSinceMs = nowMs;
            _unknownInLand  = inLand;
        }

        // Confirme : on entre directement dans la bande courante, sans traverser les crans du dessous.
        if( (nowMs - _unknownSinceMs) >= UNKNOWN_CONFIRM_MS ) {
            _mode = _state;
            _unknownSinceMs = 0;
            _stopSinceMs    = 0;
            _upgradeSinceMs = 0;
        }
    }

    // Mode etabli : il ne peut que monter, ou etre remis a zero par un arret prolonge.
    private void updateEstablished(long nowMs, double speedKm, boolean stopped) {
        if( stopped ) {
            if( _stopSinceMs == 0 )
                _stopSinceMs = nowMs;

            if( (nowMs - _stopSinceMs) >= STOP_RESET_MS ) {
                _mode = MODE_UNKNOWN;
                _stopSinceMs    = 0;
                _upgradeSinceMs = 0;
                return;
            }
        } else {
            _stopSinceMs = 0;
        }

        upgrade(nowMs, speedKm);
    }

    private void upgrade(long nowMs, double speedKm) {
        // Le mode eau ne monte pas : les crans sont des bandes de vitesse TERRESTRES, et le milieu
        // n'est plus consulte une fois un mode etabli. Un hors-bord a 60 km/h reste donc un bateau.
        if( _mode == AvatarCatalog.CAT_SEA ) {
            _upgradeSinceMs = 0;
            return;
        }

        int candidate = speedBand(speedKm);

        // Rien au-dessus du cran courant : pas de downgrade, la fenetre se desarme.
        if( candidate <= _mode ) {
            _upgradeSinceMs = 0;
            return;
        }

        if( _upgradeSinceMs == 0 ) {
            _upgradeSinceMs = nowMs;
            _upgradeMin     = candidate;
        } else if( candidate < _upgradeMin ) {
            _upgradeMin = candidate;
        }

        // On monte au cran le plus haut que TOUTES les mesures de la fenetre ont atteint, c'est a dire
        // au minimum de la fenetre. Un pic isole (5, 55, 5 km/h) casse la fenetre des le retour sous le
        // seuil et ne promeut rien ; une vraie acceleration (10, 30, 60) promeut en moyen tout de suite,
        // puis en rapide 3 s plus tard, sans etre penalisee par la traversee des bandes.
        if( (nowMs - _upgradeSinceMs) >= UPGRADE_CONFIRM_MS ) {
            _mode = _upgradeMin;
            _upgradeSinceMs = 0;
        }
    }

    // Libelle d'un mode, pour les traces.
    public static String name(int mode) {
        switch( mode ) {
            case AvatarCatalog.CAT_SEA:   return "water";
            case AvatarCatalog.CAT_WALK:  return "slow";
            case AvatarCatalog.CAT_RIDE:  return "middle";
            case AvatarCatalog.CAT_DRIVE: return "fast";
            default:                      return "unknown";
        }
    }

    // Bande de vitesse terrestre nue, sans hysteresis : la promotion ne redescend jamais, une
    // hysteresis n'aurait rien a stabiliser ici.
    public static int speedBand(double speedKm) {
        if( speedKm < AvatarCatalog.SPEED_SLOW_MAX )
            return AvatarCatalog.CAT_WALK;

        if( speedKm < AvatarCatalog.SPEED_MID_MAX )
            return AvatarCatalog.CAT_RIDE;

        return AvatarCatalog.CAT_DRIVE;
    }
}
