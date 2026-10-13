package com.artcraftmobile.bridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private static final int PICK_ZIP = 902;
    private static final int PICK_WEB_FILE = 903;
    
    private final Set<String> approvedDocumentUris = ConcurrentHashMap.newKeySet();
    private boolean landscape = false;
    private boolean photoTouchMenuEnabled = true;
    private final AtomicBoolean updateCheckActive = new AtomicBoolean(false);
    private static final String UPDATE_PREFS = "module_update_checks";
    private static final int BG = 0xff0d111b;
    private static final int SURFACE = 0xff171f2e;
    private static final int PANEL = 0xff202b3b;
    private static final int OUTLINE = 0xff334255;
    private static final int MINT = 0xff88e5ca;
    private int currentSection = 0;
    private String catalogCategory = "Tous";
    private String catalogQuery = "";
    private static final int CARD = 0xff1c2330;
    private static final int WHITE = 0xfff0f4fa;
    private static final int SOFT = 0xffa8b5c8;
    private static final int ACCENT = 0xff92c7fc;
    private static final int MAX_FILES = 6000;
    private static final long MAX_BYTES = 450L * 1024 * 1024;

    private static final String[][] APPS = new String[][] {
        {"PhotoCraft", "photocraft", "Retouche photo et calques"},
        {"VectorCraft", "vectorcraft", "Vecteurs, SVG et laser"},
        {"FilmCraft", "filmcraft", "Montage vidéo • Web expérimental"},
        {"LightCraft", "lightcraft", "Photothèque et RAW"},
        {"PdfCraft", "pdfcraft", "Lecture et édition de PDF"},
        {"EffectCraft", "effectcraft", "Effets et animation"},
        {"DesignCraft", "designcraft", "Mise en page"},
        {"FigCraft", "figcraft", "Prototypage d'interfaces"},
        {"WordCraft", "wordcraft", "Traitement de texte"},
        {"SoundCraft", "soundcraft", "Audio multipiste"},
        {"DeckCraft", "deckcraft", "Présentations"},
        {"CADCraft", "cadcraft", "Dessin technique"},
        {"GridCraft", "gridcraft", "Tableurs"},
    };

    private static final String[][] PLAY_APPS = new String[][] {
        {"PhotoCraft", "photocraft", "Retouche photo et calques"},
        {"VectorCraft", "vectorcraft", "Vecteurs, SVG et laser"}
    };

    private boolean isPlayEdition() {
        return getPackageName().endsWith(".play");
    }

    private String[][] availableApps() {
        return isPlayEdition() ? PLAY_APPS : APPS;
    }

    private void prepareBundledModules() {
        background.execute(() -> {
            String failure = null;
            for (String[] module : PLAY_APPS) {
                String slug = module[1];
                String packagedVersion = slug.equals("photocraft") ? "v0.8.2" : "v0.10.0";
                if (installed(slug) && packagedVersion.equals(readInstalledVersion(slug))) continue;
                try (InputStream source = getAssets().open("play/" + slug + ".zip")) {
                    installZip(source, slug);
                    try (FileOutputStream saved = new FileOutputStream(new File(bundle(slug), "release.txt"))) {
                        saved.write(packagedVersion.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception error) {
                    failure = slug + " : " + error.getMessage();
                    break;
                }
            }
            String message = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                showHome();
                if (message != null) new AlertDialog.Builder(this)
                    .setTitle("Préparation des ateliers")
                    .setMessage("Impossible de préparer les éditeurs intégrés : " + message)
                    .setPositiveButton("OK", null).show();
            });
        });
    }

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private LocalHttpServer server;
    private LinearLayout main;
    private WebView browser;
    private NativeSaveBridge nativeSave;
    private String incomingSlug;
    private ValueCallback<Uri[]> webFileCallback;
    
    private final java.util.ArrayDeque<String> fileEvents = new java.util.ArrayDeque<>();
    private synchronized void note(String message) {
        if (fileEvents.size() >= 24) fileEvents.removeFirst();
        fileEvents.addLast(message);
    }
    private synchronized String fileReport() {
        if (fileEvents.isEmpty()) return "Aucun évènement. Essaie une photo dans PhotoCraft.";
        return android.text.TextUtils.join("\n", fileEvents);
    }
    private void showFileReport() {
        new AlertDialog.Builder(this)
            .setTitle("Diagnostic fichiers et menus")
            .setMessage(fileReport() + "\n\nAppui long sur ↻ pour ouvrir ce journal. Teste aussi le bouton Chrome si les menus ne réagissent pas.")
            .setPositiveButton("OK", null)
            .show();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
        if (isPlayEdition()) prepareBundledModules();
        else checkUpdates(false);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        return t;
    }
    private Button button(String value, int color, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(color == ACCENT ? BG : WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private LinearLayout.LayoutParams margins(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(bottom);
        return p;
    }
    private File bundle(String slug) { return new File(new File(getFilesDir(), "bundles"), slug); }
    private boolean installed(String slug) { return new File(bundle(slug), "entry.txt").isFile(); }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle(isPlayEdition() ? "AtelierBridge Play · 1.0" : "AtelierBridge · 1.0 RC2")
            .setMessage("Lanceur indépendant, sans affiliation avec l’équipe ArtCraft. "
                + "Les modules téléchargés appartiennent à leurs auteurs respectifs. "
                + "Les documents restent sur l’appareil ou dans le dossier choisi. "
                + (isPlayEdition() ? "Les éditeurs sont intégrés et mis à jour avec l’application via Google Play. " : "Les installations et vérifications de versions interrogent GitHub. ")
                + "Aucune télémétrie n’est intégrée au lanceur. "
                + "Code, licences et politique de confidentialité : dépôt GitHub.")
            .setPositiveButton("Fermer", null)
            .setNeutralButton("GitHub", (dialog, which) ->
                openExternal("https://github.com/jojo4626/atelierbridge"))
            .show();
    }

    private void showBundledNotices() {
        try (InputStream in = getAssets().open("play/THIRD_PARTY_NOTICES.txt");
             ByteArrayOutputStream text = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (text.size() + n > 300000) throw new IOException("Notices trop volumineuses");
                text.write(buffer, 0, n);
            }
            ScrollView scroller = new ScrollView(this);
            TextView notices = label(new String(text.toByteArray(), StandardCharsets.UTF_8), 12, WHITE, false);
            notices.setPadding(dp(18), dp(12), dp(18), dp(12));
            scroller.addView(notices);
            new AlertDialog.Builder(this).setTitle("Licences et auteurs")
                .setView(scroller).setPositiveButton("Fermer", null).show();
        } catch (IOException error) {
            new AlertDialog.Builder(this).setTitle("Licences et auteurs")
                .setMessage("Notices indisponibles dans cette installation : " + error.getMessage())
                .setPositiveButton("Fermer", null).show();
        }
    }

    private GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }

    private GradientDrawable heroBackground() {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {0xff264e73, 0xff233952, 0xff172638});
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), 0xff3b6081);
        return drawable;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = text(value, size, color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView action(String title, boolean primary, Runnable click) {
        TextView view = label(title, 13, primary ? BG : WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(46));
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(shape(primary ? ACCENT : PANEL, 12, primary ? 0 : OUTLINE));
        view.setOnClickListener(v -> click.run());
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private LinearLayout panel(int color, int radius, int border) {
        LinearLayout view = vertical();
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackground(shape(color, radius, border));
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private int installedCount() {
        int count = 0;
        for (String[] app : availableApps()) if (installed(app[1])) count++;
        return count;
    }

    private String resumeSlug() {
        String last = getSharedPreferences("atelierbridge_home", MODE_PRIVATE)
            .getString("last_opened", "");
        if (last != null && installed(last)) return last;
        if (installed("photocraft")) return "photocraft";
        if (installed("vectorcraft")) return "vectorcraft";
        for (String[] app : availableApps()) if (installed(app[1])) return app[1];
        return null;
    }

    private String displayName(String slug) {
        for (String[] app : availableApps()) if (app[1].equals(slug)) return app[0];
        return slug;
    }

    private String category(String slug) {
        switch (slug) {
            case "photocraft": case "lightcraft": return "Image";
            case "vectorcraft": case "cadcraft": case "designcraft": case "figcraft": return "Dessin";
            case "filmcraft": case "effectcraft": case "soundcraft": return "Média";
            case "pdfcraft": case "wordcraft": case "deckcraft": case "gridcraft": return "Documents";
            default: return "Autres";
        }
    }

    private int appColor(String slug) {
        switch (slug) {
            case "photocraft": return 0xff8ab9ff;
            case "vectorcraft": return 0xff87e2c4;
            case "filmcraft": return 0xffd6adfa;
            case "lightcraft": return 0xffffcb87;
            default: return 0xffafc1dc;
        }
    }

    private LinearLayout sectionHeader(String title, String detail, Runnable click) {
        LinearLayout row = horizontal();
        LinearLayout names = vertical();
        names.addView(label(title, 20, WHITE, true));
        names.addView(label(detail, 12, SOFT, false));
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (click != null) {
            TextView link = label("Voir tout  ›", 13, ACCENT, true);
            link.setPadding(dp(12), dp(12), 0, dp(12));
            link.setOnClickListener(v -> click.run());
            row.addView(link);
        }
        return row;
    }

    private void showHome() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        landscape = false;
        approvedDocumentUris.clear();
        if (nativeSave != null) { nativeSave.close(); nativeSave = null; }
        if (server != null) { server.close(); server = null; }
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        showSection(0);
    }

    private void showSection(int section) {
        currentSection = section;
        LinearLayout screen = vertical();
        screen.setBackgroundColor(BG);
        LinearLayout header = horizontal();
        header.setPadding(dp(20), dp(13), dp(20), dp(12));
        header.setBackgroundColor(BG);
        TextView icon = label("A", 26, BG, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(MINT, 12, 0));
        header.addView(icon, new LinearLayout.LayoutParams(dp(45), dp(45)));
        LinearLayout brand = vertical();
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(label("AtelierBridge", 20, WHITE, true));
        brand.addView(label("Ton studio créatif", 12, SOFT, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView version = label("1.0 RC2", 11, MINT, true);
        version.setPadding(dp(11), dp(8), dp(11), dp(8));
        version.setBackground(shape(0xff1b383d, 20, 0xff2f615e));
        version.setOnClickListener(v -> showAbout());
        header.addView(version);
        screen.addView(header);

        View separator = new View(this);
        separator.setBackgroundColor(OUTLINE);
        screen.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        main = vertical();
        main.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(main);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (section == 0) renderDashboard();
        else if (section == 1) renderCatalog();
        else if (section == 2) renderFiles();
        else renderSettings();
        screen.addView(bottomNavigation());
        setContentView(screen);
    }

    private View bottomNavigation() {
        LinearLayout bar = horizontal();
        bar.setPadding(dp(10), dp(7), dp(10), dp(8));
        bar.setBackgroundColor(SURFACE);
        String[] names = {"Accueil", "Catalogue", "Fichiers", "Réglages"};
        String[] symbols = {"⌂", "▦", "▤", "⚙"};
        for (int i = 0; i < names.length; i++) {
            final int target = i;
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(4));
            if (currentSection == i) item.setBackground(shape(PANEL, 14, OUTLINE));
            TextView sym = label(symbols[i], 22, currentSection == i ? ACCENT : SOFT, true);
            sym.setGravity(Gravity.CENTER);
            item.addView(sym);
            TextView caption = label(names[i], 10, currentSection == i ? ACCENT : SOFT, currentSection == i);
            caption.setGravity(Gravity.CENTER);
            item.addView(caption);
            item.setContentDescription("Ouvrir " + names[i]);
            item.setOnClickListener(v -> { if (currentSection != target) showSection(target); });
            bar.addView(item, new LinearLayout.LayoutParams(0, dp(59), 1));
        }
        return bar;
    }

    private void renderDashboard() {
        String resume = resumeSlug();
        LinearLayout hero = panel(0xff223e5b, 22, 0);
        hero.setBackground(heroBackground());
        TextView eyebrow = label("TON ESPACE CRÉATIF  ✦", 11, MINT, true);
        eyebrow.setLetterSpacing(.12f);
        hero.addView(eyebrow, margins(12));
        hero.addView(label("Imagine. Crée.\nExprime-toi.", 29, WHITE, true), margins(12));
        hero.addView(label("Tes outils de création réunis au même endroit, directement sur ta tablette.", 14, 0xffd0e2f1, false), margins(18));
        LinearLayout ctas = horizontal();
        ctas.addView(action(resume == null ? "Explorer les ateliers  →" : "Reprendre " + displayName(resume) + "  →",
            true, () -> { if (resume == null) showSection(1); else openApp(resume); }),
            new LinearLayout.LayoutParams(0, dp(49), 1));
        TextView browse = action("Catalogue", false, () -> showSection(1));
        LinearLayout.LayoutParams secondary = new LinearLayout.LayoutParams(0, dp(49), .55f);
        secondary.leftMargin = dp(9);
        ctas.addView(browse, secondary);
        hero.addView(ctas);
        main.addView(hero, margins(16));

        LinearLayout metrics = horizontal();
        addMetric(metrics, String.valueOf(installedCount()), "Installés");
        addMetric(metrics, String.valueOf(availableApps().length), "Ateliers");
        addMetric(metrics, String.valueOf(RecentExports.list(this).size()), "Exports");
        main.addView(metrics, margins(26));

        main.addView(sectionHeader("Mes ateliers", "Prêts à créer, même hors connexion", () -> showSection(1)), margins(14));
        ArrayList<String[]> mine = new ArrayList<>();
        for (String[] app : availableApps()) if (installed(app[1])) mine.add(app);
        if (mine.isEmpty()) {
            LinearLayout empty = panel(SURFACE, 16, OUTLINE);
            empty.addView(label("Ton studio attend ses premiers outils", 16, WHITE, true), margins(6));
            empty.addView(label("Choisis PhotoCraft, VectorCraft ou un autre atelier dans le catalogue.", 13, SOFT, false), margins(12));
            empty.addView(action("Découvrir le catalogue", true, () -> showSection(1)));
            main.addView(empty, margins(24));
        } else {
            renderCards(main, mine, false);
        }

        main.addView(sectionHeader("À découvrir", "Installe uniquement les outils dont tu as besoin", () -> showSection(1)), margins(14));
        ArrayList<String[]> suggestions = new ArrayList<>();
        for (String[] app : availableApps()) if (!installed(app[1]) && suggestions.size() < 2) suggestions.add(app);
        if (!suggestions.isEmpty()) renderCards(main, suggestions, true);
        else main.addView(label("Tous les ateliers du catalogue sont installés.", 13, SOFT, false), margins(20));

        LinearLayout tip = panel(SURFACE, 16, OUTLINE);
        tip.addView(label("✦  Crée aussi hors connexion", 15, MINT, true), margins(6));
        tip.addView(label(isPlayEdition() ? "PhotoCraft et VectorCraft sont intégrés et utilisables hors ligne. Leurs mises à jour sont livrées par Google Play." :
            "Les ateliers installés restent sur ton appareil. Internet est uniquement nécessaire pour leur téléchargement et leurs mises à jour.", 12, SOFT, false));
        main.addView(tip);
    }

    private void addMetric(LinearLayout row, String amount, String detail) {
        LinearLayout cell = panel(SURFACE, 14, OUTLINE);
        cell.setPadding(dp(9), dp(12), dp(9), dp(12));
        TextView count = label(amount, 23, WHITE, true);
        count.setGravity(Gravity.CENTER);
        cell.addView(count);
        TextView name = label(detail, 11, SOFT, false);
        name.setGravity(Gravity.CENTER);
        cell.addView(name);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(79), 1);
        p.rightMargin = dp(7);
        row.addView(cell, p);
    }

    private void renderCards(LinearLayout parent, List<String[]> apps, boolean compact) {
        int columns = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 2 : 1;
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % columns == 0) {
                row = horizontal();
                row.setGravity(Gravity.TOP);
                parent.addView(row, margins(10));
            }
            LinearLayout card = moduleCard(apps.get(i), compact);
            LinearLayout.LayoutParams p = columns == 1
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1);
            if (columns == 2 && i % 2 == 0) p.rightMargin = dp(10);
            row.addView(card, p);
            if (columns == 2 && i == apps.size() - 1 && i % 2 == 0) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
            }
        }
    }

    private LinearLayout moduleCard(String[] app, boolean compact) {
        String name = app[0], slug = app[1];
        boolean ready = installed(slug);
        LinearLayout card = panel(SURFACE, 17, OUTLINE);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout headline = horizontal();
        TextView icon = label(name.substring(0, 1), 24, BG, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(appColor(slug), 13, 0));
        headline.addView(icon, new LinearLayout.LayoutParams(dp(47), dp(47)));
        LinearLayout labels = vertical();
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(label(name, 17, WHITE, true), margins(3));
        labels.addView(label(category(slug) + (ready ? "  ·  Installé" : "  ·  À installer"), 11,
            ready ? MINT : SOFT, false));
        headline.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(headline, margins(12));
        card.addView(label(app[2], 12, SOFT, false), margins(14));
        if (ready) {
            card.addView(action("Ouvrir l'atelier  →", true, () -> openApp(slug)), margins(8));
            if (!compact && !isPlayEdition()) card.addView(action("Mettre à jour", false, () -> installSelected(new String[]{slug})));
        } else {
            if (isPlayEdition()) card.addView(label("Préparation locale en cours…", 12, SOFT, false), margins(8));
            else {
                card.addView(action("Installer  ↓", true, () -> installSelected(new String[]{slug})), margins(8));
                if (!compact) card.addView(action("Importer un ZIP", false, () -> importZip(slug)));
            }
        }
        return card;
    }

    private void renderCatalog() {
        main.addView(label("Catalogue", 28, WHITE, true), margins(5));
        main.addView(label("Les ateliers de création à installer selon tes envies.", 13, SOFT, false), margins(18));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(SOFT);
        search.setTextSize(15);
        search.setHint("⌕  Rechercher un atelier");
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        search.setBackground(shape(SURFACE, 14, OUTLINE));
        search.setText(catalogQuery);
        main.addView(search, margins(14));
        HorizontalScrollView categories = new HorizontalScrollView(this);
        categories.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = horizontal();
        String[] options = {"Tous", "Image", "Dessin", "Média", "Documents"};
        for (String option : options) {
            final String target = option;
            boolean selected = catalogCategory.equals(option);
            TextView chip = label(option, 13, selected ? BG : SOFT, selected);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(17), dp(10), dp(17), dp(10));
            chip.setBackground(shape(selected ? MINT : SURFACE, 24, selected ? 0 : OUTLINE));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(41));
            params.rightMargin = dp(8);
            chips.addView(chip, params);
            chip.setOnClickListener(v -> { catalogCategory = target; showSection(1); });
        }
        categories.addView(chips);
        main.addView(categories, margins(18));
        main.addView(sectionHeader("Tous les ateliers", "Installation et mises à jour en un clic", null), margins(12));
        LinearLayout results = vertical();
        main.addView(results, margins(16));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                catalogQuery = s.toString();
                populateCatalog(results);
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        populateCatalog(results);
        if (!isPlayEdition()) main.addView(action("Installer plusieurs ateliers", false, this::chooseModules), margins(12));
        main.addView(label(isPlayEdition() ? "PhotoCraft et VectorCraft sont inclus dans l'application, avec attribution aux auteurs." :
            "Les modules sont fournis par leurs auteurs. Certaines versions Web peuvent ne pas être disponibles ou fonctionner sur Android.", 12, SOFT, false));
    }

    private void populateCatalog(LinearLayout container) {
        container.removeAllViews();
        ArrayList<String[]> matches = new ArrayList<>();
        String query = catalogQuery.trim().toLowerCase(java.util.Locale.ROOT);
        for (String[] app : availableApps()) {
            if (!catalogCategory.equals("Tous") && !category(app[1]).equals(catalogCategory)) continue;
            if (!query.isEmpty() && !(app[0] + " " + app[2] + " " + category(app[1]))
                .toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            matches.add(app);
        }
        if (matches.isEmpty()) {
            LinearLayout empty = panel(SURFACE, 16, OUTLINE);
            empty.addView(label("Aucun atelier trouvé", 16, WHITE, true), margins(5));
            empty.addView(label("Essaie un autre nom ou sélectionne la catégorie Tous.", 13, SOFT, false));
            container.addView(empty);
        } else renderCards(container, matches, false);
    }

    private void renderFiles() {
        main.addView(label("Mes fichiers", 28, WHITE, true), margins(6));
        main.addView(label("Retrouve les créations enregistrées depuis tes ateliers.", 13, SOFT, false), margins(20));
        List<RecentExports.Entry> recent = RecentExports.list(this);
        main.addView(sectionHeader("Exports récents", recent.size() + " fichier(s) enregistré(s)",
            recent.isEmpty() ? null : this::showRecentExports), margins(15));
        if (recent.isEmpty()) {
            LinearLayout empty = panel(SURFACE, 18, OUTLINE);
            TextView emoji = label("▤", 35, ACCENT, false);
            emoji.setGravity(Gravity.CENTER);
            empty.addView(emoji, margins(10));
            TextView title = label("Tes créations apparaîtront ici", 18, WHITE, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title, margins(8));
            TextView detail = label("Ouvre un atelier et exporte un PNG, une image ou un SVG vers un dossier Android.", 13, SOFT, false);
            detail.setGravity(Gravity.CENTER);
            empty.addView(detail, margins(18));
            empty.addView(action("Ouvrir un atelier", true, () -> showSection(0)));
            main.addView(empty);
        } else {
            for (int i = 0; i < recent.size() && i < 12; i++) {
                RecentExports.Entry e = recent.get(i);
                LinearLayout card = panel(SURFACE, 14, OUTLINE);
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout line = horizontal();
                TextView fileIcon = label("▤", 25, ACCENT, true);
                line.addView(fileIcon, new LinearLayout.LayoutParams(dp(37), -2));
                LinearLayout info = vertical();
                info.addView(label(e.name, 15, WHITE, true), margins(4));
                info.addView(label(e.appName + "  ·  " +
                    android.text.format.DateFormat.format("dd/MM/yyyy", e.timestamp), 12, SOFT, false));
                line.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                line.addView(label("›", 25, ACCENT, false));
                card.addView(line);
                card.setOnClickListener(v -> openRecentExport(e));
                main.addView(card, margins(9));
            }
            main.addView(action("Gérer l'historique", false, this::showRecentExports));
        }
    }

    private void renderSettings() {
        main.addView(label("Réglages", 28, WHITE, true), margins(6));
        main.addView(label("Personnalise et entretient ton studio.", 13, SOFT, false), margins(20));
        main.addView(sectionHeader("Ateliers", "Installation et compatibilité", null), margins(12));
        if (!isPlayEdition()) {
            settingsRow("↻", "Rechercher les mises à jour", "Vérifier les versions de tes modules installés", () -> checkUpdates(true));
            settingsRow("▦", "Installer plusieurs ateliers", "Télécharger plusieurs éditeurs en une fois", this::chooseModules);
        } else {
            settingsRow("↻", "Mises à jour", "Ateliers intégrés : mises à jour via Google Play", this::showAbout);
        }
        settingsRow("◇", "Tester ma tablette", "WebAssembly, WebGL2 et fonctions de fichier", this::runDiagnostics);
        main.addView(sectionHeader("À propos", "Informations et transparence", null), margins(12));
        settingsRow("ⓘ", "Confidentialité et licences", "Origine des modules et fonctionnement hors ligne", this::showAbout);
        if (isPlayEdition()) settingsRow("ⓘ", "Licences des outils inclus", "Lire les auteurs et licences embarqués", this::showBundledNotices);
        if (isPlayEdition()) settingsRow("↗", "Politique de confidentialité", "Lire la politique publique d'AtelierBridge Play", () ->
            openExternal("https://github.com/jojo4626/atelierbridge/blob/main/PLAY_PRIVACY.md"));
        settingsRow("↗", "Code source sur GitHub", "Consulter le projet AtelierBridge", () -> openExternal("https://github.com/jojo4626/atelierbridge"));
        TextView footer = label(isPlayEdition() ? "AtelierBridge Play 1.0 · Édition indépendante" : "AtelierBridge 1.0.0-rc.2 · Édition indépendante", 12, SOFT, false);
        footer.setGravity(Gravity.CENTER);
        main.addView(footer, margins(8));
    }

    private void settingsRow(String symbol, String title, String subtitle, Runnable click) {
        LinearLayout line = horizontal();
        line.setPadding(dp(16), dp(16), dp(16), dp(16));
        line.setBackground(shape(SURFACE, 14, OUTLINE));
        TextView mark = label(symbol, 23, MINT, true);
        mark.setGravity(Gravity.CENTER);
        line.addView(mark, new LinearLayout.LayoutParams(dp(35), -2));
        LinearLayout desc = vertical();
        desc.setPadding(dp(11), 0, 0, 0);
        desc.addView(label(title, 15, WHITE, true), margins(4));
        desc.addView(label(subtitle, 12, SOFT, false));
        line.addView(desc, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(label("›", 24, ACCENT, false));
        line.setOnClickListener(v -> click.run());
        main.addView(line, margins(10));
    }

    private String readInstalledVersion(String slug) {
        try {
            File file = new File(bundle(slug), "release.txt");
            if (!file.isFile() || file.length() > 120) return "";
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                if (in.read(bytes) != bytes.length) return "";
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { return ""; }
    }

    
    private void showRecentExports() {
        final List<RecentExports.Entry> entries = RecentExports.list(this);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Exports récents")
                .setMessage("Aucun fichier exporté via AtelierBridge pour le moment. Les projets internes des éditeurs ne sont pas accessibles à cette liste.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            RecentExports.Entry e = entries.get(i);
            items[i] = e.name + "  •  " + e.appName + "\n" +
                android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", e.timestamp);
        }
        new AlertDialog.Builder(this).setTitle("Exports récents")
            .setItems(items, (dialog, which) -> openRecentExport(entries.get(which)))
            .setNeutralButton("Effacer la liste", (dialog, which) ->
                new AlertDialog.Builder(this).setTitle("Effacer l'historique ?")
                    .setMessage("La liste sera vidée mais tes fichiers enregistrés ne seront pas supprimés.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Effacer", (d, w) -> RecentExports.clear(this)).show())
            .setPositiveButton("Fermer", null).show();
    }

    private void openRecentExport(RecentExports.Entry e) {
        try {
            Uri uri = Uri.parse(e.uri);
            if (!"content".equals(uri.getScheme())) throw new IOException("URI non autorisée");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, e.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Ouvrir ce fichier avec…"));
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("Ouverture impossible")
                .setMessage("Le document a pu être déplacé ou l'autorisation Android a expiré. Réexporte-le depuis l'éditeur.")
                .setPositiveButton("OK", null).show();
        }
    }

    
    private void checkUpdates(boolean manual) {
        if (isPlayEdition()) return;
        if (!updateCheckActive.compareAndSet(false, true)) return;
        List<String> installedSlugs = new ArrayList<>();
        for (String[] item : availableApps()) if (installed(item[1])) installedSlugs.add(item[1]);
        if (installedSlugs.isEmpty()) {
            updateCheckActive.set(false);
            if (manual) Toast.makeText(this, "Installe un atelier avant de chercher des mises à jour", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences preferences = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - preferences.getLong("lastCheck", 0) < 24L * 60L * 60L * 1000L) {
            updateCheckActive.set(false);
            return;
        }
        preferences.edit().putLong("lastCheck", now).apply();
        if (manual) Toast.makeText(this, "Vérification des versions installées…", Toast.LENGTH_SHORT).show();
        background.execute(() -> {
            List<String> changed = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            ReleaseInstaller.Progress progress = new ReleaseInstaller.Progress() {
                @Override public void transferred(long done, long total) { }
                @Override public boolean cancelled() { return false; }
            };
            for (String slug : installedSlugs) {
                try {
                    ReleaseInstaller.Asset asset = ReleaseInstaller.latest(slug, progress);
                    String current = readInstalledVersion(slug);
                    
                    if (!current.isEmpty() && !current.equals(asset.version)) {
                        changed.add(slug);
                        names.add(slug + "  " + current + " → " + asset.version);
                    }
                } catch (Exception e) {
                    errors.add(slug + " : " + (e.getMessage() == null ? "indisponible" : e.getMessage()));
                }
            }
            runOnUiThread(() -> {
                updateCheckActive.set(false);
                if (!changed.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Mises à jour disponibles")
                        .setMessage(String.join("\n", names) + "\n\nLes anciennes versions sont conservées si une installation échoue.")
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Mettre à jour", (dialog, which) -> installSelected(changed.toArray(new String[0]))).show();
                } else if (manual) {
                    new AlertDialog.Builder(this).setTitle("Vérification terminée")
                        .setMessage(errors.isEmpty() ? "Tes modules officiels connus sont à jour. Les ZIP importés manuellement ne sont pas vérifiés." :
                            "Aucune mise à jour confirmée. Certains modules n'ont pas pu être vérifiés :\n" + String.join("\n", errors))
                        .setPositiveButton("OK", null).show();
                }
            });
        });
    }

    private void chooseModules() {
        if (isPlayEdition()) {
            new AlertDialog.Builder(this).setTitle("Ateliers intégrés")
                .setMessage("PhotoCraft et VectorCraft sont déjà inclus dans cette édition. Les mises à jour passent par Google Play.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String[] names = new String[APPS.length];
        boolean[] checked = new boolean[APPS.length];
        for (int i = 0; i < APPS.length; i++) names[i] = APPS[i][0] + (installed(APPS[i][1]) ? " (installé)" : "");
        new AlertDialog.Builder(this)
            .setTitle("Choisir les ateliers")
            .setMultiChoiceItems(names, checked, (dialog, which, value) -> checked[which] = value)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Installer la sélection", (dialog, which) -> {
                List<String> selected = new ArrayList<>();
                for (int i = 0; i < APPS.length; i++) if (checked[i]) selected.add(APPS[i][1]);
                if (selected.isEmpty()) Toast.makeText(this, "Sélectionne au moins une application", Toast.LENGTH_SHORT).show();
                else installSelected(selected.toArray(new String[0]));
            }).show();
    }

    private void installSelected(String[] slugs) {
        if (isPlayEdition()) {
            Toast.makeText(this, "Les mises à jour passent par Google Play", Toast.LENGTH_LONG).show();
            return;
        }
        
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LinearLayout panel = vertical();
        panel.setPadding(dp(24), dp(10), dp(24), dp(12));
        TextView message = text("Recherche des versions Web officielles…", 14, SOFT);
        panel.addView(message, margins(14));
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        panel.addView(progressBar, margins(8));
        TextView remaining = text("Installation 1 / " + slugs.length, 12, SOFT);
        panel.addView(remaining);
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Installation des modules")
            .setView(panel)
            .setNegativeButton("Annuler", (d, w) -> cancelled.set(true))
            .create();
        progressDialog.setCancelable(false);
        progressDialog.show();

        background.execute(() -> {
            List<String> successes = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < slugs.length; i++) {
                if (cancelled.get()) break;
                final String slug = slugs[i];
                final int index = i + 1;
                runOnUiThread(() -> {
                    message.setText("Recherche de " + slug + "…");
                    remaining.setText("Installation " + index + " / " + slugs.length);
                    progressBar.setIndeterminate(true);
                });
                File archive = null;
                try {
                    ReleaseInstaller.Progress progress = new ReleaseInstaller.Progress() {
                        @Override public boolean cancelled() { return cancelled.get(); }
                        @Override public void transferred(long done, long total) {
                            int percent = total > 0 ? (int) Math.min(100, done * 100L / total) : 0;
                            runOnUiThread(() -> {
                                progressBar.setIndeterminate(total <= 0);
                                if (total > 0) progressBar.setProgress(percent);
                                message.setText("Téléchargement " + slug + " : " + (total > 0 ? percent + "%" : (done / 1024 / 1024) + " Mo"));
                            });
                        }
                    };
                    ReleaseInstaller.Asset asset = ReleaseInstaller.latest(slug, progress);
                    String current = readInstalledVersion(slug);
                    if (installed(slug) && !current.isEmpty() && current.equals(asset.version)) {
                        successes.add(slug + " : déjà à jour (" + current + ")");
                        continue;
                    }
                    archive = ReleaseInstaller.download(getCacheDir(), asset, progress);
                    if (cancelled.get()) throw new CancellationException();
                    runOnUiThread(() -> {
                        progressBar.setIndeterminate(true);
                        message.setText("Installation de " + slug + "…");
                    });
                    try (InputStream input = new FileInputStream(archive)) {
                        installZip(input, slug);
                    }
                    
                    try (FileOutputStream out = new FileOutputStream(new File(bundle(slug), "release.txt"))) {
                        out.write(asset.version.getBytes(StandardCharsets.UTF_8));
                    }
                    successes.add(slug + " : " + asset.version);
                } catch (CancellationException e) {
                    cancelled.set(true);
                } catch (Exception e) {
                    failures.add(slug + " : " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                } finally {
                    if (archive != null && archive.exists() && !archive.delete()) archive.deleteOnExit();
                }
            }
            boolean wasCancelled = cancelled.get();
            runOnUiThread(() -> {
                progressDialog.dismiss();
                showHome();
                StringBuilder result = new StringBuilder();
                if (!successes.isEmpty()) result.append("Installé(s) :\n").append(String.join("\n", successes));
                if (!failures.isEmpty()) result.append("\n\nÉchec(s) :\n").append(String.join("\n", failures));
                if (wasCancelled) result.append("\n\nOpération annulée.");
                if (result.length() == 0) result.append("Aucun module installé.");
                new AlertDialog.Builder(this).setTitle("Résultat de l'installation")
                    .setMessage(result.toString()).setPositiveButton("OK", null).show();
            });
        });
    }

    private void runDiagnostics() {
        Toast.makeText(this, "Préparation du test hors ligne…", Toast.LENGTH_SHORT).show();
        background.execute(() -> {
            String error = null;
            try (InputStream input = getAssets().open("test-wasm-local.zip")) {
                
                installZip(input, "testcraft");
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String failure = error;
            runOnUiThread(() -> {
                if (failure == null) openApp("testcraft");
                else new AlertDialog.Builder(this).setTitle("Diagnostic indisponible")
                    .setMessage(failure).setPositiveButton("OK", null).show();
            });
        });
    }

    private void importZip(String slug) {
        if (isPlayEdition()) return;
        incomingSlug = slug;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_ZIP);
    }

    private static Uri[] uriArrayFromIntent(Intent data) {
        if (data == null) return null;
        ClipData clips = data.getClipData();
        if (clips != null && clips.getItemCount() > 0) {
            ArrayList<Uri> found = new ArrayList<>();
            for (int i = 0; i < clips.getItemCount() && i < 30; i++) {
                Uri u = clips.getItemAt(i).getUri();
                if (u != null) found.add(u);
            }
            if (!found.isEmpty()) return found.toArray(new Uri[0]);
        }
        Uri one = data.getData();
        return one == null ? null : new Uri[] {one};
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NativeSaveBridge.REQUEST_SAVE) {
            if (nativeSave != null) nativeSave.savePickerResult(resultCode, data);
            return;
        }
        if (requestCode == PICK_WEB_FILE) {
            ValueCallback<Uri[]> callback = webFileCallback;
            webFileCallback = null;
            Uri[] selected = resultCode == RESULT_OK
                ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null;
            
            
            if ((selected == null || selected.length == 0) && resultCode == RESULT_OK && data != null) {
                selected = uriArrayFromIntent(data);
            }
            
            
            approvedDocumentUris.clear();
            ArrayList<Uri> valid = new ArrayList<>();
            if (selected != null) for (Uri picked : selected) {
                if (picked != null && "content".equals(picked.getScheme()) && valid.size() < 30) {
                    valid.add(picked);
                    approvedDocumentUris.add(picked.toString());
                }
            }
            Uri[] permitted = valid.isEmpty() ? null : valid.toArray(new Uri[0]);
            if (callback != null) callback.onReceiveValue(permitted);
            if (permitted != null) {
                note("Photo/document : " + permitted.length + " fichier(s) transmis à la WebView");
                Uri sample = permitted[0];
                background.execute(() -> {
                    String status;
                    try (InputStream probe = getContentResolver().openInputStream(sample)) {
                        if (probe == null) throw new IOException("flux absent");
                        if (probe.read() == -1) throw new IOException("fichier vide");
                        status = "Lecture Android : OK";
                    } catch (Exception e) {
                        status = "Lecture Android : ÉCHEC (" + e.getClass().getSimpleName() + ")";
                    }
                    note(status);
                    if (status.contains("ÉCHEC")) runOnUiThread(() ->
                        Toast.makeText(this, "Lecture impossible. Maintiens ↻ pour le diagnostic.", Toast.LENGTH_LONG).show());
                });
            } else {
                note("Aucun fichier compatible reçu du sélecteur Android");
            }
            return;
        }
        if (requestCode != PICK_ZIP) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { incomingSlug = null; return; }
        String slug = incomingSlug;
        Uri uri = data.getData();
        incomingSlug = null;
        if (slug == null) return;
        Toast.makeText(this, "Importation du module…", Toast.LENGTH_LONG).show();
        background.execute(() -> {
            String error = null;
            try { installZip(uri, slug); }
            catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            final String lastError = error;
            runOnUiThread(() -> {
                if (lastError != null) new AlertDialog.Builder(this).setTitle("Import impossible")
                    .setMessage(lastError).setPositiveButton("OK", null).show();
                else Toast.makeText(this, "Module importé !", Toast.LENGTH_SHORT).show();
                showHome();
            });
        });
    }

    private void installZip(Uri uri, String slug) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Fichier ZIP illisible");
            installZip(in, slug);
        }
    }

    
    private void installZip(InputStream input, String slug) throws IOException {
        if (!slug.matches("[a-z]+craft")) throw new IOException("Identifiant de module invalide");
        File root = new File(getFilesDir(), "bundles");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Dossier privé indisponible");
        File staging = new File(root, slug + ".staging");
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IOException("Impossible de créer le dossier temporaire");
        int count = 0;
        long extracted = 0L;
        try {
            {
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[32768];
                    String prefix = staging.getCanonicalPath() + File.separator;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++count > MAX_FILES) throw new IOException("Archive trop volumineuse (fichiers)");
                        if (entry.getName().length() > 240 || entry.getName().split("/", -1).length > 25)
                            throw new IOException("Chemin ZIP trop profond ou trop long");
                        File out = new File(staging, entry.getName());
                        String canonical = out.getCanonicalPath();
                        if (!canonical.startsWith(prefix)) throw new IOException("Chemin interdit dans le ZIP");
                        if (entry.isDirectory()) {
                            if (!out.exists() && !out.mkdirs()) throw new IOException("Dossier ZIP illisible");
                        } else {
                            File parent = out.getParentFile();
                            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Dossier ZIP illisible");
                            try (BufferedOutputStream dst = new BufferedOutputStream(new FileOutputStream(out))) {
                                int n;
                                while ((n = zip.read(buffer)) != -1) {
                                    extracted += n;
                                    if (extracted > MAX_BYTES) throw new IOException("Archive trop volumineuse (>450 Mo)");
                                    dst.write(buffer, 0, n);
                                }
                            }
                        }
                        zip.closeEntry();
                    }
                }
            }
            List<File> indexes = new ArrayList<>();
            scanIndexes(staging, indexes, 0);
            if (indexes.isEmpty()) throw new IOException("Ce ZIP ne contient pas d'index.html. Télécharge la version *-web-*.zip, pas l'installateur PC.");
            indexes.sort((a, b) -> Integer.compare(a.getAbsolutePath().length(), b.getAbsolutePath().length()));
            String relative = staging.toPath().relativize(indexes.get(0).toPath()).toString().replace(File.separatorChar, '/');
            try (FileOutputStream fout = new FileOutputStream(new File(staging, "entry.txt"))) {
                fout.write(relative.getBytes(StandardCharsets.UTF_8));
            }
            
            File old = bundle(slug);
            File backup = new File(root, slug + ".backup");
            deleteTree(backup);
            if (old.exists() && !old.renameTo(backup)) throw new IOException("Impossible de préparer la mise à jour");
            if (!staging.renameTo(old)) {
                if (backup.exists()) backup.renameTo(old);
                throw new IOException("Impossible d'activer le nouveau module");
            }
            deleteTree(backup);
        } finally {
            if (staging.exists()) deleteTree(staging);
        }
    }

    private static void scanIndexes(File dir, List<File> found, int depth) {
        if (depth > 8 || found.size() > 20) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File item : list) {
            if (item.isDirectory()) scanIndexes(item, found, depth + 1);
            else if (item.getName().equalsIgnoreCase("index.html")) found.add(item);
        }
    }
    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IOException("Impossible de nettoyer : " + file.getName());
    }

    private static int stablePort(String slug) {
        for (int i = 0; i < APPS.length; i++) if (APPS[i][1].equals(slug)) return 46910 + i;
        return 46950;
    }

    private void openApp(String slug) {
        File entryFile = new File(bundle(slug), "entry.txt");
        String entry;
        try {
            byte[] bytes = new byte[(int) entryFile.length()];
            try (java.io.FileInputStream is = new java.io.FileInputStream(entryFile)) {
                int n = is.read(bytes);
                if (n != bytes.length) throw new IOException("Fichier incomplet");
            }
            entry = new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            Toast.makeText(this, "Réimporte le module", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (server != null) server.close();
            server = new LocalHttpServer(new File(getFilesDir(), "bundles"), stablePort(slug));
            server.activate(slug, entry);
        } catch (IOException e) {
            if (server != null) { server.close(); server = null; }
            Toast.makeText(this, "Module invalide ou serveur indisponible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        note("Ouverture : " + slug);
        if (!"testcraft".equals(slug)) {
            getSharedPreferences("atelierbridge_home", MODE_PRIVATE)
                .edit().putString("last_opened", slug).apply();
        }
        String localUrl = "http://127.0.0.1:" + server.getPort() + "/";
        LinearLayout wrapper = vertical();
        wrapper.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(4), dp(8), dp(4));
        top.setBackgroundColor(CARD);
        top.addView(button("← Accueil", 0xff34475e, this::showHome));
        TextView name = text(slug, 17, WHITE);
        name.setTypeface(null, 1);
        name.setPadding(dp(10), 0, 0, 0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(button("Pivoter", 0xff34475e, () -> {
            landscape = !landscape;
            setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }));
        if ("photocraft".equals(slug)) {
            final Button touchMenu = button("Doigt ✓", 0xff34475e, () -> {});
            touchMenu.setText(photoTouchMenuEnabled ? "Doigt ✓" : "Doigt OFF");
            touchMenu.setOnClickListener(v -> {
                photoTouchMenuEnabled = !photoTouchMenuEnabled;
                touchMenu.setText(photoTouchMenuEnabled ? "Doigt ✓" : "Doigt OFF");
                if (browser != null) browser.evaluateJavascript(
                    "window.ArtCraftTouchCompat && window.ArtCraftTouchCompat.setEnabled(" +
                    photoTouchMenuEnabled + ");", null);
                Toast.makeText(this, photoTouchMenuEnabled
                    ? "Compatibilité des menus au doigt activée"
                    : "Compatibilité des menus au doigt désactivée", Toast.LENGTH_SHORT).show();
            });
            top.addView(touchMenu);
        }
        top.addView(button("Chrome", 0xff34475e, () -> {
            Toast.makeText(this, "Garde AtelierBridge ouvert : le module fonctionne sur son serveur local.", Toast.LENGTH_LONG).show();
            openExternal(localUrl);
        }));
        Button reload = button("↻", 0xff34475e, () -> { if (browser != null) browser.reload(); });
        
        reload.setOnLongClickListener(v -> { showFileReport(); return true; });
        top.addView(reload);
        wrapper.addView(top);
        approvedDocumentUris.clear();
        browser = new WebView(this);
        browser.setBackgroundColor(BG);
        nativeSave = new NativeSaveBridge(this, browser, server.getPort(), slug);
        browser.addJavascriptInterface(nativeSave, "ArtCraftSave");
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        
        browser.getSettings().setAllowContentAccess(true);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(true);
        browser.getSettings().setBuiltInZoomControls(true);
        browser.getSettings().setDisplayZoomControls(false);
        browser.getSettings().setSupportZoom(true);
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String msg = message.message();
                if (msg.startsWith("[ArtCraftFile] ")) {
                    note(msg.length() > 180 ? msg.substring(0, 180) : msg);
                } else if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    if (msg.length() > 180) msg = msg.substring(0, 180) + "…";
                    note("Erreur JS ligne " + message.lineNumber() + ": " + msg);
                }
                return super.onConsoleMessage(message);
            }
            @Override public boolean onShowFileChooser(WebView webView,
                            ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (webFileCallback != null) webFileCallback.onReceiveValue(null);
                webFileCallback = callback;
                note("Sélecteur demandé par l'éditeur");
                try {
                    Intent request = params.createIntent();
                    request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    note("Sélecteur WebView : mode=" + params.getMode());
                    startActivityForResult(request, PICK_WEB_FILE);
                    return true;
                } catch (Exception e) {
                    webFileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Sélecteur de fichiers indisponible", Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith(localUrl)) {
                    note("Page chargée");
                    installExportHook();
                }
                if ("photocraft".equals(slug) && url != null && url.startsWith(localUrl)) {
                    installPhotoCraftTouchMenuCompat();
                    installPhotoCraftFileDiagnostic();
                }
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                
                Uri u = request.getUrl();
                if ("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort() == server.getPort())
                    return super.shouldInterceptRequest(view, request);


                if ("content".equals(u.getScheme()) && approvedDocumentUris.contains(u.toString())) {
                    note("Accès WebView au document autorisé");
                    return super.shouldInterceptRequest(view, request);
                }
                return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort() == server.getPort()) return false;
                if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) openExternal(u.toString());
                return true;
            }
        });
        browser.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (nativeSave == null) return;
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            nativeSave.requestDownload(filename, url, mimeType);
        });
        wrapper.addView(browser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(wrapper);
        browser.loadUrl(localUrl);
    }

    
    private void installExportHook() {
        if (browser == null) return;
        try (InputStream in = getAssets().open("artcraft-export-hook.js");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            browser.evaluateJavascript(out.toString("UTF-8"), null);
        } catch (IOException e) {
            note("Pont export indisponible : " + e.getClass().getSimpleName());
        }
    }

    
    private void installPhotoCraftTouchMenuCompat() {
        if (browser == null) return;
        try (InputStream input = getAssets().open("photocraft-touch-menu.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            String script = output.toString("UTF-8") + "\n;window.ArtCraftTouchCompat && " +
                "window.ArtCraftTouchCompat.setEnabled(" + photoTouchMenuEnabled + ");";
            browser.evaluateJavascript(script, result ->
                note("Compatibilité tactile PhotoCraft initialisée (activée=" + photoTouchMenuEnabled + ")"));
        } catch (IOException error) {
            note("Compatibilité tactile non chargée : " + error.getClass().getSimpleName());
        }
    }

    
    private void installPhotoCraftFileDiagnostic() {
        if (browser == null) return;
        try (InputStream input = getAssets().open("photocraft-file-diagnostic.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            browser.evaluateJavascript(output.toString("UTF-8"), null);
            note("Diagnostic d'import PhotoCraft actif");
        } catch (IOException error) {
            note("Diagnostic d'import indisponible : " + error.getClass().getSimpleName());
        }
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Navigateur externe indisponible", Toast.LENGTH_SHORT).show(); }
    }

    @Override public void onBackPressed() {
        if (browser != null) showHome();
        else if (currentSection != 0) showSection(0);
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (nativeSave != null) { nativeSave.close(); nativeSave = null; }
        if (browser != null) { browser.destroy(); browser = null; }
        if (server != null) server.close();
        background.shutdownNow();
        super.onDestroy();
    }
}
