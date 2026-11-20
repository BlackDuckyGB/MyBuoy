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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Ecran d'attente : un bout de cordage se noue, marque un temps, se denoue, et recommence,
 * pendant que la premiere zone de carte se charge.
 *
 * Le cordage est decrit en trois dimensions, [x, y, profondeur], profondeur positive vers le
 * spectateur. Le trace passe par une spline centripete, est reechantillonne a pas constant puis
 * lisse, ce qui donne des courbures regulieres.
 *
 * Le point delicat d'un noeud est de savoir quel brin passe devant. On ne s'appuie pas sur la
 * profondeur pour decouper : on cherche les auto-intersections reelles du trace projete, on
 * compare la profondeur des deux brins AU POINT de croisement, puis on coupe a mi-chemin entre
 * deux croisements consecutifs. Chaque morceau ne porte donc qu'un seul croisement, et il suffit
 * de dessiner tous les "dessous" avant tous les "dessus" pour que l'empilement soit juste partout.
 * Un liseré de la couleur du fond, pose sous chaque morceau, creuse son passage dans les brins
 * deja dessines.
 */
public class KnotView extends View {

    private static final String TAG = KnotView.class.getName();

    // ---- geometrie des deux noeuds : x, y, profondeur ------------------------------------------

    private static final float[] BOWLINE = {
        186f,-95f,0f, 186f,-75f,0.5f, 186f,-55f,1f, 186f,-30f,0.4f,
        186f,5f,-0.5f, 186f,45f,-1f, 186f,95f,-1f, 186f,130f,-1f,
        186f,160f,-1f, 184f,180f,-0.6f, 178f,200f,0f, 158f,228f,0.4f,
        128f,241f,1f, 98f,246f,1f, 80f,244f,1f, 46f,224f,0.6f,
        20f,190f,-0.2f, 16f,150f,-1f, 32f,112f,-1f, 56f,86f,-1f,
        84f,76f,-1f, 112f,76f,-1f, 144f,86f,-1f, 170f,112f,0.2f,
        186f,150f,1f, 192f,172f,1f, 200f,200f,0.5f, 212f,236f,0f,
        222f,286f,0f, 224f,340f,0f, 206f,392f,0f, 164f,428f,0f,
        110f,438f,0f, 56f,424f,0f, 20f,388f,0f, 8f,340f,0f,
        16f,296f,-0.3f, 44f,262f,-1f, 74f,252f,-1f, 76f,230f,-1f,
        72f,196f,-0.4f, 66f,160f,0.4f, 60f,120f,1f, 56f,96f,1f,
        58f,62f,1f, 70f,28f,1f, 92f,0f,1f, 120f,-24f,0.3f,
        152f,-42f,-0.6f, 186f,-55f,-1f, 212f,-48f,-1f, 232f,-22f,0f,
        236f,12f,1f, 220f,38f,1f, 196f,45f,1f, 174f,50f,1f,
        156f,50f,0.8f, 146f,58f,1f, 144f,74f,1f, 140f,110f,1f,
        136f,150f,0.3f, 132f,190f,-1f, 128f,225f,-1f, 126f,270f,-0.4f,
        124f,312f,0f, 123f,352f,0f
    };

    private static final float[] EIGHT = {
        100f,-14f,0f, 100f,10f,0.6f, 100f,28f,1f, 100f,44f,1f,
        100f,66f,0.3f, 100f,90f,-0.6f, 100f,110f,-1f, 100f,124f,-1f,
        100f,138f,-0.6f, 100f,152f,0f, 112f,172f,0.4f, 134f,188f,1f,
        158f,193f,1f, 184f,184f,0.8f, 202f,164f,0.4f, 208f,140f,0f,
        200f,118f,-0.5f, 180f,106f,-1f, 156f,102f,-1f, 136f,104f,-0.6f,
        118f,112f,0.4f, 100f,124f,1f, 76f,126f,1f, 54f,116f,0.5f,
        42f,94f,-0.3f, 46f,68f,-1f, 64f,50f,-1f, 84f,43f,-1f,
        100f,42f,-1f, 122f,46f,-0.4f, 142f,58f,0.3f, 156f,76f,1f,
        158f,96f,1f, 156f,110f,1f, 154f,132f,0.4f, 152f,158f,-0.4f,
        151f,182f,-1f, 151f,196f,-1f, 150f,218f,-0.4f, 149f,242f,0f
    };

    // Couleurs relevees sur des bouts reels. Le noeud est tire au hasard a chaque lancement, et
    // chacun a la sienne. FOND doit rester identique au fond de l'ecran d'attente : c'est cette
    // couleur qui creuse le passage d'un brin sous un autre.
    private static final int FOND        = 0xFF0B1F33;
    private static final int MARINE_BOUT = 0xFF1E447E;
    private static final int MARINE_MECH = 0xFFDDE7F4;
    private static final int MARINE_JOUR = 0xFF6EA0DD;
    private static final int ECARL_BOUT  = 0xFFAD2F2B;
    private static final int ECARL_MECH  = 0xFFF2E8DA;
    private static final int ECARL_JOUR  = 0xFFEA7D70;

    // ---- reglages valides ----------------------------------------------------------------------

    private static final float GROSSEUR      = 31.9f;  // largeur du bout, dans les unites du noeud
    private static final float HALO          = 1.36f;  // largeur du liseré de fond, en parts de bout
    private static final float RENTRE_HALO   = 3f;     // liseré rentré sous le bout, en pixels
    private static final float MECHE_PAS     = 1.00f;  // ecart entre deux meches, en parts de bout
    private static final float MECHE_BIAIS   = 0.55f;
    private static final float MECHE_LARGE   = 0.50f;
    private static final float MECHE_TRAIT   = 0.17f;
    private static final int   MECHE_ALPHA   = 217;    // 0.85
    private static final float JOUR_TRAIT    = 0.28f;
    private static final int   JOUR_ALPHA    = 77;     // 0.30

    private static final int PAS_SPLINE   = 20;   // points calcules par intervalle de controle
    private static final float PAS_ECH    = 1f;   // reechantillonnage, en unites du noeud
    private static final int PASSES_LISSE = 26;
    private static final int PAS_CROIS    = 3;    // decimation pour la recherche des croisements

    // Duree des quatre temps, en millisecondes (vitesse rapide).
    private static final long T_NOUAGE   = 1700;
    private static final long T_NOUE     = 470;
    private static final long T_DENOUAGE = 1120;
    private static final long T_VIDE     = 270;
    private static final long T_CYCLE    = T_NOUAGE + T_NOUE + T_DENOUAGE + T_VIDE;

    // ---- etat ----------------------------------------------------------------------------------

    /** Un morceau de cordage : son trace, sa place le long du bout, et son rang d'empilement. */
    private static class Morceau {
        Path  trace;
        Path  bande;        // le trace epaissi a la largeur du bout, pour borner les meches
        float debut;        // abscisse curviligne du debut, sur l'ensemble du bout
        float longueur;
        float[] meches;     // 4 valeurs par meche : x1, y1, x2, y2
        float[] mechesPos;  // abscisse curviligne de chaque meche
        boolean dessus;
    }

    private final List<Morceau> _morceaux = new ArrayList<>();
    private final PathMeasure _mesure = new PathMeasure();
    private final Path _visible = new Path();
    private final Path _halo = new Path();

    private final Paint _pHalo  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _pBout  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _pJour  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _pMeche = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] _points;      // le noeud tire au hasard
    private final int _coulBout;
    private final int _coulMeche;
    private final int _coulJour;

    private float _longueurTotale = 0f;
    private long  _depart = 0;

    public KnotView(Context context) {
        this(context, null);
    }

    public KnotView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boolean chaise = new Random().nextBoolean();

        _points    = chaise ? BOWLINE : EIGHT;
        _coulBout  = chaise ? MARINE_BOUT : ECARL_BOUT;
        _coulMeche = chaise ? MARINE_MECH : ECARL_MECH;
        _coulJour  = chaise ? MARINE_JOUR : ECARL_JOUR;

        GBLog.d(TAG, chaise ? "noeud de chaise" : "noeud de huit");

        _pHalo.setStyle(Paint.Style.STROKE);
        _pHalo.setStrokeCap(Paint.Cap.BUTT);
        _pHalo.setStrokeJoin(Paint.Join.ROUND);
        _pHalo.setColor(FOND);

        _pBout.setStyle(Paint.Style.STROKE);
        _pBout.setStrokeCap(Paint.Cap.ROUND);
        _pBout.setStrokeJoin(Paint.Join.ROUND);
        _pBout.setColor(_coulBout);

        _pJour.setStyle(Paint.Style.STROKE);
        _pJour.setStrokeCap(Paint.Cap.BUTT);
        _pJour.setStrokeJoin(Paint.Join.ROUND);
        _pJour.setColor(_coulJour);
        _pJour.setAlpha(JOUR_ALPHA);

        _pMeche.setStyle(Paint.Style.STROKE);
        _pMeche.setStrokeCap(Paint.Cap.ROUND);
        _pMeche.setColor(_coulMeche);
        _pMeche.setAlpha(MECHE_ALPHA);
    }

    public int getCouleurFond() {
        return FOND;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if( w > 0 && h > 0 ) {
            construire(w, h);
            _depart = 0;
        }
    }

    // ---- construction du trace ------------------------------------------------------------------

    private void construire(int w, int h) {

        _morceaux.clear();

        float[] sm = spline(_points);
        sm = reechantillonner(sm);
        lisser(sm);

        float echelle = ajuster(sm, w, h);
        float bout    = GROSSEUR * echelle;

        _pHalo.setStrokeWidth(bout * HALO);
        _pBout.setStrokeWidth(bout);
        _pJour.setStrokeWidth(bout * JOUR_TRAIT);
        _pMeche.setStrokeWidth(bout * MECHE_TRAIT);

        int n = sm.length / 3;

        // abscisse curviligne, en pixels
        float[] arc = new float[n];
        for( int i = 1; i < n; i++ ) {
            arc[i] = arc[i - 1] + (float) Math.hypot(sm[i * 3] - sm[(i - 1) * 3],
                                                     sm[i * 3 + 1] - sm[(i - 1) * 3 + 1]);
        }
        _longueurTotale = arc[n - 1];

        List<float[]> passages = croisements(sm);   // {parametre, 1 = dessus}
        if( passages.isEmpty() ) {
            return;
        }

        // Les coupes tombent a mi-chemin entre deux croisements consecutifs : chaque morceau ne
        // porte donc qu'un seul croisement, ce qui suffit a ordonner l'empilement.
        int nb = passages.size();
        float[] bornes = new float[nb + 1];
        bornes[0]  = 0f;
        bornes[nb] = n - 1;
        for( int k = 0; k + 1 < nb; k++ ) {
            bornes[k + 1] = (passages.get(k)[0] + passages.get(k + 1)[0]) / 2f;
        }

        float recouvrement = 20f * echelle;
        Paint bande = new Paint(_pBout);
        bande.setStrokeWidth(bout * 0.84f);

        List<Morceau> dessous = new ArrayList<>();
        List<Morceau> dessus  = new ArrayList<>();

        for( int k = 0; k < nb; k++ ) {

            int s0 = Math.max(0, Math.round(bornes[k]));
            int s1 = Math.min(n - 1, Math.round(bornes[k + 1]));

            if( s1 - s0 < 2 ) {
                continue;
            }

            // On deborde sur le voisin pour qu'aucune jonction ne se voie, mais jamais assez pour
            // atteindre son croisement, sans quoi le liseré de fond mordrait dessus.
            float versArriere = recouvrement;
            float versAvant   = recouvrement;

            if( k > 0 ) {
                int pv = Math.max(0, Math.min(n - 1, Math.round(passages.get(k - 1)[0])));
                versArriere = Math.min(recouvrement, 0.45f * (arc[s0] - arc[pv]));
            }
            if( k + 1 < nb ) {
                int ps = Math.max(0, Math.min(n - 1, Math.round(passages.get(k + 1)[0])));
                versAvant = Math.min(recouvrement, 0.45f * (arc[ps] - arc[s1]));
            }

            int e0 = s0;
            while( e0 > 0 && arc[s0] - arc[e0] < versArriere ) {
                e0--;
            }
            int e1 = s1;
            while( e1 < n - 1 && arc[e1] - arc[s1] < versAvant ) {
                e1++;
            }

            Morceau m = new Morceau();
            m.dessus   = passages.get(k)[1] > 0f;
            m.debut    = arc[e0];
            m.longueur = arc[e1] - arc[e0];
            m.trace    = new Path();

            m.trace.moveTo(sm[e0 * 3], sm[e0 * 3 + 1]);
            for( int i = e0 + 1; i <= e1; i++ ) {
                m.trace.lineTo(sm[i * 3], sm[i * 3 + 1]);
            }

            m.bande = new Path();
            bande.getFillPath(m.trace, m.bande);

            meches(m, sm, arc, e0, e1, bout);

            if( m.dessus ) {
                dessus.add(m);
            } else {
                dessous.add(m);
            }
        }

        _morceaux.addAll(dessous);
        _morceaux.addAll(dessus);

        GBLog.d(TAG, "trace construit : " + _morceaux.size() + " morceaux, "
                     + Math.round(_longueurTotale) + " px de cordage");
    }

    /** Les meches en biais du commettage, posees a pas constant le long du bout. */
    private void meches(Morceau m, float[] sm, float[] arc, int e0, int e1, float bout) {

        float pas   = MECHE_PAS * bout;
        float biais = MECHE_BIAIS * bout;
        float demi  = MECHE_LARGE * bout / 2f;

        List<Float> pts = new ArrayList<>();
        List<Float> pos = new ArrayList<>();

        // la phase est calee sur l'abscisse globale, pour que le commettage reste continu d'un
        // morceau au suivant
        float cible = (float) Math.ceil(arc[e0] / pas) * pas;

        for( int i = e0 + 1; i <= e1; i++ ) {

            float ax = sm[(i - 1) * 3], ay = sm[(i - 1) * 3 + 1];
            float bx = sm[i * 3],       by = sm[i * 3 + 1];
            float len = arc[i] - arc[i - 1];

            while( cible <= arc[i] && len > 0.0001f ) {

                float u  = (cible - arc[i - 1]) / len;
                float px = ax + (bx - ax) * u;
                float py = ay + (by - ay) * u;
                float tx = (bx - ax) / len;
                float ty = (by - ay) / len;

                pts.add(px + ty * demi - tx * biais);
                pts.add(py - tx * demi - ty * biais);
                pts.add(px - ty * demi + tx * biais);
                pts.add(py + tx * demi + ty * biais);
                pos.add(cible);

                cible += pas;
            }
        }

        m.meches    = new float[pts.size()];
        m.mechesPos = new float[pos.size()];

        for( int i = 0; i < pts.size(); i++ ) {
            m.meches[i] = pts.get(i);
        }
        for( int i = 0; i < pos.size(); i++ ) {
            m.mechesPos[i] = pos.get(i);
        }
    }

    // ---- outils de trace ------------------------------------------------------------------------

    /** Spline centripete passant par les points de controle, profondeur interpolee comme le reste. */
    private static float[] spline(float[] p) {

        int n = p.length / 3;
        float[] out = new float[((n - 1) * PAS_SPLINE + 1) * 3];
        int k = 0;

        for( int i = 0; i < n - 1; i++ ) {

            int i0 = borne(i - 1, n), i1 = borne(i, n), i2 = borne(i + 1, n), i3 = borne(i + 2, n);

            float t0 = 0f;
            float t1 = t0 + ecart(p, i0, i1);
            float t2 = t1 + ecart(p, i1, i2);
            float t3 = t2 + ecart(p, i2, i3);

            for( int s = 0; s < PAS_SPLINE; s++ ) {

                float t = t1 + (t2 - t1) * s / PAS_SPLINE;

                for( int c = 0; c < 3; c++ ) {
                    float a1 = ((t1 - t) * p[i0 * 3 + c] + (t - t0) * p[i1 * 3 + c]) / (t1 - t0);
                    float a2 = ((t2 - t) * p[i1 * 3 + c] + (t - t1) * p[i2 * 3 + c]) / (t2 - t1);
                    float a3 = ((t3 - t) * p[i2 * 3 + c] + (t - t2) * p[i3 * 3 + c]) / (t3 - t2);
                    float b1 = ((t2 - t) * a1 + (t - t0) * a2) / (t2 - t0);
                    float b2 = ((t3 - t) * a2 + (t - t1) * a3) / (t3 - t1);
                    out[k++] = ((t2 - t) * b1 + (t - t1) * b2) / (t2 - t1);
                }
            }
        }

        out[k++] = p[(n - 1) * 3];
        out[k++] = p[(n - 1) * 3 + 1];
        out[k]   = p[(n - 1) * 3 + 2];

        return out;
    }

    private static int borne(int i, int n) {
        return Math.max(0, Math.min(n - 1, i));
    }

    private static float ecart(float[] p, int a, int b) {
        double d = Math.hypot(p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1]);
        return (float) Math.sqrt(Math.max(d, 1e-4));
    }

    /** Pas constant : indispensable avant de lisser, sinon le lissage deforme la ou c'est dense. */
    private static float[] reechantillonner(float[] sm) {

        int n = sm.length / 3;
        List<Float> out = new ArrayList<>();

        float ax = sm[0], ay = sm[1], az = sm[2];
        out.add(ax); out.add(ay); out.add(az);

        float reste = 0f;

        for( int i = 1; i < n; i++ ) {

            float bx = sm[i * 3], by = sm[i * 3 + 1], bz = sm[i * 3 + 2];
            float d = (float) Math.hypot(bx - ax, by - ay);

            while( d + reste >= PAS_ECH && d > 0.0001f ) {
                float u = (PAS_ECH - reste) / d;
                ax += (bx - ax) * u;
                ay += (by - ay) * u;
                az += (bz - az) * u;
                out.add(ax); out.add(ay); out.add(az);
                reste = 0f;
                d = (float) Math.hypot(bx - ax, by - ay);
            }

            reste += d;
            ax = bx; ay = by; az = bz;
        }

        out.add(sm[(n - 1) * 3]); out.add(sm[(n - 1) * 3 + 1]); out.add(sm[(n - 1) * 3 + 2]);

        float[] res = new float[out.size()];
        for( int i = 0; i < res.length; i++ ) {
            res[i] = out.get(i);
        }
        return res;
    }

    /** Efface les meplats laisses par la spline aux points de passage. Les deux bouts ne bougent pas. */
    private static void lisser(float[] sm) {

        int n = sm.length / 3;
        float[] tmp = new float[sm.length];

        for( int p = 0; p < PASSES_LISSE; p++ ) {

            System.arraycopy(sm, 0, tmp, 0, sm.length);

            for( int i = 1; i < n - 1; i++ ) {
                for( int c = 0; c < 3; c++ ) {
                    sm[i * 3 + c] = (tmp[(i - 1) * 3 + c] + 2f * tmp[i * 3 + c] + tmp[(i + 1) * 3 + c]) / 4f;
                }
            }
        }
    }

    /** Cadre le noeud dans la vue et rend le facteur d'echelle : le bout en est une fraction. */
    private static float ajuster(float[] sm, int w, int h) {

        int n = sm.length / 3;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;

        for( int i = 0; i < n; i++ ) {
            x0 = Math.min(x0, sm[i * 3]);     x1 = Math.max(x1, sm[i * 3]);
            y0 = Math.min(y0, sm[i * 3 + 1]); y1 = Math.max(y1, sm[i * 3 + 1]);
        }

        float lx = Math.max(1f, x1 - x0), ly = Math.max(1f, y1 - y0);

        // la marge depend de la grosseur du bout, qui depend de l'echelle : une passe a vide suffit
        float brut  = Math.min(w / lx, h / ly);
        float marge = GROSSEUR * brut * 0.70f;
        float e     = Math.min((w - 2f * marge) / lx, (h - 2f * marge) / ly);

        float ox = (w - lx * e) / 2f - x0 * e;
        float oy = (h - ly * e) / 2f - y0 * e;

        for( int i = 0; i < n; i++ ) {
            sm[i * 3]     = sm[i * 3] * e + ox;
            sm[i * 3 + 1] = sm[i * 3 + 1] * e + oy;
        }

        return e;
    }

    /**
     * Auto-intersections du trace projete. Pour chacune, la profondeur des deux brins est comparee
     * AU POINT de croisement : le plus proche du spectateur passe devant. Rend, pour chaque passage,
     * son abscisse le long du bout et son rang.
     */
    private static List<float[]> croisements(float[] sm) {

        int n = sm.length / 3;
        int m = n / PAS_CROIS;
        List<float[]> passages = new ArrayList<>();

        for( int k = 0; k + 1 < m; k++ ) {

            int i = k * PAS_CROIS;
            float ax = sm[i * 3], ay = sm[i * 3 + 1];
            float bx = sm[(i + PAS_CROIS) * 3] - ax, by = sm[(i + PAS_CROIS) * 3 + 1] - ay;

            for( int l = k + 4; l + 1 < m; l++ ) {

                int j = l * PAS_CROIS;
                float cx = sm[j * 3], cy = sm[j * 3 + 1];
                float dx = sm[(j + PAS_CROIS) * 3] - cx, dy = sm[(j + PAS_CROIS) * 3 + 1] - cy;

                float den = bx * dy - by * dx;
                if( Math.abs(den) < 1e-9f ) {
                    continue;
                }

                float t = ((cx - ax) * dy - (cy - ay) * dx) / den;
                if( t < 0f || t > 1f ) {
                    continue;
                }

                float u = ((cx - ax) * by - (cy - ay) * bx) / den;
                if( u < 0f || u > 1f ) {
                    continue;
                }

                float pk = i + t * PAS_CROIS;
                float pl = j + u * PAS_CROIS;
                float zk = profondeur(sm, pk);
                float zl = profondeur(sm, pl);

                passages.add(new float[]{ pk, zk >= zl ? 1f : 0f });
                passages.add(new float[]{ pl, zl >  zk ? 1f : 0f });
            }
        }

        Collections.sort(passages, new Comparator<float[]>() {
            @Override
            public int compare(float[] a, float[] b) {
                return Float.compare(a[0], b[0]);
            }
        });

        return passages;
    }

    private static float profondeur(float[] sm, float p) {
        int n = sm.length / 3;
        int i = Math.max(0, Math.min(n - 2, (int) Math.floor(p)));
        float f = p - i;
        return sm[i * 3 + 2] + (sm[(i + 1) * 3 + 2] - sm[i * 3 + 2]) * f;
    }

    // ---- animation ------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        if( _morceaux.isEmpty() ) {
            return;
        }

        long now = android.os.SystemClock.uptimeMillis();
        if( _depart == 0 ) {
            _depart = now;
        }

        long t = (now - _depart) % T_CYCLE;
        float debut, fin;

        if( t < T_NOUAGE ) {
            debut = 0f;
            fin   = _longueurTotale * t / (float) T_NOUAGE;
        } else if( t < T_NOUAGE + T_NOUE ) {
            debut = 0f;
            fin   = _longueurTotale;
        } else if( t < T_NOUAGE + T_NOUE + T_DENOUAGE ) {
            debut = _longueurTotale * (t - T_NOUAGE - T_NOUE) / (float) T_DENOUAGE;
            fin   = _longueurTotale;
        } else {
            debut = _longueurTotale;
            fin   = _longueurTotale;
        }

        for( Morceau m : _morceaux ) {

            // la fenetre visible est calculee en abscisse curviligne : un morceau hors fenetre
            // n'est pas dessine du tout, plutot que reduit a rien par un artifice de trace
            float a = Math.max(0f, debut - m.debut);
            float b = Math.min(m.longueur, fin - m.debut);

            if( b - a <= 0.5f ) {
                continue;
            }

            _mesure.setPath(m.trace, false);

            float ha = Math.min(a + RENTRE_HALO, b);
            float hb = Math.max(b - RENTRE_HALO, ha);

            if( hb - ha > 0.5f ) {
                _halo.reset();
                if( _mesure.getSegment(ha, hb, _halo, true) ) {
                    canvas.drawPath(_halo, _pHalo);
                }
            }

            _visible.reset();
            if( !_mesure.getSegment(a, b, _visible, true) ) {
                continue;
            }

            canvas.drawPath(_visible, _pBout);
            canvas.drawPath(_visible, _pJour);

            if( m.mechesPos.length > 0 ) {

                int i0 = -1, i1 = -1;
                float da = m.debut + a, db = m.debut + b;

                for( int i = 0; i < m.mechesPos.length; i++ ) {
                    if( m.mechesPos[i] >= da && m.mechesPos[i] <= db ) {
                        if( i0 < 0 ) {
                            i0 = i;
                        }
                        i1 = i;
                    }
                }

                if( i0 >= 0 ) {
                    canvas.save();
                    canvas.clipPath(m.bande);
                    canvas.drawLines(m.meches, i0 * 4, (i1 - i0 + 1) * 4, _pMeche);
                    canvas.restore();
                }
            }
        }

        postInvalidateOnAnimation();
    }
}
