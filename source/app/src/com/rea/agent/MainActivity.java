package com.rea.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import android.util.Base64;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int PICK_APK = 41;
    private static final int PICK_FILE = 42;
    private static final int ACCENT = Color.parseColor("#6c8cff");
    private static final int BG = Color.parseColor("#0d1117");
    private static final int BUBBLE_AI = Color.parseColor("#1c2431");
    private static final int BUBBLE_USER = Color.parseColor("#2a4dd0");
    private LinearLayout chatList;
    private EditText input;
    private ApkTools apk = new ApkTools();
    private JSONArray messages = new JSONArray();
    private SharedPreferences prefs;
    private boolean busy = false;
    private HashMap<String,Integer> toolFails = new HashMap<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("rea", MODE_PRIVATE);
        String migModel = prefs.getString("model", "");
        SharedPreferences.Editor pe = prefs.edit();
        if (migModel.contains("nvidia/")) pe.putString("model", "google/gemma-4-31b-it:free");
        if (!prefs.contains("fallback_models")) pe.putString("fallback_models", "dots-studio/dots-3-note-preview:free, openrouter/free");
        pe.apply();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(12), dp(14));
        header.setBackgroundColor(Color.parseColor("#131a24"));
        TextView title = new TextView(this);
        title.setText("REA agent");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button gear = new Button(this);
        gear.setText("settings");
        gear.setAllCaps(false);
        gear.setTextSize(13);
        gear.setTextColor(Color.parseColor("#aab6cc"));
        gear.setBackgroundColor(Color.TRANSPARENT);
        header.addView(gear);
        Button nchat = new Button(this);
        nchat.setText("new");
        nchat.setAllCaps(false);
        nchat.setTextSize(13);
        nchat.setTextColor(Color.parseColor("#aab6cc"));
        nchat.setBackgroundColor(Color.TRANSPARENT);
        header.addView(nchat);
        root.addView(header);
        View rule = new View(this);
        rule.setBackgroundColor(Color.parseColor("#232c3b"));
        rule.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(rule);

        ScrollView scroller = new ScrollView(this);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scroller.setPadding(0, dp(10), 0, 0);
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(dp(12), 0, dp(12), dp(10));
        scroller.addView(chatList);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(12));
        row.setBackgroundColor(Color.parseColor("#131a24"));
        LinearLayout inputWrap = new LinearLayout(this);
        inputWrap.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ig = new GradientDrawable();
        ig.setColor(Color.parseColor("#1a2230"));
        ig.setCornerRadius(dp(22));
        inputWrap.setBackground(ig);
        inputWrap.setPadding(dp(16), dp(10), dp(16), dp(10));
        input = new EditText(this);
        input.setHint("ask rea anything...");
        input.setHintTextColor(Color.parseColor("#6b7688"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setBackground(null);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setHorizontallyScrolling(false);
        inputWrap.addView(input);
        Button attachB = new Button(this);
        attachB.setText("+");
        attachB.setTextSize(18);
        attachB.setTextColor(Color.WHITE);
        GradientDrawable agd = new GradientDrawable();
        agd.setColor(Color.parseColor("#232c3b"));
        agd.setShape(GradientDrawable.OVAL);
        attachB.setBackground(agd);
        LinearLayout.LayoutParams atp = new LinearLayout.LayoutParams(dp(44), dp(44));
        atp.rightMargin = dp(6);
        attachB.setLayoutParams(atp);
        attachB.setOnClickListener(v -> pickAny());
        row.addView(attachB);
        row.addView(inputWrap, new LinearLayout.LayoutParams(0, -2, 1f));
        Button send = new Button(this);
        send.setText("→");
        send.setTextSize(18);
        send.setTextColor(Color.WHITE);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(ACCENT);
        sg.setShape(GradientDrawable.OVAL);
        send.setBackground(sg);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(48), dp(48));
        sp.leftMargin = dp(8);
        send.setLayoutParams(sp);
        send.setOnClickListener(v -> ask());
        row.addView(send);

        root.addView(scroller);
        root.addView(row);
        setContentView(root);

        gear.setOnClickListener(v -> showSettings());
        nchat.setOnClickListener(v -> newChat());
        if (!loadHistory()) bubble("ai", WELCOME, false);
        handleIncoming(getIntent());
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showSettings() {
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle("Settings");
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(20), dp(10), dp(20), dp(4));
        final EditText baseUrl = dlgField(p, "API base URL");
        final EditText model = dlgField(p, "Model id");
        final EditText apiKey = dlgField(p, "API key");
        final EditText ghTok = dlgField(p, "GitHub token (PAT, optional)");
        final EditText fbMods = dlgField(p, "Fallback models (comma separated)");
        baseUrl.setText(prefs.getString("base_url", "https://openrouter.ai/api/v1"));
        model.setText(prefs.getString("model", "google/gemma-4-31b-it:free"));
        apiKey.setText(prefs.getString("api_key", ""));
        ghTok.setText(prefs.getString("gh_token", ""));
        fbMods.setText(prefs.getString("fallback_models", ""));
        ab.setView(p);
        ab.setPositiveButton("save", (d, w) -> prefs.edit()
            .putString("base_url", baseUrl.getText().toString().trim())
            .putString("model", model.getText().toString().trim())
            .putString("api_key", apiKey.getText().toString().trim())
            .putString("gh_token", ghTok.getText().toString().trim())
            .putString("fallback_models", fbMods.getText().toString().trim())
            .apply());
        ab.setNegativeButton("cancel", null);
        ab.show();
    }

    private JSONObject postChat(JSONObject body, String base, String key) throws Exception {
        try {
            return post(base + "/chat/completions", body, key);
        } catch (Exception e) {
            String fb = prefs.getString("fallback_models", "");
            for (String m : fb.split(",")) {
                String mm = m.trim();
                if (mm.isEmpty() || mm.equals(body.optString("model"))) continue;
                try {
                    body.put("model", mm);
                    return post(base + "/chat/completions", body, key);
                } catch (Exception e2) { }
            }
            throw e;
        }
    }

    private EditText dlgField(LinearLayout p, String hint) {
        TextView l = new TextView(this);
        l.setText(hint); l.setTextSize(12); l.setTextColor(Color.parseColor("#8b96a8"));
        p.addView(l);
        EditText e = new EditText(this);
        e.setTextColor(Color.WHITE); e.setTextSize(14); e.setSingleLine(true);
        e.setBackground(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#1a2230")); g.setCornerRadius(dp(10));
        e.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2); lp.bottomMargin = dp(10);
        e.setLayoutParams(lp);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        p.addView(e);
        return e;
    }

    private View bubble(String who, String text, boolean chip) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        if (chip) {
            g.setColor(Color.parseColor("#171e29"));
            g.setCornerRadius(dp(12));
            g.setStroke(dp(1), Color.parseColor("#2b3648"));
            t.setTextColor(Color.parseColor("#7d8aa0"));
            t.setTextSize(12);
            wrap.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.topMargin = dp(6); wp.bottomMargin = dp(2);
            wrap.setLayoutParams(wp);
        } else if (who.equals("user")) {
            g.setColor(BUBBLE_USER); g.setCornerRadius(dp(18));
            t.setTextColor(Color.WHITE);
            wrap.setGravity(Gravity.END);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.topMargin = dp(4);
            wrap.setLayoutParams(wp);
            tp.leftMargin = dp(60);
        } else {
            g.setColor(BUBBLE_AI); g.setCornerRadius(dp(18));
            t.setTextColor(Color.parseColor("#dbe3ef"));
            wrap.setGravity(Gravity.START);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.topMargin = dp(4);
            wrap.setLayoutParams(wp);
            tp.rightMargin = dp(60);
        }
        t.setBackground(g);
        wrap.addView(t, tp);
        View[] holder = new View[]{wrap};
        chatList.post(() -> chatList.addView(holder[0]));
        return wrap;
    }

    private void log(final String s) {
        runOnUiThread(() -> {
            bubble("ai", s, false);
            scrollToBottom();
        });
    }
    private void chip(final String s) {
        runOnUiThread(() -> { bubble("chip", s, true); scrollToBottom(); });
    }
    private void scrollToBottom() {
        android.view.ViewParent vp = chatList.getParent();
        while (vp != null && !(vp instanceof ScrollView)) vp = vp.getParent();
        if (vp == null) return;
        final ScrollView sc = (ScrollView) vp;
        sc.post(() -> sc.fullScroll(View.FOCUS_DOWN));
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == PICK_APK) importUri(data.getData(), "picked.apk", true);
        else if (req == PICK_FILE) importUri(data.getData(), null, false);
    }

    private void importUri(Uri uri, String fallbackName, boolean asApk) {
        new Thread(() -> {
            try {
                String name = fallbackName == null ? "attached" : fallbackName;
                String p = uri == null ? null : uri.getLastPathSegment();
                if (p != null && p.contains(".")) name = p.substring(p.lastIndexOf('/') + 1);
                if (name.contains(":")) name = name.substring(name.lastIndexOf(':') + 1);
                boolean apkByExt = name.toLowerCase().endsWith(".apk");
                File f = asApk || apkByExt ? new File(getCacheDir(), "target.apk") : new File(getExternalFilesDir(null), name);
                InputStream in = getContentResolver().openInputStream(uri);
                OutputStream out = new FileOutputStream(f);
                byte[] buf = new byte[16384]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close(); out.close();
                if (asApk || apkByExt) {
                    apk.load(f, name);
                    log("loaded " + name + " (" + f.length() + " bytes) for reverse engineering");
                } else {
                    bubble("user", "[attached file] " + name + " (" + f.length() + " bytes)", false);
                    addMsg(new JSONObject().put("role", "user").put("content", "user attached a file: " + name + " (" + f.length() + " bytes), saved in the app folder. use read_file to read it; list_files shows everything available."));
                    scrollToBottom();
                }
            } catch (Exception e) { log("could not import file: " + e); }
        }).start();
    }

    private void pickAny() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Attach a file"), PICK_FILE);
    }

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/vnd.android.package-archive");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Choose an APK"), PICK_APK);
    }

    private static final String SYSTEM =
    "You are REA, a powerful agent running on the user's phone: a reverse engineer, chat companion, general research/task agent and GitHub operator. " +
    "Tools: pick_apk (user chooses an APK), analyze_apk, dex_strings, find_in_apk (APK inspection); fetch_url (read a web page); " +
    "web_search (search the web, returns results with titles+urls); http_request (call any API: method, url, headers, body); " +
    "notes (your durable memory across chats: action 'save' appends, 'read' returns all); save_file (write a text file to the phone); " +
    "generate_image (create any picture from a text description; it is saved and shown in the chat, use it whenever the user wants an image, drawing, poster or illustration). " +
    "Files: the + button in the chat bar lets the user attach ANY file (apk, zip, txt, json, csv) straight into the chat; an apk loads into analyze_apk right away, other files are readable with read_file and listed with list_files. " +
    "GitHub tools (need a token in settings): github_list (list the user's repos), github_read (read a file from a repo), " +
    "github_commit (create or update a file in a repo; the user confirms first), github_release (create a release and attach the APK; the user confirms first), " +
    "github_build (trigger the cloud build workflow on a repo and wait for the apk; the user confirms first; on success follow with github_latest_apk), " +
    "github_latest_apk (download the latest release APK of a repo onto the phone; an install button appears in the chat and the result carries the apk download link). " +
    "Rules you must keep: (1) some actions need the user's confirmation before they run; when a tool result starts with DECLINED, the user said no: do not retry silently, ask what to change. " +
    "(2) When a tool result starts with STOP RULE, that tool failed 3 times this session: stop calling it and tell the user what is blocked and what would fix it. " +
    "(3) Never guess values you can look up with a tool; if an input is missing and no tool can find it, ask the user. " +
    "(4) Save key findings and task state to notes so you remember them in later chats; your saved notes are already in this prompt. " +
    "Work like a pro: plan multi-step tasks, call tools before claiming anything, cite what you saw, chain tools when needed " +
    "(search -> fetch -> analyze), and report results in plain language. You cannot make calls, texts, payments, or access the " +
    "user's accounts beyond the GitHub token they saved in settings. Keep answers compact but complete.";

    private void ask() {
        String q = input.getText().toString().trim();
        if (q.isEmpty() || busy) return;
        input.setText("");
        bubble("user", q, false);
        scrollToBottom();
        busy = true;
        new Thread(() -> {
            try {
                addMsg(new JSONObject().put("role", "user").put("content", q));
                String savedNotes = getSharedPreferences("rea_notes", MODE_PRIVATE).getString("notes", "");
                if (savedNotes == null) savedNotes = "";
                String sysContent = SYSTEM;
                if (!savedNotes.isEmpty()) {
                    String tail = savedNotes.length() > 4000 ? savedNotes.substring(savedNotes.length() - 4000) : savedNotes;
                    sysContent += "\n\nYour saved notes (durable memory from earlier chats), newest last:\n" + tail;
                }
                if (messages.length() == 0 || !"system".equals(messages.getJSONObject(0).optString("role", ""))) {
                    JSONArray nm = new JSONArray();
                    nm.put(new JSONObject().put("role", "system").put("content", sysContent));
                    for (int i = 0; i < messages.length(); i++) nm.put(messages.getJSONObject(i));
                    messages = nm;
                    saveMessages();
                }
                String base = prefs.getString("base_url", "").replaceAll("/+$", "");
                String model = prefs.getString("model", "");
                String key = prefs.getString("api_key", "");
                if (key.isEmpty()) {
                    JSONArray fmsgs = new JSONArray();
                    for (int i = 0; i < messages.length(); i++) {
                        JSONObject m = messages.getJSONObject(i);
                        String r = m.optString("role", "");
                        if (r.equals("system") || r.equals("user") || (r.equals("assistant") && m.optString("content", "").length() > 0)) fmsgs.put(m);
                    }
                    JSONObject fbody = new JSONObject().put("model", "openai").put("messages", fmsgs).put("max_tokens", 2048);
                    JSONObject fresp = post("https://text.pollinations.ai/openai", fbody, "");
                    String fcontent = fresp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
                    addMsg(new JSONObject().put("role", "assistant").put("content", fcontent));
                    log(fcontent.isEmpty() ? "(empty reply, try again)" : fcontent);
                    busy = false; return;
                }
                if (base.isEmpty() || model.isEmpty()) {
                    log("set base_url and model in settings (leave the key empty to stay on the free built-in brain).");
                    busy = false; return;
                }
                for (int round = 0; round < 16; round++) {
                    JSONObject body = new JSONObject()
                        .put("model", model)
                        .put("messages", messages)
                        .put("max_tokens", 8192)
                        .put("tools", tools());
                    JSONObject resp = postChat(body, base, key);
                    JSONObject msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
                    JSONObject outMsg = new JSONObject().put("role", "assistant");
                    if (msg.has("content") && !msg.isNull("content")) outMsg.put("content", msg.getString("content"));
                    addMsg(outMsg);
                    if (msg.has("tool_calls") && !msg.isNull("tool_calls")) {
                        JSONArray calls = msg.getJSONArray("tool_calls");
                        for (int i = 0; i < calls.length(); i++) {
                            JSONObject c = calls.getJSONObject(i).getJSONObject("function");
                            String name = c.getString("name");
                            JSONObject args = c.has("arguments") ? new JSONObject(c.getString("arguments")) : new JSONObject();
                            if (name.equals("pick_apk")) { chip("waiting for you to tap attach"); runOnUiThread(this::pickApk); addMsg(new JSONObject().put("role", "tool").put("tool_call_id", calls.getJSONObject(i).getString("id")).put("content", "the picker is open, the user will choose an apk; analyze_apk works once it loads")); continue; }
                            chip(name + (name.equals("fetch_url") ? ": " + args.optString("url", "") : (name.equals("github_commit") || name.equals("github_release") || name.equals("github_build") ? ": " + args.optString("repo", "") : "")));
                            String result;
                            int tier = tierOf(name, args);
                            if (tier >= 2) {
                                boolean ok = askUser("REA wants to run " + name + ":\n" + describe(name, args) + "\n\nallow it?");
                                if (ok) {
                                    result = runTool(name, args);
                                    audit(name + " " + describe(name, args));
                                    noteOutcome(name, result);
                                } else {
                                    result = "DECLINED: the user did not confirm this action. Do not retry it silently; ask what they want to change.";
                                    audit("declined " + name + " " + describe(name, args));
                                }
                            } else if (toolFails.containsKey(name) && toolFails.get(name) >= 3) {
                                result = "STOP RULE: " + name + " has failed 3 times this session. Stop calling it and tell the user what is blocked and what would fix it.";
                            } else {
                                result = runTool(name, args);
                                audit(name + " " + describe(name, args));
                                noteOutcome(name, result);
                            }
                            addMsg(new JSONObject().put("role", "tool")
                                .put("tool_call_id", calls.getJSONObject(i).getString("id"))
                                .put("content", result));
                        }
                        continue;
                    }
                    String content = msg.optString("content", "");
                    if (!content.isEmpty()) log(content);
                    break;
                }
            } catch (Throwable t) {
                log("error: " + t.getMessage());
                if (messages.length() > 0) { messages.remove(messages.length() - 1); saveMessages(); }
            }
            busy = false;
        }).start();
    }

    private String fetchUrl(String url) {
        try {
            if (url == null || url.isEmpty()) return "error: no url given";
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) rea-agent/1.1");
            InputStream is = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0 && bo.size() < 400000) bo.write(buf, 0, n);
            is.close();
            String s = bo.toString("UTF-8");
            s = s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
            s = s.replaceAll("(?s)<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&")
                 .replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("\\s{2,}", "\n").trim();
            if (s.length() > 9000) s = s.substring(0, 9000) + "\n...(truncated)";
            return "HTTP " + c.getResponseCode() + " from " + url + ":\n" + s;
        } catch (Exception e) { return "error: " + e; }
    }


    private String webSearch(String query) {
        try {
            if (query == null || query.isEmpty()) return "error: no query";
            String u = "https://lite.duckduckgo.com/lite/?q=" + java.net.URLEncoder.encode(query, "UTF-8");
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000); c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) rea-agent/2.0");
            InputStream is = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0 && bo.size() < 300000) bo.write(buf, 0, n);
            is.close();
            String s = bo.toString("UTF-8");
            s = s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
            s = s.replaceAll("(?s)<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&")
                 .replaceAll("&#x27;", "'").replaceAll("&quot;", "\"").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                 .replaceAll("[ \\t]+", " ").replaceAll("(?m)^\\s+", "").replaceAll("(\\n\\s*){2,}", "\n").trim();
            if (s.length() > 8000) s = s.substring(0, 8000) + "\n...(truncated)";
            return "web search results for '" + query + "':\n" + s;
        } catch (Exception e) { return "error: " + e; }
    }

    private String httpRequest(String method, String url, JSONObject headers, String body) {
        try {
            if (url == null || url.isEmpty()) return "error: no url";
            if (method == null || method.isEmpty()) method = "GET";
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method.toUpperCase());
            c.setConnectTimeout(20000); c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) rea-agent/2.0");
            if (headers != null) {
                java.util.Iterator<String> it = headers.keys();
                while (it.hasNext()) { String k = it.next(); c.setRequestProperty(k, headers.optString(k, "")); }
            }
            if (body != null && !body.isEmpty() && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
            InputStream is = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0 && bo.size() < 200000) bo.write(buf, 0, n);
            is.close();
            String s = bo.toString("UTF-8");
            if (s.length() > 6000) s = s.substring(0, 6000) + "\n...(truncated)";
            return "HTTP " + c.getResponseCode() + " " + method + " " + url + "\n" + s;
        } catch (Exception e) { return "error: " + e; }
    }

    private String notes(String action, String text) {
        SharedPreferences np = getSharedPreferences("rea_notes", MODE_PRIVATE);
        if ("save".equals(action)) {
            if (text == null || text.isEmpty()) return "error: nothing to save";
            String old = np.getString("notes", "");
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date());
            np.edit().putString("notes", old + "\n[" + stamp + "] " + text).apply();
            return "saved. notes now " + (old.length() + text.length()) + " chars, newest last.";
        }
        String all = np.getString("notes", "(no notes yet)");
        if (all.length() > 8000) all = all.substring(all.length() - 8000);
        return "notes (newest last):\n" + all;
    }

    private String saveFile(String filename, String content) {
        try {
            if (filename == null || filename.isEmpty()) filename = "rea-report.txt";
            filename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
            File dir = getExternalFilesDir(null);
            File f = new File(dir, filename);
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(content.getBytes(StandardCharsets.UTF_8));
            fo.close();
            return "saved to " + f.getAbsolutePath() + " (" + f.length() + " bytes). The user finds it in their file manager under Android/data/com.rea.agent/files/.";
        } catch (Exception e) { return "error: " + e; }
    }

    private static final String WELCOME = "REA on your phone.\n\nAsk me anything, hand me an APK to rip apart (structure, permissions, strings, hidden urls and keys), give me a link to read, a search to run, or a picture to imagine.\n\nNew in v3.1: connect me to GitHub (paste a personal access token in settings) and I can list your repos, read and commit files, cut releases with the APK attached, and pull the latest APK of any repo onto your phone.\n\nPaste an API key in settings for full agent powers, or just start talking: the free built-in brain needs no key.";

    private void addMsg(JSONObject m) {
        messages.put(m);
        saveMessages();
    }
    private void saveMessages() {
        prefs.edit().putString("rea_chat", messages.toString()).apply();
    }
    private boolean loadHistory() {
        String s = prefs.getString("rea_chat", "");
        if (s == null || s.isEmpty()) return false;
        try { messages = new JSONArray(s); } catch (Exception e) { messages = new JSONArray(); return false; }
        for (int i = 0; i < messages.length(); i++) {
            try {
                JSONObject m = messages.getJSONObject(i);
                String role = m.optString("role", "");
                if (role.equals("user")) bubble("user", m.optString("content", ""), false);
                else if (role.equals("assistant")) { String c = m.optString("content", ""); if (!c.isEmpty()) bubble("ai", c, false); }
            } catch (Exception ignored) { }
        }
        chatList.post(() -> scrollToBottom());
        return messages.length() > 0;
    }
    private void newChat() {
        messages = new JSONArray();
        prefs.edit().remove("rea_chat").apply();
        chatList.removeAllViews();
        bubble("ai", WELCOME, false);
    }

    private String generateImage(String prompt, int w, int h) {
        try {
            if (prompt == null || prompt.isEmpty()) return "error: no prompt given";
            if (w <= 0) w = 1024;
            if (h <= 0) h = 1024;
            if (w > 1536) w = 1536;
            if (h > 1536) h = 1536;
            String u = "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(prompt, "UTF-8")
                + "?width=" + w + "&height=" + h + "&nologo=true&model=flux&seed=" + (int)(Math.random() * 1000000);
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(240000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) rea-agent/3.0");
            int code = c.getResponseCode();
            if (code >= 400) return "error: image api returned " + code + ", try again";
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            byte[] bytes = bo.toByteArray();
            if (bytes.length < 1000) return "error: image api returned something too small, try again";
            File f = new File(getExternalFilesDir(null), "rea-image-" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes); fo.close();
            showImage(f);
            return "image generated (" + bytes.length + " bytes), saved to " + f.getAbsolutePath() + " and shown in the chat.";
        } catch (Exception e) { return "error: " + e; }
    }
    private void showImage(final File f) {
        runOnUiThread(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 1080) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
                if (bm == null) return;
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bm);
                iv.setAdjustViewBounds(true);
                GradientDrawable g = new GradientDrawable();
                g.setColor(Color.parseColor("#1c2431"));
                g.setCornerRadius(dp(18));
                iv.setBackground(g);
                LinearLayout wrap = new LinearLayout(this);
                wrap.setGravity(Gravity.START);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
                wp.topMargin = dp(6);
                wrap.setLayoutParams(wp);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
                ip.rightMargin = dp(60);
                wrap.addView(iv, ip);
                chatList.post(() -> { chatList.addView(wrap); scrollToBottom(); });
            } catch (Throwable ignored) { }
        });
    }


    private static final int T_AUTO = 0, T_AUDIT = 1, T_CONFIRM = 2;

    private int tierOf(String name, JSONObject args) {
        if (name.equals("http_request")) return "GET".equalsIgnoreCase(args.optString("method", "GET")) ? T_AUDIT : T_CONFIRM;
        if (name.equals("github_commit") || name.equals("github_release")) return T_CONFIRM;
        if (name.equals("github_build")) return T_CONFIRM;
        if (name.equals("save_file") || name.equals("generate_image") || name.equals("github_latest_apk")) return T_AUDIT;
        if (name.equals("notes")) return "save".equals(args.optString("action", "read")) ? T_AUDIT : T_AUTO;
        return T_AUTO;
    }

    private String describe(String name, JSONObject args) {
        String s = name + " " + args.toString();
        if (s.length() > 300) s = s.substring(0, 300) + "...";
        return s;
    }

    private String runTool(String name, JSONObject args) {
        try {
            if (name.equals("analyze_apk")) return apk.analyze();
            if (name.equals("dex_strings")) return apk.dexStrings(args.optString("filter", ""), args.optInt("limit", 1500));
            if (name.equals("find_in_apk")) return apk.find(args.optString("text", ""));
            if (name.equals("fetch_url")) return fetchUrl(args.optString("url", ""));
            if (name.equals("web_search")) return webSearch(args.optString("query", ""));
            if (name.equals("http_request")) return httpRequest(args.optString("method", "GET"), args.optString("url", ""), args.optJSONObject("headers"), args.optString("body", ""));
            if (name.equals("notes")) return notes(args.optString("action", "read"), args.optString("text", ""));
            if (name.equals("save_file")) return saveFile(args.optString("filename", ""), args.optString("content", ""));
            if (name.equals("generate_image")) return generateImage(args.optString("prompt", ""), args.optInt("width", 1024), args.optInt("height", 1024));
            if (name.equals("github_list")) return ghList();
            if (name.equals("github_read")) return ghRead(args.optString("repo", ""), args.optString("path", ""), args.optString("ref", ""));
            if (name.equals("github_commit")) return ghCommit(args.optString("repo", ""), args.optString("path", ""), args.optString("content", ""), args.optString("message", "update via REA"), args.optString("branch", ""));
            if (name.equals("github_release")) return ghRelease(args.optString("repo", ""), args.optString("tag", ""), args.optString("name", ""), args.optString("body", ""), args.optString("apk_path", ""));
            if (name.equals("github_latest_apk")) return ghLatestApk(args.optString("repo", ""));
            if (name.equals("github_build")) return ghBuild(args.optString("repo", ""), args.optString("ref", "master"), args.optInt("wait_minutes", 8));
            if (name.equals("list_files")) return listFiles();
            if (name.equals("read_file")) return readFile(args.optString("filename", ""));
            return "unknown tool";
        } catch (Throwable t) { return "tool error: " + t; }
    }

    private void noteOutcome(String name, String result) {
        if (result != null && (result.startsWith("error") || result.startsWith("tool error"))) {
            Integer c = toolFails.get(name);
            toolFails.put(name, (c == null ? 0 : c) + 1);
        } else toolFails.remove(name);
    }

    private boolean askUser(String what) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = {false};
        runOnUiThread(() -> {
            LinearLayout wrap = new LinearLayout(MainActivity.this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.parseColor("#243043"));
            g.setCornerRadius(dp(14));
            g.setStroke(dp(1), Color.parseColor("#39486b"));
            TextView t = new TextView(MainActivity.this);
            t.setText(what);
            t.setTextSize(13);
            t.setTextColor(Color.parseColor("#e7edf7"));
            t.setPadding(dp(14), dp(10), dp(14), dp(10));
            wrap.addView(t);
            LinearLayout btns = new LinearLayout(MainActivity.this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button yes = new Button(MainActivity.this);
            yes.setText("allow"); yes.setAllCaps(false); yes.setTextSize(13); yes.setTextColor(Color.WHITE);
            GradientDrawable yg = new GradientDrawable(); yg.setColor(ACCENT); yg.setCornerRadius(dp(10));
            yes.setBackground(yg);
            LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(-2, -2);
            yp.rightMargin = dp(10);
            yes.setLayoutParams(yp);
            Button no = new Button(MainActivity.this);
            no.setText("skip"); no.setAllCaps(false); no.setTextSize(13); no.setTextColor(Color.parseColor("#aab6cc"));
            no.setBackgroundColor(Color.TRANSPARENT);
            btns.addView(yes); btns.addView(no);
            wrap.addView(btns);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = dp(6); wp.leftMargin = dp(40); wp.rightMargin = dp(40);
            wrap.setLayoutParams(wp);
            chatList.post(() -> { chatList.addView(wrap); scrollToBottom(); });
            yes.setOnClickListener(v -> { ok[0] = true; latch.countDown(); wrap.setVisibility(View.GONE); });
            no.setOnClickListener(v -> { latch.countDown(); wrap.setVisibility(View.GONE); });
        });
        try { latch.await(180, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
        return ok[0];
    }

    private void audit(String what) {
        try {
            if (what.length() > 300) what = what.substring(0, 300);
            String stamp = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date());
            FileOutputStream fo = new FileOutputStream(new File(getExternalFilesDir(null), "rea_audit.txt"), true);
            fo.write(("\n[" + stamp + "] " + what).getBytes(StandardCharsets.UTF_8));
            fo.close();
        } catch (Throwable ignored) { }
    }

    private HttpURLConnection gh(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("User-Agent", "rea-agent/3.1");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        String t = prefs.getString("gh_token", "");
        if (!t.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + t);
        return c;
    }

    private String ghReadAll(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) > 0 && bo.size() < 300000) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    private String ghList() {
        try {
            if (prefs.getString("gh_token", "").isEmpty()) return "error: no github token. put a personal access token (github.com/settings/tokens) in settings.";
            HttpURLConnection c = gh("https://api.github.com/user/repos?per_page=100&sort=updated");
            String s = ghReadAll(c);
            if (c.getResponseCode() >= 400) return "error: github " + c.getResponseCode() + ": " + s.substring(0, Math.min(300, s.length()));
            JSONArray arr = new JSONArray(s);
            StringBuilder sb = new StringBuilder("your repos (" + arr.length() + "), newest activity first:\n");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                sb.append("- ").append(r.optString("full_name")).append(r.optBoolean("private") ? " (private)" : "").append(", pushed ").append(r.optString("pushed_at", "?")).append("\n");
            }
            if (sb.length() > 7000) sb.setLength(7000);
            return sb.toString();
        } catch (Exception e) { return "error: " + e; }
    }

    private String ghRead(String repo, String path, String ref) {
        try {
            if (prefs.getString("gh_token", "").isEmpty()) return "error: no github token (settings).";
            if (repo.isEmpty() || path.isEmpty()) return "error: need repo (owner/name) and path";
            String u = "https://api.github.com/repos/" + repo + "/contents/" + path + (ref.isEmpty() ? "" : "?ref=" + java.net.URLEncoder.encode(ref, "UTF-8"));
            HttpURLConnection c = gh(u);
            c.setRequestProperty("Accept", "application/vnd.github.raw");
            String s = ghReadAll(c);
            if (c.getResponseCode() >= 400) return "error: github " + c.getResponseCode() + ": " + s.substring(0, Math.min(300, s.length()));
            if (s.length() > 12000) s = s.substring(0, 12000) + "\n...(truncated)";
            return "content of " + repo + "/" + path + ":\n" + s;
        } catch (Exception e) { return "error: " + e; }
    }

    private String ghCommit(String repo, String path, String content, String message, String branch) {
        try {
            if (prefs.getString("gh_token", "").isEmpty()) return "error: no github token (settings).";
            if (repo.isEmpty() || path.isEmpty() || content.isEmpty()) return "error: need repo (owner/name), path and content";
            String q = branch.isEmpty() ? "" : "?ref=" + java.net.URLEncoder.encode(branch, "UTF-8");
            HttpURLConnection c = gh("https://api.github.com/repos/" + repo + "/contents/" + path + q);
            String old = ghReadAll(c);
            String sha = null;
            if (c.getResponseCode() == 200) {
                try { sha = new JSONObject(old).optString("sha", null); } catch (Exception ignored) { }
            }
            JSONObject body = new JSONObject().put("message", message)
                .put("content", Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            if (sha != null) body.put("sha", sha);
            if (!branch.isEmpty()) body.put("branch", branch);
            HttpURLConnection p = gh("https://api.github.com/repos/" + repo + "/contents/" + path);
            p.setRequestMethod("PUT");
            p.setDoOutput(true);
            p.setRequestProperty("Content-Type", "application/json");
            OutputStream os = p.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            String s = ghReadAll(p);
            if (p.getResponseCode() >= 400) return "error: github " + p.getResponseCode() + ": " + s.substring(0, Math.min(300, s.length()));
            JSONObject j = new JSONObject(s);
            String html = j.getJSONObject("content").optString("html_url", "");
            String commit = j.getJSONObject("commit").optString("html_url", j.getJSONObject("commit").optString("sha", ""));
            return "committed to " + repo + "/" + path + (sha == null ? " (new file)" : " (updated)") + "\nfile: " + html + "\ncommit: " + commit;
        } catch (Exception e) { return "error: " + e; }
    }

    private String ghRelease(String repo, String tag, String name, String body, String apkPath) {
        try {
            if (prefs.getString("gh_token", "").isEmpty()) return "error: no github token (settings).";
            if (repo.isEmpty() || tag.isEmpty()) return "error: need repo (owner/name) and tag (e.g. v3.1)";
            JSONObject rb = new JSONObject().put("tag_name", tag);
            if (!name.isEmpty()) rb.put("name", name);
            if (!body.isEmpty()) rb.put("body", body);
            HttpURLConnection c = gh("https://api.github.com/repos/" + repo + "/releases");
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(rb.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            String s = ghReadAll(c);
            if (c.getResponseCode() >= 400) return "error: creating release " + c.getResponseCode() + ": " + s.substring(0, Math.min(300, s.length()));
            JSONObject rel = new JSONObject(s);
            long relId = rel.optLong("id", 0);
            File apkFile = pickApkForRelease(apkPath);
            if (apkFile == null || !apkFile.exists()) return "release " + tag + " created (" + rel.optString("html_url", "") + ") but no apk found to attach: pick one with pick_apk first or pass apk_path.";
            String fname = apkFile.getName().replaceAll("[^A-Za-z0-9._-]", "_");
            HttpURLConnection up = gh("https://uploads.github.com/repos/" + repo + "/releases/" + relId + "/assets?name=" + fname);
            up.setRequestMethod("POST");
            up.setDoOutput(true);
            up.setFixedLengthStreamingMode(apkFile.length());
            up.setRequestProperty("Content-Type", "application/octet-stream");
            InputStream in = new FileInputStream(apkFile);
            OutputStream uo = up.getOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) uo.write(buf, 0, n);
            in.close(); uo.close();
            String us = ghReadAll(up);
            if (up.getResponseCode() >= 400) return "release " + tag + " created (" + rel.optString("html_url", "") + ") but the apk upload failed " + up.getResponseCode() + ": " + us.substring(0, Math.min(200, us.length()));
            JSONObject asset = new JSONObject(us);
            return "release " + tag + " is live: " + rel.optString("html_url", "") + "\napk attached: " + asset.optString("browser_download_url", "") + " (" + apkFile.length() + " bytes)";
        } catch (Exception e) { return "error: " + e; }
    }

    private File pickApkForRelease(String apkPath) {
        if (apkPath != null && !apkPath.isEmpty()) {
            File f = new File(apkPath);
            if (!f.exists()) f = new File(getExternalFilesDir(null), apkPath.replaceAll("[^A-Za-z0-9._-]", "_"));
            if (f.exists()) return f;
        }
        File loaded = new File(getCacheDir(), "target.apk");
        if (loaded.exists()) return loaded;
        File dir = getExternalFilesDir(null);
        File best = null;
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.getName().endsWith(".apk") && (best == null || f.lastModified() > best.lastModified())) best = f;
        }
        return best;
    }

    private void handleIncoming(Intent i) {
        if (i == null) return;
        Uri s = (Uri) i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (s != null) {
            String t = i.getType();
            importUri(s, null, t != null && t.contains("android.package-archive"));
            return;
        }
        if (i.getData() != null && Intent.ACTION_VIEW.equals(i.getAction())) {
            String t = i.getType();
            String d = i.getData().toString().toLowerCase();
            importUri(i.getData(), null, (t != null && t.contains("android.package-archive")) || d.endsWith(".apk"));
        }
    }

    private String listFiles() {
        try {
            File[] fs = getExternalFilesDir(null).listFiles();
            if (fs == null || fs.length == 0) return "no files in the app folder yet; the user can attach one with the + button";
            java.util.Arrays.sort(fs, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
            StringBuilder sb = new StringBuilder();
            int c = 0;
            for (File f : fs) {
                if (c++ >= 60) { sb.append("... more files not shown"); break; }
                sb.append(f.getName()).append(" (").append(f.length()).append(" bytes");
                if (f.getName().toLowerCase().endsWith(".apk")) sb.append(", apk: use pick_apk then analyze_apk");
                sb.append(")\n");
            }
            return sb.toString();
        } catch (Exception e) { return "error: " + e; }
    }

    private String readFile(String filename) {
        try {
            if (filename.isEmpty()) return "error: need filename (run list_files)";
            if (filename.contains("/") || filename.contains("\\")) return "error: use a plain file name, no paths";
            File dir = getExternalFilesDir(null);
            File f = new File(dir, filename);
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) return "error: file outside the app folder";
            if (!f.exists()) return "error: no such file, run list_files";
            if (f.getName().toLowerCase().endsWith(".apk")) return "that is an apk: the user can load it with pick_apk or the + attach button, then you use analyze_apk";
            FileInputStream fi = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n; long total = 0;
            while ((n = fi.read(buf)) > 0 && total < 200000) { bo.write(buf, 0, n); total += n; }
            fi.close();
            String s = bo.toString("UTF-8");
            if (s.length() > 12000) s = s.substring(0, 12000) + "\n... (truncated; the file is " + f.length() + " bytes)";
            return s.isEmpty() ? "(empty file)" : s;
        } catch (Exception e) { return "error: " + e; }
    }

    private void showInstall(final File f) {
        runOnUiThread(() -> {
            try {
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setGravity(Gravity.START);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
                wp.topMargin = dp(6);
                wrap.setLayoutParams(wp);
                Button ib = new Button(MainActivity.this);
                ib.setText("install " + f.getName());
                ib.setAllCaps(false);
                ib.setTextColor(Color.WHITE);
                GradientDrawable g = new GradientDrawable();
                g.setColor(ACCENT);
                g.setCornerRadius(dp(10));
                ib.setBackground(g);
                ib.setOnClickListener(v -> {
                    try {
                        Uri u = Uri.parse("content://com.rea.agent.files/" + Uri.encode(f.getName()));
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(u, "application/vnd.android.package-archive");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable t) { log("install failed: " + t); }
                });
                wrap.addView(ib);
                chatList.post(() -> { chatList.addView(wrap); scrollToBottom(); });
            } catch (Throwable ignored) { }
        });
    }

    private String ghBuild(String repo, String ref, int waitMinutes) {
        try {
            if (prefs.getString("gh_token", "").isEmpty()) return "error: no github token (settings).";
            if (repo.isEmpty()) return "error: need repo (owner/name)";
            if (ref.isEmpty()) ref = "master";
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/actions/workflows/build-apk.yml/dispatches").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(20000); c.setReadTimeout(30000);
            c.setRequestProperty("Authorization", "Bearer " + prefs.getString("gh_token", ""));
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "rea-agent/3.2");
            OutputStream os = c.getOutputStream();
            os.write(("{\"ref\":\"" + ref + "\"}").getBytes(StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            if (code == 404) return "error: no build-apk.yml workflow in " + repo + " on " + ref + ". the repo needs .github/workflows/build-apk.yml (a workflow_dispatch build that publishes the apk as a release).";
            if (code >= 300) { String es = ghReadAll(c); return "error: github " + code + ": " + es.substring(0, Math.min(300, es.length())); }
            Thread.sleep(8000);
            long deadline = System.currentTimeMillis() + waitMinutes * 60000L;
            while (System.currentTimeMillis() < deadline) {
                HttpURLConnection r = gh("https://api.github.com/repos/" + repo + "/actions/runs?event=workflow_dispatch&per_page=3");
                String s = ghReadAll(r);
                if (r.getResponseCode() < 400) {
                    JSONArray runs = new JSONObject(s).optJSONArray("workflow_runs");
                    if (runs != null && runs.length() > 0) {
                        JSONObject run = runs.getJSONObject(0);
                        if (run.optString("status", "").equals("completed")) {
                            String con = run.optString("conclusion", "");
                            if (con.equals("success")) return "cloud build succeeded on " + repo + "\nrun: " + run.optString("html_url", "") + "\nrelease (give this download link to the user): https://github.com/" + repo + "/releases/latest" + "\nnext: call github_latest_apk with repo \"" + repo + "\" to download the built apk onto the phone.";
                            return "cloud build " + con + " on " + repo + "\nrun: " + run.optString("html_url", "") + "\ncheck the run log, fix the repo, and build again.";
                        }
                    }
                }
                Thread.sleep(15000);
            }
            return "build still running after " + waitMinutes + " minutes; check https://github.com/" + repo + "/actions and call github_latest_apk once it finishes.";
        } catch (Exception e) { return "error: " + e; }
    }

    private String ghLatestApk(String repo) {
        try {
            if (prefs.getString("gh_token", "").isEmpty()) return "error: no github token (settings).";
            if (repo.isEmpty()) return "error: need repo (owner/name)";
            HttpURLConnection c = gh("https://api.github.com/repos/" + repo + "/releases/latest");
            String s = ghReadAll(c);
            if (c.getResponseCode() >= 400) return "error: github " + c.getResponseCode() + ": " + s.substring(0, Math.min(300, s.length()));
            JSONObject rel = new JSONObject(s);
            JSONArray assets = rel.optJSONArray("assets");
            String url = null, aname = null;
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name", "").endsWith(".apk")) { url = a.optString("browser_download_url", ""); aname = a.optString("name", ""); break; }
            }
            if (url == null) return "no apk asset in the latest release of " + repo + " (" + rel.optString("tag_name", "") + ")";
            HttpURLConnection d = (HttpURLConnection) new URL(url).openConnection();
            d.setConnectTimeout(20000); d.setReadTimeout(600000);
            d.setRequestProperty("User-Agent", "rea-agent/3.1");
            if (!prefs.getString("gh_token", "").isEmpty()) d.setRequestProperty("Authorization", "Bearer " + prefs.getString("gh_token", ""));
            InputStream in = d.getInputStream();
            File f = new File(getExternalFilesDir(null), aname);
            FileOutputStream fo = new FileOutputStream(f);
            byte[] buf = new byte[16384]; int n; long total = 0;
            while ((n = in.read(buf)) > 0) { fo.write(buf, 0, n); total += n; }
            in.close(); fo.close();
            showInstall(f);
            return "downloaded " + aname + " (" + total + " bytes) from " + repo + " " + rel.optString("tag_name", "") + " to " + f.getAbsolutePath() + "\nrelease page: " + rel.optString("html_url", "") + "\napk download link: " + url + "\nan install button is on screen; the user can also install it from their file manager.";
        } catch (Exception e) { return "error: " + e; }
    }

    private JSONArray tools() throws Exception {
        JSONArray t = new JSONArray();
        t.put(tool("pick_apk", "Open the file picker so the user can choose an APK to analyze. Use when a task involves an APK and none is loaded.", new JSONObject()));
        t.put(tool("analyze_apk", "Full inspection of the loaded APK: size, sha256, entries, dex count, manifest strings (package, version, components), permissions, native ABIs.", new JSONObject()));
        JSONObject f = new JSONObject();
        f.put("filter", new JSONObject().put("type", "string").put("description", "only strings containing this text (optional)"));
        f.put("limit", new JSONObject().put("type", "integer").put("description", "max strings to return, default 1500"));
        t.put(tool("dex_strings", "Extract readable strings from the dex code of the loaded APK.", new JSONObject().put("type", "object").put("properties", f)));
        t.put(tool("find_in_apk", "Search every file inside the loaded APK for a piece of text (urls, keys, domains, endpoints).", new JSONObject().put("type", "object").put("properties",
            new JSONObject().put("text", new JSONObject().put("type", "string").put("description", "text to search for")))));
        t.put(tool("fetch_url", "Download a web page and return its readable text. Use for links, docs, or anything on the web.", new JSONObject().put("type", "object").put("properties",
            new JSONObject().put("url", new JSONObject().put("type", "string").put("description", "the url to fetch")))));
        t.put(tool("web_search", "Search the web and get result titles, urls and snippets. Use before fetching pages.", new JSONObject().put("type", "object").put("properties",
            new JSONObject().put("query", new JSONObject().put("type", "string").put("description", "the search query")))));
        t.put(tool("http_request", "Call any HTTP API: choose method (GET/POST/...), url, optional headers object and body. For JSON APIs, set Content-Type header yourself.", new JSONObject().put("type", "object").put("properties",
            new JSONObject()
                .put("method", new JSONObject().put("type", "string").put("description", "GET, POST, PUT, DELETE, default GET"))
                .put("url", new JSONObject().put("type", "string").put("description", "full url"))
                .put("headers", new JSONObject().put("type", "object").put("description", "optional headers object"))
                .put("body", new JSONObject().put("type", "string").put("description", "optional request body")))));
        t.put(tool("notes", "Your persistent memory on this phone: action 'save' with text appends a note; action 'read' returns all notes. Save key findings and task state here.", new JSONObject().put("type", "object").put("properties",
            new JSONObject()
                .put("action", new JSONObject().put("type", "string").put("description", "save or read"))
                .put("text", new JSONObject().put("type", "string").put("description", "the note text when saving")))));
        t.put(tool("save_file", "Write a text file to the phone (reports, extracted data).", new JSONObject().put("type", "object").put("properties",
            new JSONObject()
                .put("filename", new JSONObject().put("type", "string").put("description", "file name, e.g. report.txt"))
                .put("content", new JSONObject().put("type", "string").put("description", "full file content")))));
        JSONObject gi = new JSONObject();
        gi.put("prompt", new JSONObject().put("type", "string").put("description", "what the image should show; be specific and detailed"));
        gi.put("width", new JSONObject().put("type", "integer").put("description", "optional image width, default 1024, max 1536"));
        gi.put("height", new JSONObject().put("type", "integer").put("description", "optional image height, default 1024, max 1536"));
        t.put(tool("generate_image", "Create a picture from a text description. The image is saved on the phone and shown in the chat.", new JSONObject().put("type", "object").put("properties", gi).put("required", new JSONArray().put("prompt"))));
        t.put(tool("github_list", "List the user's GitHub repositories with recent activity. Needs a GitHub token in settings.", new JSONObject()));
        JSONObject grepo = new JSONObject();
        grepo.put("repo", new JSONObject().put("type", "string").put("description", "repo as owner/name, e.g. Olonilu1/rea-agent"));
        grepo.put("path", new JSONObject().put("type", "string").put("description", "file path inside the repo, e.g. README.md"));
        grepo.put("ref", new JSONObject().put("type", "string").put("description", "optional branch or tag"));
        t.put(tool("github_read", "Read a file's content from a GitHub repo.", new JSONObject().put("type", "object").put("properties", new JSONObject().put("repo", grepo.get("repo")).put("path", grepo.get("path")).put("ref", grepo.get("ref"))).put("required", new JSONArray().put("repo").put("path"))));
        JSONObject gcm = new JSONObject();
        gcm.put("repo", grepo.get("repo"));
        gcm.put("path", grepo.get("path"));
        gcm.put("content", new JSONObject().put("type", "string").put("description", "the full new file content"));
        gcm.put("message", new JSONObject().put("type", "string").put("description", "commit message"));
        gcm.put("branch", new JSONObject().put("type", "string").put("description", "optional branch, default the repo default"));
        t.put(tool("github_commit", "Create or update a file in a GitHub repo (one commit). The user confirms before it runs. To edit an existing file, read it with github_read first, change what you need, and commit the full new content.", new JSONObject().put("type", "object").put("properties", gcm).put("required", new JSONArray().put("repo").put("path").put("content"))));
        JSONObject grel = new JSONObject();
        grel.put("repo", grepo.get("repo"));
        grel.put("tag", new JSONObject().put("type", "string").put("description", "release tag, e.g. v3.1"));
        grel.put("name", new JSONObject().put("type", "string").put("description", "optional release title"));
        grel.put("body", new JSONObject().put("type", "string").put("description", "optional release notes"));
        grel.put("apk_path", new JSONObject().put("type", "string").put("description", "optional path of an apk on this phone to attach; default is the loaded apk or the newest apk in the app folder"));
        t.put(tool("github_release", "Create a GitHub release and upload the APK to it, then return the release and apk download links. The user confirms before it runs.", new JSONObject().put("type", "object").put("properties", grel).put("required", new JSONArray().put("repo").put("tag"))));
        t.put(tool("github_latest_apk", "Download the latest release APK of a GitHub repo onto the phone so the user can install it from their file manager.", new JSONObject().put("type", "object").put("properties", new JSONObject().put("repo", grepo.get("repo"))).put("required", new JSONArray().put("repo"))));
        JSONObject gb = new JSONObject();
        gb.put("repo", grepo.get("repo"));
        gb.put("ref", new JSONObject().put("type", "string").put("description", "optional branch to build, default master"));
        gb.put("wait_minutes", new JSONObject().put("type", "integer").put("description", "optional minutes to wait for the build, default 8"));
        t.put(tool("github_build", "Trigger the cloud build (workflow build-apk.yml) on a GitHub repo, wait for it to finish and report the result. On success the workflow publishes the freshly built APK as a new release; then call github_latest_apk to download it onto the phone. The user confirms before it runs.", new JSONObject().put("type", "object").put("properties", gb).put("required", new JSONArray().put("repo"))));
        t.put(tool("list_files", "List the files in the app's folder on the phone (attached files, saved files, downloaded apks).", new JSONObject()));
        JSONObject rf = new JSONObject();
        rf.put("filename", new JSONObject().put("type", "string").put("description", "file name exactly as list_files shows it"));
        t.put(tool("read_file", "Read a text file from the app's folder on the phone (an attached file or one saved with save_file). For APKs use analyze_apk instead.", new JSONObject().put("type", "object").put("properties", rf).put("required", new JSONArray().put("filename"))));
        return t;
    }

    private JSONObject tool(String name, String desc, JSONObject schema) throws Exception {
        return new JSONObject().put("type", "function").put("function",
            new JSONObject().put("name", name).put("description", desc).put("parameters", schema));
    }

    private JSONObject post(String url, JSONObject body, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(30000);
        c.setReadTimeout(300000);
        c.setRequestProperty("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        InputStream is = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        String s = bo.toString("UTF-8");
        if (c.getResponseCode() >= 400) throw new Exception("api " + c.getResponseCode() + ": " + s.substring(0, Math.min(300, s.length())));
        return new JSONObject(s);
    }
}
