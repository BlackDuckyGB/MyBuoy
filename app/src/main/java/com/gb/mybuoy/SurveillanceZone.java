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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

// Zone rectangulaire de surveillance (#25), transient (non persistée). Rectangle orienté :
// `angleDeg` est le cap VRAI de l'axe "longueur" ; le rectangle s'étend de +/-halfLenM le long de
// cet axe et +/-halfWidthM en travers.
public class SurveillanceZone {
    public static final int NONE = 0, ACTIVE = 1, EDIT = 2;

    private static final double M_PER_DEG = 111320.0;

    public double centerLat, centerLon;
    public double angleDeg;     // cap vrai de l'axe "longueur"
    public double halfLenM;     // demi-longueur (le long du cap)
    public double halfWidthM;   // demi-largeur (perpendiculaire)

    public SurveillanceZone(double centerLat, double centerLon, double angleDeg,
                            double halfLenM, double halfWidthM) {
        this.centerLat = centerLat; this.centerLon = centerLon;
        this.angleDeg = angleDeg; this.halfLenM = halfLenM; this.halfWidthM = halfWidthM;
    }

    // Vrai si (lat,lon) est dans le rectangle. `marginM` elargit le test (hysteresis ; 0 = exact).
    public boolean contains(double lat, double lon, double marginM) {
        double th = Math.toRadians(angleDeg);
        double dN = (lat - centerLat) * M_PER_DEG;
        double dE = (lon - centerLon) * M_PER_DEG * Math.cos(Math.toRadians(centerLat));
        double u = dE * Math.sin(th) + dN * Math.cos(th);   // le long du cap
        double v = dE * Math.cos(th) - dN * Math.sin(th);   // en travers
        return Math.abs(u) <= halfLenM + marginM && Math.abs(v) <= halfWidthM + marginM;
    }

    // Les 4 coins en {lat,lon}, ordre (+L,+l)(+L,-l)(-L,-l)(-L,+l).
    public double[][] cornersLatLon() {
        double th = Math.toRadians(angleDeg), cosLat = Math.cos(Math.toRadians(centerLat));
        double[][] uv = {{ halfLenM, halfWidthM}, { halfLenM, -halfWidthM},
                         {-halfLenM, -halfWidthM}, {-halfLenM, halfWidthM}};
        double[][] out = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double u = uv[i][0], v = uv[i][1];
            double e = u * Math.sin(th) + v * Math.cos(th);
            double n = u * Math.cos(th) - v * Math.sin(th);
            out[i][0] = centerLat + n / M_PER_DEG;
            out[i][1] = centerLon + e / (M_PER_DEG * cosLat);
        }
        return out;
    }

    // #25: the zone draws itself -- grey veil outside the oriented rectangle, thin blue outline
    // when it is active, thicker orange with corner handles and side lengths when being edited.
    //
    // This drawing used to live in MapGPSPos.draw(), where it had no business being: it sat there
    // because that is the right MOMENT to draw it -- over the map, under the player -- and not
    // because that is the right object. The caller still chooses the moment; the geometry comes
    // back to the zone.
    //
    // Same signature as GameObject.draw(), even though a zone is not one. The surface it needs --
    // a zone is in latitude and longitude, and without a surface and its chunk there is no telling
    // where its corners land on screen -- is fetched here and not held: AppManager reads it from
    // the CURRENT chunk, which changes constantly, and surfaceCreated sets it to null and back.
    // Anything cached in a constructor would be stale within seconds.
    public void draw(Canvas canvas, int layer) {

        if( layer != 0 )
            return;

        GameSurface surface = AppManager.getInstance().getGameSurface();

        if( surface == null )
            return;

        Chunk chunk = surface.getMap();

        // Surveillance zone (#25): grey veil outside the oriented rectangle + outline.
        // Active = thin blue ; editing = thicker orange + L-brackets at the corners.
        if( chunk == null )
            return;

        {
            boolean editing = AppManager.getInstance().getZoneState() == SurveillanceZone.EDIT;
            double[][] zc = cornersLatLon();
            float[] cx = new float[4], cy = new float[4];
            Path zonePath = new Path();
            for (int i = 0; i < 4; i++) {
                cx[i] = surface.getCanvasX(chunk.getX(zc[i][1], surface.getAreaWidth()));
                cy[i] = surface.getCanvasY(chunk.getY(zc[i][0], surface.getAreaHeight()));
                if (i == 0) zonePath.moveTo(cx[i], cy[i]); else zonePath.lineTo(cx[i], cy[i]);
            }
            zonePath.close();
            Path zoneMask = new Path();
            zoneMask.addRect(0, 0, surface.getWidth(), surface.getHeight(), Path.Direction.CW);
            zoneMask.addPath(zonePath);
            zoneMask.setFillType(Path.FillType.EVEN_ODD);
            Paint zoneVeil = new Paint();
            zoneVeil.setColor(Color.BLACK);
            zoneVeil.setAlpha(64); // ~25%
            canvas.drawPath(zoneMask, zoneVeil);

            int zoneColor = editing ? Color.parseColor("#FF9800") : Color.parseColor("#1565c0");
            Paint zoneOutline = new Paint();
            zoneOutline.setAntiAlias(true);
            zoneOutline.setStyle(Paint.Style.STROKE);
            zoneOutline.setStrokeWidth(editing ? 6 : 3);
            zoneOutline.setColor(zoneColor);
            canvas.drawPath(zonePath, zoneOutline);

            if (editing) {
                // Corner handles: white dots ringed in orange (clearly visible on the map).
                float r = 12f;
                Paint hFill = new Paint();
                hFill.setAntiAlias(true);
                hFill.setStyle(Paint.Style.FILL);
                hFill.setColor(Color.WHITE);
                Paint hRing = new Paint();
                hRing.setAntiAlias(true);
                hRing.setStyle(Paint.Style.STROKE);
                hRing.setStrokeWidth(4);
                hRing.setColor(zoneColor);
                for (int i = 0; i < 4; i++) {
                    canvas.drawCircle(cx[i], cy[i], r, hFill);
                    canvas.drawCircle(cx[i], cy[i], r, hRing);
                }
            }

            // Side-length labels on each edge (only in edit mode ; unit follows the speed mode).
            // Corner order (+L,+l)(+L,-l)(-L,-l)(-L,+l) -> edges 0-1 & 2-3 = width, 1-2 & 3-0 = length.
            if (editing) {
            String lenStr = PersonalMark.formatDistance(2.0 * halfLenM / 1000.0, 3);
            String widStr = PersonalMark.formatDistance(2.0 * halfWidthM / 1000.0, 3);
            float ccx = (cx[0] + cx[1] + cx[2] + cx[3]) / 4f;
            float ccy = (cy[0] + cy[1] + cy[2] + cy[3]) / 4f;
            Paint zoneLblBg = new Paint();
            zoneLblBg.setAntiAlias(true);
            zoneLblBg.setTextAlign(Paint.Align.CENTER);
            zoneLblBg.setTextSize(30f);
            zoneLblBg.setFakeBoldText(true);
            zoneLblBg.setStyle(Paint.Style.STROKE);
            zoneLblBg.setStrokeWidth(5f);
            zoneLblBg.setColor(Color.BLACK);
            Paint zoneLbl = new Paint(zoneLblBg);
            zoneLbl.setStyle(Paint.Style.FILL);
            zoneLbl.setColor(Color.WHITE);
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
            String[] edgeTxt = {widStr, lenStr, widStr, lenStr};
            float sw = surface.getWidth(), sh = surface.getHeight();
            for (int k = 0; k < 4; k++) {
                int a = edges[k][0], b = edges[k][1];
                // Middle of the VISIBLE part of the edge (clipped to the screen), not its geometric
                // middle, which may itself be off screen.
                double[] vis = Common.clipSegment(cx[a], cy[a], cx[b], cy[b], 0, 0, sw, sh);
                if (vis == null) continue;   // bord entierement hors ecran
                float mx = (float) ((vis[0] + vis[2]) / 2.0), my = (float) ((vis[1] + vis[3]) / 2.0);
                // Nudged towards the centre so the label stays readable and on screen.
                float ox = ccx - mx, oy = ccy - my;
                float ol = (float) Math.hypot(ox, oy);
                if (ol > 1e-3f) { ox = ox / ol * 22f; oy = oy / ol * 22f; }
                float tx = mx + ox, ty = my + oy + 10f;
                canvas.drawText(edgeTxt[k], tx, ty, zoneLblBg);
                canvas.drawText(edgeTxt[k], tx, ty, zoneLbl);
            }
            }
        }
    }
}
