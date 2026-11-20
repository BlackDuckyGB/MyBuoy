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

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * #71 : racine de l'ecran principal, et seul ancetre commun de la carte (dans le GBScrollView) et
 * des commandes posees par-dessus (burger, vitesse, cap, recentrage, "+", zone, panneau mark...).
 *
 * Android choisit AU MOMENT DE L'APPUI la vue qui recevra le geste, et ce choix est definitif :
 * un doigt pose sur la vitesse puis glisse ne deplacait donc pas la carte, il ne faisait rien du
 * tout, le clic etant de toute facon annule des que le doigt s'eloigne. Sur un ecran reduit
 * (fenetre, split), ou ces commandes occupent proportionnellement plus de place, le geste rate
 * souvent. Seul un PARENT peut reprendre un geste en cours, via onInterceptTouchEvent : d'ou
 * cette classe.
 *
 * Elle ne connait rien de ce que la carte fait des evenements (viseurs, chibis, alarmes, zone,
 * 2 doigts) : elle decide seulement A QUI le geste appartient, et le passe tel quel.
 */
public class GBRootLayout extends ConstraintLayout {

    private GameSurface _surface = null;

    private int _touchSlop = 0;

    private float _downX = 0f;
    private float _downY = 0f;

    // l'appui a demarre sur une vue cliquable : candidat au transfert vers la carte
    private boolean _candidate = false;

    // le transfert a eu lieu, les evenements suivants vont a la carte
    private boolean _routing = false;

    private final Rect _hitRect = new Rect();

    // --- easter egg : l'appui de trois secondes du coin haut-gauche -----------------------------
    //
    // Il lance une partie, ou il l'abandonne. Ici plutot que sur le panneau mark parce que ce
    // panneau n'existe PAS quand aucune marque n'est selectionnee : il n'y avait alors rien a
    // tenir, et donc aucun moyen de lancer le jeu. Cette racine, elle, voit tous les appuis.
    //
    // Trois secondes et non les 500 ms du systeme : la duree de ViewConfiguration est une valeur
    // systeme sans accesseur, commune a tous les autres appuis longs de l'application, et ce
    // geste-ci demarre et arrete une partie -- il ne doit pas s'attraper par megarde.
    //
    // Attention en relisant le layout : rien n'y signale ce carre. C'est volontaire, un easter egg
    // ne s'annonce pas, mais ca veut dire qu'un appui maintenu dans ce coin agit meme quand rien
    // n'y est dessine.
    private static final long HOLD_MS = 3000;
    private static final int HOLD_CORNER_DP = 120;

    // Un doigt tenu trois secondes n'est pas un doigt immobile au pixel pres. Le compte a rebours
    // s'abandonnait sur le slop du systeme -- huit points, un tremblement -- et le geste echouait
    // sans que rien ne dise pourquoi. Cette tolerance-ci ne sert QU'A l'abandon de la tenue : le
    // reste du geste, le pan compris, garde le slop habituel.
    private static final int HOLD_SLOP_DP = 48;

    // Et un DOUBLE appui dans le meme carre passe au niveau suivant, pendant une partie. Un
    // raccourci d'essai : sauter un niveau pour aller voir le suivant sans le jouer. Le deuxieme
    // appui doit tomber dans le delai systeme du double-clic et sans que le doigt ait voyage.
    private long _lastCornerTapMs = 0;
    private float _lastCornerX = 0f;
    private float _lastCornerY = 0f;

    private Handler _holdHandler = null;
    private Runnable _holdStart = null;
    private Runnable _hold = null;
    private int _holdCorner = 0;
    private long _longPressMs = 0;
    private boolean _holdFired = false;
    private int _doubleTapMs = 0;
    private int _holdSlop = 0;

    // Vrai pendant que le compte a rebours tourne. Le geste est alors a CE geste-ci : la carte ne
    // defile pas sous le doigt, parce que ce que le doigt est en train de faire est d'allumer ou
    // d'eteindre une partie, et que ce n'est pas la meme chose que de deplacer la carte.
    private boolean _holding = false;

    // decalage entre le repere de cette racine et celui de la surface, releve au transfert
    private final Rect _offsetRect = new Rect();
    private int _offX = 0;
    private int _offY = 0;

    public GBRootLayout(Context context) {
        super(context);
        init(context);
    }

    public GBRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GBRootLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        _touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        _holdCorner = Math.round(HOLD_CORNER_DP
                                 * context.getResources().getDisplayMetrics().density);

        _holdSlop = Math.round(HOLD_SLOP_DP
                               * context.getResources().getDisplayMetrics().density);

        _doubleTapMs = ViewConfiguration.getDoubleTapTimeout();

        _holdHandler = new Handler(Looper.getMainLooper());

        // A tap in the corner cycles the selected marks: the countdown must not flash on it.
        // It only starts once the finger has stayed down as long as a system long press.
        _longPressMs = ViewConfiguration.getLongPressTimeout();

        _holdStart = new Runnable() {
            @Override
            public void run() {
                _holdHandler.postDelayed(_hold, HOLD_MS);

                _holding = true;

                // Le jeu compte a rebours pendant ce temps-la, s'il a quelque chose a lancer.
                AppManager.getInstance().setCornerHold(HOLD_MS);
            }
        };

        _hold = new Runnable() {
            @Override
            public void run() {
                _holding = false;

                // Faux quand le jeu n'avait rien a en faire : le geste suit alors son cours normal.
                _holdFired = AppManager.getInstance().onCornerHold();
            }
        };
    }

    // Arme l'appui maintenu, et seulement si le doigt se pose dans le carre du coin.
    private void armHold(float x, float y) {

        _holdFired = false;

        if( (x > _holdCorner) || (y > _holdCorner) ) {
            return;
        }

        _holdHandler.postDelayed(_holdStart, _longPressMs);

        doubleTap(x, y);
    }

    /**
     * Deux appuis de suite dans le carre du coin : le niveau suivant.
     *
     * Mesure ici plutot que par un GestureDetector : le detecteur voudrait voir tout le geste, et
     * cette racine ne garde la main que sur les evenements qu'elle n'a pas laisses filer. Deux
     * ACTION_DOWN suffisent, et c'est exactement ce qu'est un double appui.
     */
    private void doubleTap(float x, float y) {

        long now = SystemClock.elapsedRealtime();

        boolean second = ((now - _lastCornerTapMs) <= _doubleTapMs)
                      && (Math.hypot(x - _lastCornerX, y - _lastCornerY) <= _touchSlop * 2);

        _lastCornerTapMs = now;
        _lastCornerX = x;
        _lastCornerY = y;

        if( !second )
            return;

        // Rien a compter pour le troisieme appui : deux font un double, trois n'en font pas un et
        // demi.
        _lastCornerTapMs = 0;

        if( !AppManager.getInstance().onCornerDoubleTap() )
            return;

        // Le geste a servi : l'appui en cours ne doit plus rien declencher d'autre, ni la tenue
        // de trois secondes ni le clic de l'enfant dessous.
        cancelHold();

        _holdFired = true;
    }

    private void cancelHold() {
        _holding = false;

        _holdHandler.removeCallbacks(_holdStart);
        _holdHandler.removeCallbacks(_hold);

        AppManager.getInstance().setCornerHold(0);
    }

    /** Cible du transfert, posee par ViewsController.ShowMainView une fois la surface creee. */
    public void setMapSurface(GameSurface surface) {
        _surface = surface;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        switch( action ) {

            case MotionEvent.ACTION_DOWN:
                _downX = ev.getX();
                _downY = ev.getY();
                _routing = false;
                // un appui sur la carte elle-meme suit le chemin habituel : rien a transferer
                _candidate = (_surface != null) && findClickable(this, _downX, _downY);

                armHold(_downX, _downY);
                break;

            case MotionEvent.ACTION_MOVE:

                // L'appui est alle au bout de ses trois secondes : on reprend la suite du geste,
                // ce qui envoie son ACTION_CANCEL a l'enfant et empeche son clic de partir. Sans
                // cela, lacher le doigt apres avoir lance la partie faisait AUSSI defiler les
                // marques du panneau.
                if( _holdFired ) {
                    _holdFired = false;
                    cancelHold();
                    return true;
                }

                // Un doigt qui glisse n'est plus un doigt pose. Teste avant la garde ci-dessous :
                // sur la carte _candidate est faux, et l'appui ne serait jamais abandonne.
                boolean wasHolding = _holding;

                if( Math.hypot(ev.getX() - _downX, ev.getY() - _downY) > _holdSlop ) {
                    cancelHold();
                }

                // Le compte a rebours tourne, ou vient d'etre abandonne par un doigt parti trop
                // loin : dans les deux cas ce geste etait une tenue, et il le reste. Il faut le
                // prendre ici meme quand l'appui est ne sur la carte, ou rien n'est intercepte
                // d'ordinaire -- sans quoi la carte defilerait pendant le decompte.
                if( wasHolding )
                    return true;

                if( !_candidate || _routing ) {
                    break;
                }

                float dx = ev.getX() - _downX;
                float dy = ev.getY() - _downY;

                if( Math.hypot(dx, dy) <= _touchSlop ) {
                    break;
                }

                // une liste ou un contenu defilant sous le doigt garde la main dans SA direction
                // (colonne des marks de proximite)
                if( findScrollable(this, _downX, _downY, dx, dy) ) {
                    _candidate = false;
                    break;
                }

                _routing = true;
                takeSurfaceOffset();

                // l'enfant recoit un ACTION_CANCEL du fait de l'interception : le clic ne partira pas
                _surface.beginExternalPan(_downX - _offX, _downY - _offY);

                GBLog.d(getClass().getName(), "gesture handed over to the map from a clickable view");
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                _candidate = false;
                _routing = false;

                cancelHold();

                // Un appui parfaitement immobile n'a produit aucun MOVE : c'est donc ici, au
                // relachement, que le clic de l'enfant doit etre empeche.
                if( _holdFired ) {
                    _holdFired = false;
                    return true;
                }

                break;

            default:
                break;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        final int action = ev.getActionMasked();

        // Une fois le geste INTERCEPTE -- la tenue du coin, ou le transfert vers la carte --,
        // Android n'appelle plus onInterceptTouchEvent du tout : la fin de ce geste n'arrive plus
        // que par ici. C'est donc ici aussi qu'il faut abandonner la tenue, sans quoi lever le
        // doigt pendant le decompte ne l'arrete plus et la partie s'allume trois secondes plus
        // tard, toute seule.
        boolean mine = _holding || _holdFired;

        if( (action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL) ) {
            cancelHold();
            _holdFired = false;
        }

        if( !_routing || _surface == null ) {
            // Garder la main tant que la tenue dure : un geste que plus personne ne prend n'est
            // plus suivi, et sa fin ne serait jamais vue.
            return mine || super.onTouchEvent(ev);
        }

        // la surface occupe tout le GBScrollView, lui-meme cale sur cette racine : l'ecart est nul
        // en pratique, mais on le retire quand meme pour ne pas dependre de la mise en page.
        ev.offsetLocation(-_offX, -_offY);
        boolean handled = _surface.onTouchEvent(ev);
        ev.offsetLocation(_offX, _offY);

        if( (action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL) ) {
            _candidate = false;
            _routing = false;
        }

        return handled;
    }

    // Origine de la surface dans le repere de cette racine, scrolls des parents compris.
    private void takeSurfaceOffset() {
        _offsetRect.set(0, 0, 0, 0);

        try {
            offsetDescendantRectToMyCoords(_surface, _offsetRect);
        } catch( IllegalArgumentException ex ) {
            _offsetRect.set(0, 0, 0, 0);   // surface pas (encore) dans l'arbre
        }

        _offX = _offsetRect.left;
        _offY = _offsetRect.top;
    }

    // Vue cliquable (ou a appui long) sous le point, en descendant l'arbre. La GameSurface n'est ni
    // l'une ni l'autre : un geste parti de la carte n'est donc jamais candidat.
    private boolean findClickable(ViewGroup parent, float x, float y) {
        for( int i = parent.getChildCount() - 1; i >= 0; i-- ) {
            View child = parent.getChildAt(i);

            if( !isHit(child, x, y) ) {
                continue;
            }

            if( child.isClickable() || child.isLongClickable() ) {
                return true;
            }

            if( child instanceof ViewGroup ) {
                if( findClickable((ViewGroup) child,
                        x - child.getLeft() + child.getScrollX(),
                        y - child.getTop() + child.getScrollY()) ) {
                    return true;
                }
            }
        }

        return false;
    }

    // Vue capable de defiler dans la direction du geste sous le point (la colonne des marks).
    private boolean findScrollable(ViewGroup parent, float x, float y, float dx, float dy) {
        for( int i = parent.getChildCount() - 1; i >= 0; i-- ) {
            View child = parent.getChildAt(i);

            if( !isHit(child, x, y) ) {
                continue;
            }

            // la carte n'est jamais "ce qui defile" : son conteneur ne reclame pas le geste
            if( child instanceof GBScrollView ) {
                continue;
            }

            // canScrollX/Y attend le sens du CONTENU, inverse de celui du doigt
            if( child.canScrollVertically((int) -dy) || child.canScrollHorizontally((int) -dx) ) {
                return true;
            }

            if( child instanceof ViewGroup ) {
                if( findScrollable((ViewGroup) child,
                        x - child.getLeft() + child.getScrollX(),
                        y - child.getTop() + child.getScrollY(), dx, dy) ) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isHit(View child, float x, float y) {
        if( child.getVisibility() != View.VISIBLE ) {
            return false;
        }

        child.getHitRect(_hitRect);

        return _hitRect.contains((int) x, (int) y);
    }
}
