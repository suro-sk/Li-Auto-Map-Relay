package org.li.maprelay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local compatibility bridge, with no permissions or background components. */
@SuppressWarnings("deprecation")
public final class RelayActivity extends Activity {
    private static final String YANDEX="ru.yandex.yandexmaps", GOOGLE="com.google.android.apps.maps";
    private static final int MAIN=0,SETTINGS=1,HISTORY=2,DIAGNOSTICS=3;
    private SharedPreferences preferences;
    private MapRequest request;
    private MapRequest.Coordinates coordinates;
    private MapRequest.Mode mode;
    private RecentLocations history;
    private String rawUri,errorDetail;
    private long receivedAt;
    private boolean rememberChoice,fromHistory;
    private int screen=MAIN;
    private LinearLayout page;
    private TextView feedback;
    private AlertDialog activeDialog;

    private static final class Target {
        final String pkg,label;
        final ComponentName component;
        final ResolveInfo info;
        Target(ResolveInfo value,PackageManager pm) {
            info=value; pkg=value.activityInfo.packageName;
            component=new ComponentName(pkg,value.activityInfo.name);
            label=value.loadLabel(pm).toString();
        }
    }
    @Override protected void attachBaseContext(Context base) {
        SharedPreferences saved=base.getSharedPreferences("relay_preferences",MODE_PRIVATE);
        String code=saved.getString("language","");
        if (!code.isEmpty()&&!code.equals("en")&&!code.equals("ru")) {
            saved.edit().remove("language").apply(); code="";
        }
        if (code.equals("en")||code.equals("ru")) {
            Configuration config=new Configuration(base.getResources().getConfiguration());
            config.setLocale(new Locale(code)); base=base.createConfigurationContext(config);
        }
        super.attachBaseContext(base);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences=getSharedPreferences("relay_preferences",MODE_PRIVATE);
        history=RecentLocations.load(preferences.getString("history_data",""));
        if (!preferences.getBoolean("history_enabled",false)) {
            history.clear(); preferences.edit().remove("history_data").apply();
        }
        receive(getIntent(),state==null);
        if (state!=null) {
            receivedAt=state.getLong("received_at",receivedAt);
            coordinates=coordinateValue(state.getString("coordinates",coordinates.name()));
            rememberChoice=state.getBoolean("remember"); fromHistory=state.getBoolean("from_history");
            screen=state.getInt("screen",MAIN);
            try { request=rawUri==null?null:MapRequest.parse(rawUri); if (request!=null) request.destination(coordinates); errorDetail=null; }
            catch (IllegalArgumentException ex) { request=null; errorDetail=ex.getMessage(); }
            render();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (preferences!=null) { reloadHistory(); if (screen==HISTORY||screen==SETTINGS) render(); }
    }
    @Override protected void onDestroy() { closeDialog(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("received_at",receivedAt); state.putString("coordinates",coordinates.name());
        state.putBoolean("remember",rememberChoice); state.putBoolean("from_history",fromHistory);
        state.putInt("screen",screen); super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); receive(intent,true); }
    @Override public void onBackPressed() { if (screen!=MAIN) { screen=MAIN; render(); } else super.onBackPressed(); }
    private MapRequest.Coordinates coordinateValue(String value) {
        try { return MapRequest.Coordinates.valueOf(value); } catch (Exception ex) { return MapRequest.Coordinates.AUTO; }
    }
    private void receive(Intent intent,boolean fresh) {
        request=null; rawUri=null; errorDetail=null; rememberChoice=false; fromHistory=false; screen=MAIN;
        coordinates=coordinateValue(preferences.getString("coordinates",preferences.getBoolean("correct_coordinates",true)?"AUTO":"WGS84"));
        try { mode=MapRequest.Mode.valueOf(preferences.getString("mode","PIN")); }
        catch (Exception ex) { mode=MapRequest.Mode.PIN; }
        receivedAt=java.lang.System.currentTimeMillis();
        if (intent!=null&&intent.getData()!=null) {
            rawUri=intent.getData().toString();
            try { request=MapRequest.parse(rawUri); request.destination(coordinates); }
            catch (IllegalArgumentException ex) { request=null; errorDetail=ex.getMessage(); }
        }
        if (fresh&&request!=null) record();
        render();
        if (fresh&&request!=null) {
            String saved=preferences.getString("default_package","");
            if (!saved.isEmpty()) {
                for (Target target:targets()) if (target.pkg.equals(saved)) { launch(target,false); return; }
                preferences.edit().remove("default_package").apply(); message("saved_unavailable");
            }
        }
    }
    private void record() {
        if (request==null||fromHistory||!preferences.getBoolean("history_enabled",false)) return;
        reloadHistory();
        history.add(rawUri,receivedAt,coordinates);
        preferences.edit().putString("history_data",history.serialize()).apply();
    }
    private void reloadHistory() {
        history=preferences.getBoolean("history_enabled",false)?RecentLocations.load(preferences.getString("history_data","")):new RecentLocations();
    }
    private void updateRecord() {
        reloadHistory();
        // A format edit may update an existing receipt, but must not re-add one
        // which the user already deleted in this or another Activity instance.
        if (request!=null&&history.update(rawUri,receivedAt,coordinates))
            preferences.edit().putString("history_data",history.serialize()).apply();
    }
    private void closeDialog() { if (activeDialog!=null) { activeDialog.dismiss(); activeDialog=null; } }
    private void showDialog(AlertDialog.Builder builder) { closeDialog(); activeDialog=builder.create(); activeDialog.show(); }
    private String s(String key,Object... args) { return getResources().getString(getResources().getIdentifier(key,"string",getPackageName()),args); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(68,89,103));
        view.setPadding(0,dp(6),0,dp(6)); page.addView(view); return view;
    }
    private void heading(String value) { TextView title=text(value,22); title.setTypeface(null,Typeface.BOLD); title.setTextColor(Color.rgb(18,37,49)); }
    private Button button(String label,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(50)); button.setTextSize(16);
        button.setOnClickListener(v->action.run()); page.addView(button,new LinearLayout.LayoutParams(-1,-2)); return button;
    }
    private void notice(String value) { feedback.setText(value); feedback.setVisibility(View.VISIBLE); }
    private void message(String key) { notice(s(key)); }
    private void render() {
        closeDialog();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(245,247,250));
        page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22),dp(20),dp(22),dp(24));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2)); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{ view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout brand=new LinearLayout(this); brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this); logo.setImageResource(getResources().getIdentifier("ic_relay","drawable",getPackageName()));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=new TextView(this); title.setText(s("app_name")); title.setTextSize(26); title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(Color.rgb(18,37,49)); title.setPadding(dp(12),dp(6),0,dp(6));
        brand.addView(title,new LinearLayout.LayoutParams(0,-2,1)); page.addView(brand);
        text(s("tagline"),14);
        feedback=text("",15); feedback.setTextColor(Color.rgb(150,45,35)); feedback.setVisibility(View.GONE);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (screen!=MAIN) button(s("back"),()->{ screen=MAIN; render(); });
        if (screen==SETTINGS) renderSettings(); else if (screen==HISTORY) renderHistory(); else if (screen==DIAGNOSTICS) renderDiagnostics(); else renderMain();
    }
    private String displayLabel(MapRequest value) { return value.label.equals("Car location")?s("car_location"):value.label; }
    private String timestamp(long value) { return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,getResources().getConfiguration().getLocales().get(0)).format(new Date(value)); }
    private void renderMain() {
        if (request==null) {
            heading(s(errorDetail==null?"ready":"invalid_title")); text(errorDetail==null?s("instructions",identity()):s("invalid_help"),16); text(s("slot_notice",identity()),13);
            if (errorDetail!=null) button(s("diagnostics"),()->{ screen=DIAGNOSTICS; render(); });
        } else {
            heading(displayLabel(request)); TextView location=text(pointText(),18); location.setTextIsSelectable(true); text(s("received",timestamp(receivedAt)),12);
            RadioGroup modes=new RadioGroup(this); modes.setOrientation(LinearLayout.HORIZONTAL);
            String[] labels={s("pin"),s("walking"),s("driving")};
            for (int i=0;i<3;i++) { RadioButton option=new RadioButton(this); option.setId(0x3000+i); option.setText(labels[i]); option.setTextSize(14); modes.addView(option,new RadioGroup.LayoutParams(0,dp(56),1)); }
            modes.check(0x3000+mode.ordinal()); page.addView(modes);
            modes.setOnCheckedChangeListener((group,id)->{ mode=MapRequest.Mode.values()[id-0x3000]; preferences.edit().putString("mode",mode.name()).apply(); render(); });
            heading(s("choose_map")); CheckBox remember=new CheckBox(this); remember.setText(s("remember")); remember.setChecked(rememberChoice);
            remember.setOnCheckedChangeListener((view,value)->rememberChoice=value); page.addView(remember);
            List<Target> choices=targets(); if (choices.isEmpty()) text(s("no_maps"),15);
            for (Target target:choices) {
                Button map=button(target.label,()->launch(target,rememberChoice)); android.graphics.drawable.Drawable icon=target.info.loadIcon(getPackageManager());
                icon.setBounds(0,0,dp(26),dp(26)); map.setCompoundDrawables(icon,null,null,null); map.setCompoundDrawablePadding(dp(10)); map.setPadding(dp(12),dp(6),dp(12),dp(6));
            }
            if (mode!=MapRequest.Mode.PIN) text(s("generic_route_hint"),12);
            button(s("browser"),this::openBrowser);
            button(s("copy_coordinates"),()->copy(pointText(),s("coordinates_copied")));
            button(s("share_location"),this::share);
            button(s("diagnostics"),()->{ screen=DIAGNOSTICS; render(); });
        }
        button(s("history"),()->{ screen=HISTORY; render(); }); button(s("settings"),()->{ screen=SETTINGS; render(); });
    }
    private String pointText() { MapRequest.Point point=request.destination(coordinates); return point.latitudeText()+", "+point.longitudeText(); }
    private void renderSettings() {
        heading(s("settings")); text(s("format_help"),14);
        String[] formats={s("auto_format"),s("wgs_format"),"GCJ02","BD09"};
        button(s("coordinate_format",formats[coordinates.ordinal()]),()->showDialog(new AlertDialog.Builder(this).setTitle(s("coordinate_format_title"))
            .setSingleChoiceItems(formats,coordinates.ordinal(),(dialog,index)->{
                MapRequest.Coordinates selected=MapRequest.Coordinates.values()[index];
                try { if (request!=null) request.destination(selected); coordinates=selected; preferences.edit().putString("coordinates",selected.name()).apply(); updateRecord(); dialog.dismiss(); render(); }
                catch (IllegalArgumentException ex) { dialog.dismiss(); message("invalid_help"); }
            }).setNegativeButton(s("cancel"),null)));
        button(s("language"),()->{
            String[] codes={"","en","ru"}; String[] labels={s("system_language"),"English","\u0420\u0443\u0441\u0441\u043a\u0438\u0439"};
            int checked=java.util.Arrays.asList(codes).indexOf(preferences.getString("language",""));
            showDialog(new AlertDialog.Builder(this).setTitle(s("language")).setSingleChoiceItems(labels,checked,(dialog,index)->{
                preferences.edit().putString("language",codes[index]).commit(); dialog.dismiss(); recreate();
            }).setNegativeButton(s("cancel"),null));
        });
        CheckBox save=new CheckBox(this); save.setText(s("save_history")); save.setChecked(preferences.getBoolean("history_enabled",false)); page.addView(save); text(s("history_privacy"),14);
        save.setOnCheckedChangeListener((view,value)->{
            preferences.edit().putBoolean("history_enabled",value).apply();
            if (!value) { history.clear(); preferences.edit().remove("history_data").apply(); } else record(); render();
        });
        button(s("clear_history"),()->{ history.clear(); preferences.edit().remove("history_data").apply(); message("history_cleared"); });
        button(s("forget_default"),()->{ preferences.edit().remove("default_package").apply(); message("default_cleared"); }); text(s("privacy"),13);
    }
    private void renderHistory() {
        reloadHistory();
        heading(s("history")); text(s("receipt_hint"),13);
        if (!preferences.getBoolean("history_enabled",false)) { text(s("history_off"),16); button(s("settings"),()->{ screen=SETTINGS; render(); }); return; }
        if (history.entries().isEmpty()) text(s("history_empty"),16);
        for (RecentLocations.Entry entry:history.entries()) {
            MapRequest past=MapRequest.parse(entry.uri);
            button(displayLabel(past)+"\n"+timestamp(entry.receivedAt),()->{
                request=past; rawUri=entry.uri; receivedAt=entry.receivedAt; coordinates=entry.coordinates; fromHistory=true; errorDetail=null; rememberChoice=false; screen=MAIN;
                setIntent(new Intent(Intent.ACTION_VIEW,Uri.parse(rawUri))); render();
            });
        }
        button(s("clear_history"),()->{ history.clear(); preferences.edit().remove("history_data").apply(); render(); message("history_cleared"); });
    }
    private void copy(String value,String result) {
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); clipboard.setPrimaryClip(ClipData.newPlainText(s("app_name"),value)); notice(result);
    }
    private void share() {
        String value=displayLabel(request)+"\n"+pointText()+"\n"+request.webUri(coordinates,MapRequest.Mode.PIN);
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value);
        try { startActivity(Intent.createChooser(send,s("share_location"))); } catch (ActivityNotFoundException|SecurityException ex) { message("no_share"); }
    }
    private String report() {
        StringBuilder report=new StringBuilder(s("app_name")+" 2.1\n"); report.append(s("compatibility")).append(": ").append(getPackageName()).append('\n');
        report.append(s("received",timestamp(receivedAt))).append('\n').append(s("receipt_hint")).append("\n\n");
        report.append(s("incoming_link")).append(":\n").append(rawUri==null?s("none"):rawUri).append("\n\n");
        if (errorDetail!=null) report.append(s("error_details")).append(": ").append(errorDetail).append("\n\n");
        if (request!=null) {
            report.append(s("original_coordinates")).append(": ").append(request.raw().latitudeText()).append(", ").append(request.raw().longitudeText()).append('\n');
            report.append(s("inferred_format")).append(": ").append(request.coordinateType()).append('\n');
            report.append(s("chosen_format")).append(": ").append(coordinates.name()).append('\n');
            report.append(s("converted_coordinates")).append(": ").append(pointText()).append('\n');
            report.append(s("route_mode")).append(": ").append(mode.name()).append('\n');
            report.append(s("default_map")).append(": ").append(preferences.getString("default_package",s("ask_each_time"))).append("\n\n");
            report.append(s("outgoing_links")).append(":\n");
            for (Target target:targets()) report.append(target.label).append(" [").append(target.pkg).append("]\n").append(outgoing(target)).append("\n\n");
            report.append(s("browser")).append(":\n").append(request.webUri(coordinates,mode));
        }
        return report.toString();
    }
    private void renderDiagnostics() {
        heading(s("diagnostics")); text(s("diagnostic_privacy"),13); button(s("copy_report"),()->copy(report(),s("report_copied")));
        TextView details=text(report(),14); details.setTextIsSelectable(true); details.setTypeface(Typeface.MONOSPACE);
    }
    private boolean usable(ResolveInfo value) {
        return value!=null&&value.activityInfo!=null&&value.activityInfo.exported&&value.activityInfo.enabled&&!value.activityInfo.packageName.equals(getPackageName());
    }
    private List<Target> targets() {
        Map<String,Target> unique=new LinkedHashMap<String,Target>(); Intent probe=new Intent(Intent.ACTION_VIEW,Uri.parse(request.geoUri(coordinates)));
        for (ResolveInfo value:getPackageManager().queryIntentActivities(probe,PackageManager.MATCH_DEFAULT_ONLY))
            if (usable(value)&&!unique.containsKey(value.activityInfo.packageName)) unique.put(value.activityInfo.packageName,new Target(value,getPackageManager()));
        Intent yandex=new Intent(Intent.ACTION_VIEW,Uri.parse(request.yandexUri(coordinates,mode))).setPackage(YANDEX);
        ResolveInfo special=getPackageManager().resolveActivity(yandex,PackageManager.MATCH_DEFAULT_ONLY);
        if (usable(special)) unique.put(YANDEX,new Target(special,getPackageManager()));
        List<Target> result=new ArrayList<Target>(unique.values());
        Collections.sort(result,(a,b)->a.pkg.equals(YANDEX)!=b.pkg.equals(YANDEX)?(a.pkg.equals(YANDEX)?-1:1):a.label.compareToIgnoreCase(b.label)); return result;
    }
    private String outgoing(Target target) {
        String value=target.pkg.equals(YANDEX)?request.yandexUri(coordinates,mode):target.pkg.equals(GOOGLE)?request.googleUri(coordinates,mode):request.geoUri(coordinates);
        Intent probe=new Intent(Intent.ACTION_VIEW,Uri.parse(value)).setPackage(target.pkg);
        return getPackageManager().resolveActivity(probe,PackageManager.MATCH_DEFAULT_ONLY)!=null?value:request.geoUri(coordinates);
    }
    private void launch(Target target,boolean save) {
        Intent next=new Intent(Intent.ACTION_VIEW,Uri.parse(outgoing(target))).setPackage(target.pkg);
        ResolveInfo resolved=getPackageManager().resolveActivity(next,PackageManager.MATCH_DEFAULT_ONLY);
        if (usable(resolved)) next.setComponent(new ComponentName(target.pkg,resolved.activityInfo.name));
        try { startActivity(next); if (save) preferences.edit().putString("default_package",target.pkg).apply(); finish(); }
        catch (ActivityNotFoundException|SecurityException ex) { preferences.edit().remove("default_package").apply(); message("launch_failed"); }
    }
    private void openBrowser() {
        Intent next=new Intent(Intent.ACTION_VIEW,Uri.parse(request.webUri(coordinates,mode))).addCategory(Intent.CATEGORY_BROWSABLE);
        // Probe a neutral reserved hostname without opening it. This excludes map-only
        // HTTPS handlers, so the explicit browser action cannot bounce back into a map.
        Intent neutral=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.invalid/")).addCategory(Intent.CATEGORY_BROWSABLE);
        java.util.Set<String> browserPackages=new java.util.HashSet<String>();
        int browserFlags=PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_ALL;
        for (ResolveInfo info:getPackageManager().queryIntentActivities(neutral,browserFlags))
            if (usable(info)) browserPackages.add(info.activityInfo.packageName);
        Map<String,Target> unique=new LinkedHashMap<String,Target>();
        for (ResolveInfo info:getPackageManager().queryIntentActivities(next,browserFlags))
            if (usable(info)&&browserPackages.contains(info.activityInfo.packageName)&&!unique.containsKey(info.activityInfo.packageName)) unique.put(info.activityInfo.packageName,new Target(info,getPackageManager()));
        List<Target> browsers=new ArrayList<Target>(unique.values());
        if (browsers.isEmpty()) { message("no_browser"); return; }
        String[] labels=new String[browsers.size()]; for (int i=0;i<labels.length;i++) labels[i]=browsers.get(i).label;
        showDialog(new AlertDialog.Builder(this).setTitle(s("choose_browser")).setItems(labels,(dialog,index)->{
            try { startActivity(new Intent(next).setComponent(browsers.get(index).component)); }
            catch (ActivityNotFoundException|SecurityException ex) { message("launch_failed"); }
        }).setNegativeButton(s("cancel"),null));
    }
    private String identity() {
        if (getPackageName().equals("com.baidu.BaiduMap")) return "Baidu Maps";
        if (getPackageName().equals("com.tencent.map")) return "Tencent Maps"; return "Amap";
    }
}
