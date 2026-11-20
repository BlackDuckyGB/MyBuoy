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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// Tests de la machine a etats du mode de deplacement. Le temps etant un simple parametre, on
// deroule les scenarios a 1 Hz (la cadence reelle du thread qui l'alimente) sans rien attendre.
public class TravelModeTest {

    private static final boolean LAND  = true;
    private static final boolean WATER = false;
    private static final boolean STOP  = true;
    private static final boolean MOVE  = false;

    private final TravelMode _mode = new TravelMode();
    private long _nowMs = 1000;   // on ne demarre pas a 0 : une tempo "desarmee" vaut 0

    // Deroule N ticks a 1 Hz dans des conditions constantes. Attention au comptage : le premier tick
    // d'une condition ARME la tempo (0 s ecoulee), donc couvrir 10 s demande 11 ticks.
    private void run(int ticks, boolean inLand, double speedKm, boolean stopped) {
        for( int i = 0; i < ticks; i++ ) {
            _mode.update(_nowMs, inLand, speedKm, stopped);
            _nowMs += 1000;
        }
    }

    // ---- Etat indetermine -------------------------------------------------------------------

    @Test
    public void startsUnknownAndShowsTheInstantState() {
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());
        assertEquals(AvatarCatalog.CAT_WALK, _mode.category());

        // Indetermine : le skin suit l'etat instantane, sans attendre.
        run(1, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.category());
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        run(1, WATER, 3.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.category());
    }

    @Test
    public void stoppedNeverLeavesUnknown() {
        // La tempo de 10 s ne s'arme qu'en mouvement : a l'arret on reste indetermine, indefiniment.
        run(300, LAND, 0.0, STOP);
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());
    }

    @Test
    public void confirmsWaterAfterTenSeconds() {
        run(11, WATER, 6.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.getMode());
    }

    @Test
    public void entersDirectlyInTheRightBand() {
        // Depuis indetermine on entre dans la bande courante, sans traverser lent puis moyen.
        run(11, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());
    }

    @Test
    public void tenSecondsIsTheThreshold() {
        run(10, LAND, 10.0, MOVE);
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        run(1, LAND, 10.0, MOVE);
        assertEquals(AvatarCatalog.CAT_RIDE, _mode.getMode());
    }

    @Test
    public void aStopRearmsTheUnknownTimer() {
        run(10, LAND, 10.0, MOVE);
        run(1, LAND, 0.0, STOP);          // desarme
        run(10, LAND, 10.0, MOVE);        // le compte reprend a zero
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        run(1, LAND, 10.0, MOVE);
        assertEquals(AvatarCatalog.CAT_RIDE, _mode.getMode());
    }

    @Test
    public void aMediumChangeRearmsTheUnknownTimer() {
        run(10, LAND, 10.0, MOVE);
        run(10, WATER, 10.0, MOVE);       // changement de milieu : la fenetre repart
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        run(1, WATER, 10.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.getMode());
    }

    // ---- Pas de downgrade -------------------------------------------------------------------

    @Test
    public void neverDowngradesOnSpeed() {
        run(11, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());

        // Feu rouge, bouchon, deceleration : on reste en voiture.
        run(600, LAND, 5.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.category());
    }

    @Test
    public void neverSwitchesToWaterWithoutALongStop() {
        run(11, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());

        // Une fois un mode etabli, le milieu n'est plus consulte du tout.
        run(600, WATER, 3.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());
    }

    // ---- Remise a zero par arret prolonge ----------------------------------------------------

    @Test
    public void aOneMinuteStopGoesBackToUnknown() {
        run(11, LAND, 60.0, MOVE);

        run(60, LAND, 0.0, STOP);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());

        run(1, LAND, 0.0, STOP);
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());
    }

    // #89: the map's double-tap calls reset(), which must be worth the minute at a standstill --
    // without the wait, and without touching the instant skin.
    @Test
    public void aResetGoesBackToUnknownAtOnce() {
        run(11, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());

        _mode.reset();
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        // Undetermined again: the skin follows the measured state, and another 10 s of continuous
        // movement is needed to settle on a mode.
        run(1, WATER, 5.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.category());
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        run(10, WATER, 5.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.getMode());
    }

    @Test
    public void movingAgainRearmsTheStopTimer() {
        run(11, LAND, 60.0, MOVE);

        run(60, LAND, 0.0, STOP);
        run(1, LAND, 5.0, MOVE);          // on rebouge : le compteur repart de zero
        run(60, LAND, 0.0, STOP);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());

        run(1, LAND, 0.0, STOP);
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());
    }

    @Test
    public void requalifiesAfterTheReset() {
        // Voiture -> arret d'une minute au port -> on embarque : le bateau peut enfin s'imposer.
        run(11, LAND, 60.0, MOVE);
        run(61, LAND, 0.0, STOP);
        assertEquals(TravelMode.MODE_UNKNOWN, _mode.getMode());

        run(11, WATER, 6.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.getMode());
    }

    // ---- Montee de cran, mode deja etabli ----------------------------------------------------

    @Test
    public void upgradesAfterThreeSeconds() {
        run(11, LAND, 5.0, MOVE);
        assertEquals(AvatarCatalog.CAT_WALK, _mode.getMode());

        run(3, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_WALK, _mode.getMode());

        run(1, LAND, 60.0, MOVE);         // 3 s soutenues : on saute directement en rapide
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());
    }

    @Test
    public void ignoresAnIsolatedSpeedSpike() {
        run(11, LAND, 5.0, MOVE);
        assertEquals(AvatarCatalog.CAT_WALK, _mode.getMode());

        // 5, 55, 5 km/h : le pic casse la fenetre, rien n'est promu.
        run(1, LAND, 55.0, MOVE);
        run(1, LAND, 5.0, MOVE);
        run(60, LAND, 5.0, MOVE);
        assertEquals(AvatarCatalog.CAT_WALK, _mode.getMode());
    }

    @Test
    public void aRealAccelerationIsNotPenalizedByCrossingBands() {
        run(11, LAND, 5.0, MOVE);

        // 10, 30, 60 : toute la fenetre est au-dessus de 8, donc moyen des les 3 s...
        run(1, LAND, 10.0, MOVE);
        run(1, LAND, 30.0, MOVE);
        run(1, LAND, 60.0, MOVE);
        run(1, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_RIDE, _mode.getMode());

        // ...puis rapide 3 s plus tard, la fenetre etant alors entierement au-dessus de 50.
        run(4, LAND, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_DRIVE, _mode.getMode());
    }

    @Test
    public void waterModeNeverUpgrades() {
        run(11, WATER, 6.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.getMode());

        // Un hors-bord a 60 km/h reste un bateau.
        run(600, WATER, 60.0, MOVE);
        assertEquals(AvatarCatalog.CAT_SEA, _mode.getMode());
    }

    // ---- Hysteresis, conservee pour le skin de l'indetermine ---------------------------------

    @Test
    public void hysteresisStillSmoothsTheUnknownSkin() {
        // Pile sur le seuil de 8 km/h : sans hysteresis le skin clignoterait a chaque mesure.
        run(1, LAND, 7.5, MOVE);
        assertEquals(AvatarCatalog.CAT_WALK, _mode.category());

        run(1, LAND, 8.5, MOVE);          // < 8 + 1 : pas encore de bascule
        assertEquals(AvatarCatalog.CAT_WALK, _mode.category());

        run(1, LAND, 9.5, MOVE);          // franchi
        assertEquals(AvatarCatalog.CAT_RIDE, _mode.category());

        run(1, LAND, 7.5, MOVE);          // > 8 - 1 : on ne redescend pas encore
        assertEquals(AvatarCatalog.CAT_RIDE, _mode.category());

        run(1, LAND, 6.5, MOVE);
        assertEquals(AvatarCatalog.CAT_WALK, _mode.category());
    }
}
