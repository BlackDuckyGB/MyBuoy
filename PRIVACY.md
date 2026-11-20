🇬🇧 English · [🇫🇷 Français](PRIVACY_FR.md)

# MyBuoy privacy policy

*Last updated: 19 November 2026.*

MyBuoy is built to work offline, and it keeps what it knows about you on your
phone. There is no account, no advertising, no usage statistics and no crash
reporting. MyBuoy does not collect personal data, does not sell it and does not
share it with anyone.

## What stays on your phone

Your position, your heading and speed, your personal marks, your alarms, your
compass tables and your settings are stored only on the device, in the app's own
storage. Nothing of this is sent to Golden-Block or to anyone else. Uninstalling
the app, or clearing its data, deletes it.

The app writes technical messages to the Android system log for debugging. They
stay on the phone and are never uploaded.

## When the app goes online

Some features need the network. In each case the app only sends what the
feature needs, and the server also sees your IP address, as with any internet
connection.

**Map data.** When a map area is not available offline, the app asks an
OpenStreetMap data server (the Overpass API, or the OpenStreetMap API in online
mode) for the objects in that area. The request contains the boundaries of the
area being displayed, so the server can infer roughly where you are looking. The
server used can be changed in the developer settings.

**Place search.** When you search for a place by name, the text you type is sent
to the Nominatim search service of OpenStreetMap.

**Offline maps and tide data.** Downloading offline maps or the tide model
fetches files from golden-block.fr and from GitHub. These requests contain no
position.

**Sharing a mark.** When you choose to share a mark, the app builds a link to
golden-block.fr that contains the mark's name, coordinates, altitude and icon,
and hands it to the app you pick. Golden-Block's server only receives these
details if someone opens the link. The web page uses no cookies.

Each of these services is run under its own privacy policy:
[OpenStreetMap Foundation](https://osmfoundation.org/wiki/Privacy_Policy) for the
OpenStreetMap API and Nominatim, the operator of the Overpass server you use,
and [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

## Permissions

**Location** is used to show your position, follow your track and trigger your
alarms. It is never sent anywhere, except indirectly through the map areas
described above.

**Notifications** are used by the alarms and by the offline map download, which
runs as a foreground service.

**Storage** is used to read and write the offline maps and tide files, which can
be kept on a memory card.

**Internet** is used only for the features listed above.

## Children

MyBuoy collects no personal data from anyone, children included.

## Changes

If MyBuoy ever needs to handle personal data (for instance for a future
multi-user mode), this policy will be updated before that feature ships, and
the change will be announced in the release notes.

## Contact

Questions about this policy: privacy@golden-block.fr
