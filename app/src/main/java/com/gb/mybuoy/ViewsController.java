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

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.widget.Toast;

import com.gb.mybuoy.databinding.ActivityMainBinding;
import com.gb.mybuoy.databinding.LayoutAlarmSoundPickerBinding;
import com.gb.mybuoy.databinding.LayoutAvatarPickerBinding;
import com.gb.mybuoy.databinding.LayoutMarkBinding;
import com.gb.mybuoy.databinding.LayoutPageCompassConfigBinding;
import com.gb.mybuoy.databinding.LayoutPageCompassesBinding;
import com.gb.mybuoy.databinding.LayoutPageOverpassBinding;
import com.gb.mybuoy.databinding.LayoutMenuPopupBinding;
import com.gb.mybuoy.databinding.LayoutPageMaintenanceBinding;
import com.gb.mybuoy.databinding.LayoutPageMapsBinding;
import com.gb.mybuoy.databinding.LayoutPageTidesBinding;
import com.gb.mybuoy.astro.AstroDay;
import com.gb.mybuoy.astro.MoonPhase;
import com.gb.mybuoy.astro.TidePotential;
import com.gb.mybuoy.astro.Topocentric;
import com.gb.mybuoy.databinding.LayoutPageSunmoonBinding;
import com.gb.mybuoy.tides.TideModel;
import com.gb.mybuoy.tides.TidePlace;
import com.gb.mybuoy.databinding.LayoutPageMarksBinding;
import com.gb.mybuoy.databinding.LayoutPageParametersBinding;
import com.gb.mybuoy.databinding.LayoutPageSearchBinding;
import com.gb.mybuoy.databinding.LayoutPageSpeedTestBinding;
import com.gb.mybuoy.databinding.LayoutPageSupportBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.Observable;

public class ViewsController extends BaseObservable {

    private static ViewsController _instance = null;
    private final AppManager _am;

    private Activity _activity = null;
    private static final ReadWriteLock _readWriteLock = new ReentrantReadWriteLock();
    private static final Lock _writeLock = _readWriteLock.writeLock();
    private static final Lock _readLock = _readWriteLock.readLock();

    private final ArrayAdapter mapsAdapter;
    private final ArrayAdapter marksAdapter;
    private final ArrayAdapter proximityMarksAdapter;

    // #  Footer de notification universel (bandeau bas rouge, auto-disparition 5 s). Injecte dans le
    // decor de la fenetre du dessus (activity ou dialog de page), donc visible partout. _openDialogs
    // suit les Dialogs ouverts pour savoir quelle fenetre est au premier plan.
    private final List<Dialog> _openDialogs = new ArrayList<>();
    private Handler _footerHandler = null;
    private TextView _footerView = null;
    private final Runnable _footerHide = new Runnable() {
        @Override public void run() { hideFooter(); }
    };

    private ViewsController(Activity activity) {
        // Constructor hidden because this is a singleton
        this._activity = activity;

        _am = AppManager.getInstance(activity);

        mapsAdapter = new MyListAdapter<MapInfo>(this._activity, R.layout.layout_mapinfo, BR.mapInfo, _am.getMaps());
        marksAdapter = new MyListAdapter<PersonalMark>(this._activity, R.layout.layout_personalmark, BR.personalMark, _am.getMarks());
        proximityMarksAdapter = new MyListAdapter<PersonalMark>(this._activity, R.layout.layout_proximitypersonalmark, BR.personalMark, _am.getProximityMarks());

        _am.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                Activity activity = null;

                _readLock.lock();
                try {
                    activity = _activity;
                } catch (Exception ex) {
                    GBLog.e("ViewController", "error", ex);
                } finally {
                    // To unlock the acquired read thread
                    _readLock.unlock();
                }

                if( propertyId == BR.maps ) {
                    GBLog.d(getClass().getName(), "maps changed");

                    if( Looper.myLooper() == Looper.getMainLooper() ) {
                        synchronized (mapsAdapter) {
                            mapsAdapter.notifyDataSetChanged();
                        }
                    } else {
                        if( activity != null ) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mapsAdapter.notifyDataSetChanged();
                                }
                            });
                        } else {
                            notifyPropertyChanged(BR.mapsAdapter);
                        }
                    }
                } else if( propertyId == BR.marks ) {
                    GBLog.d(getClass().getName(), "marks changed");

                    if( Looper.myLooper() == Looper.getMainLooper() ) {
                        synchronized (marksAdapter) {
                            marksAdapter.notifyDataSetChanged();
                        }
                    } else {
                        if( activity != null ) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    marksAdapter.notifyDataSetChanged();
                                }
                            });
                        } else {
                            notifyPropertyChanged(BR.marksAdapter);
                        }
                    }

                } else if( propertyId == BR.proximityMarks ) {
                    GBLog.d(getClass().getName(), "marks changed");

                    if( Looper.myLooper() == Looper.getMainLooper() ) {
                        synchronized (proximityMarksAdapter) {
                            proximityMarksAdapter.notifyDataSetChanged();
                        }
                    } else {
                        if( activity != null ) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    proximityMarksAdapter.notifyDataSetChanged();
                                }
                            });
                        } else {
                            notifyPropertyChanged(BR.proximityMarksAdapter);
                        }
                    }
                }

            }
        });
    }

    public static ViewsController getInstance(Activity activity) {
        // Return the instance

        _writeLock.lock();
        try {
            if (_instance == null) {
                _instance = new ViewsController(activity);
            }
        } catch (Exception ex) {
            GBLog.e("ViewController", "error", ex);
        } finally {
            // To unlock the acquired read thread
            _writeLock.unlock();
        }

        if (activity != null) {
            _instance.setActivity(activity);
        }

        return _instance;
    }

    public static ViewsController getInstance() {
        return getInstance( null );
    }

    public void setActivity(Activity activity) {
        this._activity = activity;
        this._am.setContext(activity);
    }

    @Bindable
    public ArrayAdapter getMapsAdapter() {

        if( false ) {
            mapsAdapter.clear();
            mapsAdapter.addAll(_am.getMaps());
        }
        return mapsAdapter;
    }

    @Bindable
    public ArrayAdapter getMarksAdapter() {

        if( false ) {
            marksAdapter.clear();
            marksAdapter.addAll(_am.getMarks());
        }
        return marksAdapter;
    }

    @Bindable
    public ArrayAdapter getProximityMarksAdapter() {

        if( false ) {
            proximityMarksAdapter.clear();
            proximityMarksAdapter.addAll(_am.getProximityMarks());
        }
        return proximityMarksAdapter;
    }

    // #33 : le popup du menu burger ne doit PAS prendre le focus fenetre (sinon la barre systeme
    // reapparait et le popup saute, cf. ShowMenuPopup). Or un popup non focusable n'est jamais modal
    // au toucher : FLAG_NOT_FOCUSABLE force FLAG_NOT_TOUCH_MODAL, quoi qu'on demande. Le tap qui
    // referme le menu est donc AUSSI delivre a la carte, qui demarrait un pan ou posait un segment
    // de mesure. GameSurface passe par ici pour avaler ce geste-la, et lui seul.
    private PopupWindow _menuPopup = null;
    private boolean _swallowingGesture = false;
    private long _menuGuardUntilMs = 0;

    // Le tap dehors et le geste sur la carte sont deux evenements distincts, dont l'ordre de
    // delivrance n'est pas garanti. D'ou les deux voies : le popup encore ouvert au DOWN, ou bien
    // deja referme juste avant. Le delai borne la seconde, pour ne pas avaler un geste sans rapport
    // si le tap est parti ailleurs que sur la carte.
    private static final long MENU_GUARD_MS = 400;

    static boolean consumeMenuTouch(int action) {
        ViewsController vc = _instance;

        if( vc == null ) {
            return false;
        }

        if( action == MotionEvent.ACTION_DOWN ) {
            boolean open = (vc._menuPopup != null) && vc._menuPopup.isShowing();
            boolean justClosed = SystemClock.uptimeMillis() < vc._menuGuardUntilMs;

            if( open ) {
                vc._menuPopup.dismiss();
            }

            vc._swallowingGesture = open || justClosed;
            vc._menuGuardUntilMs = 0;
        }

        if( !vc._swallowingGesture ) {
            return false;
        }

        // avaler tout le geste, pas seulement son premier evenement
        if( (action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL) ) {
            vc._swallowingGesture = false;
        }

        return true;
    }

    // #33 : wrapper pour l'appui long du burger (onLongClick exige un boolean). Ouvre la page marks.
    public boolean ShowMarksPageLong() {
        ShowMarksPage();
        return true;
    }

    // #33 : ouvre une page plein ecran (titre en haut-gauche + close en haut-droite). Pattern commun a
    // toutes les pages du menu (marks / maps / parameters / support). Le contenu (liste ou vide) est deja
    // dans le layout inflate ; ici on ne gere que le Dialog fullscreen, le close et les insets systeme.
    private Dialog showFullscreenPage(View root, View closeBtn) {
        return showFullscreenPage(root, closeBtn, null);
    }

    // Variante rendant le dialog (pour le refermer depuis un autre bouton que la croix) et acceptant
    // un menage a la fermeture, un Dialog n'acceptant qu'un seul OnDismissListener.
    private Dialog showFullscreenPage(View root, View closeBtn, Runnable onDismiss) {
        if( this._activity == null || root == null ) {
            return null;
        }

        Dialog dialog = new Dialog(this._activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen);

        if( closeBtn != null ) {
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                }
            });
        }

        dialog.setContentView(root);

        // Fullscreen (edge-to-edge) : padder par les insets systeme pour que le close reste dans la zone
        // utile quelle que soit l'orientation (en paysage la barre nav est sur le cote).
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        trackDialog(dialog, onDismiss);
        dialog.show();

        return dialog;
    }

    // #52 : la popup NON plein ecran d'ajout/modif de mark (ShowPersonalMark). Sert a geler le compte
    // a rebours du recentrage auto UNIQUEMENT pendant qu'elle est affichee (pas les dialogs plein ecran
    // du menu). Lu depuis le thread de rendu.
    private Dialog _markEditorDialog = null;

    public static boolean isMarkEditorOpen() {
        ViewsController vc = _instance;
        return vc != null && vc._markEditorDialog != null && vc._markEditorDialog.isShowing();
    }

    // Enregistre un Dialog ouvert (pour connaitre la fenetre du dessus) et le retire a sa fermeture.
    private void trackDialog(final Dialog dialog) {
        trackDialog(dialog, null);
    }

    // Variante avec un menage propre a l'appelant, execute a la fermeture. Un Dialog n'accepte qu'un
    // seul OnDismissListener, donc il passe forcement par ici sous peine d'ecraser le notre.
    private void trackDialog(final Dialog dialog, final Runnable onDismiss) {
        if( dialog == null ) return;
        _openDialogs.add(dialog);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                _openDialogs.remove(dialog);
                if( dialog == _markEditorDialog )
                    AppManager.getInstance().playPopupCloseSound();   // #52 : son de fermeture du popup mark
                if( onDismiss != null )
                    onDismiss.run();
            }
        });
    }

    /**
     * Closes the burger menu when the screen turns.
     *
     * The menu is laid out for the orientation it was opened in: one column upright,
     * two on its side. Rebuilding it mid-flight would mean rebuilding its position as
     * well, and a menu left straddling a rotation sits wrong whichever way it is
     * handled. Closing it is both simpler and what the user expects, the burger being
     * exactly where it was.
     */
    public void closeMenuOnRotation() {
        if( _menuPopup != null ) {
            _menuPopup.dismiss();
        }
    }

    // #65 : ferme tout ce qui recouvre la carte (menu burger, pages du menu, editeur de mark).
    // Appele quand une position arrive d'une autre application : l'editeur pre-rempli doit s'ouvrir
    // sur la carte, pas au-dessus d'un ecran de parametres. On itere sur une COPIE, chaque dismiss()
    // declenchant le listener pose par trackDialog, qui retire le dialog de _openDialogs.
    public void closeAllDialogs() {
        if( _menuPopup != null ) {
            _menuPopup.dismiss();
        }

        List<Dialog> open = new ArrayList<>(_openDialogs);
        for( Dialog dialog : open ) {
            if( dialog.isShowing() ) {
                dialog.dismiss();
            }
        }
    }

    // Fenetre ou afficher le footer : dernier dialog PLEIN ECRAN affiche (pages du menu), sinon la
    // fenetre de l'activity. On SAUTE le petit popup non plein ecran d'ajout/modif de mark : un message
    // (ex. "Chunk reloaded!") ne doit pas s'afficher dans son footer, mais sur la fenetre principale.
    private Window topWindow() {
        for( int i = _openDialogs.size() - 1; i >= 0; i-- ) {
            Dialog d = _openDialogs.get(i);
            if( d == _markEditorDialog )   // popup fenetre -> pas de footer dessus
                continue;
            if( d.isShowing() && d.getWindow() != null )
                return d.getWindow();
        }
        return (_activity != null) ? _activity.getWindow() : null;
    }

    // Points d'entree thread-safe (appelables depuis un thread de fond). Marshallent sur l'UI thread.
    // Rouge = erreur, bleu (colorAccent / boutons) = information.
    private static final int FOOTER_ERROR = 0xFFD32F2F; // red
    private static final int FOOTER_INFO  = 0xFF2196F3; // blue (colorAccent)
    private static final int FOOTER_WARN  = 0xFFF57C00; // orange: not an error, not a plain info either

    // #87 test feedback: the footer sits over the speed readout and the "+" mark button. Its
    // BACKGROUND is drawn at 60 % so they show through; the text itself stays opaque, with a light
    // shadow to keep it readable on the pale map.
    private static final int FOOTER_BG_ALPHA = 0x99;
    // Le compteur de resultats prend cet orange quand la reponse a ete tronquee : c'est ce qui relie
    // le chiffre au bandeau d'avertissement, sinon on ne voit pas qu'ils parlent de la meme chose.
    private static final int COUNT_NORMAL = 0xFF666666;

    public static void notifyError(String msg) { footer(msg, FOOTER_ERROR, 5000); }
    public static void notifyInfo(String msg)  { footer(msg, FOOTER_INFO, 5000); }
    public static void notifyInfo(String msg, int durationMs) { footer(msg, FOOTER_INFO, durationMs); }
    // #67 : entre les deux. Rien n'a echoue, mais le resultat affiche ne peut pas etre garanti
    // complet et l'utilisateur doit le savoir avant de s'y fier.
    public static void notifyWarn(String msg)  { footer(msg, FOOTER_WARN, 5000); }
    public static void notifyWarn(String msg, int durationMs) { footer(msg, FOOTER_WARN, durationMs); }

    private static void footer(String msg, int bgColor, int durationMs) {
        ViewsController vc = _instance;
        if( vc != null )
            vc.showFooter(msg, bgColor, durationMs);
    }

    public void showFooter(final String msg, final int bgColor, final int durationMs) {
        final Activity act = _activity;
        if( act == null || msg == null )
            return;
        act.runOnUiThread(new Runnable() {
            @Override public void run() { showFooterOnUi(msg, bgColor, durationMs); }
        });
    }

    private void showFooterOnUi(String msg, int bgColor, int durationMs) {
        Window w = topWindow();
        if( w == null )
            return;
        if( _footerHandler == null )
            _footerHandler = new Handler(Looper.getMainLooper());

        detachFooter(); // retire un footer precedent (potentiellement sur une autre fenetre)

        float density = _activity.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(_activity);
        tv.setText(msg);
        tv.setTextColor(Color.WHITE);
        tv.setShadowLayer(3 * density, 0, density, 0xCC000000);
        tv.setBackgroundColor((bgColor & 0x00FFFFFF) | (FOOTER_BG_ALPHA << 24));
        final int padH = (int)(16 * density);
        final int padV = (int)(12 * density);
        tv.setPadding(padH, padV, padH, padV);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideFooter(); } // tap = fermer avant 5 s
        });

        // Respecter les barres systeme : sans ca le bandeau, ajoute en bas du contenu, passe DERRIERE
        // la barre de navigation et n'est visible qu'a moitie. On ajoute l'inset bas en padding -> le
        // fond descend jusqu'au bord mais le texte reste au-dessus de la nav bar.
        ViewCompat.setOnApplyWindowInsetsListener(tv, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padH, padV, padH, padV + bars.bottom);
            return insets;
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        w.addContentView(tv, lp);
        _footerView = tv;
        ViewCompat.requestApplyInsets(tv);

        _footerHandler.removeCallbacks(_footerHide);
        _footerHandler.postDelayed(_footerHide, durationMs); // auto-disparition (durationMs)
    }

    private void detachFooter() {
        if( _footerView != null ) {
            ViewParent p = _footerView.getParent();
            if( p instanceof ViewGroup )
                ((ViewGroup)p).removeView(_footerView);
            _footerView = null;
        }
    }

    private void hideFooter() {
        if( _footerHandler != null )
            _footerHandler.removeCallbacks(_footerHide);
        detachFooter();
    }

    // #33 : page "Personal marks" (liste des marques perso).
    public void ShowMarksPage() {
        if( this._activity == null ) {
            return;
        }
        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageMarksBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_marks, null, false);
        binding.setViewsController(this);
        showFullscreenPage(binding.getRoot(), binding.btnBack);
    }

    // --- #67 : page "Search a place" -----------------------------------------------------------
    //
    // Page NEUVE : elle ressemble a la liste des marques perso, mais celle-ci n'est pas touchee.
    // La recherche part a la validation et JAMAIS a la frappe : la politique d'usage de Nominatim
    // interdit l'autocompletion sur son instance publique.

    // Duree du bandeau d'avertissement de troncature. Bien plus longue que les 5 s habituelles :
    // le message fait trois lignes, il porte une consigne a suivre, et il n'apparait que dans un cas
    // sur beaucoup -- il peut donc se permettre de rester.
    private static final int SEARCH_WARN_MS = 12000;

    // Au-dela, on abandonne l'attente. Large a dessein : la demande peut patienter derriere une
    // autre dans la file, et le serveur lui-meme met une bonne seconde a repondre.
    private static final long SEARCH_TIMEOUT_MS = 45000;

    public void ShowSearchPage() {
        ShowSearchPage("");
    }

    // #67 : avec un texte de depart, cherche aussitot. C'est le cas d'un lien geo: qui ne porte
    // qu'une adresse : l'utilisateur n'a rien a retaper.
    public void ShowSearchPage(String initialText) {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        final LayoutPageSearchBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_search, null, false);
        binding.setAppManager(_am);

        final List<GeoSearchItem> items = new ArrayList<GeoSearchItem>();
        final MyListAdapter<GeoSearchItem> adapter = new MyListAdapter<GeoSearchItem>(
                this._activity, R.layout.layout_searchresult, BR.searchItem, items);

        binding.lvResults.setAdapter(adapter);

        // La recherche en cours. Une nouvelle remplace la precedente, dont la reponse -- si elle
        // arrive quand meme -- est jetee : sans ce garde-fou, deux recherches lancees coup sur coup
        // versent leurs resultats dans la MEME liste et tout apparait en double.
        final GeoQuery[] current = new GeoQuery[1];

        // #67 : le mot de liaison est ce qui fait basculer Nominatim de "un lieu qui PORTE ce nom"
        // vers "les lieux de cette CATEGORIE". Sans lui, "restaurants Saint-Malo" ne rend rien du tout,
        // ce qui est deroutant. Il est donc montre en rouge dans les deux exemples, et la langue de
        // la categorie doit s'accorder avec celle du mot : "in" avec l'anglais, "a" avec le francais.
        // Le texte est pose ici, et pas dans le layout, parce qu'il est mis en forme.
        binding.tvHelp.setText(HtmlCompat.fromHtml(
                "Type a place name or an address, then tap Search. A found place can be turned into a mark."
                + "<br/>To look for a <b>category</b>, the small linking word is what makes it work: "
                + "<b>restaurants <font color=\"#d32f2f\">in</font> Saint-Malo</b> or "
                + "<b>supermarch\u00e9s <font color=\"#d32f2f\">\u00e0</font> Saint-Malo</b>. "
                + "Without it, only places whose <b>name</b> matches are found."
                + "<br/><b>Sea marks</b>: a lighthouse is found by its name "
                + "(<b>Phare du Grand-Jardin</b>) or as a category (<b>lighthouse</b>, or "
                + "<b>phare</b>). Beacons and buoys are <b>not</b> in this search: they carry no "
                + "name and the search server does not index them."
                + "<br/>Results come from OpenStreetMap and need a network connection.",
                HtmlCompat.FROM_HTML_MODE_LEGACY));

        binding.btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startSearch(binding, adapter, items, current);
            }
        });

        // La touche Rechercher du clavier fait la meme chose que le bouton.
        binding.etQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // Un clavier materiel (ou adb) fait passer ici DEUX fois, a l'enfoncement puis au
                // relachement de la touche. Sans ce filtre, une seule frappe lance deux recherches.
                if( (event != null) && (event.getAction() != KeyEvent.ACTION_DOWN) ) {
                    return true;
                }

                if( (actionId == EditorInfo.IME_ACTION_SEARCH) || (actionId == EditorInfo.IME_NULL) ) {
                    startSearch(binding, adapter, items, current);
                    return true;
                }
                return false;
            }
        });

        showFullscreenPage(binding.getRoot(), binding.btnBack);

        if( (initialText != null) && (initialText.trim().length() > 0) ) {
            binding.etQuery.setText(initialText.trim());
            startSearch(binding, adapter, items, current);
        }
    }

    private void startSearch(final LayoutPageSearchBinding binding,
                             final MyListAdapter<GeoSearchItem> adapter,
                             final List<GeoSearchItem> items,
                             final GeoQuery[] current) {

        final String text = binding.etQuery.getText().toString().trim();

        if( text.length() == 0 ) {
            return;
        }

        hideKeyboard(binding.etQuery);

        // "Around me" coche : on fige ICI la boite autour de la position courante, et pas au moment
        // ou la demande sortira de la file. C'est le cadre que l'utilisateur avait sous les yeux en
        // lancant sa recherche qui doit s'appliquer.
        final String viewbox = binding.cbAround.isChecked() ? _am.getSearchViewbox() : null;

        if( binding.cbAround.isChecked() && (viewbox == null) ) {
            showSearchStatus(binding, "No position yet: cannot search around you");
            return;
        }

        // Nombre de lieux demandes au serveur, fige ici : c'est celui que le compteur affichera au
        // retour, et celui auquel on compare le nombre trouve pour savoir s'il y a eu troncature.
        final int asked = _am.getSearchLimit();

        items.clear();
        adapter.notifyDataSetChanged();
        binding.tvCount.setVisibility(View.GONE);
        showSearchStatus(binding, "Searching...");

        // La precedente n'interesse plus personne : si elle attend encore son tour, elle ne partira
        // meme pas sur le reseau.
        if( current[0] != null ) {
            current[0].cancel();
        }

        final GeoQuery query = new GeoQuery(text, viewbox).start();
        current[0] = query;

        // On attend la reponse dans un thread jetable plutot que de scruter depuis l'interface :
        // getResult(timeout) endort celui-ci jusqu'a l'arrivee de la reponse.
        new Thread(new Runnable() {
            @Override
            public void run() {

                final List<GeoSearch.Result> results = query.getResult(SEARCH_TIMEOUT_MS);
                final String error = query.getError();
                final Activity activity = _activity;

                if( activity == null ) {
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // Une recherche plus recente a pris la main : cette reponse est perimee.
                        if( current[0] != query ) {
                            return;
                        }

                        // Pas de resultats du tout : soit la requete a echoue (reseau absent,
                        // serveur qui refuse), soit l'attente a expire. L'application est faite
                        // pour marcher sans reseau, mais une recherche de lieu, elle, ne le peut
                        // pas : on le dit au lieu d'afficher une liste vide.
                        if( results == null ) {
                            showSearchStatus(binding, (error != null)
                                    ? ("Search failed: " + error)
                                    : "Search timed out, try again");
                            return;
                        }

                        for( GeoSearch.Result result : results ) {
                            final GeoSearchItem item = new GeoSearchItem(result);

                            // Creer une marque la selectionne : la ligne doit alors remonter en
                            // tete, comme elle le ferait dans la liste des marques perso.
                            item.setOnChanged(new Runnable() {
                                @Override
                                public void run() {
                                    sortSearchItems(items);
                                    adapter.notifyDataSetChanged();
                                }
                            });

                            items.add(item);
                        }

                        sortSearchItems(items);
                        adapter.notifyDataSetChanged();

                        final int found = items.size();

                        // "24 found" plutot que "24 / 50" : le second chiffre etait le nombre
                        // DEMANDE, ce que rien ne laissait deviner. Quand la reponse est tronquee,
                        // c'est la couleur qui le dit, la meme que le bandeau.
                        final boolean truncated = (found >= asked);

                        binding.tvCount.setText(found + " found");
                        binding.tvCount.setTextColor(truncated ? FOOTER_WARN : COUNT_NORMAL);
                        binding.tvCount.setVisibility(View.VISIBLE);

                        // Autant de reponses que demande : le serveur a TRONQUE. Et il coupe selon
                        // SA notion d'importance, pas la distance -- des lieux plus proches ont donc
                        // pu rester dehors, sans qu'on puisse le savoir. C'est le seul cas ou la
                        // liste ne peut pas etre garantie, et il vaut un avertissement.
                        //
                        // Le conseil n'est pas le meme selon qu'on butait sur le plafond du serveur
                        // ou sur un reglage plus bas : dans un cas il faut resserrer la recherche,
                        // dans l'autre il suffit de demander davantage.
                        // Un seul bandeau a la fois : la troncature passe avant le conseil, elle
                        // porte l'information la plus lourde de consequences.
                        if( !truncated ) {
                            suggestAroundMe(text, viewbox, byDistanceNearestKm(results));
                        }

                        if( truncated ) {
                            if( asked >= GeoSearch.SERVER_MAX_RESULTS ) {
                                notifyWarn("The server stopped at its maximum of " + asked
                                        + " places, so nearer ones may be missing."
                                        + " Refine your search or reduce the radius.",
                                        SEARCH_WARN_MS);
                            } else {
                                notifyWarn("All " + asked + " places asked for came back, so nearer"
                                        + " ones may be missing. Refine your search, or raise"
                                        + " Search results in the parameters.",
                                        SEARCH_WARN_MS);
                            }
                        }

                        // Rien trouve avec le cadre : c'est le plus souvent le cadre lui-meme qui
                        // est en cause, pas la recherche. On le dit, plutot que de laisser croire
                        // que le lieu n'existe pas.
                        String empty = (viewbox != null)
                                ? ("Nothing found for \"" + text + "\" within "
                                        + _am.getSearchRadiusString() + " of you")
                                : ("No place found for \"" + text + "\"");

                        showSearchStatus(binding, results.isEmpty() ? empty : null);
                    }
                });
            }
        }, "GeoSearch-page").start();
    }

    /**
     * #67 : le meme ordre que la liste des marques perso -- #7 : celles qui signalent d'abord, puis
     * les selectionnees, puis les plus proches, et le nom pour departager. On abandonne donc l'ordre de PERTINENCE rendu par
     * Nominatim : dans une application de navigation, ce qui compte est ce qui est pres de soi.
     * Rejoue apres chaque creation, une marque creee etant selectionnee d'office.
     */
    private static void sortSearchItems(List<GeoSearchItem> items) {
        Collections.sort(items, new Comparator<GeoSearchItem>() {
            @Override
            public int compare(GeoSearchItem i1, GeoSearchItem i2) {
                return PersonalMark.compareForList(
                        i1.isAlarming(), i1.isSelected(), i1.getDistance(), i1.getTitle(),
                        i2.isAlarming(), i2.isSelected(), i2.getDistance(), i2.getTitle());
            }
        });
    }

    /**
     * #67 : le conseil qui manquait. Sans cadre geographique, Nominatim ne traite pas un mot comme
     * une CATEGORIE : il cherche un lieu qui PORTE ce nom, partout dans le monde. "camping" rend
     * alors une falaise en Valais et un quartier au Chili, et pas un seul terrain de camping. Rien
     * n'a echoue, aucune reponse n'a ete tronquee, et l'utilisateur reste devant une liste plausible
     * qui ne repond pas a sa question.
     *
     * Le declencheur est volontairement etroit, pour ne pas devenir un bruit de fond : Around me
     * decoche, UN seul mot cherche, et rien de trouve a l'interieur du rayon configure. Une
     * recherche par nom qui aboutit pres de soi ne dit donc rien, et une recherche a plusieurs mots
     * non plus.
     */
    private void suggestAroundMe(String text, String viewbox, double nearestKm) {

        if( viewbox != null ) {
            return;   // la case est deja cochee, le conseil n'a pas lieu d'etre
        }

        if( text.trim().split("\\s+").length != 1 ) {
            return;   // plusieurs mots : ce n'est pas le cas qu'on cherche a rattraper
        }

        if( nearestKm <= _am.getSearchRadiusKm() ) {
            return;   // quelque chose a ete trouve pres d'ici : la recherche a fait son travail
        }

        notifyInfo("Nothing found near you: a single word is searched as a NAME, worldwide."
                + " Tick Around me to look for it as a category around you.", SEARCH_WARN_MS);
    }

    // La distance du plus proche des resultats, ou l'infini s'il n'y en a aucun.
    private double byDistanceNearestKm(List<GeoSearch.Result> results) {

        double nearest = Double.MAX_VALUE;

        for( GeoSearch.Result result : results ) {
            double km = _am.distanceKmTo(result.getLatitude(), result.getLongitude());

            if( km < nearest ) {
                nearest = km;
            }
        }

        return nearest;
    }

    // Un message null efface la ligne d'etat, la liste parlant alors d'elle-meme.
    private void showSearchStatus(LayoutPageSearchBinding binding, String message) {
        binding.tvStatus.setText((message == null) ? "" : message);
        binding.tvStatus.setVisibility((message == null) ? View.GONE : View.VISIBLE);
    }

    private void hideKeyboard(View view) {
        if( this._activity == null ) {
            return;
        }
        InputMethodManager imm = (InputMethodManager)
                this._activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if( imm != null ) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // #33 : page "Offline maps" (liste des cartes hors-ligne).
    public void ShowMapsPage() {
        if( this._activity == null ) {
            return;
        }
        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageMapsBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_maps, null, false);
        binding.setViewsController(this);
        showFullscreenPage(binding.getRoot(), binding.btnBack);
    }

    // Sun and moon page. Like the tide page, the work happens on a worker thread and
    // the page fills itself when it comes back -- a day of sky is a few thousand
    // trigonometric series, which is milliseconds, but not on the main thread.
    public void ShowSunMoonPage() {
        ShowSunMoonPage(null);
    }

    /**
     * @param atUnixSeconds the moment to open on, or null for the present. The tide page
     *                      passes its own reading time, so the two charts agree.
     */
    public void ShowSunMoonPage(final Long atUnixSeconds) {

        // The same rule as the tide page, under its own memory.
        TidePlace kept = _am.getSkyFollowsUser(_activity)
                ? null
                : _am.getSkyLastPlace(_activity);

        if( kept != null ) {
            showSkyAt(kept.latitude(), kept.longitude(), kept.label(), atUnixSeconds);
            return;
        }

        showSkyAt(_am.getLatitude(), _am.getLongitude(), null, atUnixSeconds);
    }

    /**
     * The same page wherever the sky is read: under the user, or at a place picked from a
     * search. Only the heading differs, and the fact that a named place is remembered.
     *
     * @param placeName the name the place was found under, or null for the user's own
     */
    private void showSkyAt(final double latitude, final double longitude,
                           final String placeName, final Long atUnixSeconds) {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        final LayoutPageSunmoonBinding binding = DataBindingUtil.inflate(
                inflater, R.layout.layout_page_sunmoon, null, false);
        binding.setViewsController(this);

        // The compass needle on the map, kept on the phone's own heading so that pointing the
        // phone lines the disc up with the sky.
        //
        // The chip is read at its own rate and not on its binding notice, which is held back to
        // 300 ms and would show one step of the sweep out of three. A read is a lock and a copy,
        // and the needle redraws only when the bearing has actually moved. The minute tick of
        // this page is far too slow for a compass, hence a clock of its own.
        final android.os.Handler needleClock = new android.os.Handler(android.os.Looper.getMainLooper());

        final Runnable needle = new Runnable() {
            @Override
            public void run() {
                binding.skyView.setHeading(_am.getDeviceHeadingTrue());
                needleClock.postDelayed(this, NEEDLE_TICK_MS);
            }
        };

        final Dialog page = showFullscreenPage(binding.getRoot(), binding.btnBack, new Runnable() {
            @Override
            public void run() {
                needleClock.removeCallbacks(needle);
            }
        });

        needle.run();

        final long dayStart = skyMidnight(System.currentTimeMillis() / 1000L);
        final long dayEnd = dayStart + 86400L;

        new Thread(new Runnable() {
            @Override
            public void run() {

                AstroDay built = null;
                String failure = null;

                TidePotential.Series builtSeries = null;
                com.gb.mybuoy.astro.SkyTrack builtTrack = null;

                try {
                    built = new AstroDay(latitude, longitude, dayStart, dayEnd);

                    builtTrack = new com.gb.mybuoy.astro.SkyTrack(latitude, longitude);

                    // The same window as the tide page: a day behind and thirty ahead,
                    // counted from the present.
                    long now = System.currentTimeMillis() / 1000L;

                    builtSeries = TidePotential.series(
                            now - (TideModel.DAYS_BEFORE * 86400L),
                            now + (TideModel.DAYS_AFTER * 86400L),
                            Math.toRadians(latitude), Math.toRadians(longitude));
                    // Named places only, and under their own key. Where the user stands
                    // has a button of its own and is never out of reach.
                    if( placeName != null ) {
                        _am.putSkyLastPlace(_activity, latitude, longitude, placeName);
                    }
                } catch (Throwable t) {
                    failure = t.getMessage();
                }

                final AstroDay day = built;
                final TidePotential.Series series = builtSeries;
                final com.gb.mybuoy.astro.SkyTrack track = builtTrack;
                final String message = failure;

                if( _activity == null ) {
                    return;
                }

                _activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        skyReady(binding, page, day, series, track, message, atUnixSeconds,
                                latitude, longitude, placeName);
                    }
                });
            }
        }, "AstroDay").start();
    }

    // Keeps the two "now" lines and the moon disc current.
    private android.os.Handler _skyClock = null;

    private static final long SKY_TICK_MS = 60000L;

    // The compass needle, read at the rate the heading chip itself is sampled at.
    private static final long NEEDLE_TICK_MS = 100L;

    private void skyReady(final LayoutPageSunmoonBinding binding, final Dialog page,
                          final AstroDay day, final TidePotential.Series series,
                          final com.gb.mybuoy.astro.SkyTrack track,
                          String failure, Long atUnixSeconds,
                          final double latitude, final double longitude,
                          final String placeName) {

        binding.tvWait.setVisibility(View.GONE);

        if( day == null ) {
            binding.tvWait.setVisibility(View.VISIBLE);
            binding.tvWait.setText("The sky could not be worked out here."
                    + ((failure == null) ? "" : (System.getProperty("line.separator") + failure)));
            return;
        }

        binding.llSky.setVisibility(View.VISIBLE);

        skyDayBlock(binding, latitude, longitude);

        // Named, not spelled out in degrees: a page that prints the user's coordinates
        // back at him tells him nothing he wants and shows them to whoever is looking
        // over his shoulder.
        binding.tvPlace.setText((placeName == null) ? "Current position" : placeName);

        // From a named place, one tap goes back under the user's feet; from his own
        // position the button opens the chooser instead.
        binding.btnPlaceSwitch.setText((placeName == null) ? "Other place" : "Current position");

        binding.btnPlaceSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( placeName == null ) {
                    skyPicker(binding, page, latitude, longitude, null);
                    return;
                }
                openSkyElsewhere(page, _am.getLatitude(), _am.getLongitude(), null, true);
            }
        });

        binding.earthTide.setSeries(series, System.currentTimeMillis() / 1000L);

        if( atUnixSeconds != null ) {
            binding.earthTide.showAt(atUnixSeconds);
        }

        // The title is the way across to the tide page, on the same instant.
        binding.tvTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long reading = binding.earthTide.centreUnixSeconds();

                if( page != null ) {
                    page.dismiss();
                }

                ShowTidesPage(reading);
            }
        });

        // Dragging the curve moves the mark, and everything above follows it: the sky
        // disc, the moon and the two readings. Scrubbing a month of sky is the point.
        binding.earthTide.setOnInstantChanged(new com.gb.mybuoy.astro.EarthTideView.OnInstantChanged() {
            @Override
            public void instantChanged(long unixSeconds) {
                skyAt(binding, day, series, track, unixSeconds);
            }
        });

        skyRefresh(binding, day, series, track);

        // Once a minute is plenty: nothing on this page moves faster than a quarter of
        // a degree in that time.
        final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());

        _skyClock = clock;

        clock.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Another page, or the chooser, has taken the clock over, or this page is
                // gone. Either way this loop is done, and it clears nothing: the field is
                // no longer its own, and emptying it would stop whoever took over.
                if( (_skyClock != clock) || (binding.llSky.getWindowToken() == null) ) {
                    return;
                }

                // Left alone, the mark rides the present and carries the whole page with
                // it through its listener. Once the reader has dragged it somewhere, that
                // instant is his and nothing moves it; only the block about his own day
                // goes on keeping up, which is what tells him the hour has turned.
                long tick = System.currentTimeMillis() / 1000L;

                if( binding.earthTide.followsNow() ) {
                    binding.earthTide.showNow(tick);
                } else {
                    // The mark belongs to the reader, the present does not: the red line
                    // goes on advancing under whatever hour he is looking at.
                    binding.earthTide.setNow(tick);
                }

                skyDayBlock(binding, latitude, longitude);

                clock.postDelayed(this, SKY_TICK_MS - (System.currentTimeMillis() % SKY_TICK_MS));
            }
        }, SKY_TICK_MS - (System.currentTimeMillis() % SKY_TICK_MS));
    }

    /*
     * The block under the date, which answers "where am I in the day".
     *
     * It is anchored to the PRESENT and never follows the curve. The map above is the
     * sky at whatever instant the reader is pointing at; this is his own standing in his
     * own day. Letting the mark drive it would turn "night in two hours" into a count
     * from a date nobody chose.
     */
    private void skyDayBlock(final LayoutPageSunmoonBinding binding,
                             final double latitude, final double longitude) {

        final long now = System.currentTimeMillis() / 1000L;

        // A track of its own, pinned to now, so that the one driving the map stays free
        // to wander with the mark. At the place the page is about, not at the user: the
        // twilights beside it come from that place, and two positions in one block would
        // be read as one.
        //
        // Finding the crossings means walking the sky, several times over, so none of it
        // is done on the main thread: the texts are set when the worker returns.
        new Thread(new Runnable() {
            @Override
            public void run() {

                com.gb.mybuoy.astro.SkyTrack built = null;

                Twilight civil = null;
                Twilight nautical = null;
                Twilight astronomical = null;

                try {
                    built = new com.gb.mybuoy.astro.SkyTrack(latitude, longitude);

                    built.update(now);

                    com.gb.mybuoy.astro.RiseSet.Altitude sun =
                            com.gb.mybuoy.astro.RiseSet.sun(Math.toRadians(longitude),
                                    Math.toRadians(latitude));

                    civil = skyTwilight(sun, Math.toRadians(-6.0), now);
                    nautical = skyTwilight(sun, Math.toRadians(-12.0), now);
                    astronomical = skyTwilight(sun, Math.toRadians(-18.0), now);
                } catch (Throwable t) {
                    built = null;
                }

                final com.gb.mybuoy.astro.SkyTrack here = built;
                final Twilight civilReady = civil;
                final Twilight nauticalReady = nautical;
                final Twilight astroReady = astronomical;

                if( (here == null) || (civilReady == null) || (_activity == null) ) {
                    return;
                }

                _activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if( binding.llSky.getWindowToken() == null ) {
                            return;     // page closed while we were walking
                        }

                        skyDayTexts(binding, here, civilReady, nauticalReady, astroReady, now);
                    }
                });
            }
        }, "SkyDayBlock").start();
    }

    /** The two ends of the twilight stretch the observer is in, and nothing else. */
    private static final class Twilight {

        private final long _riseSeconds;
        private final long _setSeconds;
        private final boolean _crossed;
        private final boolean _above;

        private Twilight(long riseSeconds, long setSeconds, boolean crossed, boolean above) {
            _riseSeconds = riseSeconds;
            _setSeconds = setSeconds;
            _crossed = crossed;
            _above = above;
        }
    }

    /*
     * The twilight line, taken around the PRESENT rather than inside a calendar day.
     *
     * A day cut at midnight pairs the first climb past the angle with the first fall
     * below it, which at high latitudes are two different nights: in May at Reykjavik
     * the line read "begins 02:45, ends 00:01" with the end belonging to the night
     * before, and the day the fall landed just the wrong side of midnight it read
     * "--:--" though the Sun had gone down all the same.
     *
     * Taking the two crossings that BOUND the instant gives the stretch under the
     * reader's feet: by day the climb behind him and the fall ahead, by night the fall
     * behind and the climb to come, which is how the rise and set lines above already
     * read.
     */
    private Twilight skyTwilight(com.gb.mybuoy.astro.RiseSet.Altitude sun,
                                 double targetRadians, long now) {

        final long reach = 26L * 3600L;

        com.gb.mybuoy.astro.RiseSet.Window window =
                com.gb.mybuoy.astro.RiseSet.scan(sun, now - reach, now + reach, targetRadians);

        boolean above = sun.radiansAt(now) > targetRadians;

        if( window.neverCrossed() ) {
            return new Twilight(Long.MIN_VALUE, Long.MIN_VALUE, false, above);
        }

        long rise = above ? skyLastBefore(window.rises(), now) : skyFirstAfter(window.rises(), now);
        long set = above ? skyFirstAfter(window.sets(), now) : skyLastBefore(window.sets(), now);

        return new Twilight(rise, set, true, above);
    }

    private long skyLastBefore(java.util.List<Long> moments, long now) {

        long found = Long.MIN_VALUE;

        for( int n = 0; n < moments.size(); n++ ) {
            long moment = moments.get(n);

            if( (moment <= now) && (moment > found) ) {
                found = moment;
            }
        }
        return found;
    }

    private long skyFirstAfter(java.util.List<Long> moments, long now) {

        long found = Long.MIN_VALUE;

        for( int n = 0; n < moments.size(); n++ ) {
            long moment = moments.get(n);

            if( (moment > now) && ((found == Long.MIN_VALUE) || (moment < found)) ) {
                found = moment;
            }
        }
        return found;
    }

    /* The block itself, once the walking is done: nothing here costs anything. */
    private void skyDayTexts(LayoutPageSunmoonBinding binding,
                             com.gb.mybuoy.astro.SkyTrack here,
                             Twilight civil, Twilight nautical, Twilight astronomical,
                             long now) {

        java.text.SimpleDateFormat dayFormat =
                new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK);

        binding.tvDay.setText(dayFormat.format(new java.util.Date(now * 1000L)));

        skyTimes(binding.tvDaySunNextRise, binding.tvDaySunLabelRise, binding.tvDaySunRise,
                binding.tvDaySunNextSet, binding.tvDaySunLabelSet, binding.tvDaySunSet,
                here.sunRiseSeconds(), here.sunSetSeconds(), now);

        skyTimes(binding.tvDayMoonNextRise, binding.tvDayMoonLabelRise, binding.tvDayMoonRise,
                binding.tvDayMoonNextSet, binding.tvDayMoonLabelSet, binding.tvDayMoonSet,
                here.moonRiseSeconds(), here.moonSetSeconds(), now);

        skyTwilightRow(binding.tvTwCivilBegin, binding.tvTwCivilEnd, civil, now);
        skyTwilightRow(binding.tvTwNauticalBegin, binding.tvTwNauticalEnd, nautical, now);
        skyTwilightRow(binding.tvTwAstroBegin, binding.tvTwAstroEnd, astronomical, now);

        binding.tvNextEvent.setText(skyNextEvent(civil, here, now));
    }

    /*
     * The local day an instant falls in, as the user's own clock draws it: midnight to
     * midnight where the device is, not UTC. The sky does not care, but the user does.
     */
    private long skyMidnight(long unixSeconds) {

        java.util.Calendar midnight = java.util.Calendar.getInstance();

        midnight.setTimeInMillis(unixSeconds * 1000L);
        midnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
        midnight.set(java.util.Calendar.MINUTE, 0);
        midnight.set(java.util.Calendar.SECOND, 0);
        midnight.set(java.util.Calendar.MILLISECOND, 0);

        return midnight.getTimeInMillis() / 1000L;
    }

    /*
     * Two times on one line, and each one stands on its own: by the afternoon the
     * morning twilight is behind us while the evening one is still to come.
     */
    private void skyTwilightRow(TextView beginView, TextView endView,
                                Twilight twilight, long now) {

        if( !twilight._crossed ) {
            // The two times are the ends of the stretch ABOVE this line's angle, so a Sun
            // that never leaves it has that stretch all day, and one that never reaches it
            // has none at all.
            beginView.setText(twilight._above ? "all day" : "none");
            endView.setText("");

            skyTwilightTime(beginView, Long.MIN_VALUE, now);
            skyTwilightTime(endView, Long.MIN_VALUE, now);
            return;
        }

        beginView.setText((twilight._riseSeconds == Long.MIN_VALUE)
                ? "--:--" : skyClock(twilight._riseSeconds));

        endView.setText((twilight._setSeconds == Long.MIN_VALUE)
                ? "--:--" : skyClock(twilight._setSeconds));

        skyTwilightTime(beginView, twilight._riseSeconds, now);
        skyTwilightTime(endView, twilight._setSeconds, now);
    }

    /* Grey and italic once it is behind us, as the rise and set times are. */
    private void skyTwilightTime(TextView time, long unixSeconds, long now) {

        boolean past = (unixSeconds != Long.MIN_VALUE) && (unixSeconds <= now);

        time.setTypeface(null, past ? android.graphics.Typeface.ITALIC
                : android.graphics.Typeface.NORMAL);

        time.setTextColor(past ? SKY_PAST : SKY_AHEAD);
    }

    /*
     * The one line that says what is about to happen. Nightfall is the END OF CIVIL
     * TWILIGHT rather than sunset: half an hour separates them, and it is the later one
     * that decides whether a lamp is wanted and whether the navigation lights are due.
     */
    private CharSequence skyNextEvent(Twilight civil, com.gb.mybuoy.astro.SkyTrack here,
                                      long now) {

        long best = Long.MAX_VALUE;
        String what = null;

        long[] moments = {
                civil._setSeconds,
                civil._riseSeconds,
                here.sunRiseSeconds(),
                here.sunSetSeconds(),
                here.moonRiseSeconds(),
                here.moonSetSeconds(),
        };

        String[] names = { "night", "daylight", "sunrise", "sunset", "moonrise", "moonset" };

        for( int n = 0; n < moments.length; n++ ) {

            if( (moments[n] == Long.MIN_VALUE) || (moments[n] <= now) ) {
                continue;
            }

            if( moments[n] < best ) {
                best = moments[n];
                what = names[n];
            }
        }

        if( what == null ) {
            return "";
        }

        long left = (best - now) / 60L;

        return String.format(java.util.Locale.UK, "Now: %s in %s", what,
                (left >= 60L)
                        ? String.format(java.util.Locale.UK, "%dh%02d", left / 60L, left % 60L)
                        : String.format(java.util.Locale.UK, "%d min", Math.max(0L, left)));
    }

    /*
     * The place picker, ported from the tide page so the two read and behave alike.
     *
     * The places it remembers are its OWN: the sky is interesting inland and the tides are
     * not, so a town looked up here has no business turning up in the tide picker, and the
     * two keep separate stores in the preferences.
     */
    private void skyPicker(final LayoutPageSunmoonBinding binding, final Dialog page,
                           final double latitude, final double longitude,
                           final String placeName) {

        // The picker IS the page while it is open: the whole body goes, the place line with
        // it, and the only ways out are a button here or the back button of the page.
        binding.llSkyBody.setVisibility(View.GONE);
        binding.rowPlace.setVisibility(View.GONE);
        binding.llSkyPicker.setVisibility(View.VISIBLE);

        // Nothing left on screen that the minute tick has to keep current.
        _skyClock = null;

        // The sky never has nothing to show, so the title is the question rather than an
        // apology for empty readings.
        binding.tvDay.setText("Sun and moon elsewhere");

        // Where the user stands, offered whenever it is not already what the page is on.
        boolean offerHere = (placeName != null);

        binding.btnSkyCurrentPlace.setVisibility(offerHere ? View.VISIBLE : View.GONE);

        if( offerHere ) {
            binding.btnSkyCurrentPlace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openSkyElsewhere(page, _am.getLatitude(), _am.getLongitude(), null, true);
                }
            });
        }

        // The last place looked up here, for the same reason and under the same condition.
        final TidePlace last = _am.getSkyLastPlace(_activity);

        boolean offerLast = (last != null) && !samePlace(last, latitude, longitude);

        binding.btnSkyLastPlace.setVisibility(offerLast ? View.VISIBLE : View.GONE);

        if( offerLast ) {
            binding.btnSkyLastPlace.setText("Sun and moon at " + last.label());

            binding.btnSkyLastPlace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openSkyElsewhere(page, last.latitude(), last.longitude(), last.label(), true);
                }
            });
        }

        // "Or" only makes sense after something to be an alternative to.
        binding.tvSkyLookUp.setText((offerHere || offerLast)
                ? "Or look up a place: a town, a harbour, an island."
                : "Look up a place: a town, a harbour, an island.");

        // One search at a time, as on the place search page: an answer whose query is no longer
        // the current one is dropped, or two searches launched one after the other both pour
        // their results into the same column.
        final GeoQuery[] current = new GeoQuery[1];

        binding.btnSkyPlaceSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                skyPlaceSearch(binding, page, current);
            }
        });

        // The Search key of the keyboard does what the button does.
        binding.etSkyPlace.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // A hardware keyboard (or adb) comes through here twice, on the press and on
                // the release. Without this filter one keystroke starts two searches.
                if( (event != null) && (event.getAction() != KeyEvent.ACTION_DOWN) ) {
                    return true;
                }

                if( (actionId == EditorInfo.IME_ACTION_SEARCH) || (actionId == EditorInfo.IME_NULL) ) {
                    skyPlaceSearch(binding, page, current);
                    return true;
                }
                return false;
            }
        });
    }

    private void skyPlaceSearch(final LayoutPageSunmoonBinding binding, final Dialog page,
                                  final GeoQuery[] current) {

        final String text = binding.etSkyPlace.getText().toString().trim();

        if( text.length() == 0 ) {
            return;
        }

        hideKeyboard(binding.etSkyPlace);

        binding.llSkyPlaceResults.removeAllViews();
        showSkyPlaceStatus(binding, "Searching...");

        if( current[0] != null ) {
            current[0].cancel();
        }

        // No viewbox here, unlike the place search page: the whole point is to reach water
        // that is NOT around the user, and a box centred on them would hide it.
        final GeoQuery query = new GeoQuery(text, null).start();
        current[0] = query;

        new Thread(new Runnable() {
            @Override
            public void run() {

                final List<GeoSearch.Result> results = query.getResult(SEARCH_TIMEOUT_MS);
                final String error = query.getError();
                final Activity activity = _activity;

                if( activity == null ) {
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // A more recent search has taken over: this answer is stale.
                        if( current[0] != query ) {
                            return;
                        }

                        // The sky itself is computed offline, but looking a place up is not.
                        // Say which of the two failed rather than show an empty column.
                        if( results == null ) {
                            showSkyPlaceStatus(binding, (error != null)
                                    ? ("Search failed: " + error)
                                    : "Search timed out, try again");
                            return;
                        }

                        if( results.isEmpty() ) {
                            showSkyPlaceStatus(binding, "No place found for \"" + text + "\"");
                            return;
                        }

                        showSkyPlaceStatus(binding, results.size()
                                + ((results.size() == 1) ? " place found. " : " places found. ")
                                + "Tap one to read its sky.");

                        for( GeoSearch.Result result : results ) {
                            binding.llSkyPlaceResults.addView(skyPlaceRow(result, page, text));
                        }
                    }
                });
            }
        }, "GeoSearch-sky").start();
    }

    // One result line, built by hand rather than with the adapter of the place search page:
    // that one carries mark creation and an order of its own, neither of which belongs here,
    // and a ListView inside this page's ScrollView would fight it for the scroll gesture.
    private View skyPlaceRow(final GeoSearch.Result result, final Dialog page, String typed) {

        final float density = _activity.getResources().getDisplayMetrics().density;

        // Nominatim does not always name a result -- an address has none -- and an empty line
        // would be untappable in practice. What was typed is then the best title available.
        final String name = ((result.getName() != null) && (result.getName().trim().length() > 0))
                ? result.getName().trim()
                : typed;

        LinearLayout row = new LinearLayout(_activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding((int) (2 * density), (int) (10 * density),
                (int) (2 * density), (int) (8 * density));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);

        TextView title = new TextView(_activity);
        title.setText(name);
        title.setTextSize(15.0f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(title);

        // The full address is what tells two places of the same name apart, and there are a
        // great many Saint-Pierre.
        TextView label = new TextView(_activity);
        label.setText(result.getLabel());
        label.setTextColor(0xFF666666);
        label.setTextSize(12.0f);
        row.addView(label);

        View rule = new View(_activity);
        rule.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) density)));
        rule.setBackgroundColor(0xFFDDDDDD);
        row.addView(rule);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSkyElsewhere(page, result.getLatitude(), result.getLongitude(), name, true);
            }
        });

        return row;
    }

    // Replaces the page rather than stacking another on top, as the tide page does.
    /** @param chosen as on the tide page: only a deliberate choice pins the page. */
    private void openSkyElsewhere(Dialog page, double latitude, double longitude, String name,
                                  boolean chosen) {

        if( chosen ) {
            _am.putSkyFollowsUser(_activity, name == null);
        }

        if( page != null ) {
            page.dismiss();
        }

        // No instant given: the new place opens on now, as the page does when entered
        // from the menu.
        showSkyAt(latitude, longitude, name, null);
    }

    // A null message clears the line, the results then speaking for themselves.
    private void showSkyPlaceStatus(LayoutPageSunmoonBinding binding, String message) {
        binding.tvSkyPlaceStatus.setText((message == null) ? "" : message);
        binding.tvSkyPlaceStatus.setVisibility((message == null) ? View.GONE : View.VISIBLE);
    }

    /* The clock tick: follows the mark on the curve, which the user may have moved. */
    private void skyRefresh(LayoutPageSunmoonBinding binding, AstroDay day,
                            TidePotential.Series series,
                            com.gb.mybuoy.astro.SkyTrack track) {

        skyAt(binding, day, series, track, binding.earthTide.centreUnixSeconds());
    }

    /* Everything that depends on an instant rather than on the day as a whole. */
    private void skyAt(LayoutPageSunmoonBinding binding, AstroDay day,
                       TidePotential.Series series,
                       com.gb.mybuoy.astro.SkyTrack track, long when) {


        // Taken at the instant, not at the middle of the day: scrubbing a month moves
        // the Moon through a whole cycle of phase.
        MoonPhase.Phase phase = MoonPhase.at(com.gb.mybuoy.astro.AstroTime.julianDateTt(when));

        double tilt = day.moonTiltRadians(when);

        binding.moonPhase.setPhase(phase.illuminatedFraction(), phase.waxing(), tilt);

        // Beside the disc, the two numbers that say which Moon this is: how far and how
        // old. Both follow the mark on the curve, like everything else up here.
        binding.tvMoonDistance.setText(String.format(java.util.Locale.UK, "%.0f km",
                com.gb.mybuoy.astro.MoonErfa.at(
                        com.gb.mybuoy.astro.AstroTime.julianDateTt(when)).distanceKm()));

        binding.tvMoonAge.setText(String.format(java.util.Locale.UK, "age %.1f d",
                phase.ageDays()));



        Topocentric.Horizontal sun = day.sunAt(when);
        Topocentric.Horizontal moon = day.moonAt(when);

        // Below the horizon a body keeps the bearing the user would point at through the
        // ground, and only its height is turned over. It therefore never leaves the map,
        // and the pull it is exerting stays legible: overhead and underfoot are the same
        // bulge, and they land the same distance from the middle.
        // Up or down is the rise and set rule, asked of the track so that one answer
        // serves the whole page: the colour of the body, the road under it drawn solid
        // or dotted, and which of the two is in front.
        boolean sunVisible = track.sunUp(when);
        boolean moonVisible = track.moonUp(when);

        // The height each body stands at, beside its two times: the map shows it by how
        // far in the body sits, and a figure says it exactly.
        binding.tvSunAngle.setText(skyAngle(sun.altitudeRadians()));
        binding.tvMoonAngle.setText(skyAngle(moon.altitudeRadians()));

        binding.skyView.setSky(
                sun.azimuthRadians(), Math.abs(sun.altitudeRadians()), sunVisible,
                moon.azimuthRadians(), Math.abs(moon.altitudeRadians()), moonVisible,
                phase.illuminatedFraction(), phase.waxing(), tilt);

        binding.sunDial.setSun(day.sunHourAngleRadians(when), sunVisible);

        // The roads are those of the passage live at this instant, not those of today:
        // once a body has set, the arc jumps forward to the rise that is coming. This has
        // to happen BEFORE the two times below are read, or the first drawing of the page
        // finds no arc at all and prints dashes.
        track.update(when);

        // Beside the sun, the one figure that belongs to the day as a whole rather than
        // to an instant: how long it lasts.
        long daylight = track.sunSetSeconds() - track.sunRiseSeconds();

        binding.tvDaylight.setText(
                ((track.sunRiseSeconds() == Long.MIN_VALUE)
                        || (track.sunSetSeconds() == Long.MIN_VALUE) || (daylight <= 0L))
                ? ""
                : String.format(java.util.Locale.UK, "daylight %dh%02d",
                        daylight / 3600L, (daylight % 3600L) / 60L));

        skyTimes(binding.tvSunNextRise, binding.tvSunLabelRise, binding.tvSunRise,
                binding.tvSunNextSet, binding.tvSunLabelSet, binding.tvSunSet,
                track.sunRiseSeconds(), track.sunSetSeconds(), when);

        skyTimes(binding.tvMoonNextRise, binding.tvMoonLabelRise, binding.tvMoonRise,
                binding.tvMoonNextSet, binding.tvMoonLabelSet, binding.tvMoonSet,
                track.moonRiseSeconds(), track.moonSetSeconds(), when);

        // The pull of each body at this instant, against the strongest of the month, so
        // the halos say the same thing whatever date the reader has dragged to.
        com.gb.mybuoy.astro.TidePotential.Effect pull =
                com.gb.mybuoy.astro.TidePotential.at(
                        com.gb.mybuoy.astro.AstroTime.julianDateTt(when), when,
                        Math.toRadians(_am.getLatitude()), Math.toRadians(_am.getLongitude()));

        // Measured against the most ONE body can do here, not against the peak of the two
        // together: otherwise neither halo could ever fill.
        double yardstick = com.gb.mybuoy.astro.TidePotential.strongestSingleMicroGal(
                Math.toRadians(_am.getLatitude()), day.moonDistanceKm());

        binding.skyView.setPull(pull.gravityMicroGal() - pull.moonShareMicroGal(),
                pull.moonShareMicroGal(), yardstick);

        // The flat gauge takes the same live arc as the map: the day while the Sun is up,
        // the night while it is down, and the meridian it passes in between.
        long[] span = track.sunSpan(sunVisible);

        if( span != null ) {
            binding.skyView.setSunSpan(span[0], span[1], span[2], when);
        }

        long[] moonStretch = track.moonSpan(moonVisible);

        if( moonStretch != null ) {
            binding.skyView.setMoonSpan(moonStretch[0], moonStretch[1], moonStretch[2]);
        }

        binding.skyView.setTracks(
                track.sunAbove(), track.sunBelow(),
                track.sunRiseAzimuth(sunVisible), track.sunSetAzimuth(sunVisible),
                track.moonAbove(), track.moonBelow(),
                track.moonRiseAzimuth(moonVisible), track.moonSetAzimuth(moonVisible));
    }


    private CharSequence skyTimes(String riseLabel, java.util.List<Long> rises,
                                  String setLabel, java.util.List<Long> sets,
                                  com.gb.mybuoy.astro.RiseSet.Window window,
                                  String alwaysUp, String alwaysDown) {

        if( window.neverCrossed() ) {
            return window.upThroughout() ? alwaysUp : alwaysDown;
        }

        String rise = rises.isEmpty() ? "--" : skyClock(rises.get(0));
        String set = sets.isEmpty() ? "--" : skyClock(sets.get(0));

        return riseLabel + " " + rise + "   " + setLabel + " " + set;
    }



    /*
     * The two times beside an emblem, in six cells: an arrow, a name and an hour, twice
     * over. The grid they sit in is what lines the hours up under one another; nothing
     * here pads a string or changes the face to get there.
     *
     * What has already happened is greyed and italic, what is still to come stands
     * upright, and the arrow picks out whichever of the two comes next. A glance then
     * says not only when the Sun sets but whether it already has.
     */
    private void skyTimes(TextView riseArrow, TextView riseLabel, TextView riseTime,
                          TextView setArrow, TextView setLabel, TextView setTime,
                          long riseSeconds, long setSeconds, long when) {

        boolean riseNext = (riseSeconds > when)
                && ((setSeconds <= when) || (riseSeconds < setSeconds));

        boolean setNext = (setSeconds > when)
                && ((riseSeconds <= when) || (setSeconds < riseSeconds));

        skyOneTime(riseArrow, riseLabel, riseTime, riseSeconds, when, riseNext);
        skyOneTime(setArrow, setLabel, setTime, setSeconds, when, setNext);
    }

    private void skyOneTime(TextView arrow, TextView label, TextView time,
                            long unixSeconds, long when, boolean next) {

        boolean known = unixSeconds != Long.MIN_VALUE;
        boolean past = known && (unixSeconds <= when);

        arrow.setText(next ? "\u2192" : "");

        time.setText(known ? skyClock(unixSeconds) : "--:--");

        int style = past ? android.graphics.Typeface.ITALIC
                : android.graphics.Typeface.NORMAL;

        int colour = past ? SKY_PAST : SKY_AHEAD;

        label.setTypeface(null, style);
        time.setTypeface(null, style);

        arrow.setTextColor(colour);
        label.setTextColor(colour);
        time.setTextColor(colour);
    }

    /** Behind us, and still to come: the second stands out, the first steps back. */
    private static final int SKY_PAST = 0xFF9AA8B0;
    private static final int SKY_AHEAD = 0xFF37474F;

    /* A height above the horizon, or below it, which is where the minus sign comes from. */
    private String skyAngle(double altitudeRadians) {
        return String.format(java.util.Locale.UK, "%.0f\u00b0", Math.toDegrees(altitudeRadians));
    }

    private String skyClock(long unixSeconds) {

        java.util.Calendar when = java.util.Calendar.getInstance();
        when.setTimeInMillis(unixSeconds * 1000L);

        return String.format(java.util.Locale.UK, "%02d:%02d",
                when.get(java.util.Calendar.HOUR_OF_DAY), when.get(java.util.Calendar.MINUTE));
    }

    // Tide page. The model costs seconds to build -- the chart datum alone walks a full
    // nodal cycle -- so the page opens on a waiting line and fills itself when the worker
    // comes back. Three states: no grid installed, preparing, ready.
    public void ShowTidesPage() {
        // Opened from the menu, so the page may re-route itself: a user who has read tides
        // somewhere before is better served by that place again than by a dead end.
        ShowTidesPage(null);
    }

    /**
     * @param atUnixSeconds the moment to open the curve on, or null for the present.
     *                      The sun and moon page passes its own reading time, so that
     *                      moving between the two never loses the reader's place.
     */
    public void ShowTidesPage(Long atUnixSeconds) {

        // Back to wherever it was left: a place chosen by hand stays chosen until the user
        // asks for his own position again.
        TidePlace kept = _am.getTideFollowsUser(_activity)
                ? null
                : _am.getTideLastPlace(_activity);

        if( kept != null ) {
            showTidesAt(kept.latitude(), kept.longitude(), kept.label(), true, atUnixSeconds);
            return;
        }

        showTidesAt(_am.getLatitude(), _am.getLongitude(), null, true, atUnixSeconds);
    }

    /**
     * The same page wherever the tide is read: under the user, or at a place picked from a
     * search. Only the heading differs, and the fact that a named place is remembered.
     *
     * @param placeName   the name the place was found under, or null for the user's own position
     * @param mayFallBack whether a position without data may be answered with the last place
     *                    that had some. True only when the page opens by itself: a user who
     *                    asked for this very position must be shown what it holds, or the two
     *                    places would hand the page back and forth for ever.
     */
    private void showTidesAt(final double latitude, final double longitude,
                             final String placeName, final boolean mayFallBack) {
        showTidesAt(latitude, longitude, placeName, mayFallBack, null);
    }

    private void showTidesAt(final double latitude, final double longitude,
                             final String placeName, final boolean mayFallBack,
                             final Long atUnixSeconds) {
        if( this._activity == null ) {
            return;
        }
        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        final LayoutPageTidesBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_tides, null, false);
        binding.setViewsController(this);

        final String gridPath = _am.getTidesGridPath();

        if( gridPath == null ) {
            binding.llNoTides.setVisibility(View.VISIBLE);

            final Dialog noGridPage = showFullscreenPage(binding.getRoot(), binding.btnBack);

            // Replace rather than stack: this page has nothing to say until the grid is
            // installed, and it would not notice if it were. Closing it means the download
            // screen falls back to the map, not to a tide page frozen on its old answer.
            binding.btnGoToMaps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if( noGridPage != null ) {
                        noGridPage.dismiss();
                    }
                    ShowMapsPage();
                }
            });

            return;
        }

        binding.tvTidesWait.setVisibility(View.VISIBLE);

        final Dialog page = showFullscreenPage(binding.getRoot(), binding.btnBack);

        final long now = System.currentTimeMillis() / 1000L;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                TideModel built = null;
                String failure = null;

                try {
                    Double known = _am.getTideDatum(_activity, gridPath, latitude, longitude);

                    built = new TideModel(gridPath, latitude, longitude, now,
                            known == null ? Double.NaN : known, _am.getTideReachKm());

                    // The datum belongs to the point the tide was actually read at, which is
                    // not always the one that was asked for: a place looked up by name is
                    // usually inland, and the model then slides to the nearest cell holding
                    // data. It is written under BOTH, because the next visit starts from the
                    // name again and knows nothing of the cell it will end up in. Keyed on the
                    // asked point alone the value would never be found, and the datum, which
                    // costs seconds to work out, would be computed afresh every single time.
                    if( known == null ) {
                        _am.putTideDatum(_activity, gridPath,
                                built.latitude(), built.longitude(), built.chartDatumMetres());

                        _am.putTideDatum(_activity, gridPath,
                                latitude, longitude, built.chartDatumMetres());
                    }

                    // Only a place that answered is worth offering again, so this is written
                    // once the model stands. The position kept is the one that was ASKED for,
                    // not the cell it was read from: asking again from the same point is what
                    // reproduces this page.
                    //
                    // Named places only. Where the user stands has a button of its own and is
                    // never out of reach, so recording it would buy nothing and would cost the
                    // one thing this is for: reading the tides at sea, then walking inland and
                    // still being able to get back to them.
                    if( placeName != null ) {
                        _am.putTideLastPlace(_activity, latitude, longitude, placeName);
                    }
                } catch (Throwable t) {
                    failure = t.getMessage();
                }

                final TideModel model = built;
                final String message = failure;

                _activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tidesReady(binding, page, model, message,
                                latitude, longitude, placeName, mayFallBack, atUnixSeconds);
                    }
                });
            }
        }, "TideModel");

        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    // Handler that keeps the clock and the "now" line of the tide page current.
    private android.os.Handler _tideClock = null;

    // How often the tide page catches up with the clock.
    private static final long TIDE_TICK_MS = 30000L;

    private void tidesReady(final LayoutPageTidesBinding binding, final Dialog page,
                            final TideModel model, String failure,
                            final double latitude, final double longitude,
                            final String placeName, boolean mayFallBack,
                            final Long atUnixSeconds) {

        binding.tvTidesWait.setVisibility(View.GONE);

        if( model == null ) {
            final TidePlace last = _am.getTideLastPlace(_activity);

            // Nothing here, but somewhere else answered once: open on it rather than on a
            // page that only explains why it is empty. The explanation is still one button
            // away, and it is the first visit that needs it, not the hundredth.
            if( mayFallBack && (last != null) ) {
                openTidesElsewhere(page, last.latitude(), last.longitude(), last.label(), false);
                return;
            }

            tidesPicker(binding, page, latitude, longitude, placeName, true);
            return;
        }

        // The place is always named, the user's own position included: the readings carry no
        // position of their own, and one page looks exactly like the other.
        binding.tvPlace.setText((placeName == null) ? "Current position" : placeName);
        binding.rowPlace.setVisibility(View.VISIBLE);

        // From a named place, one tap goes back under the user's feet; from the user's own
        // position there is nowhere obvious to go, so the button opens the chooser instead.
        binding.btnPlaceSwitch.setText((placeName == null) ? "Other place" : "Current position");

        binding.btnPlaceSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( placeName == null ) {
                    tidesPicker(binding, page, latitude, longitude, null, false);
                    return;
                }
                openTidesElsewhere(page, _am.getLatitude(), _am.getLongitude(), null, true);
            }
        });

        binding.llTides.setVisibility(View.VISIBLE);

        tideClocksForOrientation(binding);

        // The activity handles rotation itself, so this page is not rebuilt when the screen
        // turns: the choice has to be made again, or the wide clock stays and crushes the
        // text column back in portrait.
        binding.llTides.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                tideClocksForOrientation(binding);
            }
        });

        long now = System.currentTimeMillis() / 1000L;
        binding.tideCurve.setModel(model, now);

        if( atUnixSeconds != null ) {
            binding.tideCurve.showAt(atUnixSeconds);
        }

        // The title is the way across to the other page, on the same instant.
        binding.tvTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long reading = binding.tideCurve.centreUnixSeconds();

                if( page != null ) {
                    page.dismiss();
                }

                ShowSunMoonPage(reading);
            }
        });

        tidesRefresh(binding, model);

        // One tick a second: the clock is a line of text and the "now" line is a single
        // stroke, so the cost is nil and the reading stays live.
        // Twice a minute, on the half minute: the clock on the curve, the countdowns and the
        // "now" line all move together, and a minute change is on screen within thirty
        // seconds of happening. The work is a few strings and one stroke, so the cost is nil.
        final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());

        _tideClock = clock;

        clock.postDelayed(new Runnable() {
            @Override
            public void run() {
                // As on the sky page: a loop whose field has been taken over by another
                // page simply stops, and leaves that field alone.
                if( (_tideClock != clock) || (binding.llTides.getWindowToken() == null) ) {
                    return;
                }
                tidesRefresh(binding, model);

                clock.postDelayed(this, TIDE_TICK_MS - (System.currentTimeMillis() % TIDE_TICK_MS));
            }
        }, TIDE_TICK_MS - (System.currentTimeMillis() % TIDE_TICK_MS));
    }

    // The chooser. One block for two situations, because they ask the same question: there
    // is nothing to read HERE -- either because the model holds nothing within reach, or
    // because the user asked to look elsewhere -- so where instead?
    //
    // The only difference is the paragraph explaining the hole in the data, which belongs to
    // the first case alone. A user who already has tides on screen does not need to be told
    // why he might not have had them.
    //
    // @param latitude/longitude  the position the page is currently on, excluded from the
    //                            choices offered: no button may lead back where we already are
    // @param placeName           its name, or null when it is the user's own position
    // @param explainNoData       whether that position came back empty
    private void tidesPicker(final LayoutPageTidesBinding binding, final Dialog page,
                             final double latitude, final double longitude,
                             final String placeName, final boolean explainNoData) {

        // Opened over a page that was showing tides: the readings go, and the handler that
        // kept them current goes with them.
        binding.llTides.setVisibility(View.GONE);
        _tideClock = null;

        binding.rowPlace.setVisibility(View.GONE);
        binding.llNoHere.setVisibility(View.VISIBLE);

        binding.tvNoHere.setVisibility(explainNoData ? View.VISIBLE : View.GONE);

        if( explainNoData ) {
            binding.tvNoHere.setText("No tide data within " + _am.getTideReachKm() + " km of "
                    + ((placeName == null) ? "your current position" : placeName) + "."
                    + System.getProperty("line.separator")
                    + "The model is built from satellite altimetry, which cannot measure close "
                    + "to land, so it covers nothing inland and little inside narrow bays. "
                    + "Raising the tide search distance in the developer parameters may reach it.");
        }

        // Where the user stands, offered whenever it is not already what the page is on.
        boolean offerHere = (placeName != null);

        binding.btnCurrentPlace.setVisibility(offerHere ? View.VISIBLE : View.GONE);

        if( offerHere ) {
            binding.btnCurrentPlace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openTidesElsewhere(page, _am.getLatitude(), _am.getLongitude(), null, true);
                }
            });
        }

        // The last place that answered, for the same reason and under the same condition. Only
        // places that answered are ever written, so it cannot be a place known to be empty.
        final TidePlace last = _am.getTideLastPlace(_activity);

        boolean offerLast = (last != null) && !samePlace(last, latitude, longitude);

        binding.btnLastPlace.setVisibility(offerLast ? View.VISIBLE : View.GONE);

        if( offerLast ) {
            binding.btnLastPlace.setText("Tides at " + last.label());

            binding.btnLastPlace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openTidesElsewhere(page, last.latitude(), last.longitude(), last.label(), true);
                }
            });
        }

        // "Or" only makes sense after something to be an alternative to.
        binding.tvLookUp.setText((offerHere || offerLast)
                ? "Or look up a place: a town, a harbour, an island."
                : "Look up a place: a town, a harbour, an island.");

        // One search at a time, as on the place search page: an answer whose query is no longer
        // the current one is dropped, or two searches launched one after the other both pour
        // their results into the same column.
        final GeoQuery[] current = new GeoQuery[1];

        binding.btnTidePlaceSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tidesPlaceSearch(binding, page, current);
            }
        });

        // The Search key of the keyboard does what the button does.
        binding.etTidePlace.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // A hardware keyboard (or adb) comes through here twice, on the press and on
                // the release. Without this filter one keystroke starts two searches.
                if( (event != null) && (event.getAction() != KeyEvent.ACTION_DOWN) ) {
                    return true;
                }

                if( (actionId == EditorInfo.IME_ACTION_SEARCH) || (actionId == EditorInfo.IME_NULL) ) {
                    tidesPlaceSearch(binding, page, current);
                    return true;
                }
                return false;
            }
        });
    }

    // Two positions are the same place when they fall in the same tide cell -- a quarter of a
    // degree apart is the same reading, and comparing doubles for equality would miss it.
    private static boolean samePlace(TidePlace place, double latitude, double longitude) {
        return (Math.abs(place.latitude() - latitude) < 0.0625)
                && (Math.abs(place.longitude() - longitude) < 0.0625);
    }

    private void tidesPlaceSearch(final LayoutPageTidesBinding binding, final Dialog page,
                                  final GeoQuery[] current) {

        final String text = binding.etTidePlace.getText().toString().trim();

        if( text.length() == 0 ) {
            return;
        }

        hideKeyboard(binding.etTidePlace);

        binding.llTidePlaceResults.removeAllViews();
        showTidePlaceStatus(binding, "Searching...");

        if( current[0] != null ) {
            current[0].cancel();
        }

        // No viewbox here, unlike the place search page: the whole point is to reach water
        // that is NOT around the user, and a box centred on them would hide it.
        final GeoQuery query = new GeoQuery(text, null).start();
        current[0] = query;

        new Thread(new Runnable() {
            @Override
            public void run() {

                final List<GeoSearch.Result> results = query.getResult(SEARCH_TIMEOUT_MS);
                final String error = query.getError();
                final Activity activity = _activity;

                if( activity == null ) {
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // A more recent search has taken over: this answer is stale.
                        if( current[0] != query ) {
                            return;
                        }

                        // The tides themselves work offline, but looking a place up does not.
                        // Say which of the two failed rather than show an empty column.
                        if( results == null ) {
                            showTidePlaceStatus(binding, (error != null)
                                    ? ("Search failed: " + error)
                                    : "Search timed out, try again");
                            return;
                        }

                        if( results.isEmpty() ) {
                            showTidePlaceStatus(binding, "No place found for \"" + text + "\"");
                            return;
                        }

                        showTidePlaceStatus(binding, results.size()
                                + ((results.size() == 1) ? " place found. " : " places found. ")
                                + "Tap one to read its tides.");

                        for( GeoSearch.Result result : results ) {
                            binding.llTidePlaceResults.addView(tidePlaceRow(result, page, text));
                        }
                    }
                });
            }
        }, "GeoSearch-tides").start();
    }

    // One result line, built by hand rather than with the adapter of the place search page:
    // that one carries mark creation and an order of its own, neither of which belongs here,
    // and a ListView inside this page's ScrollView would fight it for the scroll gesture.
    private View tidePlaceRow(final GeoSearch.Result result, final Dialog page, String typed) {

        final float density = _activity.getResources().getDisplayMetrics().density;

        // Nominatim does not always name a result -- an address has none -- and an empty line
        // would be untappable in practice. What was typed is then the best title available.
        final String name = ((result.getName() != null) && (result.getName().trim().length() > 0))
                ? result.getName().trim()
                : typed;

        LinearLayout row = new LinearLayout(_activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding((int) (2 * density), (int) (10 * density),
                (int) (2 * density), (int) (8 * density));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);

        TextView title = new TextView(_activity);
        title.setText(name);
        title.setTextSize(15.0f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(title);

        // The full address is what tells two places of the same name apart, and there are a
        // great many Saint-Pierre.
        TextView label = new TextView(_activity);
        label.setText(result.getLabel());
        label.setTextColor(0xFF666666);
        label.setTextSize(12.0f);
        row.addView(label);

        View rule = new View(_activity);
        rule.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) density)));
        rule.setBackgroundColor(0xFFDDDDDD);
        row.addView(rule);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openTidesElsewhere(page, result.getLatitude(), result.getLongitude(), name, true);
            }
        });

        return row;
    }

    // Replaces the page rather than stacking another on top: the tide page is read from the
    // top down, and a pile of them would have the close button walk back through every place
    // ever looked at.
    /**
     * @param chosen true when the user pressed something to get here. Only then is the page
     *               pinned: the automatic re-route of a position without data is the page
     *               making the best of it, not a choice, and must not outlive the visit.
     */
    private void openTidesElsewhere(Dialog page, double latitude, double longitude, String name,
                                    boolean chosen) {

        if( chosen ) {
            _am.putTideFollowsUser(_activity, name == null);
        }

        if( page != null ) {
            page.dismiss();
        }

        // Never with the fallback: this is the user's own choice of place, and he is to be
        // shown what it holds -- including nothing at all.
        showTidesAt(latitude, longitude, name, false);
    }

    // A null message clears the line, the results then speaking for themselves.
    private void showTidePlaceStatus(LayoutPageTidesBinding binding, String message) {
        binding.tvTidePlaceStatus.setText((message == null) ? "" : message);
        binding.tvTidePlaceStatus.setVisibility((message == null) ? View.GONE : View.VISIBLE);
    }

    // Same grid either way: the wide clock has a column of its own only when the screen is
    // on its side. Exactly one of the two is ever shown. Setting a visibility that is
    // already set does nothing, so calling this from a layout pass cannot loop.
    private void tideClocksForOrientation(LayoutPageTidesBinding binding) {

        boolean landscape = _activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        binding.tideClockWide.setVisibility(landscape ? View.VISIBLE : View.GONE);
        binding.tideClockNarrow.setVisibility(landscape ? View.GONE : View.VISIBLE);
    }

    private void tidesRefresh(LayoutPageTidesBinding binding, TideModel model) {

        long now = System.currentTimeMillis() / 1000L;

        java.text.SimpleDateFormat dayFormat =
                new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK);

        binding.tvNow.setText(dayFormat.format(new java.util.Date(now * 1000L)));

        binding.tvNowHeight.setText(model.awayKm() < 1.0
                ? String.format(java.util.Locale.UK, "Now: height %.2f m", model.heightAt(now))
                : String.format(java.util.Locale.UK, "Now: height %.2f m, read %.0f km away",
                        model.heightAt(now), model.awayKm()));

        boolean fishing = model.isFishingTime(now);
        binding.ivFishing.setVisibility(fishing ? View.VISIBLE : View.GONE);
        binding.tvFishing.setVisibility(fishing ? View.VISIBLE : View.GONE);

        binding.ivTrend.setImageResource(model.isRising(now)
                ? R.drawable.ic_tide_rising
                : R.drawable.ic_tide_falling);

        java.util.List<TideModel.Tide> next = model.nextTides(now, 2);

        binding.tvNext.setText(next.size() > 0
                ? tideSentence(next.get(0), now, true)
                : "No further tide in the prepared window.");

        binding.tvThen.setText(next.size() > 1
                ? tideSentence(next.get(1), now, false)
                : "");

        // Left alone, the chart rides the present, as the sun and moon one does; once the
        // reader has dragged it somewhere, only the line marking the present advances.
        if( binding.tideCurve.followsNow() ) {
            binding.tideCurve.showNow(now);
        } else {
            binding.tideCurve.setNow(now);
        }

        // The clock reads the two tides the moment sits between, so it is right wherever the
        // user is, whatever the local tidal regime.
        TideModel.Tide previous = model.previousTide(now);

        if( previous != null && next.size() > 0 ) {
            // Both are fed: the hidden one costs nothing and is right the moment a rotation
            // brings it on screen.
            binding.tideClockNarrow.setPhase(previous.unixSeconds, previous.high,
                    next.get(0).unixSeconds, now);
            binding.tideClockWide.setPhase(previous.unixSeconds, previous.high,
                    next.get(0).unixSeconds, now);
        }
    }

    // Clock times read "18:23"; a wait reads "1h05" or "45 min". Two different things, so
    // they are not written the same way.
    private CharSequence tideSentence(TideModel.Tide tide, long now, boolean first) {

        java.util.Calendar today = java.util.Calendar.getInstance();
        today.setTimeInMillis(now * 1000L);

        java.util.Calendar when = java.util.Calendar.getInstance();
        when.setTimeInMillis(tide.unixSeconds * 1000L);

        String day = (today.get(java.util.Calendar.DAY_OF_YEAR) == when.get(java.util.Calendar.DAY_OF_YEAR))
                ? "today" : "tomorrow";

        long minutes = (tide.unixSeconds - now) / 60L;
        String left = (minutes >= 60L)
                ? String.format(java.util.Locale.UK, "%dh%02d", minutes / 60L, minutes % 60L)
                : String.format(java.util.Locale.UK, "%d min", Math.max(0L, minutes));

        String head = String.format(java.util.Locale.UK, "%s%s %02d:%02d %s",
                first ? "" : "then ",
                tide.high ? "high water" : "low water",
                when.get(java.util.Calendar.HOUR_OF_DAY), when.get(java.util.Calendar.MINUTE),
                day);

        // The height starts the second line behind an arrow rather than a dash: a dash in
        // front of a number reads as a minus sign, and a tide height is never negative here.
        String tail = String.format(java.util.Locale.UK, "\u2192 %.2f m in %s",
                tide.heightMetres, left);

        android.text.SpannableString text =
                new android.text.SpannableString(head + "\n" + tail);

        text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                head.length() + 1, text.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return text;
    }

    // #33 : page "Parameters" (vide pour l'instant, cf. tache dediee).
    public void ShowParametersPage() {
        if( this._activity == null ) {
            return;
        }
        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageParametersBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_parameters, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);
        showFullscreenPage(binding.getRoot(), binding.btnBack);
    }

    // Section "Avatar" de la page Parameters : choix du sprite d'une categorie (AvatarCatalog.CAT_*).
    // Simple Dialog ouvert PAR-DESSUS le dialog plein ecran de la page Parameters, qui reste dessous
    // et reapparait a la fermeture ; les deux sont suivis par trackDialog.
    // #81 : choix du son d'une alarme (0 radar, 1 mark perso, 2 zone). Meme forme que le selecteur
    // de sprites, avec en plus un bouton d'ecoute par ligne : on essaie le son avant de le choisir,
    // sinon il faudrait valider puis attendre une vraie alarme pour savoir ce qu'on a pris.
    // #81 : ecoute d'un son dans l'ecran de choix, par MediaPlayer et non par le SoundPool du jeu,
    // pour deux raisons. SoundPool.load() est ASYNCHRONE : jouer dans la foulee ne donne rien tant
    // que l'echantillon n'est pas pret, ce qui rendait un clic sur deux muet. Et SoundPool ne dit
    // pas quand un son se termine, alors qu'il faut rendre au bouton sa fleche a ce moment-la.
    private android.media.MediaPlayer _preview = null;

    // #81 : liste et adaptateur de l'ecran de choix en cours, pour le rafraichir au retour du
    // selecteur de fichiers. L'ecran reste ouvert pendant que le systeme prend la main.
    private Object[] _sonsARafraichir = null;

    private void rafraichirSons(List<AlarmSoundCatalog.Entry> entries,
                                MyListAdapter<AlarmSoundCatalog.Entry> adapter) {
        entries.clear();
        entries.addAll(AlarmSoundCatalog.list(this._activity));
        adapter.notifyDataSetChanged();
    }

    /** #81 : appele par MainActivity quand l'utilisateur vient d'ajouter un son. */
    @SuppressWarnings("unchecked")
    public void onAlarmSoundAdded() {

        if( _sonsARafraichir == null )
            return;

        rafraichirSons((List<AlarmSoundCatalog.Entry>) _sonsARafraichir[0],
                       (MyListAdapter<AlarmSoundCatalog.Entry>) _sonsARafraichir[1]);
    }

    private void stopPreview() {

        if( _preview == null )
            return;

        try {
            _preview.stop();
        } catch( IllegalStateException ignored ) {
        }

        _preview.release();
        _preview = null;
    }

    private void startPreview(AlarmSoundCatalog.Entry e, final Runnable surFin) {

        stopPreview();

        if( (e == null) || (this._activity == null) )
            return;

        if( e.path != null ) {                       // son ajoute par l'utilisateur
            _preview = new android.media.MediaPlayer();
            try {
                _preview.setDataSource(e.path);
                _preview.prepare();
            } catch( Exception ex ) {
                GBLog.e(getClass().getName(), "lecture impossible : " + ex);
                _preview.release();
                _preview = null;
                return;
            }
        } else if( e.resId != 0 ) {                  // son livre avec l'application
            _preview = android.media.MediaPlayer.create(this._activity, e.resId);
        } else {
            return;                                  // "No sound" : rien a ecouter
        }

        if( _preview == null )
            return;

        _preview.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(android.media.MediaPlayer mp) {
                stopPreview();
                if( surFin != null )
                    surFin.run();
            }
        });

        _preview.start();
    }

    public void ShowAlarmSoundPicker(final int quoi) {

        if( this._activity == null )
            return;

        final String[] TITRES = { "Speed camera sound", "Personal mark sound", "Watch zone sound" };

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutAlarmSoundPickerBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.layout_alarm_sound_picker, null, false);
        binding.setTitle(TITRES[(quoi < 0 || quoi > 2) ? 0 : quoi]);

        final List<AlarmSoundCatalog.Entry> entries = new ArrayList<>(AlarmSoundCatalog.list(this._activity));

        // Ligne en cours d'ecoute, ou -1. Elle porte une icone d'ARRET au lieu de la fleche : sans
        // ce reperage on entend un son sans savoir lequel joue, ce qui rend l'essai inutile.
        final int[] enEcoute = { -1 };

        final MyListAdapter<AlarmSoundCatalog.Entry> adapter =
                new MyListAdapter<AlarmSoundCatalog.Entry>(
                        this._activity, R.layout.layout_alarm_sound_item, BR.sound, entries) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {

                View v = super.getView(position, convertView, parent);
                final int pos = position;
                final AlarmSoundCatalog.Entry e = entries.get(position);
                android.widget.ImageButton play = v.findViewById(R.id.btnPlay);

                if( play != null ) {

                    play.setImageResource(enEcoute[0] == position
                            ? android.R.drawable.ic_media_pause
                            : android.R.drawable.ic_media_play);

                    play.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {

                            if( enEcoute[0] == pos ) {          // deuxieme appui : on arrete
                                stopPreview();
                                enEcoute[0] = -1;
                            } else {
                                enEcoute[0] = pos;
                                // A la fin du son, le bouton doit redevenir une fleche : c'est le
                                // MediaPlayer qui nous le dit, SoundPool ne sait pas le faire.
                                startPreview(e, new Runnable() {
                                    @Override public void run() {
                                        if( enEcoute[0] == pos ) {
                                            enEcoute[0] = -1;
                                            notifyDataSetChanged();
                                        }
                                    }
                                });
                            }

                            notifyDataSetChanged();
                        }
                    });
                }

                return v;
            }
        };

        binding.lvSounds.setAdapter(adapter);

        final Dialog dialog = new Dialog(this._activity);

        binding.lvSounds.setOnItemClickListener((parent, view, position, id) -> {
            _am.setAlarmSound(quoi, entries.get(position).name);
            dialog.dismiss();
        });

        // Appui long : retirer un son AJOUTE. Les sons livres avec l'application ne s'effacent pas.
        binding.lvSounds.setOnItemLongClickListener((parent, view, position, id) -> {

            final AlarmSoundCatalog.Entry e = entries.get(position);

            if( e.kind != AlarmSoundCatalog.KIND_CUSTOM )
                return false;

            stopPreview();

            new android.app.AlertDialog.Builder(_activity)
                    .setTitle("Remove this sound?")
                    .setMessage(e.label)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, which) -> {
                        AlarmSoundCatalog.removeUserSound(_activity, e.name);
                        // Une alarme qui pointait dessus retombe sur le silence, pas sur un son mort.
                        for( int q = 0; q < 3; q++ ) {
                            if( e.name.equals(_am.getAlarmSound(q)) )
                                _am.setAlarmSound(q, AlarmSoundCatalog.SILENCE);
                        }
                        rafraichirSons(entries, adapter);
                    })
                    .show();

            return true;
        });

        binding.btnAddSound.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                stopPreview();
                _sonsARafraichir = new Object[]{ entries, adapter };
                if( _activity instanceof MainActivity )
                    ((MainActivity) _activity).pickAlarmSound();
            }
        });

        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });

        dialog.setContentView(binding.getRoot());
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        binding.executePendingBindings();

        // Retenir un son ou refermer arrete l'essai en cours : on ne sort jamais de cet ecran avec
        // une alarme qui continue de sonner dans le vide. Le menage passe OBLIGATOIREMENT par
        // trackDialog : un Dialog n'accepte qu'un seul OnDismissListener, et trackDialog pose le
        // sien. En poser un autre a cote revient a se le faire ecraser sans rien voir.
        trackDialog(dialog, new Runnable() {
            @Override public void run() { stopPreview(); }
        });

        dialog.show();
    }

    public void ShowAvatarPicker(final int category) {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutAvatarPickerBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_avatar_picker, null, false);
        binding.setTitle(AvatarCatalog.title(category));

        // Sprites de la categorie, decouverts par enumeration de R.drawable : deposer un PNG
        // correctement prefixe suffit a le faire apparaitre ici. Copie de la liste : l'adaptateur la
        // garde par reference et ne doit pas pouvoir toucher au catalogue.
        final List<AvatarCatalog.Entry> entries = new ArrayList<>(AvatarCatalog.list(category));
        AvatarCatalog.loadPreviews(this._activity.getResources(), entries, _am.getLowResourceMode());

        binding.lvAvatars.setAdapter(new MyListAdapter<AvatarCatalog.Entry>(
                this._activity, R.layout.layout_avatar_item, BR.avatar, entries));

        // Categorie encore vide : on l'explique au lieu d'afficher une liste vide.
        binding.tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        final Dialog dialog = new Dialog(this._activity);

        binding.lvAvatars.setOnItemClickListener((parent, view, position, id) -> {
            _am.setAvatarName(category, entries.get(position).name);
            dialog.dismiss();
        });

        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.setContentView(binding.getRoot());
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);

        // A la fermeture : arreter les animations et lacher leurs bitmaps (une douzaine par sprite).
        trackDialog(dialog, new Runnable() {
            @Override
            public void run() {
                for( AvatarCatalog.Entry entry : entries ) {
                    if( entry.preview instanceof AnimationDrawable )
                        ((AnimationDrawable) entry.preview).stop();
                    entry.preview = null;
                }
            }
        });
        dialog.show();
    }


    // #54 : page de choix du compas, ouverte depuis la section "Magnetic heading" de la page
    // Parameters. Un tap sur une ligne choisit ce compas et referme la page ; le bouton Edit de la
    // ligne, comme le bouton New du pied de page, ouvre la page de configuration par-dessus.
    private MyListAdapter<Compass> _compassAdapter = null;

    public void ShowCompassList() {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageCompassesBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_compasses, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);

        _compassAdapter = new MyListAdapter<Compass>(this._activity, R.layout.layout_compass_item, BR.compass, _am.getCompasses());
        binding.lvCompasses.setAdapter(_compassAdapter);

        final Dialog dialog = showFullscreenPage(binding.getRoot(), binding.btnBack, new Runnable() {
            @Override
            public void run() {
                _compassAdapter = null;
            }
        });

        if( dialog == null ) {
            return;
        }

        binding.lvCompasses.setOnItemClickListener((parent, view, position, id) -> {
            _am.setSelectedCompassIndex(position);
            dialog.dismiss();
        });

        binding.tvNone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _am.setSelectedCompassIndex(-1);
                dialog.dismiss();
            }
        });

        binding.btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ShowCompassConfig(_am.addCompass());
            }
        });
    }

    // --- helper du serveur de donnees cartographiques ------------------------------------------
    //
    // Il ne remplace pas la saisie libre de la page Parameters : il ECRIT dans ce meme champ. Le
    // premier serveur de la liste est celui par defaut, donc la remise a zero vit ici aussi.

    private MyListAdapter<OverpassServer> _overpassAdapter = null;
    private java.util.List<OverpassServer> _overpassServers = null;

    // Quel essai a le droit d'ecrire dans le pied de page. Un essai lance sur un serveur muet
    // tient vingt-cinq secondes, pendant lesquelles on en choisit un autre et on retape Test :
    // sans ce compteur, le vieil essai revenait ensuite ecraser le resultat du nouveau, et on
    // lisait "repond en 25 s" une seconde apres avoir appuye. On ne peut pas interrompre un appel
    // HTTP bloquant ; on peut refuser ce qu'il rapporte.
    private int _overpassTry = 0;

    // Le pied de page dit de quoi il parle par sa couleur : noir ce qui est en cours, vert ce qui
    // repond vite, orange ce qui repond mais se fait attendre, rouge ce qui ne va pas.
    private static final int SAY_PLAIN = 0xFF222222;
    private static final int SAY_GOOD  = 0xFF2E7D32;
    private static final int SAY_SLOW  = 0xFFE65100;
    private static final int SAY_BAD   = 0xFFB71C1C;

    // Au-dela, un serveur repond mais fera attendre a chaque chunk.
    private static final long SAY_SLOW_MS = 2000;

    public void ShowOverpassPicker() {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageOverpassBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_overpass, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);

        _overpassServers = OverpassServer.all();
        _overpassAdapter = new MyListAdapter<OverpassServer>(this._activity, R.layout.layout_overpass_item, BR.server, _overpassServers);
        binding.lvServers.setAdapter(_overpassAdapter);

        final Dialog dialog = showFullscreenPage(binding.getRoot(), binding.btnBack, new Runnable() {
            @Override
            public void run() {
                _overpassAdapter = null;
                _overpassServers = null;
            }
        });

        if( dialog == null ) {
            return;
        }

        // Choisir une ligne ecrit son adresse dans le reglage. La page RESTE ouverte : on vient
        // souvent d'en choisir un pour le tester dans la foulee.
        binding.lvServers.setOnItemClickListener((parent, view, position, id) -> {

            OverpassServer server = _overpassServers.get(position);

            if( server.getNeedsKey() && !server.getHasKey() ) {
                overpassSays(binding, server, "This server needs a key. Tap Key on its line, or get one here.");
                return;
            }

            _am.setOverpassUrl(server.getAddress());

            // Ce qui tourne encore parle d'un autre serveur.
            _overpassTry++;

            binding.tvResult.setOnClickListener(null);

            overpassRefresh(binding, null);
        });

        binding.btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testOverpass(binding);
            }
        });
    }

    // Reaffiche les lignes : "In use" a bouge, ou une cle vient d'etre saisie.
    private void overpassRefresh(LayoutPageOverpassBinding binding, String message) {
        overpassRefresh(binding, message, SAY_BAD);
    }

    private void overpassRefresh(LayoutPageOverpassBinding binding, String message, int colour) {

        if( _overpassServers != null ) {
            for( OverpassServer s : _overpassServers )
                s.refresh();
        }

        if( _overpassAdapter != null )
            _overpassAdapter.notifyDataSetChanged();

        if( binding == null )
            return;

        if( (message == null) || message.isEmpty() ) {
            binding.tvResult.setVisibility(View.GONE);
            return;
        }

        binding.tvResult.setTextColor(colour);
        binding.tvResult.setText(message);
        binding.tvResult.setVisibility(View.VISIBLE);
    }

    // Le pied de page en rouge, et un tap dessus mene au site ou l'on obtient la cle.
    private void overpassSays(LayoutPageOverpassBinding binding, final OverpassServer server, String message) {

        overpassRefresh(binding, message);

        binding.tvResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( (server != null) && (server.getSignup() != null) )
                    OpenUrl(server.getSignup());
            }
        });
    }

    /**
     * Essaie le serveur en cours et dit ce qu'il repond.
     *
     * Sur un thread : le reseau sur le thread principal est interdit, et c'est justement quand le
     * serveur ne repond pas qu'on a besoin de l'ecran.
     */
    private void testOverpass(final LayoutPageOverpassBinding binding) {

        final String base = _am.getOverpassUrl();

        if( (base == null) || base.isEmpty() ) {
            overpassRefresh(binding, "No server address to test.");
            return;
        }

        // Une adresse a cle encore trouee : inutile d'appeler, on sait deja ce qui manque.
        if( base.contains("{key}") ) {

            OverpassServer waiting = null;

            if( _overpassServers != null ) {
                for( OverpassServer s : _overpassServers ) {
                    if( base.equals(s.getAddress()) )
                        waiting = s;
                }
            }

            overpassSays(binding, waiting, "No API key yet for this server. Tap Key on its line, or tap here to get one.");
            return;
        }

        binding.tvResult.setOnClickListener(null);

        overpassRefresh(binding, "Testing " + base + " ...", SAY_PLAIN);

        final Activity activity = this._activity;

        _overpassTry++;

        final int mine = _overpassTry;

        new Thread(new Runnable() {
            @Override
            public void run() {

                final Chunk.OverpassTry said = Chunk.tryOverpass(base);

                if( activity == null )
                    return;

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // Un essai que l'on a cesse d'attendre ne parle plus.
                        if( mine != _overpassTry )
                            return;

                        int colour = SAY_BAD;

                        if( said.ok )
                            colour = (said.ms <= SAY_SLOW_MS) ? SAY_GOOD : SAY_SLOW;

                        overpassRefresh(binding, said.said, colour);
                    }
                });
            }
        }).start();
    }

    /**
     * Bouton Key d'une ligne. Statique pour la meme raison que EditCompass : la ligne ne porte
     * qu'une variable de binding, donc le layout appelle l'instance a travers la classe.
     *
     * Croix en haut a droite pour sortir sans rien changer, SAVE en bas a droite : la grammaire
     * des autres boites de l'application. Les deux liens sont au-dessus du champ, parce qu'on va
     * les lire AVANT d'avoir quelque chose a taper.
     */
    public static void EditOverpassKey(final OverpassServer server) {

        final ViewsController vc = _instance;

        if( (vc == null) || (vc._activity == null) || (server == null) ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                vc._activity.getSystemService(vc._activity.LAYOUT_INFLATER_SERVICE);

        final View box = inflater.inflate(R.layout.layout_overpass_key, null);

        ((TextView) box.findViewById(R.id.tvKeyTitle)).setText(server.getName());

        final EditText input = (EditText) box.findViewById(R.id.etKey);
        input.setText(AppManager.getInstance().getOverpassKey(server.getId()));

        overpassLink(vc, box.findViewById(R.id.tvKeyDoc), server.getDoc());
        overpassLink(vc, box.findViewById(R.id.tvKeyPage), server.getPage());

        final AlertDialog dialog = new AlertDialog.Builder(vc._activity).setView(box).create();

        box.findViewById(R.id.btnKeyClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        box.findViewById(R.id.btnKeySave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                AppManager am = AppManager.getInstance();

                am.setOverpassKey(server.getId(), input.getText().toString());

                // Deja en service : l'adresse enregistree porte l'ancienne cle, ou le trou laisse
                // par son absence. On la reecrit.
                if( server.getInUse() || am.getOverpassUrl().contains("{key}") )
                    am.setOverpassUrl(server.getAddress());

                server.refresh();

                if( vc._overpassAdapter != null )
                    vc._overpassAdapter.notifyDataSetChanged();

                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // Une ligne de lien de cette boite. Sans adresse elle disparait : une adresse qu'on n'a pas
    // vaut mieux qu'une ligne qui ne mene nulle part.
    private static void overpassLink(final ViewsController vc, View view, final String url) {

        if( view == null )
            return;

        if( (url == null) || url.isEmpty() ) {
            view.setVisibility(View.GONE);
            return;
        }

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vc.OpenUrl(url);
            }
        });
    }

    // Bouton Edit d'une ligne de la liste. Statique parce que MyListAdapter ne pose qu'une seule
    // variable de binding sur la ligne (le compas lui-meme) : le layout appelle donc l'instance.
    public static void EditCompass(Compass compass) {
        ViewsController vc = _instance;

        if( vc == null || compass == null ) {
            return;
        }

        vc.ShowCompassConfig(AppManager.getInstance().indexOfCompass(compass));
    }

    // #54 : page de configuration d'un compas, ouverte par-dessus la liste. A sa fermeture on relit
    // la liste : un compas a pu y etre renomme, clone ou supprime.
    public void ShowCompassConfig(final int index) {
        if( this._activity == null || index < 0 ) {
            return;
        }

        _am.beginCompassAdjust(index);

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageCompassConfigBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_compass_config, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);

        final Dialog dialog = showFullscreenPage(binding.getRoot(), binding.btnBack, new Runnable() {
            @Override
            public void run() {
                _am.endCompassAdjust();
                refreshCompassList();
            }
        });

        if( dialog == null ) {
            return;
        }

        // Cloner : on bascule le reglage sur la copie, la page reste ouverte sur elle.
        binding.btnClone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int clone = _am.cloneCompass(_am.getAdjustCompassIndex());

                if( clone >= 0 ) {
                    _am.beginCompassAdjust(clone);
                    notifyInfo("Compass cloned");
                }
            }
        });

        binding.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(_activity)
                        .setTitle("Delete compass")
                        .setMessage("Delete this compass and its deviation card?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, which) -> {
                            _am.removeCompass(_am.getAdjustCompassIndex());
                            dialog.dismiss();
                        })
                        .show();
            }
        });
    }

    // La ListView des compas garde sa propre copie de la liste : on la resynchronise au retour de la
    // page de configuration. Sans effet si la liste n'est pas affichee.
    private void refreshCompassList() {
        if( _compassAdapter == null ) {
            return;
        }

        _compassAdapter.clear();
        _compassAdapter.addAll(_am.getCompasses());
        _compassAdapter.notifyDataSetChanged();
    }

    // #79 : page "Speed test", ouverte depuis la section "Developer pages" de la page Parameters.
    // Rafraichie sur BR.speedKmString, deja notifie a chaque fix, a chaque changement d'unite et a la
    // perte du GPS : le calcul de vitesse lui-meme n'est pas touche. Calcul du tableau hors verrou,
    // sur une copie de l'historique.
    public void ShowSpeedTestPage() {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        final LayoutPageSpeedTestBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_speed_test, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);

        final MyListAdapter<SpeedTest.Row> adapter = new MyListAdapter<SpeedTest.Row>(this._activity, R.layout.layout_speed_test_row, BR.row, new ArrayList<SpeedTest.Row>());
        binding.lvSpeedRows.setAdapter(adapter);

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                refreshSpeedTestPage(binding, adapter);
            }
        };

        final Observable.OnPropertyChangedCallback callback = new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                if( propertyId == BR.speedKmString ) {
                    binding.getRoot().post(refresh);
                }
            }
        };

        _am.addOnPropertyChangedCallback(callback);
        refresh.run();

        showFullscreenPage(binding.getRoot(), binding.btnBack, new Runnable() {
            @Override
            public void run() {
                _am.removeOnPropertyChangedCallback(callback);
            }
        });
    }

    // #79 : one raw log line per NEW fix while the Speed test page is open (offline noise analysis).
    private long _speedTestLastLoggedMs = -1;

    private void refreshSpeedTestPage(LayoutPageSpeedTestBinding binding, MyListAdapter<SpeedTest.Row> adapter) {
        int size = AppManager.getSpeedTestHistorySize();
        double[] lat = new double[size];
        double[] lon = new double[size];
        double[] acc = new double[size];
        long[] timeMs = new long[size];
        int unit = _am.getSpeedUnit();
        double[] speeds = _am.getSpeedTestSpeeds();
        int fill = _am.getSpeedTestFixes(lat, lon, acc, timeMs);

        if( fill > 0 && timeMs[0] != _speedTestLastLoggedMs ) {
            _speedTestLastLoggedMs = timeMs[0];
            GBLog.i("SPEEDTEST", String.format(Locale.US, "fix t=%d lat=%.8f lon=%.8f acc=%.1f avg=%.2f short=%.2f hw=%.2f",
                    timeMs[0], lat[0], lon[0], acc[0], speeds[0], speeds[1], speeds[2]));
        }

        binding.tvSpeedAvg.setText(SpeedTest.formatSpeed(speeds[0], unit));
        binding.tvSpeedShort.setText(SpeedTest.formatSpeed(speeds[1], unit));
        binding.tvSpeedHw.setText(SpeedTest.formatSpeed(speeds[2], unit));
        binding.tvSpeedV2.setText(SpeedTest.formatSpeed(speeds[3], unit));

        List<SpeedTest.Row> rows = SpeedTest.compute(lat, lon, timeMs, fill, unit);

        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // #74 : adresse partagee. URL NUE, sans parametre de langue : la page detecte la langue du
    // lecteur par son en-tete Accept-Language, donc un meme lien s'affiche correctement pour tout
    // le monde et l'apercu social sort dans la bonne langue.
    private static final String SHARE_URL = "https://golden-block.fr/mybuoy/";

    // #33 / #37 : page "Support the development". Le contenu est entierement declare dans le
    // layout ; les lignes cliquables appellent OpenUrl() par data binding.
    public void ShowSupportPage() {
        if( this._activity == null ) {
            return;
        }
        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageSupportBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_page_support, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);
        animateShareArrow(binding.imgShareArrow);
        showFullscreenPage(binding.getRoot(), binding.btnBack);
    }

    // #86 step 1: Maintenance page. For now it holds the chunk cache alone -- what it takes up, and
    // four ways to trim it. The summary is refreshed by hand rather than through data binding: it is
    // read from the file system, which only this page looks at, and only while it is open.
    private volatile TextView _maintenanceSummary = null;   // also read by the cache thread

    public void ShowMaintenancePage() {
        if( this._activity == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutPageMaintenanceBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.layout_page_maintenance, null, false);
        binding.setAppManager(_am);
        binding.setViewsController(this);

        _maintenanceSummary = binding.tvCacheSummary;
        _maintenanceSummary.setText("Counting map data…");

        // Counted by the cache thread, never here: the folder may hold hundreds of files on the SD
        // card. When a purge is already running, its end refreshes the page just the same.
        ChunkCacheThread.request(ChunkCacheThread.CMD_STATS);

        showFullscreenPage(binding.getRoot(), binding.btnBack, new Runnable() {
            @Override public void run() { _maintenanceSummary = null; }
        });
    }

    // What the cache holds, in plain words: how many areas, how much room, and when the least
    // recently used one last served.
    private void refreshMaintenanceSummary(ChunkCache.Stats stats) {
        if( (_maintenanceSummary == null) || (this._activity == null) ) {
            return;
        }

        String text;
        if( stats.count == 0 ) {
            text = "No map data kept yet.";
        } else {
            text = String.format(Locale.US, "%d area%s kept, %s.",
                    stats.count, (stats.count > 1) ? "s" : "", AppManager.readableSize(stats.bytes));

            if( stats.oldestUseMs > 0 ) {
                long days = (System.currentTimeMillis() - stats.oldestUseMs) / (24L * 3600L * 1000L);
                text += (days <= 0)
                        ? "\nAll of it was used today."
                        : String.format(Locale.US, "\nThe least recently used one goes back %d day%s.",
                                days, (days > 1) ? "s" : "");
            }
        }

        _maintenanceSummary.setText(text);
    }

    // #86: the four purges do NOT run here. The page only posts a command; the cache thread does the
    // work and refreshes this page when it is done (refreshMaintenancePage). Sweeping a folder on
    // the UI thread would freeze the page for as long as the disk takes.
    public void PurgeChunkCacheFar() {
        requestPurge(ChunkCacheThread.CMD_PURGE_FAR);
    }

    public void PurgeChunkCacheMonth() {
        requestPurge(ChunkCacheThread.CMD_PURGE_MONTH);
    }

    public void PurgeChunkCacheWeek() {
        requestPurge(ChunkCacheThread.CMD_PURGE_WEEK);
    }

    public void PurgeChunkCacheAll() {
        requestPurge(ChunkCacheThread.CMD_PURGE_ALL);
    }

    // A purge already asked for is never overwritten: the page says so instead of looking as if
    // nothing had happened.
    private void requestPurge(int command) {
        if( !ChunkCacheThread.request(command) ) {
            notifyWarn("A cleanup is already running, try again in a moment.");
            return;
        }

        notifyInfo("Cleaning up…");
    }

    /** For the cache thread: is there a page to count the cache for at all. */
    public static boolean isMaintenancePageOpen() {
        final ViewsController vc = _instance;

        return (vc != null) && (vc._activity != null) && (vc._maintenanceSummary != null);
    }

    /**
     * #86: called by the cache thread once its work is done, from ITS thread, with the count it made
     * there -- hence the hop onto the UI thread, which only writes the text. Does nothing when the
     * page is closed, which is the usual case.
     */
    public static void refreshMaintenancePage(final ChunkCache.Stats stats) {
        final ViewsController vc = _instance;

        if( (vc == null) || (vc._activity == null) || (vc._maintenanceSummary == null) )
            return;

        vc._activity.runOnUiThread(new Runnable() {
            @Override public void run() { vc.refreshMaintenanceSummary(stats); }
        });
    }

    // #74 : la fleche qui designe le bouton de partage va et vient de quelques pixels. Pur Android
    // (ObjectAnimator, API 21) : pas de GIF, qu'Android ne lit nativement qu'a partir de l'API 28,
    // ni de bibliotheque d'images pour une seule fleche.
    // L'animation est liee au CYCLE DE VIE DE LA VUE : demarree a l'attachement, annulee au
    // detachement. Sans cela elle continuerait de redessiner apres la fermeture de la page.
    private void animateShareArrow(final View arrow) {
        if( arrow == null ) {
            return;
        }

        final ObjectAnimator animator = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_X, 0f, -10f);
        animator.setDuration(650);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        arrow.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                animator.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                animator.cancel();
                v.setTranslationX(0f);
            }
        });
    }

    // #74 : partage de l'application. ACTION_SEND ouvre la feuille de partage du systeme, qui liste
    // deja toutes les applications installees -- inutile d'aligner un bouton par reseau.
    // EXTRA_TEXT ne porte QUE l'URL, volontairement : la page publie des balises Open Graph, donc
    // le destinataire verra un apercu complet (titre, description, banniere, et meme dans sa propre
    // langue). Y coller une phrase en plus serait redondant, et surtout Facebook ignore le texte
    // pre-rempli tandis que d'autres applications ne retiennent que la premiere URL trouvee.
    // EXTRA_SUBJECT ne sert qu'aux applications de courrier et de SMS ; les autres l'ignorent.
    public void ShareApp() {
        if( this._activity == null ) {
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "MyBuoy");
            intent.putExtra(Intent.EXTRA_TEXT, SHARE_URL);
            this._activity.startActivity(Intent.createChooser(intent, "Share MyBuoy"));
        }
        catch( ActivityNotFoundException e ) {
            notifyError("No application can share a link");
        }
    }

    // #65 : partage d'une mark, par la meme feuille systeme que ShareApp(). Le texte partage est une
    // URI geo: : un autre MyBuoy la rouvre avec le nom et l'icone, Maps ou Waze y vont a la position,
    // et une messagerie qui ignore le schema transmet simplement la ligne.
    public void ShareMark(PersonalMark mark) {
        if( this._activity == null || mark == null ) {
            return;
        }

        String title = mark.getTitle();
        String uri = GeoUriParser.buildShareUrl(mark.getLatitude(), mark.getLongitude(), mark.getAltitude(), title, mark.getBitmapId());

        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, ((title == null) || title.trim().isEmpty()) ? "MyBuoy position" : title);
            intent.putExtra(Intent.EXTRA_TEXT, uri);
            this._activity.startActivity(Intent.createChooser(intent, "Share position"));
        }
        catch( ActivityNotFoundException e ) {
            notifyError("No application can share a position");
        }
    }

    // #37 : ouvre une adresse dans l'application qui la revendique, sinon dans le navigateur par
    // defaut. Rien de special a faire pour Telegram : une URL https://t.me/... est captee par
    // l'application Telegram si elle est installee, et retombe sinon sur la page web du canal.
    public void OpenUrl(String url) {
        if( this._activity == null || url == null || url.isEmpty() ) {
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            this._activity.startActivity(intent);
        }
        catch( ActivityNotFoundException e ) {
            notifyError("No application can open " + url);
        }
    }

    // #37 : ouvre le canal Telegram. On tente d'abord le schema tg:// que seule l'application
    // Telegram declare : elle s'ouvre directement sur le canal, sans passer par le navigateur ni par
    // le choix "ouvrir avec". Si elle n'est pas installee, le lancement leve ActivityNotFoundException
    // et on retombe sur la page web du canal, lisible sans compte. Pas besoin d'interroger le
    // PackageManager au prealable : depuis Android 11 il repondrait "non" faute de bloc <queries>,
    // alors que le lancement, lui, fonctionne.
    public void OpenTelegramChannel(String channel) {
        if( this._activity == null || channel == null || channel.isEmpty() ) {
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + channel));
            this._activity.startActivity(intent);
        }
        catch( ActivityNotFoundException e ) {
            OpenUrl("https://t.me/" + channel);
        }
    }

    // #33 (essai) : variante du menu en POPUP ancre sur le burger (au lieu du dialog plein ecran).
    public void ShowMenuPopup(View anchor) {
        if( this._activity == null || anchor == null ) {
            return;
        }

        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);
        LayoutMenuPopupBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_menu_popup, null, false);

        // Two columns on a screen lying on its side. With one, the entries already ran
        // past the bottom edge before this page was added: the last of them lost its
        // lower padding, and the next would simply not have been reachable. The menu is
        // rebuilt every time it opens, so reading the orientation here is enough: a
        // rotation while it is open closes it, in closeMenuOnRotation.
        boolean landscape = _activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        binding.gridMenu.setColumnCount(landscape ? 2 : 1);

        // focusable = false : ne pas voler le focus fenetre (sinon la barre systeme reapparait puis le
        // sticky-immersive la re-cache ~1s apres -> le popup "saute"). Le tap dehors ferme quand meme.
        final PopupWindow popup = new PopupWindow(binding.getRoot(),
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(20f);

        // Le meme tap va arriver a la carte : on arme le garde pour qu'elle l'avale (cf.
        // consumeMenuTouch). L'evenement n'est pas consomme ici, le popup doit se fermer normalement.
        popup.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if( event.getActionMasked() == MotionEvent.ACTION_OUTSIDE ) {
                    _menuGuardUntilMs = SystemClock.uptimeMillis() + MENU_GUARD_MS;
                }

                return false;
            }
        });

        _menuPopup = popup;
        popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if( _menuPopup == popup ) {
                    _menuPopup = null;
                }
            }
        });

        binding.btnMenuMarks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowMarksPage();
            }
        });
        binding.btnMenuTides.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowTidesPage();
            }
        });
        binding.btnMenuSunMoon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowSunMoonPage();
            }
        });
        binding.btnMenuMaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowMapsPage();
            }
        });
        binding.btnMenuParams.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowParametersPage();
            }
        });
        binding.btnMenuMaintenance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowMaintenancePage();
            }
        });
        binding.btnMenuSupport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popup.dismiss();
                ShowSupportPage();
            }
        });

        // showAsDropDown SUIT l'ancre (repositionne le popup quand le layout se recompose -> "saut" quand
        // l'immersif re-cache la barre). On calcule une position absolue et on utilise showAtLocation, qui
        // NE suit PAS l'ancre : sous le burger, aligne a droite (Gravity.TOP|END).
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);

        // #70 : les offsets se prennent sur la FENETRE, pas sur l'ecran. En multi-fenetre les deux
        // different, et un calcul en coordonnees d'ecran posait le menu par rapport au bord droit
        // de l'ecran entier, donc hors de la moitie occupee par MyBuoy.
        View root = anchor.getRootView();
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);

        float density = this._activity.getResources().getDisplayMetrics().density;
        int gapPx = (int) (8 * density);
        int xOffFromRight = (rootLoc[0] + root.getWidth()) - (loc[0] + anchor.getWidth());
        int yOff = (loc[1] - rootLoc[1]) + anchor.getHeight() + gapPx;
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.END, xOffFromRight, yOff);
    }

    public void ShowPersonalMark(PersonalMark mark) {
        ShowPersonalMark(mark, null);
    }

    // #67 : meme editeur, avec un menage a la fermeture. La page de recherche s'en sert pour
    // rafraichir sa ligne : l'utilisateur a pu enregistrer la marque, la renommer ou changer son
    // icone, et le bouton doit alors passer de Create a Edit.
    public void ShowPersonalMark(PersonalMark mark, Runnable onDismiss) {

        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater)
                this._activity.getSystemService(this._activity.LAYOUT_INFLATER_SERVICE);

        // #67 : l'ordre du roller d'icones est recalcule a CHAQUE ouverture (derniere icone
        // enregistree en tete, puis les plus utilisees). Il doit l'etre AVANT que le binding
        // n'evalue selectedItemPosition : sinon getBitmapPosition() ne trouve pas encore sa table de
        // correspondance, rend 0, le spinner peint la premiere icone du roller, puis saute sur la
        // bonne des que l'adaptateur arrive. C'est ce clignotement d'une image a l'ouverture.
        mark.getMarksAdapter();

        LayoutMarkBinding binding = DataBindingUtil.inflate(inflater, R.layout.layout_mark,null, false );
        binding.setMark(mark);
        //binding.lvMaps.setAdapter(mapsAdapter);

        //Dialog dialog = new Dialog(this._activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        Dialog dialog = new Dialog(this._activity);

        binding.btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        binding.btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( mark.save() ) {
                    dialog.dismiss();
                }
            }
        });

        binding.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( mark.delete() ) {
                    dialog.dismiss();
                }
            }
        });

        dialog.setContentView(binding.getRoot());
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        //dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);

        // Le data-binding est paresseux : sans cet appel il ne s'execute qu'a la frame suivante, et
        // la premiere image du popup est peinte avec des valeurs non encore posees. On le force a
        // s'executer maintenant, pour que le popup apparaisse deja rempli.
        binding.executePendingBindings();

        _markEditorDialog = dialog;   // #52 : gel du recentrage tant que CETTE popup est ouverte (isMarkEditorOpen)
        trackDialog(dialog, onDismiss);
        dialog.show();
        AppManager.getInstance().playPopupOpenSound();   // #52 : son d'ouverture du popup mark
    }

    public void ShowMainView() {
        if( this._activity != null ) {
            //this._activity.setContentView(R.layout.activity_main);

            AppManager _am = AppManager.getInstance();

            ActivityMainBinding binding = DataBindingUtil.setContentView(this._activity, R.layout.activity_main );
            binding.setAppManager(_am);
            binding.setViewsController(this);

            // #70 : la surface de carte doit faire exactement la taille de son conteneur. La laisser
            // en MATCH_PARENT ne marche pas (le GBScrollView mesure ses enfants en UNSPECIFIED, ou
            // un MATCH_PARENT vaut zero), et la taille de l'ECRAN, utilisee jusqu'ici, est fausse des
            // que la fenetre n'occupe pas tout : split screen, vue pop-up, DeX. Une surface plus
            // grande que le GBScrollView rend d'ailleurs celui-ci scrollable, et il intercepte alors
            // le geste de pan destine a la carte. On la recale donc sur le GBScrollView a chaque
            // changement de sa mise en page, ce qui couvre aussi la rotation.
            final GameSurface surface = new GameSurface(this._activity);
            final GBScrollView scroll = binding.ScVw;

            binding.LLvw.addView(surface, scroll.getWidth(), scroll.getHeight());

            scroll.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                final int w = right - left;
                final int h = bottom - top;

                if( w <= 0 || h <= 0 ) {
                    return;
                }

                ViewGroup.LayoutParams lp = surface.getLayoutParams();
                if( lp == null || (lp.width == w && lp.height == h) ) {
                    return;
                }

                // hors de la passe de mise en page en cours : setLayoutParams en relance une
                v.post(() -> {
                    ViewGroup.LayoutParams params = surface.getLayoutParams();
                    if( params != null && (params.width != w || params.height != h) ) {
                        params.width = w;
                        params.height = h;
                        surface.setLayoutParams(params);
                        GBLog.d(getClass().getName(), "map surface resized to " + w + "x" + h);
                    }
                });
            });

            // #71 : la racine reprend a son compte un glissement demarre sur une commande cliquable
            // et le rend a la carte (cf. GBRootLayout).
            binding.mainRoot.setMapSurface(surface);

            applyOverlayInsets(binding.overlayTop);
            applyOverlayInsets(binding.linearLayout3);

            // Waiting screen: a first tip, and a button drawing another among those not seen yet.
            // The minimum display time starts here, the view being in place now, and starts again
            // on every "next" tap so the new tip can be read too (#86 test feedback).
            final SplashTips astuces = new SplashTips(this._activity);

            if( astuces.isEmpty() ) {
                binding.splashTipNext.setVisibility(View.GONE);
            } else {
                binding.splashTip.setText(astuces.suivante());
                binding.splashTipNext.setOnClickListener(v -> {
                    binding.splashTip.setText(astuces.suivante());
                    _am.splashRestartMinimum();
                });
            }

            _am.splashStart();
        }
    }

    // #70 : en fenetre, la carte passe sous les barres systeme (cf. MainActivity.applyImmersiveMode),
    // mais les commandes, elles, ne doivent pas finir dessous : elles se decalent des insets, en
    // conservant le padding d'origine de la vue. Le cas qui compte est l'application collee a droite
    // en paysage, ou la barre de navigation tombe pile sur le burger et la colonne des marks. En
    // plein ecran les barres sont cachees, leurs insets valent zero, et rien ne bouge.
    private void applyOverlayInsets(final View overlay) {
        if( overlay == null ) {
            return;
        }

        final int padLeft = overlay.getPaddingLeft();
        final int padTop = overlay.getPaddingTop();
        final int padRight = overlay.getPaddingRight();
        final int padBottom = overlay.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(overlay, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padLeft + bars.left, padTop + bars.top,
                    padRight + bars.right, padBottom + bars.bottom);
            GBLog.d(getClass().getName(), "overlay insets applied: left=" + bars.left
                    + " top=" + bars.top + " right=" + bars.right + " bottom=" + bars.bottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(overlay);
    }

}
