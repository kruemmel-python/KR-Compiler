package kr.compile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity implements
        BuildManager.Listener,
        DocumentManager.Listener,
        CodeEditorView.OnModifiedListener {

    private static final int PICK_PROJECT = 42;
    private static final String PREF_KEY_URI = "project_uri";
    private static final String PREF_KEY_LOCAL = "project_local";
    private static final String PREF_KEY_TARGET = "selected_target";
    private static final String PREF_KEY_ONLINE = "allow_online";
    private static final String PREF_KEY_THEME = "dark_theme";
    private static final String PREF_KEY_FONT_SIZE = "editor_font_size";
    private static final String PREF_KEY_AUTO_BRACKETS = "auto_brackets";
    private static final String PREF_KEY_AUTO_INDENT = "auto_indent";
    private static final String PREF_KEY_TAB_WIDTH = "tab_width";
    private static final String PREF_KEY_WRAP = "wrap_lines";

    private enum ViewMode { CODE, DATEIEN, BUILD, ARTEFAKTE }
    private ViewMode currentMode = ViewMode.CODE;

    // UI Root and Structure
    private LinearLayout rootContainer;
    private LinearLayout headerContainer;
    private LinearLayout topBar;
    private TextView titleLabel;
    private TextView fileInfoLabel;
    private Button btnSave;
    private Button btnSearch;
    private Button btnMenu;

    // View Switcher Bar
    private LinearLayout navTabBar;
    private Button tabCode;
    private Button tabFiles;
    private Button tabBuild;
    private Button tabArtifacts;
    private Button krAgentButton;

    // Open Document Tabs Strip
    private HorizontalScrollView docTabStripScroll;
    private LinearLayout docTabStrip;

    // Main Content Containers
    private FrameLayout contentFrame;
    private CodeEditorView codeEditor;
    private SymbolBarView symbolBar;
    private LinearLayout filesView;
    private LinearLayout buildView;
    private LinearLayout artifactsView;

    // Bottom Action Bar
    private LinearLayout bottomBar;
    private Spinner targetSpinner;
    private CheckBox onlineCheckbox;
    private Button btnCompile;
    private TextView statusIndicator;

    // Build View Controls
    private ScrollView buildLogScroll;
    private LinearLayout buildLogContainer;
    private Button btnCancelBuild;
    private Button btnCopyLog;
    private Button btnClearLog;

    // Settings
    private boolean isDarkTheme = true;
    private float editorFontSize = 17f;
    private boolean autoBrackets = true;
    private boolean autoIndent = true;
    private int tabWidth = 4;
    private boolean wrapLines = false;

    // Multi-pane / Landscape mode
    private boolean isWideLayout = false;
    private boolean compactLandscapeMode = false;
    private LinearLayout splitContainer = null;
    private FrameLayout sidebarContainer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPreferences();

        BuildManager.getInstance().addListener(this);
        DocumentManager.getInstance().addListener(this);

        createUi();
        restoreLastProject();
        applyTheme();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BuildManager.getInstance().removeListener(this);
        DocumentManager.getInstance().removeListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Clean dynamic adaptation without destroying Activity or losing build/editor state
        adaptLayout(newConfig);
    }

    private void loadPreferences() {
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        isDarkTheme = p.getBoolean(PREF_KEY_THEME, true);
        editorFontSize = p.getFloat(PREF_KEY_FONT_SIZE, 17f);
        autoBrackets = p.getBoolean(PREF_KEY_AUTO_BRACKETS, true);
        autoIndent = p.getBoolean(PREF_KEY_AUTO_INDENT, true);
        tabWidth = p.getInt(PREF_KEY_TAB_WIDTH, 4);
        wrapLines = p.getBoolean(PREF_KEY_WRAP, false);

        int targetIdx = p.getInt(PREF_KEY_TARGET, 0);
        if (targetIdx >= 0 && targetIdx < CompilerEngine.Target.values().length) {
            BuildManager.getInstance().setCurrentTarget(CompilerEngine.Target.values()[targetIdx]);
        }
        BuildManager.getInstance().setOnlineDependencies(p.getBoolean(PREF_KEY_ONLINE, true));
    }

    private void savePreferences() {
        getPreferences(MODE_PRIVATE).edit()
                .putBoolean(PREF_KEY_THEME, isDarkTheme)
                .putFloat(PREF_KEY_FONT_SIZE, editorFontSize)
                .putBoolean(PREF_KEY_AUTO_BRACKETS, autoBrackets)
                .putBoolean(PREF_KEY_AUTO_INDENT, autoIndent)
                .putInt(PREF_KEY_TAB_WIDTH, tabWidth)
                .putBoolean(PREF_KEY_WRAP, wrapLines)
                .putInt(PREF_KEY_TARGET, BuildManager.getInstance().getCurrentTarget().ordinal())
                .putBoolean(PREF_KEY_ONLINE, BuildManager.getInstance().isOnlineDependencies())
                .apply();
    }

    private void createUi() {
        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Responsive header. In portrait the two rows stay stacked; in
        // landscape they share one row so the editor keeps useful height.
        headerContainer = new LinearLayout(this);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        headerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topBar = createTopBar();
        headerContainer.addView(topBar);

        // 2. Navigation Tabs (CODE | DATEIEN | BUILD | ARTEFAKTE)
        navTabBar = createNavTabBar();
        krAgentButton = new Button(this);
        krAgentButton.setText("AGENT");
        krAgentButton.setAllCaps(false);
        krAgentButton.setOnClickListener(v -> KrAgentDialog.show(this));
        navTabBar.addView(krAgentButton);
        headerContainer.addView(navTabBar);
        rootContainer.addView(headerContainer);

        // 3. Document Tabs Strip
        docTabStripScroll = new HorizontalScrollView(this);
        docTabStripScroll.setHorizontalScrollBarEnabled(false);
        docTabStripScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        docTabStrip = new LinearLayout(this);
        docTabStrip.setOrientation(LinearLayout.HORIZONTAL);
        docTabStrip.setGravity(Gravity.CENTER_VERTICAL);
        docTabStripScroll.addView(docTabStrip);
        rootContainer.addView(docTabStripScroll);

        // 4. Central Content Frame
        contentFrame = new FrameLayout(this);
        contentFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Views initialization
        initCodeEditorView();
        initFilesView();
        initBuildView();
        initArtifactsView();

        rootContainer.addView(contentFrame);

        // 5. C++ Symbol Bar (Above Keyboard)
        symbolBar = new SymbolBarView(this);
        symbolBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        symbolBar.setOnSymbolActionListener(new SymbolBarView.OnSymbolActionListener() {
            @Override public void onInsertText(String symbol) { codeEditor.insertTextAtCursor(symbol); }
            @Override public void onTab() { codeEditor.indentSelection(); }
            @Override public void onIndent() { codeEditor.indentSelection(); }
            @Override public void onUnindent() { codeEditor.unindentSelection(); }
            @Override public void onCursorMove(int offset) { codeEditor.moveCursor(offset); }
            @Override public void onUndo() { codeEditor.undo(); }
            @Override public void onRedo() { codeEditor.redo(); }
        });
        rootContainer.addView(symbolBar);

        // 6. Bottom Action Bar (Platform target spinner + Compile button)
        bottomBar = createBottomBar();
        rootContainer.addView(bottomBar);

        setContentView(rootContainer);

        // Check wide layout
        adaptLayout(getResources().getConfiguration());
        switchView(ViewMode.CODE);

        KrAgentCore agentCore = KrAgentDialog.getAgent(this);
        agentCore.setStatusListener((statusText, isRunning) -> {
            runOnUiThread(() -> {
                if (krAgentButton != null) {
                    if (isRunning) {
                        krAgentButton.setText("🤖 AGENT (aktiv)");
                        krAgentButton.setTextColor(Color.parseColor("#4CAF50"));
                    } else {
                        krAgentButton.setText("AGENT");
                        krAgentButton.setTextColor(Color.WHITE);
                    }
                }
                if (statusIndicator != null && isRunning) {
                    statusIndicator.setText("🤖 Agent: " + statusText);
                }
            });
        });
        agentCore.setApproval((title, detail, callback) -> {
            if (isFinishing() || isDestroyed()) {
                callback.accept(false);
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(detail)
                    .setCancelable(false)
                    .setNegativeButton("Ablehnen", (d, w) -> callback.accept(false))
                    .setPositiveButton("Genehmigen", (d, w) -> callback.accept(true))
                    .show();
        });
    }

    private LinearLayout createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setMinimumHeight(dp(52));

        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.VERTICAL);

        titleLabel = new TextView(this);
        titleLabel.setText("KR IDE v" + getAppVersionName());
        titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        titleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleLayout.addView(titleLabel);

        fileInfoLabel = new TextView(this);
        fileInfoLabel.setText("Kein Projekt");
        fileInfoLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        titleLayout.addView(fileInfoLabel);

        bar.addView(titleLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnSave = createTopBarButton("💾", "Speichern", v -> saveActiveDocument());
        bar.addView(btnSave);

        btnSearch = createTopBarButton("🔍", "Suchen", v -> codeEditor.toggleSearchBar());
        bar.addView(btnSearch);

        Button btnLine = createTopBarButton("#", "Gehe zu Zeile", v -> codeEditor.showGoToLineDialog());
        bar.addView(btnLine);

        btnMenu = createTopBarButton("⋮", "Menü", this::showMainMenu);
        bar.addView(btnMenu);

        return bar;
    }

    private Button createTopBarButton(String text, String desc, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setContentDescription(desc);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout createNavTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setMinimumHeight(dp(48));

        tabCode = createNavTab("CODE", ViewMode.CODE);
        tabFiles = createNavTab("DATEIEN", ViewMode.DATEIEN);
        tabBuild = createNavTab("BUILD", ViewMode.BUILD);
        tabArtifacts = createNavTab("ARTEFAKTE", ViewMode.ARTEFAKTE);

        bar.addView(tabCode, new LinearLayout.LayoutParams(0, dp(48), 1f));
        bar.addView(tabFiles, new LinearLayout.LayoutParams(0, dp(48), 1f));
        bar.addView(tabBuild, new LinearLayout.LayoutParams(0, dp(48), 1f));
        bar.addView(tabArtifacts, new LinearLayout.LayoutParams(0, dp(48), 1f));
        return bar;
    }

    private Button createNavTab(String text, ViewMode mode) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(v -> switchView(mode));
        return b;
    }

    private LinearLayout createBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(6), dp(12), dp(6));
        bar.setMinimumHeight(dp(54));

        LinearLayout leftSection = new LinearLayout(this);
        leftSection.setOrientation(LinearLayout.VERTICAL);

        targetSpinner = new Spinner(this);
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                new String[]{"Android APK (ARM64)", "Windows EXE (x64)", "Linux (x64)"}) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = super.getView(pos, convert, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
                    ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                }
                return v;
            }
            @Override
            public View getDropDownView(int pos, View convert, ViewGroup parent) {
                View v = super.getDropDownView(pos, convert, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
                    ((TextView) v).setBackgroundColor(isDarkTheme ? Color.parseColor("#282C34") : Color.WHITE);
                    ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
                    ((TextView) v).setPadding(dp(16), dp(12), dp(16), dp(12));
                }
                return v;
            }
        };
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(targetAdapter);
        targetSpinner.setSelection(BuildManager.getInstance().getCurrentTarget().ordinal());
        targetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                BuildManager.getInstance().setCurrentTarget(CompilerEngine.Target.values()[pos]);
                savePreferences();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        leftSection.addView(targetSpinner);

        statusIndicator = new TextView(this);
        statusIndicator.setText("Bereit");
        statusIndicator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        statusIndicator.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        leftSection.addView(statusIndicator);

        bar.addView(leftSection, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnCompile = new Button(this);
        btnCompile.setText("▶ Kompilieren");
        btnCompile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        btnCompile.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btnCompile.setMinimumHeight(dp(48));
        btnCompile.setPadding(dp(18), 0, dp(18), 0);
        btnCompile.setOnClickListener(v -> compileCurrentProject());
        bar.addView(btnCompile);

        return bar;
    }

    private void initCodeEditorView() {
        codeEditor = new CodeEditorView(this);
        codeEditor.setFontSize(editorFontSize);
        codeEditor.setDarkTheme(isDarkTheme);
        codeEditor.setAutoBrackets(autoBrackets);
        codeEditor.setAutoIndent(autoIndent);
        codeEditor.setTabWidth(tabWidth);
        codeEditor.setWrapLines(wrapLines);
        codeEditor.setOnModifiedListener(this);
    }

    private void initFilesView() {
        filesView = new LinearLayout(this);
        filesView.setOrientation(LinearLayout.VERTICAL);
        filesView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Horizontal scrolling toolbar for Files
        HorizontalScrollView filesToolbarScroll = new HorizontalScrollView(this);
        filesToolbarScroll.setHorizontalScrollBarEnabled(false);
        filesToolbarScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout filesToolbar = new LinearLayout(this);
        filesToolbar.setOrientation(LinearLayout.HORIZONTAL);
        filesToolbar.setPadding(dp(6), dp(4), dp(6), dp(4));
        filesToolbar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnOpen = createSmallToolbarButton("Ordner öffnen", v -> chooseProjectFolder());
        filesToolbar.addView(btnOpen);

        Button btnNewFile = createSmallToolbarButton("+ Datei", v -> showCreateFileDialog(null));
        filesToolbar.addView(btnNewFile);

        Button btnNewFolder = createSmallToolbarButton("+ Ordner", v -> showCreateFolderDialog(null));
        filesToolbar.addView(btnNewFolder);

        Button btnRefresh = createSmallToolbarButton("Aktualisieren", v -> refreshProjectTree());
        filesToolbar.addView(btnRefresh);

        filesToolbarScroll.addView(filesToolbar);
        filesView.addView(filesToolbarScroll);

        // Scrollable tree container
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout treeLayout = new LinearLayout(this);
        treeLayout.setId(View.generateViewId());
        treeLayout.setOrientation(LinearLayout.VERTICAL);
        treeLayout.setPadding(dp(12), dp(8), dp(12), dp(16));

        scroll.addView(treeLayout);
        filesView.addView(scroll);
    }

    private Button createSmallToolbarButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        b.setMinimumHeight(dp(44));
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void initBuildView() {
        buildView = new LinearLayout(this);
        buildView.setOrientation(LinearLayout.VERTICAL);
        buildView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Horizontal scrolling action bar for build
        HorizontalScrollView buildActionsScroll = new HorizontalScrollView(this);
        buildActionsScroll.setHorizontalScrollBarEnabled(false);
        buildActionsScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buildActions = new LinearLayout(this);
        buildActions.setOrientation(LinearLayout.HORIZONTAL);
        buildActions.setPadding(dp(6), dp(4), dp(6), dp(4));
        buildActions.setGravity(Gravity.CENTER_VERTICAL);

        btnCancelBuild = createSmallToolbarButton("Abbrechen", v -> BuildManager.getInstance().cancelBuild());
        btnCancelBuild.setEnabled(false);
        buildActions.addView(btnCancelBuild);

        btnCopyLog = createSmallToolbarButton("Kopieren", v -> copyBuildLogToClipboard());
        buildActions.addView(btnCopyLog);

        btnClearLog = createSmallToolbarButton("Leeren", v -> {
            BuildManager.getInstance().clearLog();
            buildLogContainer.removeAllViews();
        });
        buildActions.addView(btnClearLog);

        onlineCheckbox = new CheckBox(this);
        onlineCheckbox.setText("Online-Deps");
        onlineCheckbox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        onlineCheckbox.setChecked(BuildManager.getInstance().isOnlineDependencies());
        onlineCheckbox.setOnCheckedChangeListener((cb, checked) -> {
            BuildManager.getInstance().setOnlineDependencies(checked);
            savePreferences();
        });
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        cbLp.setMargins(dp(6), 0, dp(4), 0);
        onlineCheckbox.setLayoutParams(cbLp);
        buildActions.addView(onlineCheckbox);

        buildActionsScroll.addView(buildActions);
        buildView.addView(buildActionsScroll);

        // Scrollable Console Log
        buildLogScroll = new ScrollView(this);
        buildLogScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildLogScroll.setFillViewport(true);

        buildLogContainer = new LinearLayout(this);
        buildLogContainer.setOrientation(LinearLayout.VERTICAL);
        buildLogContainer.setPadding(dp(12), dp(8), dp(12), dp(16));

        buildLogScroll.addView(buildLogContainer);
        buildView.addView(buildLogScroll);

        // Load existing logs if any
        for (String line : BuildManager.getInstance().getLogBuffer()) {
            appendLogLineView(line);
        }
    }

    private void initArtifactsView() {
        artifactsView = new LinearLayout(this);
        artifactsView.setOrientation(LinearLayout.VERTICAL);
        artifactsView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topActions = new LinearLayout(this);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setPadding(dp(12), dp(8), dp(12), dp(8));
        topActions.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = new TextView(this);
        heading.setText("Erzeugte Programme (KR_BUILD)");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        topActions.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnRefresh = createSmallToolbarButton("Aktualisieren", v -> refreshArtifactsView());
        topActions.addView(btnRefresh);

        artifactsView.addView(topActions);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout list = new LinearLayout(this);
        list.setId(View.generateViewId());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(16));

        scroll.addView(list);
        artifactsView.addView(scroll);
    }

    private void adaptLayout(Configuration config) {
        boolean isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int screenWidthDp = config.screenWidthDp;
        boolean compactLandscape = isLandscape && config.screenHeightDp > 0 && config.screenHeightDp < 500;
        compactLandscapeMode = compactLandscape;
        // Large screen / tablet or landscape phone
        isWideLayout = isLandscape && screenWidthDp >= 540;

        applyResponsiveChrome(isLandscape, compactLandscape);

        contentFrame.removeAllViews();

        if (isWideLayout) {
            // Tablet / Landscape Split Layout
            splitContainer = new LinearLayout(this);
            splitContainer.setOrientation(LinearLayout.HORIZONTAL);
            splitContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Left Pane (Files or Build)
            sidebarContainer = new FrameLayout(this);
            int sidebarWidth = compactLandscape ? 220 : 260;
            sidebarContainer.setLayoutParams(new LinearLayout.LayoutParams(dp(sidebarWidth), ViewGroup.LayoutParams.MATCH_PARENT));
            sidebarContainer.addView(filesView);
            splitContainer.addView(sidebarContainer);

            // Center Pane: Code Editor
            FrameLayout editorFrame = new FrameLayout(this);
            editorFrame.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            editorFrame.addView(codeEditor);
            splitContainer.addView(editorFrame);

            contentFrame.addView(splitContainer);
            symbolBar.setVisibility(View.VISIBLE);
        } else {
            // Portrait / Single-Pane Smartphone Mode
            switchView(currentMode);
        }
    }

    private void applyResponsiveChrome(boolean landscape, boolean compact) {
        if (landscape) {
            int headerHeight = dp(compact ? 42 : 48);
            headerContainer.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setLayoutParams(new LinearLayout.LayoutParams(0, headerHeight, 1.05f));
            navTabBar.setLayoutParams(new LinearLayout.LayoutParams(0, headerHeight, 1.15f));
            topBar.setMinimumHeight(0);
            topBar.setPadding(dp(6), 0, dp(4), 0);
            navTabBar.setMinimumHeight(0);
            fileInfoLabel.setVisibility(compact ? View.GONE : View.VISIBLE);
            titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 14f : 15f);
            codeEditor.setFontSize(Math.min(editorFontSize, compact ? 14f : 15f));
            resizeTopBarButtons(compact ? 36 : 40);
            resizeNavTabs(ViewGroup.LayoutParams.MATCH_PARENT, compact ? 11f : 12f);

            docTabStripScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 30 : 34)));
            symbolBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 40 : 44)));
            symbolBar.setCompactMode(true);

            bottomBar.setMinimumHeight(0);
            bottomBar.setPadding(dp(8), 0, dp(8), 0);
            bottomBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 44 : 48)));
            statusIndicator.setVisibility(compact ? View.GONE : View.VISIBLE);
            btnCompile.setMinimumHeight(0);
            btnCompile.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 14f : 15f);
            btnCompile.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(compact ? 38 : 42)));
        } else {
            headerContainer.setOrientation(LinearLayout.VERTICAL);
            topBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            navTabBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            topBar.setMinimumHeight(dp(52));
            topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
            navTabBar.setMinimumHeight(dp(48));
            fileInfoLabel.setVisibility(View.VISIBLE);
            titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            codeEditor.setFontSize(editorFontSize);
            resizeTopBarButtons(44);
            resizeNavTabs(dp(48), 13f);

            docTabStripScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            symbolBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            symbolBar.setCompactMode(false);

            bottomBar.setMinimumHeight(dp(54));
            bottomBar.setPadding(dp(12), dp(6), dp(12), dp(6));
            bottomBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            statusIndicator.setVisibility(View.VISIBLE);
            btnCompile.setMinimumHeight(dp(48));
            btnCompile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            btnCompile.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        updateBottomBarVisibility();
    }

    private void updateBottomBarVisibility() {
        bottomBar.setVisibility(compactLandscapeMode && currentMode == ViewMode.CODE
                ? View.GONE : View.VISIBLE);
    }

    private void resizeTopBarButtons(int sizeDp) {
        for (int i = 1; i < topBar.getChildCount(); i++) {
            View child = topBar.getChildAt(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
            lp.setMargins(dp(2), 0, dp(2), 0);
            child.setLayoutParams(lp);
        }
    }

    private void resizeNavTabs(int height, float textSizeSp) {
        for (int i = 0; i < navTabBar.getChildCount(); i++) {
            View child = navTabBar.getChildAt(i);
            child.setLayoutParams(new LinearLayout.LayoutParams(0, height, 1f));
            if (child instanceof Button) {
                ((Button) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            }
        }
    }

    private void switchView(ViewMode mode) {
        currentMode = mode;
        updateNavTabStyles();
        updateBottomBarVisibility();

        if (isWideLayout) {
            if (sidebarContainer != null) {
                sidebarContainer.removeAllViews();
                if (mode == ViewMode.BUILD) {
                    sidebarContainer.addView(buildView);
                } else if (mode == ViewMode.ARTEFAKTE) {
                    refreshArtifactsView();
                    sidebarContainer.addView(artifactsView);
                } else {
                    sidebarContainer.addView(filesView);
                }
            }
            return;
        }

        contentFrame.removeAllViews();
        switch (mode) {
            case CODE:
                contentFrame.addView(codeEditor);
                symbolBar.setVisibility(View.VISIBLE);
                docTabStripScroll.setVisibility(View.VISIBLE);
                break;
            case DATEIEN:
                refreshProjectTree();
                contentFrame.addView(filesView);
                symbolBar.setVisibility(View.GONE);
                docTabStripScroll.setVisibility(View.GONE);
                break;
            case BUILD:
                contentFrame.addView(buildView);
                symbolBar.setVisibility(View.GONE);
                docTabStripScroll.setVisibility(View.GONE);
                break;
            case ARTEFAKTE:
                refreshArtifactsView();
                contentFrame.addView(artifactsView);
                symbolBar.setVisibility(View.GONE);
                docTabStripScroll.setVisibility(View.GONE);
                break;
        }
    }

    private void updateNavTabStyles() {
        int activeColor = isDarkTheme ? Color.parseColor("#006B5E") : Color.parseColor("#006B5E");
        int activeText = Color.WHITE;
        int inactiveColor = isDarkTheme ? Color.parseColor("#21252B") : Color.parseColor("#E0E0E0");
        int inactiveText = isDarkTheme ? Color.parseColor("#ABB2BF") : Color.parseColor("#555555");

        setTabStyle(tabCode, currentMode == ViewMode.CODE, activeColor, activeText, inactiveColor, inactiveText);
        setTabStyle(tabFiles, currentMode == ViewMode.DATEIEN, activeColor, activeText, inactiveColor, inactiveText);
        setTabStyle(tabBuild, currentMode == ViewMode.BUILD, activeColor, activeText, inactiveColor, inactiveText);
        setTabStyle(tabArtifacts, currentMode == ViewMode.ARTEFAKTE, activeColor, activeText, inactiveColor, inactiveText);
    }

    private void setTabStyle(Button b, boolean active, int aBg, int aFg, int iBg, int iFg) {
        b.setBackgroundColor(active ? aBg : iBg);
        b.setTextColor(active ? aFg : iFg);
    }

    private void applyTheme() {
        int bg = isDarkTheme ? Color.parseColor("#181A1F") : Color.parseColor("#F5F6F8");
        int barBg = isDarkTheme ? Color.parseColor("#21252B") : Color.parseColor("#EAEAEA");
        int textColor = isDarkTheme ? Color.parseColor("#ABB2BF") : Color.parseColor("#282C34");

        rootContainer.setBackgroundColor(bg);
        topBar.setBackgroundColor(barBg);
        bottomBar.setBackgroundColor(barBg);
        docTabStripScroll.setBackgroundColor(barBg);

        titleLabel.setTextColor(textColor);
        fileInfoLabel.setTextColor(isDarkTheme ? Color.parseColor("#5C6370") : Color.parseColor("#7F848E"));
        statusIndicator.setTextColor(textColor);

        codeEditor.setDarkTheme(isDarkTheme);
        symbolBar.setDarkTheme(isDarkTheme);

        updateNavTabStyles();
        refreshDocTabs();
    }

    private void restoreLastProject() {
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        String savedUri = p.getString(PREF_KEY_URI, null);
        String savedLocal = p.getString(PREF_KEY_LOCAL, null);

        if (savedLocal != null) {
            File localDir = new File(savedLocal);
            if (localDir.isDirectory()) {
                ProjectManager.getInstance().setProject(this, null, localDir, localDir.getName());
                updateProjectHeader();
                openDefaultProjectFile();
                return;
            }
        }

        if (savedUri != null) {
            Uri uri = Uri.parse(savedUri);
            ProjectManager.getInstance().setProject(this, uri, null, null);
            updateProjectHeader();
            openDefaultProjectFile();
        }

        if (DocumentManager.getInstance().getOpenDocuments().isEmpty()) {
            DocumentModel starter = DocumentManager.getInstance().openDocument("main.cpp",
                    "#include <iostream>\n\nint main() {\n    std::cout << \"KR IDE v" + getAppVersionName() + " C++/Rust/Go ready!\" << std::endl;\n    return 0;\n}\n",
                    null, null);
            codeEditor.setDocument(starter);
            refreshDocTabs();
            updateProjectHeader();
        }
    }

    private void openDefaultProjectFile() {
        // Prefer the primary source of KR (.kr), C++, Rust and Go projects.
        ProjectManager.FileNode root = ProjectManager.getInstance().getRootNode();
        if (root == null) return;
        ProjectManager.FileNode mainFile = findSourceFile(root, "main.kr");
        if (mainFile == null) mainFile = findSourceFile(root, "main.cpp");
        if (mainFile == null) mainFile = findSourceFile(root, "lib.rs");
        if (mainFile == null) mainFile = findSourceFile(root, "main.rs");
        if (mainFile == null) mainFile = findSourceFile(root, "main.go");
        if (mainFile == null) mainFile = findSourceFile(root, "CMakeLists.txt");
        if (mainFile != null) {
            openFileNode(mainFile);
        }
    }

    private ProjectManager.FileNode findSourceFile(ProjectManager.FileNode node, String targetName) {
        if (!node.isDirectory && node.name.equalsIgnoreCase(targetName)) return node;
        for (ProjectManager.FileNode child : node.children) {
            ProjectManager.FileNode found = findSourceFile(child, targetName);
            if (found != null) return found;
        }
        return null;
    }

    private void updateProjectHeader() {
        String projName = ProjectManager.getInstance().getActiveProjectName();
        DocumentModel active = DocumentManager.getInstance().getActiveDocument();
        String fileText = (active != null ? active.getDisplayName() + (active.isModified() ? " *" : "") : "Keine Datei");
        fileInfoLabel.setText(projName + " · " + fileText);
    }

    // --- Document Tab Bar ---
    private void refreshDocTabs() {
        docTabStrip.removeAllViews();
        List<DocumentModel> docs = DocumentManager.getInstance().getOpenDocuments();
        DocumentModel active = DocumentManager.getInstance().getActiveDocument();

        for (DocumentModel doc : docs) {
            Button tab = new Button(this);
            String title = doc.getDisplayName() + (doc.isModified() ? " *" : "");
            tab.setText(title);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            tab.setGravity(Gravity.CENTER);

            boolean isCurrent = (doc == active);
            int bg = isCurrent ? (isDarkTheme ? Color.parseColor("#282C34") : Color.parseColor("#FFFFFF"))
                    : (isDarkTheme ? Color.parseColor("#1E2227") : Color.parseColor("#E0E0E0"));
            int fg = isCurrent ? (isDarkTheme ? Color.parseColor("#61AFEF") : Color.parseColor("#006B5E"))
                    : (isDarkTheme ? Color.parseColor("#ABB2BF") : Color.parseColor("#555555"));

            tab.setBackgroundColor(bg);
            tab.setTextColor(fg);
            tab.setMinimumWidth(dp(90));
            tab.setMinimumHeight(dp(36));
            tab.setPadding(dp(12), 0, dp(12), 0);

            tab.setOnClickListener(v -> DocumentManager.getInstance().setActiveDocument(doc));
            tab.setOnLongClickListener(v -> {
                showCloseDocumentDialog(doc);
                return true;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMargins(dp(2), 0, dp(2), 0);
            docTabStrip.addView(tab, lp);
        }
    }

    private void showCloseDocumentDialog(DocumentModel doc) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Datei schließen");
        b.setMessage("Möchtest du '" + doc.getDisplayName() + "' schließen?");
        b.setPositiveButton("Schließen", (d, w) -> {
            if (doc.isModified()) {
                saveDocumentWithConfirm(doc, () -> DocumentManager.getInstance().closeDocument(doc));
            } else {
                DocumentManager.getInstance().closeDocument(doc);
            }
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    private void saveDocumentWithConfirm(DocumentModel doc, Runnable onDone) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Ungespeicherte Änderungen");
        b.setMessage("Möchtest du '" + doc.getDisplayName() + "' vor dem Schließen speichern?");
        b.setPositiveButton("Speichern", (d, w) -> {
            DocumentManager.getInstance().saveDocument(this, doc);
            onDone.run();
        });
        b.setNegativeButton("Verwerfen", (d, w) -> onDone.run());
        b.show();
    }

    // --- Project Selection & Tree ---
    private void chooseProjectFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, PICK_PROJECT);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_PROJECT && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (SecurityException ignored) {}
            getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_URI, uri.toString()).remove(PREF_KEY_LOCAL).apply();
            ProjectManager.getInstance().setProject(this, uri, null, null);
            updateProjectHeader();
            refreshProjectTree();
            openDefaultProjectFile();
        }
    }

    private void refreshProjectTree() {
        if (filesView == null) return;
        ScrollView scroll = (ScrollView) filesView.getChildAt(1);
        if (scroll == null) return;
        LinearLayout treeLayout = (LinearLayout) scroll.getChildAt(0);
        if (treeLayout == null) return;
        treeLayout.removeAllViews();

        ProjectManager.FileNode root = ProjectManager.getInstance().getRootNode();
        if (root == null) {
            TextView empty = new TextView(this);
            empty.setText("Kein Projektordner geöffnet.\nTippe auf 'Ordner öffnen' oder 'Menü' → 'GitHub-Projekt klonen'.");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            empty.setTextColor(Color.GRAY);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            treeLayout.addView(empty);
            return;
        }

        renderTreeNode(treeLayout, root);
    }

    private void renderTreeNode(LinearLayout container, ProjectManager.FileNode node) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(node.depth * 16 + 8), dp(6), dp(8), dp(6));
        row.setMinimumHeight(dp(44));

        TextView icon = new TextView(this);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        if (node.isDirectory) {
            icon.setText(node.isExpanded ? "📂 " : "📁 ");
        } else {
            icon.setText(getFileIcon(node.name) + " ");
        }
        row.addView(icon);

        TextView label = new TextView(this);
        label.setText(node.name);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        label.setTypeface(Typeface.DEFAULT, node.isDirectory ? Typeface.BOLD : Typeface.NORMAL);
        label.setTextColor(isDarkTheme ? Color.parseColor("#ABB2BF") : Color.parseColor("#282C34"));
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (node.isDirectory) {
            row.setOnClickListener(v -> {
                node.isExpanded = !node.isExpanded;
                refreshProjectTree();
            });
            row.setOnLongClickListener(v -> {
                showFolderContextMenu(node);
                return true;
            });
        } else {
            row.setOnClickListener(v -> openFileNode(node));
            row.setOnLongClickListener(v -> {
                showFileContextMenu(node);
                return true;
            });
        }

        container.addView(row);

        if (node.isDirectory && node.isExpanded) {
            for (ProjectManager.FileNode child : node.children) {
                renderTreeNode(container, child);
            }
        }
    }

    private String getFileIcon(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "🔷";
        if (lower.endsWith(".h") || lower.endsWith(".hpp")) return "🔶";
        if (lower.endsWith(".c")) return "🔹";
        if (lower.endsWith(".rs") || lower.equals("cargo.toml")) return "🦀";
        if (lower.endsWith(".go") || lower.equals("go.mod")) return "🐹";
        if (lower.equals("cmakelists.txt") || lower.endsWith(".cmake")) return "⚙️";
        if (lower.endsWith(".json") || lower.endsWith(".xml")) return "📜";
        if (lower.endsWith(".md") || lower.endsWith(".txt")) return "📄";
        if (ProjectManager.isBinaryFile(filename)) return "🔒";
        return "📄";
    }

    private void openFileNode(ProjectManager.FileNode node) {
        if (node.isDirectory) return;
        if (ProjectManager.isBinaryFile(node.name)) {
            Toast.makeText(this, "Binärdatei kann nicht im Texteditor geöffnet werden.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String content = ProjectManager.getInstance().readFile(this, node);
            DocumentModel doc = DocumentManager.getInstance().openDocument(node.relativePath, content, node.uri, node.localFile);
            codeEditor.setDocument(doc);
            switchView(ViewMode.CODE);
            updateProjectHeader();
        } catch (IOException e) {
            Toast.makeText(this, "Datei konnte nicht geöffnet werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showFileContextMenu(ProjectManager.FileNode node) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(node.name);
        String[] options = {"Umbenennen", "Löschen"};
        b.setItems(options, (d, which) -> {
            if (which == 0) showRenameDialog(node);
            else if (which == 1) showDeleteConfirmDialog(node);
        });
        b.show();
    }

    private void showFolderContextMenu(ProjectManager.FileNode node) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Ordner: " + node.name);
        String[] options = {"Neue Datei in Ordner", "Neuer Unterordner", "Umbenennen", "Löschen"};
        b.setItems(options, (d, which) -> {
            if (which == 0) showCreateFileDialog(node);
            else if (which == 1) showCreateFolderDialog(node);
            else if (which == 2) showRenameDialog(node);
            else if (which == 3) showDeleteConfirmDialog(node);
        });
        b.show();
    }

    private void showCreateFileDialog(ProjectManager.FileNode parentDir) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Neue Datei erstellen");
        final EditText input = new EditText(this);
        input.setHint("Dateiname, z.B. main.cpp, helper.hpp");
        b.setView(input);
        b.setPositiveButton("Erstellen", (d, w) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                try {
                    String initial = name.endsWith(".cpp") ? "#include <iostream>\n\nint main() {\n    std::cout << \"Hallo von KR IDE!\" << std::endl;\n    return 0;\n}\n" : "";
                    ProjectManager.getInstance().createNewFile(this, parentDir, name, initial);
                    refreshProjectTree();
                    Toast.makeText(this, "Datei erstellt: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    private void showCreateFolderDialog(ProjectManager.FileNode parentDir) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Neuen Ordner erstellen");
        final EditText input = new EditText(this);
        input.setHint("Ordnername");
        b.setView(input);
        b.setPositiveButton("Erstellen", (d, w) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                try {
                    ProjectManager.getInstance().createNewFolder(this, parentDir, name);
                    refreshProjectTree();
                    Toast.makeText(this, "Ordner erstellt: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    private void showRenameDialog(ProjectManager.FileNode node) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Umbenennen: " + node.name);
        final EditText input = new EditText(this);
        input.setText(node.name);
        b.setView(input);
        b.setPositiveButton("Speichern", (d, w) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(node.name)) {
                try {
                    ProjectManager.getInstance().renameNode(this, node, newName);
                    refreshProjectTree();
                } catch (Exception e) {
                    Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    private void showDeleteConfirmDialog(ProjectManager.FileNode node) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Löschen bestätigen");
        b.setMessage("Möchtest du '" + node.name + "' wirklich unwiderruflich löschen?");
        b.setPositiveButton("Löschen", (d, w) -> {
            try {
                ProjectManager.getInstance().deleteNode(this, node);
                refreshProjectTree();
                Toast.makeText(this, "Gelöscht: " + node.name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    // --- Build & Compilation ---
    private void compileCurrentProject() {
        if (BuildManager.getInstance().isBuilding()) {
            Toast.makeText(this, "Build läuft bereits …", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save all unsaved open documents first
        DocumentManager.getInstance().saveAll(this);

        Uri safUri = ProjectManager.getInstance().getActiveSafTreeUri();
        File localDir = ProjectManager.getInstance().getActiveLocalDir();

        if (safUri == null && localDir == null) {
            Toast.makeText(this, "Bitte zuerst einen Projektordner auswählen oder ein Repository klonen.", Toast.LENGTH_LONG).show();
            switchView(ViewMode.DATEIEN);
            return;
        }

        switchView(ViewMode.BUILD);
        btnCompile.setEnabled(false);
        btnCancelBuild.setEnabled(true);
        statusIndicator.setText("Wird kompiliert …");
        tabBuild.setText("BUILD (läuft…)");

        CompilerEngine.Target target = BuildManager.getInstance().getCurrentTarget();
        boolean allowOnline = BuildManager.getInstance().isOnlineDependencies();

        BuildManager.getInstance().startBuild(this, safUri, localDir, target, allowOnline);
    }

    private void copyBuildLogToClipboard() {
        String log = BuildManager.getInstance().getFullLog();
        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(android.content.ClipData.newPlainText("KR_BUILD_LOG", log));
            Toast.makeText(this, "Build-Protokoll in die Zwischenablage kopiert.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- BuildManager Callbacks ---
    @Override
    public void onLogLine(String line) {
        appendLogLineView(line);
    }

    @Override
    public void onStatusChanged(String status, boolean isBuilding) {
        statusIndicator.setText(status);
        btnCompile.setEnabled(!isBuilding);
        btnCancelBuild.setEnabled(isBuilding);
        tabBuild.setText(isBuilding ? "BUILD (läuft…)" : "BUILD");
    }

    @Override
    public void onBuildFinished(boolean success, String summary) {
        statusIndicator.setText(summary);
        btnCompile.setEnabled(true);
        btnCancelBuild.setEnabled(false);
        tabBuild.setText(success ? "BUILD (Fertig)" : "BUILD (Fehler)");
        Toast.makeText(this, summary, Toast.LENGTH_LONG).show();

        if (success) {
            refreshArtifactsView();
        }
    }

    private void appendLogLineView(final String line) {
        if (buildLogContainer == null) return;
        TextView tv = new TextView(this);
        tv.setText(line);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(15f, editorFontSize - 2f));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(0, dp(2), 0, dp(2));

        // Highlight diagnostics & errors
        final DiagnosticParser.Item diag = DiagnosticParser.parseLine(line);
        if (diag != null) {
            tv.setTextIsSelectable(false);
            tv.setFocusable(true);
            tv.setClickable(true);

            if (diag.severity == DiagnosticParser.Severity.ERROR) {
                tv.setTextColor(Color.parseColor("#E06C75")); // Red
                tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            } else if (diag.severity == DiagnosticParser.Severity.WARNING) {
                tv.setTextColor(Color.parseColor("#E5C07B")); // Yellow/Amber
            }

            // Clickable diagnostic navigation to source code!
            tv.setOnClickListener(v -> navigateToDiagnostic(diag));
            tv.setBackgroundColor(isDarkTheme ? Color.parseColor("#2C313A") : Color.parseColor("#EAEAEA"));
        } else {
            tv.setTextIsSelectable(true);
            if (line.startsWith("FEHLER") || line.contains("failed") || line.contains("error:")) {
                tv.setTextColor(Color.parseColor("#E06C75"));
            } else if (line.startsWith("===") || line.startsWith("Fertig")) {
                tv.setTextColor(Color.parseColor("#98C379")); // Green
                tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            } else {
                tv.setTextColor(isDarkTheme ? Color.parseColor("#ABB2BF") : Color.parseColor("#383A42"));
            }
        }

        buildLogContainer.addView(tv);

        // Auto-scroll to bottom
        buildLogScroll.post(() -> buildLogScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void navigateToDiagnostic(DiagnosticParser.Item diag) {
        if (diag == null || diag.file == null) return;
        // Normalize filename
        String file = diag.file.replace('\\', '/');
        int lastSlash = file.lastIndexOf('/');
        String basename = (lastSlash >= 0) ? file.substring(lastSlash + 1) : file;

        // 1. Check if matching document is already open
        DocumentModel doc = DocumentManager.getInstance().findByRelativePath(file);
        if (doc == null) doc = DocumentManager.getInstance().findByRelativePath(basename);
        if (doc != null) {
            DocumentManager.getInstance().setActiveDocument(doc);
            codeEditor.setDocument(doc);
            switchView(ViewMode.CODE);
            final int l = diag.line;
            final int c = diag.column;
            codeEditor.post(() -> codeEditor.jumpToLine(l, c));
            Toast.makeText(this, basename + " : Zeile " + diag.line + " - " + diag.message, Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Search in project tree
        ProjectManager.FileNode root = ProjectManager.getInstance().getRootNode();
        if (root != null) {
            ProjectManager.FileNode targetNode = findNodeByPathOrName(root, file);
            if (targetNode == null) targetNode = findNodeByPathOrName(root, basename);
            if (targetNode != null) {
                openFileNode(targetNode);
                final int l = diag.line;
                final int c = diag.column;
                codeEditor.post(() -> codeEditor.jumpToLine(l, c));
                Toast.makeText(this, targetNode.name + " : Zeile " + diag.line + " - " + diag.message, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(this, "Quelldatei nicht gefunden: " + basename, Toast.LENGTH_SHORT).show();
    }

    private ProjectManager.FileNode findNodeByPathOrName(ProjectManager.FileNode current, String target) {
        if (!current.isDirectory) {
            if (current.relativePath.endsWith(target) || current.name.equals(target)) return current;
            int slash = target.lastIndexOf('/');
            if (slash >= 0 && current.name.equals(target.substring(slash + 1))) return current;
        }
        for (ProjectManager.FileNode child : current.children) {
            ProjectManager.FileNode found = findNodeByPathOrName(child, target);
            if (found != null) return found;
        }
        return null;
    }

    // --- Artifacts View ---
    private void refreshArtifactsView() {
        if (artifactsView == null) return;
        ScrollView scroll = (ScrollView) artifactsView.getChildAt(1);
        if (scroll == null) return;
        LinearLayout list = (LinearLayout) scroll.getChildAt(0);
        if (list == null) return;
        list.removeAllViews();

        List<ArtifactsManager.ArtifactItem> artifacts = ArtifactsManager.getArtifacts(this);
        if (artifacts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Noch keine Build-Artefakte vorhanden.\nStarte einen Build unter 'Kompilieren'.");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            empty.setTextColor(Color.GRAY);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            list.addView(empty);
            return;
        }

        for (ArtifactsManager.ArtifactItem item : artifacts) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(isDarkTheme ? Color.parseColor("#21252B") : Color.parseColor("#FFFFFF"));
            card.setPadding(dp(16), dp(12), dp(16), dp(12));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, 0, dp(12));
            card.setLayoutParams(clp);

            TextView title = new TextView(this);
            title.setText(item.name);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
            card.addView(title);

            TextView desc = new TextView(this);
            desc.setText(item.getPlatformDescription() + " · " + item.getReadableSize());
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            desc.setTextColor(Color.GRAY);
            desc.setPadding(0, dp(4), 0, dp(8));
            card.addView(desc);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            if (item.isApk) {
                Button btnInstall = new Button(this);
                btnInstall.setText("Installieren & Ausführen");
                btnInstall.setOnClickListener(v -> ArtifactsManager.installApk(this, item));
                btnRow.addView(btnInstall);
            }

            Button btnShare = new Button(this);
            btnShare.setText("Teilen");
            btnShare.setOnClickListener(v -> ArtifactsManager.shareFile(this, item));
            btnRow.addView(btnShare);

            card.addView(btnRow);
            list.addView(card);
        }
    }

    // --- DocumentManager & Editor Callbacks ---
    @Override public void onDocumentOpened(DocumentModel doc) { refreshDocTabs(); updateProjectHeader(); }
    @Override public void onDocumentClosed(DocumentModel doc) { refreshDocTabs(); updateProjectHeader(); }
    @Override public void onActiveDocumentChanged(DocumentModel doc) {
        codeEditor.setDocument(doc);
        refreshDocTabs();
        updateProjectHeader();
    }
    @Override public void onDocumentModified(DocumentModel doc) { refreshDocTabs(); updateProjectHeader(); }
    @Override public void onModified(DocumentModel doc) { updateProjectHeader(); }

    private void saveActiveDocument() {
        DocumentModel doc = DocumentManager.getInstance().getActiveDocument();
        if (doc != null) {
            boolean ok = DocumentManager.getInstance().saveDocument(this, doc);
            if (ok) {
                Toast.makeText(this, "Gespeichert: " + doc.getDisplayName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Speichern fehlgeschlagen!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- Main Menu ---
    private void showMainMenu(View anchor) {
        String[] items = {
                "Projektordner öffnen…",
                "GitHub-Projekt klonen…",
                "Neues KR Unified-Projekt (.kr)…",
                "Neues GUI-Projekt…",
                "Neues Rust-/Go-Projekt…",
                "VCPKG-Bibliotheken…",
                "Neue Datei…",
                "Neuer Ordner…",
                "Zuletzt geöffnete Projekte…",
                "Adaptive Compute analysieren…",
                "Einstellungen…",
                "Über KR IDE"
        };
        new AlertDialog.Builder(this)
                .setTitle("Menü")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: chooseProjectFolder(); break;
                        case 1: showGitCloneDialog(); break;
                        case 2: showCreateKrProjectDialog(); break;
                        case 3: showCreateGuiProjectDialog(); break;
                        case 4: showCreateLanguageProjectDialog(); break;
                        case 5: showVcpkgLibrariesDialog(); break;
                        case 6: showCreateFileDialog(null); break;
                        case 7: showCreateFolderDialog(null); break;
                        case 8: showRecentProjectsDialog(); break;
                        case 9: showAdaptiveComputeDialog(); break;
                        case 10: showSettingsDialog(); break;
                        case 11: showAboutDialog(); break;
                    }
                })
                .setNegativeButton("Schließen", null)
                .show();
    }

    private void showAdaptiveComputeDialog() {
        ProjectManager projects = ProjectManager.getInstance();
        File localProject = projects.getActiveLocalDir();
        Uri safProject = projects.getActiveSafTreeUri();
        if ((localProject == null || !localProject.isDirectory()) && safProject == null) {
            new AlertDialog.Builder(this).setTitle("Adaptive Compute")
                    .setMessage("Für die Analyse muss zuerst ein KR-Projektordner geöffnet werden.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        DocumentModel activeDocument = DocumentManager.getInstance().getActiveDocument();
        if (activeDocument != null && activeDocument.isModified()
                && !DocumentManager.getInstance().saveDocument(this, activeDocument)) {
            new AlertDialog.Builder(this).setTitle("Adaptive Compute – Fehler")
                    .setMessage("Die geöffnete Datei konnte vor der Analyse nicht gespeichert werden.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Adaptive Compute")
                .setMessage(safProject != null
                        ? "Android-Projektordner wird für die Analyse eingelesen …"
                        : "KR-Projekt wird analysiert …")
                .setCancelable(false)
                .create();
        progress.show();

        Context applicationContext = getApplicationContext();
        String projectName = projects.getActiveProjectName();
        new Thread(() -> {
            File temporaryProject = null;
            try {
                File analysisProject = localProject;
                if (safProject != null) {
                    temporaryProject = new File(getCacheDir(), "adaptive-compute-project");
                    resetAnalysisDirectory(temporaryProject);
                    DocumentTree.importProject(applicationContext, safProject, temporaryProject);
                    analysisProject = temporaryProject;
                }
                if (analysisProject == null || !analysisProject.isDirectory()) {
                    throw new IOException("Der geöffnete Projektordner ist nicht lesbar.");
                }

                KrProjectConfig config = KrProjectConfig.parse(
                        KrProjectConfig.findProjectConfigFile(analysisProject), projectName, false);
                KrComputePlan plan = KrAdaptiveComputeAnalyzer.analyzeProject(analysisProject, config.computeMode);
                String formattedReport = KrAdaptiveComputeAnalyzer.formatReport(plan);
                File telemetryFile = new File(CompilerEngine.getActiveWorkspaceOutput(applicationContext),
                        "adaptive_compute_telemetry.jsonl");
                KrComputeTelemetry telemetry = KrComputeTelemetry.readLast(telemetryFile);
                formattedReport += telemetry != null ? telemetry.formatReport()
                        : "\nLaufzeit-Telemetrie\nNoch keine Messung vorhanden. Nach einem Programmlauf wird hier "
                        + "Gerätestatus, Zeit, Transfer, Speicher und Validierung angezeigt.\n";
                final String completeReport = formattedReport;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.dismiss();
                    TextView report = new TextView(this);
                    report.setText(completeReport);
                    report.setTextIsSelectable(true);
                    report.setPadding(32, 24, 32, 24);
                    ScrollView scroll = new ScrollView(this);
                    scroll.addView(report);
                    new AlertDialog.Builder(this).setTitle("Adaptive Compute")
                            .setView(scroll).setPositiveButton("Schließen", null).show();
                });
            } catch (Exception ex) {
                String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.dismiss();
                    new AlertDialog.Builder(this).setTitle("Adaptive Compute – Fehler")
                            .setMessage(message).setPositiveButton("OK", null).show();
                });
            } finally {
                if (temporaryProject != null) deleteAnalysisDirectory(temporaryProject);
            }
        }, "kr-adaptive-compute").start();
    }

    private static void resetAnalysisDirectory(File directory) throws IOException {
        deleteAnalysisDirectory(directory);
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Temporärer Analyseordner kann nicht angelegt werden.");
        }
    }

    private static void deleteAnalysisDirectory(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteAnalysisDirectory(child);
        }
        file.delete();
    }

    private void showCreateKrProjectDialog() {
        String[] kinds = {
                "KR Konsole",
                "KR GUI mit SDL3",
                "KR Sprachfusion – C++ / Rust / Go",
                "KR Sprachfusion – GUI"
        };
        LanguageProjectManager.KrTemplate[] templates = {
                LanguageProjectManager.KrTemplate.KR_CONSOLE,
                LanguageProjectManager.KrTemplate.KR_GUI,
                LanguageProjectManager.KrTemplate.KR_FUSION_CONSOLE,
                LanguageProjectManager.KrTemplate.KR_FUSION_GUI
        };
        new AlertDialog.Builder(this).setTitle("Neues KR Unified-Projekt (.kr)")
                .setItems(kinds, (first, selected) -> {
                    LanguageProjectManager.KrTemplate template = templates[selected];
                    final EditText input = new EditText(this);
                    input.setSingleLine(true);
                    input.setText("KR-" + template.name().replace('_', '-'));
                    input.setSelectAllOnFocus(true);
                    new AlertDialog.Builder(this)
                            .setTitle(kinds[selected])
                            .setMessage("Erstellt ein Projekt mit der KR Unified Language (.kr). C++20, Rust und Go können frei kombiniert und sprachübergreifend aufgerufen werden.")
                            .setView(input)
                            .setPositiveButton("Projekt erstellen", (dialog, which) -> {
                                try {
                                    File created = LanguageProjectManager.createKr(this, template, input.getText().toString());
                                    ProjectManager.getInstance().setProject(this, null, created, created.getName());
                                    getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_LOCAL, created.getAbsolutePath()).remove(PREF_KEY_URI).apply();
                                    updateProjectHeader(); refreshProjectTree(); openDefaultProjectFile(); switchView(ViewMode.CODE);
                                    Toast.makeText(this, "KR-Projekt erstellt: " + created.getName(), Toast.LENGTH_LONG).show();
                                } catch (Exception error) {
                                    new AlertDialog.Builder(this).setTitle("Projekt konnte nicht erstellt werden")
                                            .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
                                }
                            }).setNegativeButton("Abbrechen", null).show();
                }).setNegativeButton("Abbrechen", null).show();
    }

    private void showCreateLanguageProjectDialog() {
        String[] kinds = {"Rust Konsole", "Rust GUI (SDL3)", "Go Konsole", "Go GUI (SDL3)"};
        new AlertDialog.Builder(this).setTitle("Neues Rust-/Go-Projekt")
                .setItems(kinds, (first, selected) -> {
                    boolean rust = selected < 2;
                    boolean gui = selected % 2 == 1;
                    final EditText input = new EditText(this);
                    input.setSingleLine(true);
                    input.setText("KR-" + (rust ? "Rust" : "Go") + (gui ? "-GUI" : "-Konsole"));
                    input.setSelectAllOnFocus(true);
                    new AlertDialog.Builder(this)
                            .setTitle(kinds[selected])
                            .setMessage("Das Projekt kann als Android-APK (ARM64), Windows-EXE (x64) und Linux-ELF (x64) kompiliert werden.")
                            .setView(input)
                            .setPositiveButton("Projekt erstellen", (dialog, which) -> {
                                try {
                                    LanguageProjectManager.Language language = rust
                                            ? LanguageProjectManager.Language.RUST : LanguageProjectManager.Language.GO;
                                    File created = LanguageProjectManager.create(this, language, gui, input.getText().toString());
                                    ProjectManager.getInstance().setProject(this, null, created, created.getName());
                                    getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_LOCAL, created.getAbsolutePath()).remove(PREF_KEY_URI).apply();
                                    updateProjectHeader(); refreshProjectTree(); openDefaultProjectFile(); switchView(ViewMode.CODE);
                                    Toast.makeText(this, "Projekt erstellt: " + created.getName(), Toast.LENGTH_LONG).show();
                                } catch (Exception error) {
                                    new AlertDialog.Builder(this).setTitle("Projekt konnte nicht erstellt werden")
                                            .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
                                }
                            }).setNegativeButton("Abbrechen", null).show();
                }).setNegativeButton("Abbrechen", null).show();
    }

    private void showCreateGuiProjectDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("KR-GUI-Beispiel");
        input.setSelectAllOnFocus(true);
        input.setHint("Projektname");
        new AlertDialog.Builder(this)
                .setTitle("Neues GUI-Projekt")
                .setMessage("Erstellt eine portable SDL3-/Dear-ImGui-Anwendung mit CMake und vcpkg.json für Android ARM64, Windows x64 und Linux x64.")
                .setView(input)
                .setPositiveButton("Projekt erstellen", (dialog, which) -> {
                    try {
                        File project = VcpkgManager.createGuiProject(this, input.getText().toString());
                        ProjectManager.getInstance().setProject(this, null, project, project.getName());
                        getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_LOCAL, project.getAbsolutePath()).remove(PREF_KEY_URI).apply();
                        updateProjectHeader();
                        refreshProjectTree();
                        openDefaultProjectFile();
                        switchView(ViewMode.DATEIEN);
                        Toast.makeText(this, "GUI-Projekt erstellt: " + project.getName(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        new AlertDialog.Builder(this).setTitle("Projekt konnte nicht erstellt werden")
                                .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void showVcpkgLibrariesDialog() {
        File project = ProjectManager.getInstance().getActiveLocalDir();
        if (project == null || !project.isDirectory()) {
            new AlertDialog.Builder(this).setTitle("VCPKG-Bibliotheken")
                    .setMessage("Die Bibliotheksverwaltung benötigt derzeit ein lokales Projekt. Öffne ein geklontes Projekt oder erstelle ein neues GUI-Projekt.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        List<VcpkgManager.Library> catalog = VcpkgManager.catalog();
        String[] labels = new String[catalog.size()];
        boolean[] checked = new boolean[catalog.size()];
        final Set<String> selected = new LinkedHashSet<>();
        try { selected.addAll(VcpkgManager.readDependencies(project)); }
        catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("vcpkg.json konnte nicht gelesen werden")
                    .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
            return;
        }
        for (int i = 0; i < catalog.size(); i++) {
            VcpkgManager.Library library = catalog.get(i);
            labels[i] = library.title + "\n" + library.description;
            checked[i] = selected.contains(library.port);
        }
        new AlertDialog.Builder(this)
                .setTitle("VCPKG-Bibliotheken")
                .setMultiChoiceItems(labels, checked, (dialog, which, enabled) -> {
                    String port = catalog.get(which).port;
                    if (enabled) selected.add(port); else selected.remove(port);
                })
                .setPositiveButton("Manifest speichern", (dialog, which) -> {
                    try {
                        if (selected.contains("imgui")) selected.add("sdl3");
                        VcpkgManager.writeDependencies(project, selected);
                        refreshProjectTree();
                        Toast.makeText(this, "vcpkg.json gespeichert: " + selected.size() + " Bibliothek(en)", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        new AlertDialog.Builder(this).setTitle("Manifest konnte nicht gespeichert werden")
                                .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void showGitCloneDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("GitHub-Projekt klonen");
        b.setMessage("Gib die öffentliche Repository-URL ein:");
        final EditText input = new EditText(this);
        input.setHint("https://github.com/Benutzer/Repo.git");
        input.setText("https://github.com/kruemmel-python/Mycelia-CSS.git");
        b.setView(input);

        b.setPositiveButton("Herunterladen", (d, w) -> {
            String url = input.getText().toString().trim();
            if (!GitImporter.isValidGitHubUrl(url)) {
                Toast.makeText(this, "Ungültige URL. Bitte https://github.com/… eingeben.", Toast.LENGTH_LONG).show();
                return;
            }

            final AlertDialog progressDialog = new AlertDialog.Builder(this)
                    .setTitle("Repository wird heruntergeladen")
                    .setMessage("Verbindung wird hergestellt …")
                    .setCancelable(false)
                    .show();

            GitImporter.cloneOrDownload(this, url, new GitImporter.ProgressCallback() {
                @Override public void onProgress(String message) {
                    progressDialog.setMessage(message);
                }
                @Override public void onSuccess(File projectDir) {
                    progressDialog.dismiss();
                    ProjectManager.getInstance().setProject(MainActivity.this, null, projectDir, projectDir.getName());
                    getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_LOCAL, projectDir.getAbsolutePath()).remove(PREF_KEY_URI).apply();
                    updateProjectHeader();
                    refreshProjectTree();
                    openDefaultProjectFile();
                    switchView(ViewMode.DATEIEN);
                    Toast.makeText(MainActivity.this, "Projekt bereit: " + projectDir.getName(), Toast.LENGTH_LONG).show();
                }
                @Override public void onError(String error) {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Fehler beim Herunterladen")
                            .setMessage(error)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    private void showRecentProjectsDialog() {
        List<ProjectManager.RecentProject> recents = ProjectManager.getRecentProjects(this);
        if (recents.isEmpty()) {
            Toast.makeText(this, "Keine früheren Projekte vorhanden.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[recents.size()];
        for (int i = 0; i < recents.size(); i++) names[i] = recents.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle("Zuletzt geöffnete Projekte")
                .setItems(names, (d, which) -> {
                    ProjectManager.RecentProject rp = recents.get(which);
                    if (rp.uriOrPath.startsWith("file:")) {
                        File dir = new File(rp.uriOrPath.substring(5));
                        if (dir.isDirectory()) {
                            ProjectManager.getInstance().setProject(this, null, dir, rp.name);
                            getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_LOCAL, dir.getAbsolutePath()).remove(PREF_KEY_URI).apply();
                            updateProjectHeader();
                            refreshProjectTree();
                            openDefaultProjectFile();
                        }
                    } else {
                        Uri uri = Uri.parse(rp.uriOrPath);
                        ProjectManager.getInstance().setProject(this, uri, null, rp.name);
                        getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_URI, uri.toString()).remove(PREF_KEY_LOCAL).apply();
                        updateProjectHeader();
                        refreshProjectTree();
                        openDefaultProjectFile();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Einstellungen");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));

        // Dark Theme Switch
        CheckBox cbDark = new CheckBox(this);
        cbDark.setText("Dunkles Design (Dark Theme)");
        cbDark.setChecked(isDarkTheme);
        layout.addView(cbDark);

        // Auto Brackets
        CheckBox cbBrackets = new CheckBox(this);
        cbBrackets.setText("Automatische Klammern () {} [] \"\" ''");
        cbBrackets.setChecked(autoBrackets);
        layout.addView(cbBrackets);

        // Auto Indent
        CheckBox cbIndent = new CheckBox(this);
        cbIndent.setText("Automatische Einrückung nach { und Zeilenwechsel");
        cbIndent.setChecked(autoIndent);
        layout.addView(cbIndent);

        // Word Wrap
        CheckBox cbWrap = new CheckBox(this);
        cbWrap.setText("Zeilenumbruch im Editor (Word Wrap)");
        cbWrap.setChecked(wrapLines);
        layout.addView(cbWrap);

        // Font Size Label & Slider
        final TextView fontLabel = new TextView(this);
        fontLabel.setText(String.format(Locale.GERMANY, "Editor-Schriftgröße: %.0f sp (14–26)", editorFontSize));
        fontLabel.setPadding(0, dp(12), 0, dp(4));
        layout.addView(fontLabel);

        SeekBar fontSeekBar = new SeekBar(this);
        fontSeekBar.setMax(12); // 14 + 12 = 26 sp
        fontSeekBar.setProgress((int) (editorFontSize - 14));
        fontSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float size = 14f + progress;
                fontLabel.setText(String.format(Locale.GERMANY, "Editor-Schriftgröße: %.0f sp (14–26)", size));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(fontSeekBar);

        b.setView(layout);
        b.setPositiveButton("Übernehmen", (d, w) -> {
            isDarkTheme = cbDark.isChecked();
            autoBrackets = cbBrackets.isChecked();
            autoIndent = cbIndent.isChecked();
            wrapLines = cbWrap.isChecked();
            editorFontSize = 14f + fontSeekBar.getProgress();

            codeEditor.setFontSize(editorFontSize);
            codeEditor.setAutoBrackets(autoBrackets);
            codeEditor.setAutoIndent(autoIndent);
            codeEditor.setWrapLines(wrapLines);

            applyTheme();
            savePreferences();
        });
        b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    public String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "4.3.0";
        }
    }

    private void showAboutDialog() {
        String ver = getAppVersionName();
        new AlertDialog.Builder(this)
                .setTitle("KR IDE v" + ver)
                .setMessage("Entwickler: Ralf Krümmel\n\n" +
                        "Adaptive, hochproduktive Entwicklungsumgebung für Android.\n\n" +
                        "• KR Unified Language (.kr) mit C++20, Rust & Go Sprachfusion\n" +
                        "• Modulare Großprojekte (*.krproject) & Hierarchische Multi-File-Architektur\n" +
                        "• Clang/LLVM 21.1.8 C++20 Toolchain & LLD Linker\n" +
                        "• Cargo/rustc & Go-Toolchains\n" +
                        "• Targets: Android ARM64, Windows x64, Linux x64\n" +
                        "• CMake & Ninja Integration, VCPKG-Paketverwaltung\n" +
                        "• GUI-Projekte mit SDL3/Dear ImGui & OpenCL 3.0\n" +
                        "• Mobile Symbol-Tastatur & automatische Fehlernavigation\n" +
                        "• Entwickelt für Smartphones und Tablets")
                .setPositiveButton("Schließen", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
