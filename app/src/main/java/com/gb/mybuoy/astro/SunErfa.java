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

/*
 * ===========================================================================
 * THIS FILE IS A JAVA PORT OF ERFA's epv00.c (the eraEpv00 routine), together
 * with the general precession polynomial of its p06e.c (eraP06e).
 *
 * The copyright, conditions and disclaimer reproduced below belong to that
 * material and travel with it. The Java adaptation is Golden-Block's and is
 * distributed under the terms at the top of this file; the conditions below
 * continue to apply to everything derived from ERFA, under either of those
 * terms.
 *
 * Source: https://github.com/liberfa/erfa (src/epv00.c, src/p06e.c)
 *
 * This is a library derived from SOFA, not SOFA itself.
 *
 *
 *
 *  Copyright (C) 2013-2023, NumFOCUS Foundation.
 *  All rights reserved.
 *
 *  This library is derived, with permission, from the International
 *  Astronomical Union's "Standards of Fundamental Astronomy" library,
 *  available from http://www.iausofa.org.
 *
 *  The ERFA version is intended to retain identical functionality to
 *  the SOFA library, but made distinct through different function and
 *  file names, as set out in the SOFA license conditions.  The SOFA
 *  original has a role as a reference standard for the IAU and IERS,
 *  and consequently redistribution is permitted only in its unaltered
 *  state.  The ERFA version is not subject to this restriction and
 *  therefore can be included in distributions which do not support the
 *  concept of "read only" software.
 *
 *  Although the intent is to replicate the SOFA API (other than
 *  replacement of prefix names) and results (with the exception of
 *  bugs;  any that are discovered will be fixed), SOFA is not
 *  responsible for any errors found in this version of the library.
 *
 *  If you wish to acknowledge the SOFA heritage, please acknowledge
 *  that you are using a library derived from SOFA, rather than SOFA
 *  itself.
 *
 *
 *  TERMS AND CONDITIONS
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1 Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  2 Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 *  3 Neither the name of the Standards Of Fundamental Astronomy Board,
 *    the International Astronomical Union nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 * ===========================================================================
 */

/*
 * Three deliberate departures from the original, all narrowing it.
 *
 * The original returns the Earth's position AND velocity, heliocentric AND
 * barycentric. Only the heliocentric position is wanted here, so the whole
 * barycentric half of the model is absent and no velocity is computed.
 *
 * The series is TRUNCATED. The original carries 1323 terms for the
 * heliocentric Earth; every term whose amplitude is below 1e-7 au is dropped,
 * leaving 247. That threshold is about fifteen kilometres on the position of
 * the Earth, or two hundredths of an arcsecond in the direction of the Sun,
 * which is far finer than a sunrise time or a tidal potential can use. What
 * the truncation actually costs is measured by the regression check rather
 * than assumed.
 *
 * The original ends by rotating from the ecliptic into the BCRS. The series
 * itself yields an ECLIPTIC vector, which is what is wanted here, so that
 * rotation is left out. The longitude is then carried forward to the ecliptic
 * of date by the general precession polynomial, so that the Sun and the Moon
 * are expressed in the same frame.
 */
package com.gb.mybuoy.astro;

/** Geocentric position of the Sun, from the Earth series published by ERFA. */
public final class SunErfa
{
    /** Arcseconds to radians. */
    private static final double AS2R = Math.PI / (180.0 * 3600.0);

    /** Days in a Julian year, the time unit of the Earth series. */
    private static final double YEAR = 365.25;

    /** Days in a Julian century, the time unit of the precession polynomial. */
    private static final double CENTURY = 36525.0;

    /** Julian date of J2000.0. */
    private static final double J2000 = 2451545.0;

    /** One astronomical unit, in kilometres (IAU 2012). */
    private static final double AU_KM = 149597870.7;

    private static final double[] E0X = {
        9.9982928781320e-01, 1.7534851715040e+00, 6.2830758504460e+00,
        8.3525795674140e-03, 1.7103444045820e+00, 1.2566151700890e+01,
        5.6114453351480e-03, 0.0000000000000e+00, 0.0000000000000e+00,
        1.0466642955720e-04, 1.6672254167700e+00, 1.8849227551340e+01,
        3.1108425346770e-05, 6.6875133902510e-01, 8.3996847318570e+01,
        2.5524135035500e-05, 5.8306373584130e-01, 5.2969097211180e-01,
        2.1372078457810e-05, 1.0923309540110e+00, 1.5773435434340e+00,
        1.6802401829510e-05, 4.9553661349870e-01, 6.2795526908240e+00,
        1.6790123707950e-05, 6.1530140919010e+00, 6.2865990100680e+00,
        1.4455269467770e-05, 3.4727441004920e+00, 2.3528661535060e+00,
        1.0910382461840e-05, 3.6898457861190e+00, 5.2236939062220e+00,
        9.3443997339320e-06, 6.0739346456720e+00, 1.2036460728780e+01,
        8.9931829106520e-06, 3.1757052490690e+00, 1.0213285547390e+01,
        5.6655460341160e-06, 2.1524846722460e+00, 1.0593819442240e+00,
        6.8441467030350e-06, 1.3069640997500e+00, 5.7533848783340e+00,
        7.3466109055650e-06, 4.3549800704660e+00, 3.9814901898930e-01,
        6.8153964744140e-06, 2.2182292112670e+00, 4.7057323070120e+00,
        6.1127872530530e-06, 5.3847884254580e+00, 6.8127668225580e+00,
        4.5181207112390e-06, 6.0876040122910e+00, 5.8849268314560e+00,
        4.5219634307060e-06, 1.2794245249060e+00, 6.2567775271560e+00,
        4.4974267640850e-06, 5.3691291442660e+00, 6.3093741737360e+00,
        4.0621905669590e-06, 5.4364733033670e-01, 6.6812248694350e+00,
        5.4121934801920e-06, 7.8678385283950e-01, 7.7552261007200e-01,
        5.4698390493860e-06, 1.4614403111340e+00, 1.4143495244330e+01,
        5.2052640834770e-06, 4.4329446961160e+00, 7.8604193938800e+00,
        2.1497599354550e-06, 4.5022374968460e+00, 1.1506769756670e+01,
        2.2791096185010e-06, 1.2394413088150e+00, 7.0585984605180e+00,
        2.2592829396830e-06, 3.2724309853310e+00, 4.6940029341100e+00,
        2.5589502713190e-06, 2.2654710864040e+00, 1.2168002681900e+01,
        2.5615814475550e-06, 1.4547406532450e+00, 7.0993304901260e-01,
        1.7814411154400e-06, 2.9620686302060e+00, 7.9629803797860e-01,
        1.6120058746440e-06, 1.4732550410060e+00, 5.4867778124670e+00,
        1.8186306671050e-06, 3.7439032934470e-01, 6.2830087150210e+00,
        1.8186013775290e-06, 6.2741743545540e+00, 6.2831429858700e+00,
        1.5544759252570e-06, 1.6241109068160e+00, 2.5132303401780e+01,
        2.0909480292410e-06, 5.8520522762560e+00, 1.1790629090820e+01,
        2.0001763454600e-06, 4.0720932985130e+00, 1.7789845607110e+01,
        1.2895359177590e-06, 5.2170193310690e+00, 7.0793738884240e+00,
        1.2811353078810e-06, 4.8020545389340e+00, 3.7387614537070e+00,
        1.5182290056920e-06, 8.6919147425020e-01, 2.1329907977830e-01,
        9.4501285790270e-07, 4.6018595299500e+00, 1.0977078784560e+01,
        7.7811194949960e-07, 1.8443528166940e+00, 8.8273902471850e+00,
        7.7334077599120e-07, 3.5827901547500e+00, 5.5075532403740e+00,
        7.3506443181200e-07, 2.6952777882300e+00, 1.5890729163350e+00,
        6.5359288270230e-07, 3.6513279861420e+00, 1.1769853662910e+01,
        6.3246241836560e-07, 2.2413023758620e+00, 6.2623004225390e+00,
        6.2985653005570e-07, 4.4071224060810e+00, 6.3038512783520e+00,
        8.5870370891790e-07, 3.0243072231190e+00, 1.6728376158810e+02,
        8.2999544910350e-07, 6.1925394282370e+00, 3.3406124347170e+00,
        6.3112635034010e-07, 2.0147587954160e+00, 7.1134546679000e-03,
        6.0056467454520e-07, 3.3995005033970e+00, 4.1369104726960e+00,
        7.9177151099290e-07, 2.4933868778370e+00, 6.0697767706670e+00,
        7.5569580996850e-07, 4.1594917401430e+00, 6.4963749302240e+00,
        6.7732282449490e-07, 4.0341629342300e+00, 9.4377629373130e+00,
        5.3707085778470e-07, 1.5622191637340e+00, 1.1944470569680e+00,
        5.7108042662030e-07, 2.6627308033860e+00, 6.2820953346050e+00,
        5.7098245837260e-07, 3.9858284308330e+00, 6.2840563662860e+00,
        5.1439508964470e-07, 1.3081446886890e+00, 6.2901893051140e+00,
        5.0880106045460e-07, 5.3528172148040e+00, 6.2759623957780e+00,
        4.9603690851720e-07, 2.6442679223490e+00, 6.1276555676430e+00,
        4.8031378911830e-07, 4.0088441920800e+00, 6.4384961332490e+00,
        5.7317477682250e-07, 3.7945501745970e+00, 3.1546870868680e+00,
        4.7359479605790e-07, 6.1071183089820e+00, 3.1283887635780e+00,
        4.8083487966250e-07, 4.7714586181630e+00, 8.0182093336190e-01,
        4.1150737431370e-07, 3.3271113351590e+00, 8.4292412281950e+00,
        5.2305758892870e-07, 5.3057085516940e+00, 1.3367972634250e+01,
        5.1339778892150e-07, 5.7842307388140e+00, 1.2352852621110e+01,
        5.0658158253270e-07, 2.0520647936790e+00, 1.1856218651880e+01,
        4.3398315938680e-07, 3.6449941958300e+00, 1.7260154635000e+01,
        3.9529286389530e-07, 4.9303764367580e+00, 5.4812549170840e+00,
        4.8984981119420e-07, 4.5420842197310e-01, 9.2255392661740e+00,
        4.7574902093280e-07, 3.1611263888780e+00, 5.8564776908890e+00,
        4.7277016697490e-07, 6.2149938454460e-01, 2.5443143967390e+00,
        3.8009666818630e-07, 3.0401323392970e+00, 4.2659815955660e-01,
        3.2573010779390e-07, 8.0649773600870e-01, 3.9302096969400e+00,
        3.2558105286740e-07, 1.9741479810340e+00, 2.1461653777500e+00,
        3.2520297481870e-07, 2.8459249131350e+00, 4.1643119619990e+00,
        3.2555056353080e-07, 3.0179008241200e+00, 5.0886287934780e+00,
        2.8013452119900e-07, 6.1097177931790e+00, 1.2569674860510e+01,
        3.6889877409700e-07, 2.9115502352890e+00, 1.8073704941270e+01,
        2.4751534294580e-07, 2.1791460258560e+00, 2.6298323289900e-02,
        3.0334577491500e-07, 1.9941610507440e+00, 4.5350594916850e+00,
        2.1867437631100e-07, 5.1256872379360e+00, 1.1371704643920e+01,
        2.7647770327740e-07, 4.8226468602520e-01, 1.2562628541270e+01,
        2.1990287685920e-07, 4.6376332938310e+00, 1.2559038246220e+01,
        2.0464828247600e-07, 1.4670387330930e+00, 7.0848967838080e+00,
        2.6112091475070e-07, 3.0447187834850e-01, 7.1430695617670e+01,
        2.2860796568180e-07, 4.7642203568050e+00, 8.0310922092060e+00,
        1.8550712025870e-07, 3.3836377744280e+00, 1.7480163587600e+00,
        2.3246695067840e-07, 6.1890884492510e+00, 1.8319536579230e+01,
        1.7095280156880e-07, 5.8749667297740e-01, 4.9332085106750e+00,
        2.1681568758280e-07, 4.3029940091320e+00, 1.0447387812440e+01,
        2.1066755565350e-07, 3.8004754198910e+00, 7.4775229074140e+00,
        1.4302138304650e-07, 1.2946608465020e+00, 2.9424634157280e+00,
        1.3883969019440e-07, 4.5947972021140e+00, 8.6359420039520e+00,
        1.9222588441900e-07, 4.9430445435910e-01, 1.7298182331190e+01,
        1.8884600582920e-07, 2.4269439120280e+00, 1.5613747598530e+02,
        1.7894493861070e-07, 1.5829733034990e-01, 1.5925960759570e+00,
        1.3608036853740e-07, 5.1972404405040e+00, 1.3095842673000e+01,
        1.5040380147090e-07, 3.1203609162170e+00, 1.6496361397830e+01,
        1.3827695333890e-07, 6.1647028882050e+00, 7.6329431902170e+00,
        1.4380597690790e-07, 1.4374237709790e+00, 2.0426571094770e+01,
        1.3263032600370e-07, 3.6096887996790e+00, 1.2139553541330e+01,
        1.1592449505400e-07, 5.4630181672250e+00, 5.3313575296640e+00,
        1.4331181491360e-07, 6.0289099120970e+00, 7.3424577946690e+00,
        1.2346231485940e-07, 3.1096455749970e+00, 6.2794855554000e+00,
        1.2339498753440e-07, 3.5393593328660e+00, 6.2866661454920e+00,
        1.2423021913160e-07, 1.0659493926090e+00, 1.5110466097630e+01,
        1.0984021952010e-07, 2.1925087438370e+00, 1.0988808157460e+01,
        1.1581913953150e-07, 4.0544112786500e+00, 5.7295065486530e+00,
        1.0486942421640e-07, 2.6288580308060e+00, 6.8366451522380e+00,
        1.1123083786460e-07, 4.1772927199070e+00, 1.5720838787760e+01,
        1.0517829052360e-07, 4.7950358160880e+00, 1.3498673397710e+00,
        1.0482199431640e-07, 2.9529833952300e+00, 5.9992165162940e+00
    };

    private static final double[] E0Y = {
        9.9989210988980e-01, 1.8265839138460e-01, 6.2830758504460e+00,
        -2.4427008937350e-02, 0.0000000000000e+00, 0.0000000000000e+00,
        8.3529297429150e-03, 1.3952779986800e-01, 1.2566151700890e+01,
        1.0466973001770e-04, 9.6414231097630e-02, 1.8849227551340e+01,
        3.1108418766630e-05, 5.3811404017120e+00, 8.3996847318570e+01,
        2.5702690945930e-05, 5.3010164071280e+00, 5.2969097211180e-01,
        2.1473896236100e-05, 2.6625108698500e+00, 1.5773435434340e+00,
        1.6803443840500e-05, 5.2079041197040e+00, 6.2795526908240e+00,
        1.6791173121930e-05, 4.5821874869680e+00, 6.2865990100680e+00,
        1.4405120684400e-05, 1.9006885177260e+00, 2.3528661535060e+00,
        1.1351396649990e-05, 5.2731085385560e+00, 5.2236939062220e+00,
        9.3454825710180e-06, 4.5030476877380e+00, 1.2036460728780e+01,
        9.0074187195680e-06, 1.6056210596370e+00, 1.0213285547390e+01,
        5.6715367123140e-06, 5.8128490708610e-01, 1.0593819442240e+00,
        7.4514018616660e-06, 2.8073467948360e+00, 3.9814901898930e-01,
        6.3934700571140e-06, 6.0292241338550e+00, 5.7533848783340e+00,
        6.8142758816970e-06, 6.4729901459740e-01, 4.7057323070120e+00,
        6.1137056288870e-06, 3.8138434197000e+00, 6.8127668225580e+00,
        4.5038513672730e-06, 4.5278043709960e+00, 5.8849268314560e+00,
        4.5222491419260e-06, 5.9917830292240e+00, 6.2567775271560e+00,
        4.5017943070180e-06, 3.7987038443970e+00, 6.3093741737360e+00,
        5.5149274801800e-06, 3.9612578333880e+00, 5.5075532403740e+00,
        4.0628627999950e-06, 5.2562472963690e+00, 6.6812248694350e+00,
        5.4149004297120e-06, 5.4990320140970e+00, 7.7552261007200e-01,
        5.4631539874240e-06, 6.1730924540970e+00, 1.4143495244330e+01,
        5.0716118593290e-06, 2.8702442476510e+00, 7.8604193938800e+00,
        2.1951120944550e-06, 2.9523386172010e+00, 1.1506769756670e+01,
        2.2791392339190e-06, 5.9517751329330e+00, 7.0585984605180e+00,
        2.2783861008760e-06, 4.8454563987850e+00, 4.6940029341100e+00,
        2.5590880033080e-06, 6.9453211173110e-01, 1.2168002681900e+01,
        2.5610792868560e-06, 6.1672246083010e+00, 7.0993304901260e-01,
        1.7927557963870e-06, 1.4001225096320e+00, 7.9629803797860e-01,
        1.8187156565020e-06, 4.7033476118300e+00, 6.2831429858700e+00,
        1.8187449247910e-06, 5.0867489002370e+00, 6.2830087150210e+00,
        1.5545187913900e-06, 5.3310080427130e-02, 2.5132303401780e+01,
        2.0632657372390e-06, 4.2836804841780e+00, 1.1790629090820e+01,
        1.4976135200410e-06, 6.0742078260730e+00, 5.4867778124670e+00,
        2.0006179404270e-06, 2.5014262814500e+00, 1.7789845607110e+01,
        1.2897311955800e-06, 3.6463405995360e+00, 7.0793738884240e+00,
        1.2826579989340e-06, 3.2328648049020e+00, 3.7387614537070e+00,
        1.5289159686580e-06, 5.5814334166690e+00, 2.1329907977830e-01,
        1.1873040984320e-06, 5.4535764536940e+00, 9.4377629373130e+00,
        7.8427829281180e-07, 2.8239539222730e-01, 8.8273902471850e+00,
        7.3528922808680e-07, 1.1243695801750e+00, 1.5890729163350e+00,
        6.5701893607970e-07, 2.0891540428400e+00, 1.1769853662910e+01,
        6.3249675904100e-07, 6.7048555812300e-01, 6.2623004225390e+00,
        6.2982898722830e-07, 2.8364148558400e+00, 6.3038512783520e+00,
        6.4766864658550e-07, 4.8524338664670e-01, 7.1134546679000e-03,
        8.5870346512340e-07, 1.4535110056680e+00, 1.6728376158810e+02,
        8.0689487881130e-07, 9.2240877986090e-01, 6.0697767706670e+00,
        8.3537860116610e-07, 4.6317071848950e+00, 3.3406124347170e+00,
        6.0093245321320e-07, 1.8294988277260e+00, 4.1369104726960e+00,
        7.5581585595660e-07, 2.5885968003170e+00, 6.4963749302240e+00,
        5.8092795045030e-07, 5.5168188534760e-01, 1.0977078784560e+01,
        5.3741319502540e-07, 6.2756747349600e+00, 1.1944470569680e+00,
        5.7111605073260e-07, 1.0919059568720e+00, 6.2820953346050e+00,
        5.7101831707460e-07, 2.4150016350900e+00, 6.2840563662860e+00,
        5.1443735906100e-07, 6.0203364434380e+00, 6.2901893051140e+00,
        5.1031089272670e-07, 3.7756345646050e+00, 6.2759623957780e+00,
        4.9606546978910e-07, 1.0734509467560e+00, 6.1276555676430e+00,
        4.7863856892800e-07, 2.4311780123100e+00, 6.4384961332490e+00,
        6.1099112636650e-07, 5.3433561579140e+00, 3.1546870868680e+00,
        4.8398989440240e-07, 5.8308335940470e-02, 8.0182093336190e-01,
        4.7348226239190e-07, 4.5360801348210e+00, 3.1283887635780e+00,
        4.8347414732900e-07, 2.5850904897540e-01, 7.0848967838080e+00,
        5.1348585811560e-07, 4.2133171726030e+00, 1.2352852621110e+01,
        5.0640042649780e-07, 4.8144188064780e-01, 1.1856218651880e+01,
        3.7534767727610e-07, 1.5999533997880e+00, 8.4292412281950e+00,
        4.9352640142830e-07, 2.1574175568730e+00, 2.5443143967390e+00,
        3.9509296008970e-07, 3.3593941842540e+00, 5.4812549170840e+00,
        4.8958497897770e-07, 5.1657043765580e+00, 9.2255392661740e+00,
        4.2152416888860e-07, 2.0653688009930e+00, 1.7260154635000e+01,
        3.7967737311320e-07, 1.4686063466120e+00, 4.2659815955660e-01,
        3.1141781425150e-07, 3.6156380794740e+00, 2.1461653777500e+00,
        3.2606642208380e-07, 4.4171349224350e+00, 4.1643119619990e+00,
        3.9769961230080e-07, 4.7008668830040e+00, 5.8564776908890e+00,
        2.8014596729240e-07, 4.5389020609220e+00, 1.2569674860510e+01,
        3.6389318688610e-07, 1.3341979914750e+00, 1.8073704941270e+01,
        2.4870132694760e-07, 3.7492755582750e+00, 2.6298323289900e-02,
        3.0341654819940e-07, 4.2366220308730e-01, 4.5350594916850e+00,
        2.6762788255860e-07, 5.9708480078110e+00, 3.9302096969400e+00,
        2.7649038189180e-07, 5.1946367545010e+00, 1.2562628541270e+01,
        2.4851499305070e-07, 1.0024342078460e+00, 5.0886287934780e+00,
        2.1993055409410e-07, 3.0667730984030e+00, 1.2559038246220e+01,
        2.5711065004350e-07, 7.5883124590630e-01, 1.3367972634250e+01,
        2.0497518171580e-07, 3.4449774348560e+00, 1.1371704643920e+01,
        2.5997072962970e-07, 1.8731285422050e+00, 7.1430695617670e+01,
        1.7850180722170e-07, 5.0158913066150e+00, 1.7480163587600e+00,
        2.3248338911150e-07, 4.6182712397300e+00, 1.8319536579230e+01,
        1.7097111195450e-07, 5.3000034556690e+00, 4.9332085106750e+00,
        2.1071593517160e-07, 2.2298198151150e+00, 7.4775229074140e+00,
        1.7503330802950e-07, 6.1614858800080e+00, 1.0447387812440e+01,
        2.0005982103390e-07, 2.9673572999990e+00, 8.0310922092060e+00,
        1.3809202486810e-07, 3.0270079239170e+00, 8.6359420039520e+00,
        1.4124604702990e-07, 6.0375971637980e+00, 2.9424634157280e+00,
        1.8884598030010e-07, 8.5614762433740e-01, 1.5613747598530e+02,
        1.7883705425850e-07, 4.8697362902090e+00, 1.5925960759570e+00,
        1.3608932961670e-07, 3.6264118864360e+00, 1.3095842673000e+01,
        1.5068465301600e-07, 1.5509753774270e+00, 1.6496361397830e+01,
        1.8009133761760e-07, 2.0758260331900e+00, 1.7298182331190e+01,
        1.4362613906490e-07, 6.1488764202550e+00, 2.0426571094770e+01,
        1.2202271141510e-07, 4.3825838799060e+00, 7.6329431902170e+00,
        1.3378836035920e-07, 2.0366443273610e+00, 1.2139553541330e+01,
        1.1593266507380e-07, 3.8922769946870e+00, 5.3313575296640e+00,
        1.3528531285690e-07, 1.4479506497440e+00, 1.6730463662890e+01,
        1.4334082960830e-07, 4.4578546929610e+00, 7.3424577946690e+00,
        1.2347016665180e-07, 1.5388181471510e+00, 6.2794855554000e+00,
        1.2340271920070e-07, 1.9685232207600e+00, 6.2866661454920e+00,
        1.2440240917970e-07, 5.7798034999850e+00, 1.5110466097630e+01,
        1.0979349455160e-07, 6.2109752213880e-01, 1.0988808157460e+01,
        1.2546113298560e-07, 2.5919638079980e+00, 1.5720838787760e+01,
        1.1582472867840e-07, 2.4836128126700e+00, 5.7295065486530e+00,
        1.0197595789880e-07, 1.3195987387320e-01, 1.3498673397710e+00,
        1.0100376962360e-07, 9.9378601156180e-01, 6.8366451522380e+00,
        1.0477275482660e-07, 1.3821384053990e+00, 5.9992165162940e+00
    };

    private static final double[] E0Z = {
        2.7962076390750e-06, 3.1987015602090e+00, 8.4334661581310e+01,
        1.0160421981420e-06, 5.4223603959130e+00, 5.5075532403740e+00,
        8.0443050336470e-07, 3.8802228666520e+00, 5.2236939062220e+00,
        4.3853479092740e-07, 3.7043699374680e+00, 2.3528661535060e+00,
        3.1861564149060e-07, 3.9996393632350e+00, 1.5773435434340e+00,
        2.2724122857920e-07, 3.9847383159520e+00, 1.0477473117550e+00,
        1.6456201030070e-07, 3.5654125168410e+00, 5.8564776908890e+00,
        1.8158369211660e-07, 4.9845070590200e+00, 6.2830758504460e+00,
        1.4474616763640e-07, 3.7027535701080e+00, 9.4377629373130e+00,
        1.4307608763820e-07, 3.4096587123570e+00, 1.0213285547390e+01,
        1.1204457532260e-07, 4.8295615702460e+00, 1.4143495244330e+01,
        1.0902328407970e-07, 2.0807291780660e+00, 6.8127668225580e+00,
        1.0362671362170e-07, 4.0566395366480e+00, 7.1092881354930e+01
    };

    private static final double[] E1X = {
        1.2340463260040e-06, 0.0000000000000e+00, 0.0000000000000e+00,
        5.1500688247010e-07, 6.0026645575010e+00, 1.2566151700890e+01
    };

    private static final double[] E1Y = {
        9.3046905465280e-07, 0.0000000000000e+00, 0.0000000000000e+00,
        5.1507155706630e-07, 4.4318071162940e+00, 1.2566151700890e+01
    };

    private static final double[] E1Z = {
        2.2782904499660e-06, 3.4137160338630e+00, 6.2830758504460e+00
    };

    private static final double[] E2X = {

    };

    private static final double[] E2Y = {

    };

    private static final double[] E2Z = {

    };

    /** Where the Sun is, in the ecliptic frame of the date asked for. */
    public static final class Position
    {
        private final double _longitudeRadians;
        private final double _latitudeRadians;
        private final double _distanceKm;

        private Position(double longitudeRadians, double latitudeRadians, double distanceKm)
        {
            _longitudeRadians = longitudeRadians;
            _latitudeRadians = latitudeRadians;
            _distanceKm = distanceKm;
        }

        public double longitudeRadians()
        {
            return _longitudeRadians;
        }

        public double latitudeRadians()
        {
            return _latitudeRadians;
        }

        public double distanceKm()
        {
            return _distanceKm;
        }
    }

    private SunErfa()
    {
    }

    /**
     * @param julianDateTT Julian date in Terrestrial Time
     * @return the Sun referred to the mean equinox and ecliptic of that date
     */
    public static Position at(double julianDateTT)
    {
        // The Earth series counts in Julian years, the precession in centuries.
        double years = (julianDateTT - J2000) / YEAR;
        double centuries = (julianDateTT - J2000) / CENTURY;

        // Sun to Earth, ecliptic of J2000.
        double x = _component(E0X, E1X, E2X, years);
        double y = _component(E0Y, E1Y, E2Y, years);
        double z = _component(E0Z, E1Z, E2Z, years);

        // Earth to Sun is the same vector the other way round.
        x = -x;
        y = -y;
        z = -z;

        double flat = Math.sqrt(x * x + y * y);

        double longitude = (flat == 0.0 && z == 0.0) ? 0.0 : Math.atan2(y, x);
        double latitude = (flat == 0.0 && z == 0.0) ? 0.0 : Math.atan2(z, flat);
        double distance = Math.sqrt(x * x + y * y + z * z);

        // General precession in longitude, P03, carrying J2000 forward to the date.
        double precession = (5028.796195
                + (1.1054348
                + (0.00007964
                + (-0.000023857
                + (-0.0000000383)
                * centuries) * centuries) * centuries) * centuries) * centuries * AS2R;

        return new Position(longitude + precession, latitude, distance * AU_KM);
    }

    /*
     * One component of the vector: a constant series, a series multiplied by the
     * time, and a series multiplied by its square. Each term is an amplitude, a
     * phase and a frequency.
     */
    private static double _component(double[] constant, double[] linear, double[] quadratic,
                                     double years)
    {
        double sum = 0.0;

        // Smallest terms first, as the original does: the series spans seven orders
        // of magnitude and the order of addition is not free.
        for (int n = constant.length - 3; n >= 0; n -= 3) {
            sum += constant[n] * Math.cos(constant[n + 1] + constant[n + 2] * years);
        }

        for (int n = linear.length - 3; n >= 0; n -= 3) {
            sum += linear[n] * years * Math.cos(linear[n + 1] + linear[n + 2] * years);
        }

        for (int n = quadratic.length - 3; n >= 0; n -= 3) {
            sum += quadratic[n] * years * years
                    * Math.cos(quadratic[n + 1] + quadratic[n + 2] * years);
        }

        return sum;
    }
}
