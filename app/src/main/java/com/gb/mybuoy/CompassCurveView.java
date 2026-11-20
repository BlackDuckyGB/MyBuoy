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
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.databinding.BindingAdapter;

/**
 * #54 : la courbe de deviation d'un compas, dans sa page de configuration.
 *
 * En abscisse le cap compas de -180 a +180 (le nord au centre, le sud aux deux bords), en ordonnee
 * la correction de -25 a +25 degres. La courbe est celle que Compass.deviationAt() appliquera au cap
 * une fois l'interpolation branchee : elle passe exactement par les 8 valeurs saisies, marquees par
 * un point, celui en cours de reglage etant mis en evidence.
 */
public class CompassCurveView extends View {

    private static final double HEADING_SPAN = 180.0;   // abscisse : -180 a +180
    private static final double DEV_SPAN     = 25.0;    // ordonnee  : -25 a +25
    private static final double CURVE_STEP   = 2.0;     // pas d'echantillonnage de la courbe, en degres

    private static final int COLOR_FRAME    = 0xFFBDBDBD;
    private static final int COLOR_GRID     = 0xFFE8E8E8;
    private static final int COLOR_AXIS     = 0xFF9E9E9E;
    private static final int COLOR_CURVE    = 0xFF2196F3;
    private static final int COLOR_POINT    = 0xFF1565C0;
    private static final int COLOR_SELECTED = 0xFFD32F2F;
    private static final int COLOR_LABEL    = 0xFF888888;
    private static final int COLOR_TRUE     = 0xAAD32F2F;   // repere du cap vrai, en second plan
    private static final int COLOR_MAGNETIC  = 0xAA000000;  // repere du cap compas

    private final Compass _card = new Compass("");
    private int _selected = -1;
    private double _headingTrue = 0.0;
    private double _headingMagnetic = 0.0;

    private final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path _path = new Path();
    private final float _density;

    public CompassCurveView(Context context) {
        this(context, null);
    }

    public CompassCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        _density = getResources().getDisplayMetrics().density;
    }

    // Les 8 valeurs arrivent serialisees : la chaine change des qu'une correction bouge, ce qui
    // suffit au data binding pour redemander un dessin.
    @BindingAdapter({ "deviations", "selectedSector", "headingTrue", "headingMagnetic" })
    public static void bindCard(CompassCurveView view, String deviations, int selectedSector,
                                double headingTrue, double headingMagnetic) {
        view._card.parseDeviations(deviations);
        view._selected = selectedSector;
        view._headingTrue = headingTrue;
        view._headingMagnetic = headingMagnetic;
        view.invalidate();
    }

    private float dp(float value) {
        return value * _density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = dp(34);            // place pour les graduations de l'ordonnee
        float right = getWidth() - dp(6);
        float top = dp(6);
        float bottom = getHeight() - dp(16);   // place pour les graduations de l'abscisse

        if ((right <= left) || (bottom <= top))
            return;

        _paint.setTextSize(dp(9));

        drawGrid(canvas, left, top, right, bottom);
        // En second plan, sous la courbe. L'ecart entre les deux reperes se lit directement : c'est
        // la correction totale appliquee au cap.
        drawHeading(canvas, left, top, right, bottom, _headingMagnetic, COLOR_MAGNETIC);
        drawHeading(canvas, left, top, right, bottom, _headingTrue, COLOR_TRUE);
        drawCurve(canvas, left, top, right, bottom);
        drawPoints(canvas, left, top, right, bottom);
    }

    // Abscisse : cap compas ramene dans [-180, +180].
    private float xOf(double heading, float left, float right) {
        return left + (float) (((heading + HEADING_SPAN) / (2 * HEADING_SPAN)) * (right - left));
    }

    // Ordonnee : correction, bornee au cadre pour qu'un depassement de la courbe ne deborde pas.
    private float yOf(double deviation, float top, float bottom) {
        if (deviation >  DEV_SPAN) deviation =  DEV_SPAN;
        if (deviation < -DEV_SPAN) deviation = -DEV_SPAN;

        return bottom - (float) (((deviation + DEV_SPAN) / (2 * DEV_SPAN)) * (bottom - top));
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        _paint.setStyle(Paint.Style.STROKE);
        _paint.setStrokeWidth(dp(1));

        // Verticales tous les 45 degres : une par point cardinal.
        _paint.setColor(COLOR_GRID);
        for (int h = -180; h <= 180; h += 45) {
            float x = xOf(h, left, right);
            canvas.drawLine(x, top, x, bottom, _paint);
        }

        // Horizontales tous les 5 degres de correction.
        for (int d = -25; d <= 25; d += 5) {
            float y = yOf(d, top, bottom);
            canvas.drawLine(left, y, right, y, _paint);
        }

        // Axe des caps (correction nulle) et axe du nord, plus marques que la grille.
        _paint.setColor(COLOR_AXIS);
        canvas.drawLine(left, yOf(0, top, bottom), right, yOf(0, top, bottom), _paint);
        canvas.drawLine(xOf(0, left, right), top, xOf(0, left, right), bottom, _paint);

        _paint.setColor(COLOR_FRAME);
        canvas.drawRect(left, top, right, bottom, _paint);

        // Graduations. En abscisse on nomme les points cardinaux plutot que les degres : c'est ce
        // qu'on regle, et ca tient dans la largeur.
        _paint.setStyle(Paint.Style.FILL);
        _paint.setColor(COLOR_LABEL);

        String[] labels = { "S", "SW", "W", "NW", "N", "NE", "E", "SE", "S" };
        _paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < labels.length; i++)
            canvas.drawText(labels[i], xOf(-180 + (45 * i), left, right), bottom + dp(12), _paint);

        _paint.setTextAlign(Paint.Align.RIGHT);
        for (int d = -25; d <= 25; d += 25)
            canvas.drawText(String.format("%+d°", d), left - dp(3), yOf(d, top, bottom) + dp(3), _paint);
    }

    // Repere d'un cap sur la courbe : une barre verticale coiffee d'un triangle a chaque bout. Deux
    // sont traces, le cap vrai en rouge et le cap compas en noir ; leur ecart est la correction.
    private void drawHeading(Canvas canvas, float left, float top, float right, float bottom,
                             double value, int color) {
        double heading = value % 360.0;

        if (heading > HEADING_SPAN)
            heading -= 360.0;
        else if (heading < -HEADING_SPAN)
            heading += 360.0;

        float x = xOf(heading, left, right);

        _paint.setStyle(Paint.Style.STROKE);
        _paint.setStrokeWidth(dp(1.5f));
        _paint.setColor(color);
        canvas.drawLine(x, top, x, bottom, _paint);

        float half = dp(4);
        float depth = dp(7);

        _path.reset();
        _path.moveTo(x - half, top);
        _path.lineTo(x + half, top);
        _path.lineTo(x, top + depth);
        _path.close();
        _path.moveTo(x - half, bottom);
        _path.lineTo(x + half, bottom);
        _path.lineTo(x, bottom - depth);
        _path.close();

        _paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(_path, _paint);
    }

    private void drawCurve(Canvas canvas, float left, float top, float right, float bottom) {
        _path.reset();

        for (double h = -HEADING_SPAN; h <= HEADING_SPAN; h += CURVE_STEP) {
            float x = xOf(h, left, right);
            float y = yOf(_card.deviationAt(h), top, bottom);

            if (h == -HEADING_SPAN)
                _path.moveTo(x, y);
            else
                _path.lineTo(x, y);
        }

        _paint.setStyle(Paint.Style.STROKE);
        _paint.setStrokeWidth(dp(2));
        _paint.setColor(COLOR_CURVE);
        canvas.drawPath(_path, _paint);
    }

    private void drawPoints(Canvas canvas, float left, float top, float right, float bottom) {
        _paint.setStyle(Paint.Style.FILL);

        for (int k = 0; k < Compass.SECTOR_COUNT; k++) {
            int heading = Compass.sectorHeading(k);
            double x = (heading > 180) ? (heading - 360) : heading;
            boolean selected = (k == _selected);

            _paint.setColor(selected ? COLOR_SELECTED : COLOR_POINT);
            float radius = dp(selected ? 5 : 3.5f);
            float y = yOf(_card.getDeviation(k), top, bottom);

            canvas.drawCircle(xOf(x, left, right), y, radius, _paint);

            // Le sud est a la fois a +180 et a -180 : on le montre aux deux bords, sinon la courbe
            // semble arriver sur rien du cote gauche.
            if (heading == 180)
                canvas.drawCircle(xOf(-180, left, right), y, radius, _paint);
        }
    }
}
