package com.qelvoriflix.keygate;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public final class KeyGateActivity extends Activity {
    public static final String SERVER="https://qelvoriflix-keys.onrender.com";
    public static final String PREFS="qelvoriflix_pro_keys_2026";
    public static final String MAIN_ACTIVITY="com.th.lisoprime.MainActivity";
    private static final int WHITE=0xfff6faff, BLUE=0xff18adff, MUTED=0xffadbfd6;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String deviceId;
    private EditText keyInput;
    private TextView message;
    private Button activate;
    private volatile boolean busy;

    private int dp(float value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
    private LinearLayout vertical() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout.LayoutParams lp(int width,int height) {
        return new LinearLayout.LayoutParams(width,height);
    }
    private LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top);
        return p;
    }
    private GradientDrawable gradient(int start,int finish,int radius,int strokeColor,int width) {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{start,finish});
        g.setCornerRadius(dp(radius));
        if(width>0)g.setStroke(dp(width),strokeColor);
        return g;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value); t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private ImageView assetImage(String name) {
        ImageView view=new ImageView(this);
        try(InputStream stream=getAssets().open("qelvoriflix_keygate/"+name)) {
            Bitmap bitmap=BitmapFactory.decodeStream(stream);
            view.setImageBitmap(bitmap);
        }catch(Exception ignored) {
            view.setBackgroundColor(0xff050b17);
        }
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        return view;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff050914);
        getWindow().setNavigationBarColor(0xff030609);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        deviceId=prefs.getString("device_id","");
        if(deviceId.length()<16) {
            deviceId=UUID.randomUUID().toString();
            prefs.edit().putString("device_id",deviceId).apply();
        }

        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setBackgroundColor(0xff03070f);
        LinearLayout page=vertical();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        scroller.addView(page,new ScrollView.LayoutParams(-1,-2));

        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int headerHeight=(int)Math.round(screenWidth*454.0/691.0);
        ImageView header=assetImage("header.webp");
        page.addView(header,new LinearLayout.LayoutParams(-1,headerHeight));

        LinearLayout.LayoutParams cardP=new LinearLayout.LayoutParams(-1,-2);
        cardP.leftMargin=dp(15);cardP.rightMargin=dp(15);
        cardP.topMargin=-dp(2);
        LinearLayout card=vertical();
        card.setPadding(dp(16),dp(11),dp(16),dp(20));
        card.setBackground(gradient(0xff081122,0xff020a15,30,0xff078de4,2));
        card.setElevation(dp(9));
        page.addView(card,cardP);

        ImageView shield=assetImage("shield.webp");
        LinearLayout.LayoutParams shieldP=new LinearLayout.LayoutParams(dp(145),dp(79));
        shieldP.gravity=Gravity.CENTER_HORIZONTAL;
        card.addView(shield,shieldP);

        TextView title=text("Ativação de Licença",24,WHITE,true);
        card.addView(title,topMargin(0));

        View accent=new View(this);
        accent.setBackground(gradient(0xff00cdff,0xff0957d1,4,0,0));
        LinearLayout.LayoutParams accentP=new LinearLayout.LayoutParams(dp(65),dp(2));
        accentP.gravity=Gravity.CENTER_HORIZONTAL;accentP.topMargin=dp(10);
        card.addView(accent,accentP);

        TextView description=text("Insira sua Key de 2, 4 ou 8 semanas para continuar.",13,MUTED,false);
        card.addView(description,topMargin(17));

        LinearLayout entry=new LinearLayout(this);
        entry.setOrientation(LinearLayout.HORIZONTAL);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setPadding(dp(12),dp(2),dp(8),dp(2));
        entry.setBackground(gradient(0xff08172a,0xff020b19,25,0xff4b9be8,1));
        entry.setElevation(dp(3));
        card.addView(entry,topMargin(14));

        TextView keyGlyph=text("⚿",25,BLUE,true);
        entry.addView(keyGlyph,new LinearLayout.LayoutParams(dp(34),dp(48)));

        View verticalLine=new View(this);
        verticalLine.setBackgroundColor(0xff29669b);
        entry.addView(verticalLine,new LinearLayout.LayoutParams(dp(1),dp(25)));

        keyInput=new EditText(this);
        keyInput.setBackgroundColor(Color.TRANSPARENT);
        keyInput.setTextSize(13);
        keyInput.setSingleLine(true);
        keyInput.setTextColor(WHITE);
        keyInput.setHintTextColor(0xff9ba9ba);
        keyInput.setHint("QELV-XXXXX-XXXXX-XXXXX-XXXXX");
        keyInput.setPadding(dp(9),0,dp(2),0);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        entry.addView(keyInput,new LinearLayout.LayoutParams(0,dp(50),1f));

        TextView paste=text("▢",29,BLUE,true);
        entry.addView(paste,new LinearLayout.LayoutParams(dp(37),dp(48)));
        paste.setOnClickListener(v->pasteKey());

        activate=new Button(this);
        activate.setAllCaps(false);
        activate.setText("✓   ATIVAR KEY   ❯");
        activate.setTextColor(Color.WHITE);
        activate.setTextSize(18);
        activate.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        activate.setBackground(gradient(0xff09c6ff,0xff064ae5,40,0xff18d5ff,2));
        activate.setElevation(dp(8));
        LinearLayout.LayoutParams buttonP=new LinearLayout.LayoutParams(-1,dp(51));
        buttonP.topMargin=dp(15);
        card.addView(activate,buttonP);

        message=text("●  Conexão com o servidor necessária para acessar.",12,0xffc0cee0,false);
        card.addView(message,topMargin(14));

        View separator=new View(this);separator.setBackgroundColor(0xff173c5c);
        LinearLayout.LayoutParams sepP=new LinearLayout.LayoutParams(-1,dp(1));
        sepP.topMargin=dp(20);
        card.addView(separator,sepP);

        LinearLayout features=new LinearLayout(this);
        features.setOrientation(LinearLayout.HORIZONTAL);
        features.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(features,topMargin(12));

        TextView online=text("☑  Licenças\nverificadas online",12,0xffc4d9f4,false);
        TextView revoke=text("⟳  Revogação\nautomática",12,0xffc4d9f4,false);
        features.addView(online,new LinearLayout.LayoutParams(0,dp(43),1));
        View split=new View(this);
        split.setBackgroundColor(0xff2b658c);
        features.addView(split,new LinearLayout.LayoutParams(dp(1),dp(36)));
        features.addView(revoke,new LinearLayout.LayoutParams(0,dp(43),1));

        ImageView footer=assetImage("footer.webp");
        LinearLayout.LayoutParams footerP=new LinearLayout.LayoutParams(-1,(int)Math.round(screenWidth*244.0/691.0));
        footerP.topMargin=dp(4);
        page.addView(footer,footerP);
        setContentView(scroller);

        activate.setOnClickListener(v->verify(keyInput.getText().toString().trim().toUpperCase(Locale.ROOT),true));
        String saved=prefs.getString("key","");
        if(!saved.isEmpty()) {
            keyInput.setText(saved);
            verify(saved,false);
        }
    }
    private void pasteKey() {
        try {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard==null || !clipboard.hasPrimaryClip())return;
            ClipData data=clipboard.getPrimaryClip();
            if(data==null || data.getItemCount()<1)return;
            CharSequence value=data.getItemAt(0).coerceToText(this);
            if(value!=null) {
                keyInput.setText(value.toString().trim().toUpperCase(Locale.ROOT));
                keyInput.setSelection(keyInput.length());
            }
        }catch(Exception ignored) {}
    }
    private void status(String info,int color) {
        if(message!=null) {
            message.setText(info);
            message.setTextColor(color);
        }
    }
    private void verify(String key,boolean activateRequest) {
        if(busy)return;
        if(!key.matches("QELV(?:-[A-HJ-NP-Z2-9]{5}){4}")) {
            status("Key inválida. Confira os caracteres.",0xffff8089);return;
        }
        busy=true;activate.setEnabled(false);
        status("Consultando o servidor...",0xffc6e4fb);
        String device=deviceId;
        new Thread(()->{
            LicenseMonitor.Check result=LicenseMonitor.call(SERVER,activateRequest?"activate":"verify",key,device);
            ui.post(()->{
                busy=false;
                activate.setEnabled(true);
                if(isFinishing()||isDestroyed())return;
                if(result.valid) {
                    prefs.edit().putString("key",key).putLong("expires_at",result.expiresAt)
                         .putLong("last_valid_at",System.currentTimeMillis()).apply();
                    long seconds=Math.max(0,result.expiresAt-System.currentTimeMillis()/1000L);
                    status("Key ativa • "+(seconds/86400)+"d "+((seconds%86400)/3600)+"h "+((seconds%3600)/60)+"min",0xff4af3b2);
                    LicenseMonitor.attach(getApplication(),SERVER,PREFS);
                    try {
                        Intent intent=new Intent();
                        intent.setClassName(getPackageName(),MAIN_ACTIVITY);
                        startActivity(intent);
                        finish();
                    } catch(Exception e) {
                        status("Falha ao abrir o Qelvoriflix Pro.",0xffff8089);
                    }
                }else {
                    if("revoked".equals(result.status)||"expired".equals(result.status)
                        ||"invalid_key".equals(result.status)) {
                        prefs.edit().remove("key").remove("expires_at").apply();
                    }
                    status(result.message,0xffff8089);
                }
            });
        },"QelvoriflixKeyActivation").start();
    }
}
