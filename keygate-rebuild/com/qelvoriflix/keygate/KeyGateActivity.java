package com.qelvoriflix.keygate;

import android.app.Activity;
import android.app.Application;
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
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class KeyGateActivity extends Activity {
    public static final String SERVER="https://qelvoriflix-keys.onrender.com";
    public static final String PREFS="qelvoriflix_pro_keys_2026";
    public static final String MAIN_ACTIVITY="com.th.lisoprime.MainActivity";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView message;
    private TextView title;
    private EditText keyInput;
    private Button activate;
    private SharedPreferences prefs;
    private String deviceId;
    private volatile boolean busy;
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rect(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void padding(View v,int all) {v.setPadding(dp(all),dp(all),dp(all),dp(all));}
    private LinearLayout.LayoutParams margin(int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff060910);
        getWindow().setNavigationBarColor(0xff060910);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        deviceId=prefs.getString("device_id","");
        if(deviceId.length()<16) {
            deviceId=UUID.randomUUID().toString();
            prefs.edit().putString("device_id",deviceId).apply();
        }
        final ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff060910);
        LinearLayout outer=new LinearLayout(this);outer.setGravity(Gravity.CENTER);
        outer.setPadding(dp(20),dp(30),dp(20),dp(30));outer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(outer);
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);padding(card,22);
        card.setBackground(rect(0xff121a29,19));outer.addView(card,margin(0));
        title=text("QELVORIFLIX PRO",25,0xff51b0ff,true);title.setGravity(Gravity.CENTER);
        card.addView(title,margin(0));
        TextView sub=text("Ativação de licença",17,Color.WHITE,true);
        sub.setGravity(Gravity.CENTER);card.addView(sub,margin(11));
        TextView info=text("Insira sua Key de 2, 4 ou 8 semanas para continuar.",
            14,0xffafbdce,false);
        info.setGravity(Gravity.CENTER);card.addView(info,margin(11));
        keyInput=new EditText(this);keyInput.setSingleLine(true);
        keyInput.setTextColor(Color.WHITE);keyInput.setTextSize(15);
        keyInput.setHintTextColor(0xff8c9aaf);keyInput.setHint("QELV-XXXXX-XXXXX-XXXXX-XXXXX");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        padding(keyInput,16);keyInput.setBackground(rect(0xff222e42,12));
        card.addView(keyInput,margin(20));
        activate=new Button(this);activate.setText("ATIVAR KEY");activate.setAllCaps(false);
        activate.setTextColor(0xff091421);activate.setTextSize(16);activate.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        activate.setBackground(rect(0xff4eacff,12));card.addView(activate,margin(16));
        message=text("Conexão com o servidor necessária para acessar.",13,0xffa9bad1,false);
        message.setGravity(Gravity.CENTER);card.addView(message,margin(17));
        TextView server=text("Licenças verificadas online • Revogação automática",
             11,0xff8195ad,false);
        server.setGravity(Gravity.CENTER);card.addView(server,margin(16));
        setContentView(scroll);
        activate.setOnClickListener(v->verify(keyInput.getText().toString().trim().toUpperCase(),true));
        String old=prefs.getString("key","");
        if(!old.isEmpty()) {
            keyInput.setText(old);
            verify(old,false);
        }
    }
    private void status(String msg,int color) {
        if(message!=null) {message.setText(msg);message.setTextColor(color);}
    }
    private void verify(String key,boolean activateRequest) {
        if(busy) return;
        if(!key.matches("QELV(?:-[A-HJ-NP-Z2-9]{5}){4}")) {
            status("Formato inválido. Confira sua Key.",0xffff838d);return;
        }
        busy=true;activate.setEnabled(false);status("Verificando licença no servidor...",0xffa9c6ec);
        final String device=deviceId;
        new Thread(()->{
            LicenseMonitor.Check result=LicenseMonitor.call(SERVER,
                activateRequest?"activate":"verify",key,device);
            ui.post(()->{
                busy=false;activate.setEnabled(true);
                if(isFinishing()||isDestroyed())return;
                if(result.valid) {
                    prefs.edit().putString("key",key).putLong("expires_at",result.expiresAt)
                      .putLong("last_valid_at",System.currentTimeMillis()).apply();
                    long rem=Math.max(0,result.expiresAt-System.currentTimeMillis()/1000L);
                    status("Key ativa: "+(rem/86400)+" dias, "+((rem%86400)/3600)+" horas, "+
                       ((rem%3600)/60)+" minutos restantes.",0xff65d8ae);
                    LicenseMonitor.attach(getApplication(),SERVER,PREFS);
                    try {
                        Intent next=new Intent();
                        next.setClassName(getPackageName(),MAIN_ACTIVITY);
                        startActivity(next);
                        finish();
                    } catch(Exception e) {
                        status("Não foi possível iniciar o aplicativo: "+e.getClass().getSimpleName(),0xffff838d);
                    }
                } else {
                    if("revoked".equals(result.status)||"expired".equals(result.status)
                            ||"invalid_key".equals(result.status)) {
                        prefs.edit().remove("key").remove("expires_at").apply();
                    }
                    status(result.message,0xffff838d);
                }
            });
        },"QelvoriflixKeyGate").start();
    }
    @Override public void onBackPressed() {
        // O app protegido não abre sem licença válida.
        super.onBackPressed();
    }
}
