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

//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OSMData {

    public String version;
    public String generator;
    public String copyright;
    public String attribution;
    public String license;

    public OSMBounds bounds;

    // Overpass ONLY. A query that fails at run time (out of memory, timeout) still
    // answers HTTP 200, with this field set and an EMPTY elements list. Gson silently
    // ignores unknown fields, so without this member a failed query is indistinguishable
    // from "nothing here" -> a blank map, forever, with no error anywhere.
    // Always test hasRemark() before trusting an empty result.
    public String remark;

    public List<OSMElement> elements;

    public boolean hasRemark() {
        return (remark != null) && (remark.length() > 0);
    }


    //Indexes Part
    private Map<Long,OSMElement> _nodes = null;
    public Map<Long, OSMElement> getNodes() {
        createIndexes();

        return _nodes;
    }

    private Map<Long,OSMElement> _ways = null;
    public Map<Long,OSMElement> getWays() {
        createIndexes();

        return _ways;
    }

    private Map<Long,OSMElement> _relations = null;
    public Map<Long, OSMElement> getRelations() {
        createIndexes();
        return _relations;
    }

    private boolean _idxOK = false;
    private void createIndexes() {
        if( !_idxOK ) {

            _nodes = new HashMap<Long, OSMElement>();
            _ways = new HashMap<Long, OSMElement>();
            _relations = new HashMap<Long, OSMElement>();

            // 'elements' is absent from an Overpass answer that carries only a remark,
            // and from any malformed payload. Indexing must not blow up on it.
            if( elements == null ) {
                GBLog.e("OSMData", "no elements in payload");
                _idxOK = true;
                return;
            }

            final int size = elements.size();
            for (int i = 0; i < size; i++)
            {
                OSMElement elm = elements.get(i);
                if( elm.type.equals("node") )
                    _nodes.put( elm.id, elm);
                else if( elm.type.equals( "way") )
                    _ways.put( elm.id, elm);
                else if( elm.type.equals("relation") )
                    _relations.put( elm.id, elm);
                else
                    GBLog.e("OSMData", "type KO:"+elm.type);
            }

            // Link Ways Nodes
            for(OSMElement way:  _ways.values()) {
                // A way has no 'nodes' array when the query asked for 'out center' or
                // 'out skel' instead of 'out body' + the way nodes. We never do that
                // (it would also drop the geometry), but a wrong query must degrade
                // instead of throwing.
                if( way.nodes == null ) {
                    GBLog.e( "OSMData", "WAY without nodes (out center/skel?) id:" + way.id);
                    continue;
                }

                for( Long node: way.nodes) {

                    OSMElement nd = _nodes.get(node);

                    if( nd == null ) {
                        GBLog.e( "OSMData", "BAD WAY: incomplete Data");
                        way.latN = null;
                        way.elements.clear();

                        break;
                    }
                    else
                    {
                        if (way.latN == null) {
                            way.latN = nd.lat;
                            way.latS = nd.lat;
                            way.longW = nd.lon;
                            way.longE = nd.lon;
                        } else {

                            // Les points sont censé etre proche donc si l'on a
                            // un premier point a -179 de longitude
                            // et que le suivant est a 179 de longitude
                            // il faut considerer celui si comme etant a 179 -> -181
                            // donc soit longW=-181 longE=-179
                            // ou soit longW=179 longE=181

                            double lat = nd.lat;
                            double lon = nd.lon;

                            if (Math.abs(lon - way.longW) > 180)
                                lon -= 360;

                            if (Math.abs(lat - way.latN) > 90)
                                lat -= 180;

                            if (lat > way.latN)
                                way.latN = lat;
                            else if (lat < way.latS)
                                way.latS = lat;

                            if (lon > way.longE)
                                way.longE = lon;
                            else if (lon < way.longW)
                                way.longW = lon;
                        }

                        way.elements.add(nd);
                    }
                }

                if( way.latN == null ) {
                    way.latN = 0.0;
                    way.latS = 0.0;
                    way.longW = 0.0;
                    way.longE = 0.0;
                    way.kmX = 0.0;
                    way.kmY = 0.0;
                } else {
                    way.kmX = Math.abs(Chunk.longitudeGetKmFrom( way.longW,  way.latN, way.longE));
                    way.kmY = Math.abs(Chunk.latitudeGetKmFrom( way.latN,  way.latS));

                    int nbValuesOverScale = 0;

                    if( (way.latN < -90) || (way.latN > 90) )
                        nbValuesOverScale ++;
                    if( (way.latS < -90) || (way.latS > 90) )
                        nbValuesOverScale ++;
                    if( (way.longW < -180) || (way.longW > 180) )
                        nbValuesOverScale ++;
                    if( (way.longE < -180) || (way.longE > 180) )
                        nbValuesOverScale ++;

                    if( nbValuesOverScale > 2 ) {
                        // Invert all values
                        GBLog.d("OSMData", String.format("Invert ALL"));
                    }
                }
            }

            // Link Relations Ways
            for(OSMElement rel:  _relations.values()) {
                // Same as ways above: no 'members' when the query did not ask for them.
                if( rel.members == null ) {
                    GBLog.e( "OSMData", "RELATION without members (out center/skel?) id:" + rel.id);
                    continue;
                }

                for( OSMMember mbr: rel.members) {
                    switch( mbr.type )
                    {
                        case "node":
                            mbr.node = _nodes.get(mbr.ref);
                            if( mbr.node == null )
                                GBLog.e( "OSMData", "BAD RELATION MEMBER: incomplete Data");

                            break;

                        case "way":
                            mbr.way = _ways.get(mbr.ref);
                            if( mbr.way == null )
                                GBLog.e( "OSMData", String.format("BAD RELATION MEMBER: incomplete Data: %s",
                                        (rel.tags != null) ? rel.tags.get("landuse") : null));
                            break;

                        case "relation":
                            mbr.relation = _relations.get(mbr.ref);
                            if( mbr.relation == null )
                                GBLog.e( "OSMData", "BAD RELATION MEMBER: incomplete Data");
                            break;

                        default:
                            GBLog.d( "OSMDATA", String.format("REL %s", mbr.type) );
                            break;
                    }

                }
            }

            _idxOK = true;
        }
    }



    public static OSMData jsonToOSMData( String jsonString ) {

        OSMData data = null;
        try {
            Gson gson = new Gson();
            data = gson.fromJson(jsonString, OSMData.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }

    /**
     * Streaming variant: Gson reads straight from the socket, so we never materialise the
     * whole response as a String (a tens-of-MB body would need a char[] twice its size ->
     * OutOfMemoryError). Caller owns the reader / underlying stream (closed in its finally).
     */
    public static OSMData jsonToOSMData( Reader reader ) {

        OSMData data = null;
        try {
            Gson gson = new Gson();
            data = gson.fromJson(reader, OSMData.class);
        } catch (Exception e) {
            e.printStackTrace();
            MemLog.logEvent("jsonToOSMData EXC: " + e);   // capte la cause : tronqué ? timeout ? JSON cassé ?
        }

        return data;
    }
}
