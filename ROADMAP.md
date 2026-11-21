🇬🇧 English · [🇫🇷 Français](ROADMAP_FR.md)

# ![MyBuoy](design/app-icon/logo48x48.svg) MyBuoy — Roadmap

Everything planned, under way and already done, straight from the working list. Last generated on 21 November 2026.

## 💰 Budget

0 € available 😭 🙏

## 📋 Tasks

- [ ] **🚧 Rework proximity alarms and extend them to personal marks**  
      *`feature №7` · [issue #1](https://github.com/BlackDuckyGB/MyBuoy/issues/1)*  
      The app warns you when you come near a mark, based on distance and on the time left before reaching it. Your own marks now use it too: each has its own alarm switch, and an armed mark is recognisable at a glance on the map and in the lists. Still to come: extending it to sea marks.
- [ ] **🚧 Finish the icon set for personal marks**  
      *`feature №30`*  
      Round out the choice of pictures offered when creating a personal mark, which is too narrow today.
- [ ] **🚧 Finish the Support the development page**  
      *`feature №37`*  
      A page explains what MyBuoy is, why the project is open, how to contribute and what the money is used for, with a link to the Telegram channel. Still to come: settling the payment methods.
- [ ] **🚧 Avatar creation template**  
      *`feature №58`*  
      A blank sheet and a step-by-step guide for drawing a new avatar without starting from scratch: formats, order of the directions, alignment guides and integration steps.
- [ ] **🚧 Muted mark alarm coming back, and one tap doing several things**  
      *`fix №59`*  
      A mark muted by tapping started sounding again as soon as the map reloaded, and a single tap could trigger several actions at once. A muted mark now stays muted for the whole session, while the visual alert still signals the danger.
- [ ] **🚧 Edge arrow pointing to an off-screen alarming mark**  
      *`feature №60`*  
      A mark raising an alarm off screen was shown by nothing at all. An arrow now sits on the edge of the screen pointing at it, with the distance and, for a speed camera, the speed limit.
- [ ] **🚧 New algorithm for the movement modes**  
      *`feature №61`*  
      The avatar changed as soon as the speed varied: a car stopped at a light turned back into a walker. The movement mode is now confirmed over time before being applied, and no longer drops back at the slightest slowdown.
- [ ] **🚧 Publish the task list on GitHub in readable Markdown**  
      *`feature №62`*  
      The roadmap lives today in a spreadsheet, on a single computer. The goal is to publish it on GitHub in a form anyone can read, giving each task its state, its type and a description.
- [ ] **🚧 Place and point-of-interest search**  
      *`feature №67` · [issue #2](https://github.com/BlackDuckyGB/MyBuoy/issues/2)*  
      A magnifier button to look a place up by name, a lighthouse or a supermarket, and get its coordinates, ready to be saved as a mark.
- [ ] **🚧 Document the offline maps server API**  
      *`feature №69`*  
      Describe what a server must answer in order to hand out offline maps, so that anyone can host their own — the counterpart of the address becoming configurable.
- [ ] **🚧 Broom button: clear measurements without leaving pan mode**  
      *`feature №75`*  
      A measurement point placed by mistake could be cleared, but the map jumped straight back to the boat position and the view being looked at was lost. The broom now clears the drawing while leaving the map exactly where it is, and the automatic recentring countdown restarts from five seconds. Each of the button's two actions now has its own sound, played on tap only: a muffled sweep for the erase, a soft bloup for the recentring. An automatic recentring stays silent.
- [ ] **🚧 Loading screen at start-up**  
      *`feature №76`*  
      At start-up the map takes a moment to appear. A loading screen fills the wait: a rope ties itself into a knot, picked at random between a bowline and a figure-eight, while tips explain the gestures the application never shows anywhere.
- [ ] **🚧 Alarm settings: choice of sound, and radar alarms that can be turned off**  
      *`feature №81`*  
      Each of the three alarms (speed camera, personal mark, watch zone) gets its own sound setting: no sound and visual alert only, a sound from the library shipped with the app, or a file picked from the phone. A second setting turns speed-camera alarms off entirely, for people who only want to be warned about their own marks.
- [ ] **🚧 Store a mark’s altitude**  
      *`feature №82`*  
      A mark currently keeps only its latitude and longitude: the altitude of the spot, although the GPS knows it the moment the mark is dropped, is lost. Keeping it would make the app meaningful in the mountains, where a point is not described by its ground position alone, and would allow it to travel with the shared position.
- [ ] **🚧 Distances and headings that take altitude into account**  
      *`feature №83`*  
      Every distance shown is measured flat, as if the ground were level. In the mountains, a point three hundred metres higher up is announced as much closer than it really is on foot. The point is to rework that computation so it accounts for the height difference, and probably to show that difference on its own.
- [ ] **🚧 Share a mark’s position**  
      *`feature №84`*  
      From a mark’s detail view, a button opens the phone’s share sheet and sends the place to whoever you like: the name, the coordinates and the icon travel with it. A recipient who has MyBuoy sees the mark open as it is; anyone else lands on a web page showing the position and offering to open it in their own map application. It also serves to share your current position, by opening a mark without saving it.
- [ ] **🚧 Low-resource mode for modest phones**  
      *`feature №85`*  
      On an entry-level phone, the settings page and the map turn choppy. A single switch, turned on by the user in the settings, then cuts what is purely decorative without touching what is needed to navigate: avatar thumbnails stop animating, cosmetic sounds go quiet and the map redraws less often. Alarms, position and measurements are untouched.
- [ ] **🚧 Local cache for Overpass data**  
      *`feature №86`*  
      Every chunk change asks Overpass again for that area's data, even when the app downloaded it moments ago. It is now kept in a cache folder whose name carries the area covered, and read back instead of downloaded again: moving from one chunk to the next gets snappier, the wait at startup shortens, and a small data plan lasts longer. A new Maintenance page shows the state of the cache and offers several ways to clear it. Double-tapping the map, which forces a reload, deliberately bypasses the cache.
- [ ] **🚧 Double-tap: misleading reload messages, and travel mode reset**  
      *`fix №89`*  
      A double-tap on the map reloads the data around the current position and says so twice, once for the request and once for the confirmation. Trouble is, the confirmation also shows up when nobody asked for anything, in particular at app startup, suggesting a gesture that never happened. It will now appear only for a real double-tap, and when an offline map download finishes, where it is meaningful. The same double-tap also resets the travel mode to undetermined and rearms its timers, to start afresh whenever the avatar on screen no longer matches what you are doing.
- [ ] **🚧 Offline land drawing performance**  
      *`fix №91`*  
      With the offline land map installed the app crawls on a modest phone: it tops out around 18 to 20 frames per second instead of the usual 25, while without that map it runs at full rate. The land is now drawn in one go and without the outline that fattened it, which removes close to two thirds of its cost. Three timing readouts were added to the debug list of the main screen, so it can be measured without a cable. Under test on a phone.
- [ ] **🚧 Offline tide times and heights**  
      *`feature №92`*  
      The application now works out the tide anywhere in the world, with no network and no subscription to a tide service. A page of its own, reached from the menu, gives the time and height of the next two tides, a curve that can be dragged through thirty days, a tide clock, and a green band marking the best times to fish. The data it needs is a 20 MB file downloaded separately, alongside the offline maps.
- [ ] **🚧 Phone orientation heading**  
      *`feature №94`*  
      The phone knows which way it is held thanks to its orientation sensor (gyroscope, accelerometer and compass combined). The app now computes this orientation heading ten times per second, steadied so that it does not shake while standing still, yet reacting at once as soon as you turn. When stopped, when GPS can no longer tell a direction, it drives the arrows towards the marks and the displayed heading: just turn on the spot to see the arrows follow. While moving, the heading remains the GPS one. A next step will also use it to steady the GPS heading at low speed. The phone's motion sensors (accelerometer and gyroscope) also make stop detection faster and more reliable: once the phone no longer moves, the stop is recognised at once and GPS drift no longer fakes a movement; as soon as you move off again, the app notices in just over a second. The status shows "Stopped + Still" when stopped and perfectly motionless.
- [ ] **🚧 Personal marks draw order**  
      *`feature №97`*  
      Today marks are drawn in list order (alarm, selection, distance, title): the selected or alarming mark is drawn first and ends up under the others. New order: north to south, then west to east at equal height; alarming marks after that, on top; the main selected mark last of all.
- [ ] **🚧 Streamer mode (on-screen anonymity)**  
      *`feature №98`*  
      To record YouTube videos without showing where you are: a "Streamer mode" switch in the developer parameters. Displayed coordinates are shifted by 1000 to 5000 km (offset drawn at random on each launch), the mark editor shows them read-only, and map names (streets, roads, places, buoys) are replaced with made-up ones.
- [ ] **🚧 Offline Sun and Moon page**  
      *`feature №99`*  
      The Sun and Moon page shows, with no network, where the Sun and the Moon stand overhead. A sky map places them on the road each is travelling, beside the rise and set times, the three twilights, the Moon drawn at the phase and the tilt it really has, its distance and its age. An Earth tide curve at the foot of the page drags through a month, and everything above follows the mark, so the sky of any moment can be read. The page keeps itself current while it is open, and it works as well for the user's own position as for a place chosen anywhere in the world, remembered apart from the tide one.
- [ ] **💰 Finish zoom and scale handling**  
      *`feature №4` · ⏳ awaiting funding*  
      Finish the map zoom so it moves cleanly from a wide view to a close-up, keeping the display readable at every scale.
- [ ] **💰 Offline maps**  
      *`feature №5` · ⏳ awaiting funding*  
      Navigate with no network: the maps are stored on the phone, which is essential at sea where mobile coverage stops early.
- [ ] **💰 Load map areas beyond the GPS position**  
      *`feature №10` · ⏳ awaiting funding*  
      The map is only loaded around your own position. It should also load the area you pan towards, so you can look further afield.
- [ ] **💰 Automatic zoom based on how close a mark is**  
      *`feature №14` · ⏳ awaiting funding*  
      The map zooms in by itself as you get closer to a point, and back out as you move away. A single tap turns the behaviour off.
- [ ] **💰 Pinch-to-zoom with two fingers**  
      *`feature №15` · ⏳ awaiting funding*  
      Change the map scale by spreading or pinching two fingers, the gesture everyone expects on a phone.
- [ ] **Course-up orientation mode**  
      *`feature №16`*  
      Turn the map so that the direction of travel points to the top of the screen, instead of north always being up. A button switches between the two modes.
- [ ] **💰 Multi-user mode with a server**  
      *`feature №18` · ⏳ awaiting funding*  
      A server to share marks between several users and to hand out the offline maps: everyone sees the others' marks, between boats on the same outing for instance.
- [ ] **Add a release number taken from the git commit**  
      *`feature №21`*  
      Add to the version number the exact identifier of the source the app was built from, so there is no doubt about what is running on a phone.
- [ ] **Fill in the field-tuning sheet for the prediction settings**  
      *`feature №23`*  
      Record on the ground, speed by speed, the settings that give the best display, so that the app can then pick them on its own.
- [ ] **Show the speed reported by the GPS receiver**  
      *`feature №24`*  
      Show the speed reported by the GPS receiver itself next to the one the app works out, to compare the two and keep whichever is closer to the truth.
- [ ] **Offer the sea mark icons when creating a personal mark**  
      *`feature №31` · [issue #4](https://github.com/BlackDuckyGB/MyBuoy/issues/4)*  
      Let a personal mark use the sea mark icons — buoys, beacons, lights — on top of the pictures already on offer.
- [ ] **💰 Fetch the neighbouring map area when panning to its edge**  
      *`feature №32` · ⏳ awaiting funding*  
      When you pan the map to the edge of the loaded area, fetch the neighbouring one ahead of time so the movement does not run into emptiness.
- [ ] **Documentation in video: a YouTube channel and tutorials**  
      *`feature №34`*  
      Show the app in video rather than explain it in writing: first install, allowing an app from outside the store, downloading the offline map, then the everyday gestures. Subtitles let people follow in their own language.
- [ ] **Keep the avatar in place when the map area changes**  
      *`fix №35`*  
      When moving from one map area to the next, the avatar should stay put on screen instead of jumping.
- [ ] **Sea marks that are lines or areas, not points**  
      *`feature №42`*  
      Some marine information is not a single point but lines and areas: navigation alignments, restricted zones. They are not displayed today and need a rendering of their own.
- [ ] **Recognise red-light cameras, not only speed cameras**  
      *`fix №43` · [issue #5](https://github.com/BlackDuckyGB/MyBuoy/issues/5)*  
      Red-light cameras are not described like speed cameras in the map data, and the app ignores them: roughly one in seven is missed.
- [ ] **Do not alarm for a camera facing the other way**  
      *`fix №44` · [issue #6](https://github.com/BlackDuckyGB/MyBuoy/issues/6)*  
      A camera only watches one direction. The app currently alarms even when you pass it the other way round, which is a false alert; its orientation has to be compared with the heading you are on.
- [ ] **💰 Load alarm objects well beyond the screen**  
      *`feature №46` · ⏳ awaiting funding*  
      Load the objects that can raise an alarm, sea marks and speed cameras, over an area far wider than the screen, so you are warned before they even come into view.
- [ ] **Route planning and following**  
      *`feature №56`*  
      Chain several marks into a route, then follow it: active waypoint, heading to hold, distance off the route, distance and time to arrival. The exact scope is still to be agreed.
- [ ] **Dark mode following the phone's setting**  
      *`feature №73` · [issue #3](https://github.com/BlackDuckyGB/MyBuoy/issues/3)*  
      A dark version of the app that follows the phone's own setting — which switches by itself with the time of day or at sunset — so the screen does not dazzle at night, at sea as on the road.
- [ ] **Log the unhandled highway types drawn in red**  
      *`feature №78`*  
      Paths whose type the app does not know, a motorway rest area or a bus lane for instance, are drawn in red for want of anything better. The idea is to record their coordinates and their properties in a file along the way, so that what is missing can be listed and then given a proper rendering.
- [ ] **Heading and speed stability at low speed**  
      *`feature №79`*  
      At walking pace, two GPS readings a second apart are so close together that the error on each one is enough to skew the computed heading and speed. The point is to rework that computation so it stays right when moving slowly, without turning sluggish once you speed up.
- [ ] **Overlapping alarm panels with three or more marks**  
      *`fix №80`*  
      When several marks raise an alarm at the same time and at the same spot on the edge of the screen, their panels can overlap. Two panels are handled; three or more is still to be done.
- [ ] **Offline chunk when the download no longer gets through**  
      *`feature №87`*  
      At sea or in a dead zone, the app becomes unusable as soon as you leave the area already loaded: the map stays frozen on a sector you have left, and on startup it gets no further than the waiting screen. It now falls back on an offline area, built from what the phone already has, the coastline and the marks, rather than showing nothing at all. That area knows it is incomplete: as long as the network stays away, the app keeps trying to replace it with the real one.
- [ ] **Option to keep the navigation bar on screen**  
      *`feature №88`*  
      Immersive mode wipes Android's button bar off the main screen, which gets in the way of anyone using it to go back or switch apps. A setting keeps it on screen for good, as it already is on the other pages. The map then still draws all the way down, underneath the bar, which becomes a translucent white veil instead of an opaque strip; the controls shift so they do not end up beneath it, in portrait as well as landscape. The top bar stays hidden.
- [ ] **💰 Cut the land polygons upstream, when the map database is built**  
      *`feature №90` · ⏳ awaiting funding*  
      The land of the offline map is stored as pieces cut on a one-degree grid, about 111 km across, while the screen shows 200 m. A piece that big is not drawn at all: wherever it carries the coastline, no land shows up. These pieces need to be re-cut to a sensible size when the database is built, so the app only has to display them.
- [ ] **Export and restore personal marks and settings**  
      *`feature №93`*  
      Uninstalling the application, or clearing its data, loses the personal marks and the settings for good: nothing keeps them outside the app's own private folder, which Android removes along with it. The idea is a button that writes all of it to a file the user keeps wherever they like, and a second one that reads it back.
- [ ] **Draw depth: sort game objects by latitude**  
      *`feature №95`*  
      The drawing order is fixed today: map and marks at the very back, then the creatures, the player and the bullets over everything. A creature therefore always passes behind the marker even when it stands to the south, and a bullet always in front. Marks, creatures, bullets and the player should be painted in one pass sorted by decreasing latitude (increasing y in area coordinates), which is what gives the false-3D effect: what is north goes behind, what is south goes in front.
- [ ] **Detect "in the water" on lakes, ponds and rivers**  
      *`feature №96`*  
      Today the app can only tell that you are in the water from the coastline (the offline land database). On an inland lake or pond, it therefore answers "in the land" while the map shows water. The answer should always match what is on screen, islands included, and also when an area (housing, for instance) is drawn over a large stretch of water.
- [ ] **Identify who created a shared mark**  
      *`feature №100`*  
      For the multi-user mode and mark sharing, each mark must be able to tell who created it without revealing anything personal: an identifier specific to the installation, drawn at random by the app, and a nickname chosen by the user.
- [ ] **Thin seams where map polygon tiles meet**  
      *`fix №101`*  
      Thin light (sometimes grey) straight lines show on the map, horizontal and vertical, where two pieces of the same water or land polygon meet. They come from cutting large polygons into tiles, done for drawing performance.
- [x] **Hide the navigation bar (immersive mode)**  
      *`feature №1`*  
      The map fills the whole screen: Android's button bar disappears, and a swipe down from the top brings it back.
- [x] **Stabilise position and heading**  
      *`feature №2`*  
      A raw GPS fix jitters and jumps. The app smooths the displayed position and heading so movement stays steady, even at walking pace.
- [x] **Uncertainty cone for the heading**  
      *`feature №3`*  
      Heading is never known exactly. Rather than a single line, the screen shows a cone whose width reflects how reliable the current heading is.
- [x] **Investigate the missing sea marks**  
      *`feature №6`*  
      Some beacons and buoys present in the map data never show up on screen. The point is to find out why before fixing it.
- [x] **Smooth out the moving figure**  
      *`feature №8`*  
      The avatar glides along continuously instead of jumping from spot to spot at every GPS fix.
- [x] **Inertial scrolling when panning the map**  
      *`feature №9`*  
      Flick the map with a finger and it keeps gliding, slowing down gently instead of stopping dead.
- [x] **Show the current heading in degrees**  
      *`feature №11`*  
      The heading you are following is shown in degrees at the bottom right. A tap switches between true and magnetic north, with the local declination applied.
- [x] **Show the bearing to the selected mark**  
      *`feature №12`*  
      A panel at the top left gives the direction to steer to reach the selected point; a tap moves on to the next one, from nearest to furthest.
- [x] **Show distance and time to the selected mark**  
      *`feature №13`*  
      Below the bearing, the screen shows the distance left to the selected point and how long it will take to get there at the current pace.
- [x] **Add and edit a mark**  
      *`feature №17`*  
      Create a mark and edit it: coordinates typed in the usual formats, a button to take the current position, and a long press to open the editor, from the proximity list as well as from the map. Moving a mark is done by editing its coordinates, or with a single button that drops it where you stand.
- [x] **Finish the selected-mark indicator**  
      *`feature №19`*  
      The selected mark's name and distance are drawn along the line leading to it, and stay readable when the mark goes off screen; nothing is drawn if there is no room.
- [x] **Software version numbering**  
      *`feature №20`*  
      The app carries a version number in the form Major.Minor.build, the first two set by hand and the last one bumped automatically at every build.
- [x] **Publish the project on GitHub**  
      *`feature №22`*  
      The code is published on GitHub under the AGPL-3.0, with a commercial licence as an alternative: legal texts, a Contributor License Agreement signed through a bot, a fresh repository without the private history, and credit to the map data sources.
- [x] **Watch zone for anchoring or a tacking corridor**  
      *`feature №25`*  
      Draw a zone around yourself and your marks: everything outside is greyed out and an alarm sounds as soon as you leave it, which at anchor catches a dragging anchor. The zone is set with two fingers, for both angle and size.
- [x] **Button to recentre the map on your position**  
      *`feature №26`*  
      A button brings the view back to your current position. It only appears once you have panned the map, so only when it is of any use.
- [x] **Speed up the offline map download**  
      *`fix №27`*  
      Downloading an offline map was slow given its size. The way the file is fetched was reworked to make the most of the connection, while keeping the ability to resume after a break.
- [x] **Keep the map download running in the background**  
      *`fix №28`*  
      A map download used to stop as soon as you left the app. It now carries on in the background, with a notification showing how far along it is.
- [x] **Re-download a map after deleting it, without restarting**  
      *`fix №29`*  
      After deleting an offline map you had to quit and restart the app before you could download it again. The list of available maps now refreshes on its own.
- [x] **Burger menu opening the extra pages**  
      *`feature №33`*  
      A three-line button opens a menu onto the app's four pages: personal marks, offline maps, parameters, and supporting the development.
- [x] **Finish the Parameters page**  
      *`feature №36`*  
      A settings page gathers the options that used to hide behind debug buttons: alarm distances and delays, coordinate format, track display, avatar choice, plus a section kept for advanced settings.
- [x] **Direction arrow on each personal mark**  
      *`feature №38`*  
      In the personal marks list, an arrow shows which way each mark lies from your current position, among the eight points of the compass.
- [x] **Move the online map data over to Overpass**  
      *`fix №39`*  
      Online, the app was drawing its map data from OpenStreetMap's editing service, which is not meant for that and could block it once the project is public. It is moving to the service meant for reading, with nothing lost on the way.
- [x] **Flashing border alert alongside the alarm sound**  
      *`feature №40`*  
      Alongside the sound, the edge of the screen pulses red when an alarm goes off. Muting does not clear it: the alert stays visible as long as the situation lasts.
- [x] **Screen rotation restarted alarms and leaked memory**  
      *`fix №41`*  
      Rotating the phone restarted the running alarms and stacked them up, while eating memory every time. The app now takes rotation in its stride without rebuilding itself, keeping the view where it was.
- [x] **Off-screen position indicator while panning**  
      *`feature №45`*  
      When you explore the map by hand you lose sight of where you are. A red marker on the edge of the screen points towards your real position, with the distance to it.
- [x] **Universal notification bar at the bottom of the screen**  
      *`feature №47`*  
      A banner appears at the bottom of any screen to report an error in red or a piece of information in blue, then fades away on its own after a few seconds.
- [x] **Crash when no map area had been loaded yet**  
      *`fix №48`*  
      As long as no map had been loaded, the app crashed over and over instead of simply showing open sea while it waited.
- [x] **Wrongly reported as being in the water at start-up**  
      *`fix №49`*  
      Started on land, the app believed it was in the water for a while: it drew the map before opening the coastline data. It now waits for that data.
- [x] **Double-tap the map to force a reload**  
      *`feature №50`*  
      A double tap on the map forces the current area to reload, with a confirmation message — handy when the display looks stuck or incomplete.
- [x] **Make the main selected mark stand out**  
      *`feature №51`*  
      When several marks are selected, the one whose bearing is displayed keeps its normal look while the others are dimmed, so it can be picked out at a glance.
- [x] **Sighting reticles to measure distances and drop marks**  
      *`feature №52`*  
      In pan mode, sighting reticles appear on the map: tapping one creates a mark at the spot aimed at, tapping elsewhere measures a distance leg by leg, each leg carrying its length and travel time.
- [x] **Map lost when returning from another app while measuring**  
      *`fix №53`*  
      Coming back from another app while a measurement was under way, the map vanished and never reloaded. The link to the display is no longer broken, and the view comes back exactly as it was left.
- [x] **Compass deviation tables**  
      *`feature №54`*  
      A boat's compass never shows true north: every vessel has its own errors, measured heading by heading. The app stores several compasses with their deviation curve and corrects every displayed heading accordingly.
- [x] **Identity banner: version, copyright and licence on the Support page**  
      *`feature №55`*  
      The Support the development page doubles as an About page: it shows the app's name, its version number, the Golden-Block copyright and the licence it is distributed under.
- [x] **Draw the missing Middle and Fast avatars**  
      *`feature №57`*  
      The app picks the avatar from your pace: a boat on water, a walker on land, and two faster categories that have no artwork yet. They still need drawing.
- [x] **Speed frozen on its last value when GPS is lost**  
      *`fix №63`*  
      Walking into a building, the speed stayed frozen on its last value instead of dropping to zero, and the position kept drifting. The app now watches how old the last fix is.
- [x] **First tap picks one reticle and the others disappear**  
      *`feature №64`*  
      With three reticles on screen, measuring got confusing. The first tap keeps the nearest reticle and the other two disappear, leaving a single chain of measurements to follow.
- [x] **Open MyBuoy from a shared position**  
      *`feature №65`*  
      When a position is shared from a message, a website or another app, MyBuoy now appears in the list of apps that can open it, and the mark editor opens ready filled in.
- [x] **Mark distances cut off in the nearby list**  
      *`fix №66`*  
      In the nearby marks list, a slightly long distance was cut off on screen. The number of decimals now adapts to the room available.
- [x] **Server addresses moved into the settings**  
      *`feature №68`*  
      The addresses of the servers the app talks to were fixed in the code. They can now be changed in the advanced settings, without rebuilding the app.
- [x] **Windowed mode: white band, dead panning, hidden controls**  
      *`fix №70`*  
      In split screen, a white band took up the top, the map no longer panned under the finger, and the controls ended up beneath the system bars. The map now fills the whole frame, and only the controls move clear of the bars.
- [x] **A drag started on a control now moves the map**  
      *`fix №71`*  
      Putting a finger on a button then sliding did nothing at all: the gesture stayed captive to the button. It now hands over to the map as soon as the finger moves.
- [x] **Window resizing: object scale, panning and measurements**  
      *`fix №72`*  
      When the window was resized, the coastline and the map objects drifted away from the boat until the next load. Everything is now rescaled at the moment of resizing, measurement chains included.
- [x] **Share button for the app (Support page)**  
      *`feature №74`*  
      A single button at the top of the Support page opens Android's share sheet and sends the site link. A line of text and an animated arrow invite the user to tap it.
- [x] **Watch zone misplaced with several marks: instant alarm**  
      *`fix №77`*  
      When the watch zone is created with several marks selected, it is sometimes placed askew or too far off, so the boat is already outside it the moment it appears and the leaving-the-zone alarm goes off at once.
