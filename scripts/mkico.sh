#!/bin/bash
# MyBuoy. Copyright (C) 2026 Golden-Block.
# SPDX-License-Identifier: AGPL-3.0-or-later
# Commercial licensing: licence@golden-block.fr
# mkico.sh — génère l'icône (et le splash) d'une app à partir d'une source PNG ou SVG.
# Produit : .iconset (toutes tailles), .icns (macOS), .ico (Windows),
#           <nom>.png (1024), <nom>-<taille>.png (copies icône), <nom>-<taille>.png (splash).
#
# Version nettoyée pour MyBuoy — différences avec l'original générique :
#   - entrée SVG rasterisée proprement via rsvg-convert (sinon rendu interne ImageMagick dégradé) ;
#   - 2e argument optionnel = préfixe de sortie (ex. « mybuoy ») pour garder des noms stables ;
#   - texte et police du splash paramétrables (défauts MyBuoy) :
#       MKICO_TXT1 (défaut "MyBuoy"), MKICO_TXT2 (défaut "by BlackDucky"),
#       MKICO_FONT (défaut : police embarquée scripts/fonts/BuoyRound.ttf).
#
# Dépendances : imagemagick (convert/magick), iconutil (macOS), rsvg-convert (si entrée SVG).
#
# Usage :  mkico.sh <source.svg|png> [prefixe-sortie]
# Exemple: mkico.sh design/app-icon/svg/mybuoy-all.svg mybuoy

export LANG=fr_FR.UTF-8
PWD=$(pwd)
HOME=$(echo ~)
OS=$(uname -s)
SCRIPT_NAME=$(basename $0)
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
STEP=0

# Maybe a day this script can be use for debian/linux
if [[ "$OS" = *[dD]arwin* ]]; then
  BREW=brew
else
  BREW=apt-get
fi

################################
# Function Info
################################
Info()
{
   echo "$(date '+%Y-%m-%d %H:%M:%S') $*"
}

################################
# Function Error
################################
Error()
{
   echo "$(date '+%Y-%m-%d %H:%M:%S') *** ERROR *** $*"
}

################################
# Function BuildInstallPrefix
################################
BuildInstallPrefix()
{
  PREF_HOME=$1

  Info "###### BuildInstallPrefix ($PREF_HOME) ######"

  # set PATH so it includes user's private bin if it exists
  if [ -d "$PREF_HOME/bin" ] ; then
      export PATH="$PREF_HOME/bin:$PATH"
  fi

  # set PATH so it includes user's private sbin if it exists
  if [ -d "$PREF_HOME/sbin" ] ; then
      export PATH="$PREF_HOME/sbin:$PATH"
  fi

  # configure var for gcc
  if [ -d "$PREF_HOME/include" ] ; then
      export C_INCLUDE_PATH="$PREF_HOME/include:$C_INCLUDE_PATH"
      export CPLUS_INCLUDE_PATH="$PREF_HOME/include:$CPLUS_INCLUDE_PATH"
      export CPATH="$PREF_HOME/include:$CPATH"
  fi

  # configure var for gcc
  if [ -d "$PREF_HOME/lib" ] ; then
      export LD_LIBRARY_PATH="$PREF_HOME/lib:$LD_LIBRARY_PATH"
      export LD_RUN_PATH="$PREF_HOME/lib:$LD_RUN_PATH"
      export LIBRARY_PATH="$PREF_HOME/lib:$LIBRARY_PATH"
  fi

  # configure var for pkg-config
  if [ -d "$PREF_HOME/lib/pkgconfig" ] ; then
      export PKG_CONFIG_PATH="$PREF_HOME/lib/pkgconfig:$PKG_CONFIG_PATH"
  fi

  Info ""

}

################################
# Function BrewCheckAndInstall
################################
BrewCheckAndInstall()
{
  PKG=$1
  CMD_TEST_PKG=$2
  CHK_ONLY=$3


  if [ $STEP -le 0 ] ; then
    Info "- HOMEBREW VERSION :"

    ${BREW} --version 2>/dev/null
    ERR=$?
    if [ ${ERR} -gt 0 ] ; then
        Error "Cannot find HOMBREW manager package (${BREW} ERROR:${ERR})"
        Info "We need to install it before run this bash"
        Info "Good Luck :D"
        exit 127
    fi

    Info ""

    STEP=$((STEP+1))
  fi


  Info "- Test Package ${PKG} :"

  ${CMD_TEST_PKG} | grep -v "man ld" 2>/dev/null
  #ERR=$?
  ERR=${PIPESTATUS[0]}  # Get Error Code of the command and not of grep

  # Need It for gettext (OR do before run script: brew link gettext -orce)
  # And maybe OpenSSL
  # 127 -> "bin" not found (e.g. openssl version)
  # 1 -> Library not found (e.g cc -lcrypto.1.1 -Wl,--help)
  if [ ${ERR} -eq 127 ] || [ ${ERR} -eq 1 ] ; then
    BuildInstallPrefix /usr/local/opt/${PKG}
    ${CMD_TEST_PKG} | grep -v "man ld" 2>/dev/null
    #ERR=$?
    ERR=${PIPESTATUS[0]}  # Get Error Code of the command and not of grep
  fi


  if [ ${ERR} -gt 0 ] ; then

    if [ "${CHK_ONLY}" == "true" ]; then
      Error "Cannot find package ${PKG} (ERROR:${ERR})"
      Info "You are not lucky! Sorry :("
      exit 127
    else
        Info "- Install Package ${PKG} :"

        ${BREW} install ${PKG}

        BrewCheckAndInstall "${PKG}" "${CMD_TEST_PKG}" "true"
    fi
  fi

  Info ""
}



################################
# Function ResizePict
################################
ResizePicts()
{
    PICT_SRC=$1
    PICT_SIZE=$2
    PICT_DIR=$3

    PICT_SIZEX2=$((PICT_SIZE / 2))

    Info "****** START (ResizePict $PICT_SRC $PICT_SIZE) ******"

    mkdir ${PICT_DIR} 2>/dev/null

    convert -density 72 -units PixelsPerInch -flatten -background none -resize x${PICT_SIZE} -gravity center -crop ${PICT_SIZE}x${PICT_SIZE}+0+0 "${PICT_SRC}" "${PICT_DIR}/icon_${PICT_SIZE}x${PICT_SIZE}.png"

    convert -density 72 -units PixelsPerInch -flatten -background none -resize x${PICT_SIZE} -gravity center -crop ${PICT_SIZE}x${PICT_SIZE}+0+0 "${PICT_SRC}" "${PICT_DIR}/icon_${PICT_SIZEX2}x${PICT_SIZEX2}@2x.png"

    Info "****** END (ResizePict $PICT_SRC $PICT_SIZE) ******"
    Info ""
}

################################
# Function MakeSplash
################################
MakeSplash()
{
    PICT_SRC=$1
    PICT_SIZE=$2
    PICT_DIR=$3

    # --- Paramétrable (défauts MyBuoy) ---
    PICT_TXT1="${MKICO_TXT1:-MyBuoy}"
    PICT_TXT2="${MKICO_TXT2:-by BlackDucky}"
    FONT_FILE="${MKICO_FONT:-$SCRIPT_DIR/fonts/BuoyRound.ttf}"
    if [ ! -f "$FONT_FILE" ]; then
        Info "Police $FONT_FILE absente -> fallback Helvetica"
        FONT_FILE="Helvetica"
    fi

    if (( ${PICT_SIZE} <= 320 )) ; then
        PICT_FONT1=20
        PICT_FONT2=10

    elif (( ${PICT_SIZE} <= 480 )) ; then
        PICT_FONT1=30
        PICT_FONT2=15

    elif (( ${PICT_SIZE} <= 640 )) ; then
        PICT_FONT1=40
        PICT_FONT2=20

    elif (( ${PICT_SIZE} <= 720 )) ; then
        PICT_FONT1=45
        PICT_FONT2=22

    elif (( ${PICT_SIZE} <= 960 )) ; then
        PICT_FONT1=60
        PICT_FONT2=30

    elif (( ${PICT_SIZE} <= 1024 )) ; then
        PICT_FONT1=65
        PICT_FONT2=33

    elif (( ${PICT_SIZE} <= 1080 )) ; then
        PICT_FONT1=70
        PICT_FONT2=33

    elif (( ${PICT_SIZE} <= 1280 )) ; then
        PICT_FONT1=80
        PICT_FONT2=40

    elif (( ${PICT_SIZE} <= 1440 )) ; then
        PICT_FONT1=90
        PICT_FONT2=45

    elif (( ${PICT_SIZE} <= 1920 )) ; then
        PICT_FONT1=120
        PICT_FONT2=60

    elif (( ${PICT_SIZE} <= 2048 )) ; then
        PICT_FONT1=130
        PICT_FONT2=65

    elif (( ${PICT_SIZE} <= 2880 )) ; then
        PICT_FONT1=140
        PICT_FONT2=70

    else #3072
        PICT_FONT1=160
        PICT_FONT2=80
    fi

    PICT_MINISIZE=$((PICT_SIZE - PICT_FONT1*4))

    PICT_MARGE=$(((PICT_SIZE - PICT_MINISIZE) / 2))
    PICT_YOFFSET=$(((PICT_MINISIZE / 2)+PICT_FONT1))



    Info "****** START (MakeSplash $PICT_SRC $PICT_SIZE) ******"
    Info " -PICT_MARGE: $PICT_MARGE"
    Info " -PICT_YOFFSET: $PICT_YOFFSET"

    mkdir ${PICT_DIR} 2>/dev/null

    convert -density 144 -units PixelsPerInch -flatten -colors 256 -background white -resize x${PICT_MINISIZE} -gravity Center -crop ${PICT_MINISIZE}x${PICT_MINISIZE}+0+0 "${PICT_SRC}" \
            -extent ${PICT_SIZE}x${PICT_SIZE} \
            -font "${FONT_FILE}" \
            -gravity Center \
            -pointsize ${PICT_FONT1} -fill black \
            -annotate +0-${PICT_YOFFSET} "${PICT_TXT1}" \
            -gravity East \
            -font "${FONT_FILE}" \
            -pointsize ${PICT_FONT2} -fill grey \
            -annotate +${PICT_MARGE}+${PICT_YOFFSET} "${PICT_TXT2}" \
            "${PICT_DIR}/splash_${PICT_SIZE}x${PICT_SIZE}.png"

    Info "****** END (MakeSplash $PICT_SRC $PICT_SIZE) ******"
    Info ""
}


Info "##########################################################"
Info "###### INSTALL DEV. ENV. AND SUPLLY LIBRARIES (ALL) ######"
Info "##########################################################"

# Add $HOME build prefix
BuildInstallPrefix "$HOME"

BrewCheckAndInstall imagemagick "magick -version"


################################
# Entrée + préfixe de sortie
################################
INPUT="$1"
if [ -z "$INPUT" ] || [ ! -f "$INPUT" ]; then
    Error "Usage: $SCRIPT_NAME <source.svg|png> [prefixe-sortie]"
    exit 1
fi

# Préfixe de sortie (chemin + nom sans extension). 2e arg optionnel (ex. « mybuoy »).
NAME_PIC="${2:-${INPUT%.*}}"

# Entrée SVG -> rasterisation nette via rsvg-convert (ImageMagick rend mal le SVG en interne)
SRC="$INPUT"
RASTER_TMP=""
case "${INPUT,,}" in
  *.svg)
    if command -v rsvg-convert >/dev/null 2>&1 ; then
        RASTER_TMP="${NAME_PIC}_raster.png"
        Info "Entrée SVG -> rasterisation rsvg-convert (2048px) : $RASTER_TMP"
        rsvg-convert -w 2048 -h 2048 -a "$INPUT" -o "$RASTER_TMP"
        SRC="$RASTER_TMP"
    else
        Info "rsvg-convert absent -> rendu SVG interne ImageMagick (qualité dégradée)"
    fi
    ;;
esac

ICONSET_DIR=${NAME_PIC}.iconset
PICT_ICO=${NAME_PIC}.ico
PICT_ICNS=${NAME_PIC}.icns

rm -r ${ICONSET_DIR} 2>/dev/null

#Icon
ResizePicts $SRC 16 ${ICONSET_DIR}
ResizePicts $SRC 20 ${ICONSET_DIR}
ResizePicts $SRC 29 ${ICONSET_DIR}
ResizePicts $SRC 32 ${ICONSET_DIR}
ResizePicts $SRC 40 ${ICONSET_DIR}
ResizePicts $SRC 48 ${ICONSET_DIR}
ResizePicts $SRC 58 ${ICONSET_DIR}
ResizePicts $SRC 60 ${ICONSET_DIR}
ResizePicts $SRC 64 ${ICONSET_DIR}
ResizePicts $SRC 72 ${ICONSET_DIR}
ResizePicts $SRC 76 ${ICONSET_DIR}
ResizePicts $SRC 80 ${ICONSET_DIR}
ResizePicts $SRC 87 ${ICONSET_DIR}
ResizePicts $SRC 96 ${ICONSET_DIR}
ResizePicts $SRC 120 ${ICONSET_DIR}
ResizePicts $SRC 128 ${ICONSET_DIR}
ResizePicts $SRC 144 ${ICONSET_DIR}
ResizePicts $SRC 152 ${ICONSET_DIR}
ResizePicts $SRC 167 ${ICONSET_DIR}
ResizePicts $SRC 180 ${ICONSET_DIR}
ResizePicts $SRC 192 ${ICONSET_DIR}
ResizePicts $SRC 256 ${ICONSET_DIR}
ResizePicts $SRC 512 ${ICONSET_DIR}
ResizePicts $SRC 1024 ${ICONSET_DIR}

#Splash
MakeSplash $SRC 320 ${ICONSET_DIR}
MakeSplash $SRC 480 ${ICONSET_DIR}
MakeSplash $SRC 640 ${ICONSET_DIR}
MakeSplash $SRC 720 ${ICONSET_DIR}
MakeSplash $SRC 960 ${ICONSET_DIR}
MakeSplash $SRC 1024 ${ICONSET_DIR}
MakeSplash $SRC 1080 ${ICONSET_DIR}
MakeSplash $SRC 1280 ${ICONSET_DIR}
MakeSplash $SRC 1440 ${ICONSET_DIR}
MakeSplash $SRC 1920 ${ICONSET_DIR}
MakeSplash $SRC 2048 ${ICONSET_DIR}
MakeSplash $SRC 2880 ${ICONSET_DIR}
MakeSplash $SRC 3072 ${ICONSET_DIR}

#.ico and .icns
iconutil --convert icns ${ICONSET_DIR}
magick convert ${ICONSET_DIR}/icon_*x16.png \
               ${ICONSET_DIR}/icon_*x32.png \
               ${ICONSET_DIR}/icon_*x48.png \
               ${ICONSET_DIR}/icon_*x64.png \
               ${ICONSET_DIR}/icon_*x96.png \
               ${ICONSET_DIR}/icon_*x128.png \
               ${ICONSET_DIR}/icon_*x256.png \
               ${PICT_ICO}

#Icon
cp "${ICONSET_DIR}/icon_1024x1024.png" "${NAME_PIC}.png"
for PICT_SIZE in {48,72,96,144,192} ; do
    cp "${ICONSET_DIR}/icon_${PICT_SIZE}x${PICT_SIZE}.png" "${NAME_PIC}-${PICT_SIZE}.png"
done

#Splash
for PICT_SIZE in {320,480,640,720,960,1080,1280,1440,1920,2880} ; do
    cp "${ICONSET_DIR}/splash_${PICT_SIZE}x${PICT_SIZE}.png" "${NAME_PIC}-${PICT_SIZE}.png"
done

# Nettoyage du raster temporaire éventuel
if [ -n "$RASTER_TMP" ] && [ -f "$RASTER_TMP" ]; then
    rm -f "$RASTER_TMP"
fi
