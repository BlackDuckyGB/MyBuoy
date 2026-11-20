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

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the map data server helper: a public Overpass instance, and what is known of it.
 *
 * The HELPER is all this is. The address of the server stays a plain string typed into the
 * Parameters page, as it always has been, and this page only writes into that field: whoever has
 * his own server, or an address nobody here has heard of, goes on typing it by hand.
 *
 * The addresses are the BASE of the API, without the trailing /interpreter or /status -- the
 * application completes them itself, exactly as it does for a hand-typed one. Those that need a
 * key carry it in the PATH, which is why the key is a piece of the address rather than a header:
 * {@code https://overpass.nextgis.com/MY_KEY/api}.
 *
 * No key ships inside the application. Each is the user's own, asked for on this page and kept in
 * the preferences, one per server, so that going to look elsewhere and coming back does not mean
 * typing it again.
 */
public class OverpassServer extends BaseObservable {

    // Where the key goes in an address that needs one.
    private static final String KEY_MARK = "{key}";

    private final String id;
    private final String name;
    private final String about;
    private final String template;

    // Deux adresses, parce qu'elles ne repondent pas a la meme question : DOC explique ce qu'est
    // le service et ce qu'il coute, PAGE est l'endroit ou l'on va chercher sa cle une fois le
    // compte cree. Celle qui manque n'affiche pas son bouton.
    private final String doc;
    private final String page;

    private OverpassServer(String id, String name, String about, String template,
                           String doc, String page) {
        this.id = id;
        this.name = name;
        this.about = about;
        this.template = template;
        this.doc = doc;
        this.page = page;
    }

    /** Short name, for the list. */
    @Bindable
    public String getName() {
        return name;
    }

    /** What is known of it: who runs it, what it costs, what it limits. */
    @Bindable
    public String getAbout() {
        return about;
    }

    /** The address as it will be written in the field, key included once there is one. */
    @Bindable
    public String getAddress() {
        return address();
    }

    /** True when this one is the server the application is set to right now. */
    @Bindable
    public boolean getInUse() {

        String current = AppManager.getInstance().getOverpassUrl();

        return (current != null) && current.equals(address());
    }

    /** Does it want a key at all? Drives the KEY button of the row. */
    @Bindable
    public boolean getNeedsKey() {
        return (template != null) && template.contains(KEY_MARK);
    }

    /** And has one been given? */
    @Bindable
    public boolean getHasKey() {

        String key = AppManager.getInstance().getOverpassKey(id);

        return (key != null) && (key.length() > 0);
    }

    /** KEY, or KEY OK once there is one: the row says at a glance what is still missing. */
    @Bindable
    public String getKeyLabel() {
        return getHasKey() ? "Key ✓" : "Key";
    }

    public String getId() {
        return id;
    }

    /** What the service is, or null when there is nothing to read. */
    public String getDoc() {
        return doc;
    }

    /** Where the key itself is handed out, or null when it is not known. */
    public String getPage() {
        return page;
    }

    /** The one to open when there is only room for one: the key first, the reading after. */
    public String getSignup() {
        return (page != null) ? page : doc;
    }

    // The address with whatever key we hold put in. Without a key the mark is left standing, so
    // that what is written in the field says plainly what is missing.
    private String address() {

        if( !getNeedsKey() )
            return template;

        String key = AppManager.getInstance().getOverpassKey(id);

        if( (key == null) || (key.length() == 0) )
            return template;

        return template.replace(KEY_MARK, key);
    }

    /** Everything this page knows has to be read again: a key was typed, or a server chosen. */
    public void refresh() {
        notifyPropertyChanged(BR.inUse);
        notifyPropertyChanged(BR.hasKey);
        notifyPropertyChanged(BR.keyLabel);
        notifyPropertyChanged(BR.address);
    }

    /**
     * The public instances, in the order they are worth trying.
     *
     * Taken from the list the OpenStreetMap wiki keeps, which is the only one that is maintained.
     * The first is the application's own default, so choosing it is also how one goes back.
     *
     * What each one says of itself is written here as it was found, because it is what a user
     * needs to choose with: free or not, limited or not, and by whom it is run.
     */
    public static List<OverpassServer> all() {

        List<OverpassServer> list = new ArrayList<OverpassServer>();

        list.add(new OverpassServer(
                "fossgis",
                "Overpass API (default)",
                "Run by FOSSGIS, the reference instance. No key. Fair use of about 10 000 queries "
                        + "a day, and it is often saturated.",
                "https://overpass-api.de/api",
                null, null));

        list.add(new OverpassServer(
                "lz4",
                "Overpass LZ4",
                "The same machine as the default, behind another front end. No key. Worth trying "
                        + "when the first one refuses, not when it is overloaded.",
                "https://lz4.overpass-api.de/api",
                null, null));

        list.add(new OverpassServer(
                "coffee",
                "Private.coffee",
                "Run by Private.coffee in Austria, successor to the Kumi Systems mirror. No key, "
                        + "and no rate limit announced.",
                "https://overpass.private.coffee/api",
                null, null));

        list.add(new OverpassServer(
                "vk",
                "VK Maps (Mail.ru)",
                "Run by VK in Russia. No key, no announced limit. Your position goes to a Russian "
                        + "server: read the warning above.",
                "https://maps.mail.ru/osm/tools/overpass/api",
                null, null));

        list.add(new OverpassServer(
                "nextgis",
                "NextGIS",
                "Free with a key, and no request limit announced. Sign up for a NextGIS ID to be "
                        + "given one.",
                "https://overpass.nextgis.com/" + KEY_MARK + "/api",
                "https://overpass.nextgis.com/",
                "https://my.nextgis.com/software?tab=api-keys"));

        list.add(new OverpassServer(
                "overpassch",
                "Overpass.ch",
                "Free with a key up to 10 000 requests a month and 30 a minute. Paid tiers above "
                        + "that.",
                "https://api.overpass.ch/v1/" + KEY_MARK,
                "https://overpass.ch/",
                "https://overpass.ch/dashboard/keys"));

        list.add(new OverpassServer(
                "fairway",
                "FairwayMapper",
                "A planet-wide mirror they host themselves. Free with a key for active "
                        + "OpenStreetMap contributors: sign in with your OSM account.",
                "https://api.fairwaymapper.com/k/" + KEY_MARK + "/api",
                "https://www.fairwaymapper.com/api/overpass",
                "https://www.fairwaymapper.com/api/keys"));

        return list;
    }
}
