package com.qelvoriflix.tvkeys;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.UUID;

public final class TVKeyGateActivity extends Activity {
    private static final String SERVER="https://qelvoriflix-keys.onrender.com";
    private static final String PREFS="qelvoriflix_tv_license";
    private static final String MAIN="com.th.vtela_tv.ui.MainActivity";
    private static final int BG=0xff030915, PANEL=0xff0c192e, BLUE=0xff159dfa, WHITE=0xfff2f7ff;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText entry;
    private Button activate;
    private TextView notice;
    private volatile boolean busy;
    private String deviceId;

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int border,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));
        if(border!=0)d.setStroke(dp(2),border);
        return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout.LayoutParams margin(int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;
    }
    private Button focusButton(String s){
        Button b=new Button(this);
        b.setText(s);b.setAllCaps(false);b.setTextSize(19);b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setFocusable(true);
        b.setBackground(shape(0xff096ed1,0xff25b8ff,13));
        b.setOnFocusChangeListener((v,focused)->{
            b.setBackground(shape(focused?0xff159efa:0xff096ed1,
                focused?0xffc0efff:0xff25b8ff,13));
            b.setScaleX(focused?1.02f:1f);b.setScaleY(focused?1.02f:1f);
        });
        return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        deviceId=prefs.getString("device_id","");
        if(deviceId.length()<16){
            deviceId=UUID.randomUUID().toString();
            prefs.edit().putString("device_id",deviceId).apply();
        }
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);shell.setGravity(Gravity.CENTER);
        shell.setPadding(dp(24),dp(20),dp(24),dp(20));
        scroll.addView(shell,new ScrollView.LayoutParams(-1,-1));

        TextView brand=text("QELVORIFLIX.TV",31,0xff39bbff,true);brand.setGravity(Gravity.CENTER);
        shell.addView(brand,margin(0));
        TextView tagline=text("FILMES  •  SÉRIES  •  TV",13,0xffb1c7e4,false);
        tagline.setGravity(Gravity.CENTER);shell.addView(tagline,margin(4));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28),dp(20),dp(28),dp(22));
        box.setBackground(shape(PANEL,0xff067bbd,20));
        LinearLayout.LayoutParams boxP=margin(18);
        boxP.width=Math.min(dp(740),getResources().getDisplayMetrics().widthPixels-dp(48));
        boxP.gravity=Gravity.CENTER_HORIZONTAL;
        shell.addView(box,boxP);

        TextView symbol=text("◆   🔑   ◆",30,0xff4cb7ff,true);symbol.setGravity(Gravity.CENTER);
        box.addView(symbol,margin(0));
        TextView title=text("Ativação de Licença",25,WHITE,true);title.setGravity(Gravity.CENTER);
        box.addView(title,margin(8));
        TextView detail=text("Digite a Key de 2, 4 ou 8 semanas.",16,0xffb3c5de,false);
        detail.setGravity(Gravity.CENTER);box.addView(detail,margin(9));

        entry=new EditText(this);
        entry.setSingleLine(true);entry.setSelectAllOnFocus(false);
        entry.setTextSize(18);entry.setTextColor(WHITE);entry.setHintTextColor(0xff7fa0c2);
        entry.setHint("QELV-XXXXX-XXXXX-XXXXX-XXXXX");
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        entry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        entry.setPadding(dp(18),dp(12),dp(18),dp(12));
        entry.setBackground(shape(0xff122743,0xff2775ba,12));
        entry.setFocusable(true);
        box.addView(entry,margin(18));

        activate=focusButton("ATIVAR KEY");
        LinearLayout.LayoutParams p=margin(14);p.height=dp(55);
        box.addView(activate,p);
        activate.setOnClickListener(v->check(true));
        entry.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE){check(true);return true;}
            return false;
        });
        notice=text("Conexão com o servidor necessária para acessar.",14,0xffacc7e2,false);
        notice.setGravity(Gravity.CENTER);box.addView(notice,margin(13));
        TextView footer=text("✓ Licenças verificadas online     ⟳ Revogação automática",
            13,0xff7facd7,false);footer.setGravity(Gravity.CENTER);
        box.addView(footer,margin(17));

        setContentView(scroll);
        String old=prefs.getString("key","");
        if(!old.isEmpty()){
            entry.setText(old);
            activate.requestFocus();
            check(false);
        } else entry.requestFocus();
    }
    private void say(String msg,int color){notice.setText(msg);notice.setTextColor(color);}
    private void check(boolean activateNow){
        if(busy)return;
        String key=entry.getText().toString().trim().toUpperCase(Locale.ROOT);
        if(!key.matches("QELV(?:-[A-HJ-NP-Z2-9]{5}){4}")){
            say("Key inválida. Confira os caracteres.",0xffff838a);return;
        }
        busy=true;activate.setEnabled(false);
        say("Verificando a Key no servidor... (a primeira conexão pode demorar)",0xffcee9ff);
        String device=deviceId;
        new Thread(()->{
            TVLicenseMonitor.Result r=TVLicenseMonitor.call(SERVER,activateNow?"activate":"verify",key,device);
            handler.post(()->{
                busy=false;activate.setEnabled(true);
                if(isFinishing()||isDestroyed())return;
                if(r.valid){
                    prefs.edit().putString("key",key).putLong("expires_at",r.expiration).apply();
                    TVLicenseMonitor.attach(getApplication(),SERVER,PREFS);
                    try{
                        Intent intent=new Intent();
                        intent.setClassName(getPackageName(),MAIN);
                        startActivity(intent);finish();
                    }catch(Exception error){
                        say("Não foi possível abrir a TV: "+error.getClass().getSimpleName(),0xffff838a);
                    }
                }else{
                    if("revoked".equals(r.status)||"expired".equals(r.status)||
                       "invalid_key".equals(r.status)){
                        prefs.edit().remove("key").remove("expires_at").apply();
                    }
                    say(r.message,0xffff838a);
                }
            });
        },"TVKeyCheck").start();
    }
}
