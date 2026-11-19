package com.th.lisoprime.license;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public final class KeyGateActivity extends Activity {
  private static final String SERVER="https://license.example.invalid";
  private final Handler handler=new Handler(Looper.getMainLooper());
  private EditText keyEntry;
  private TextView status;
  private Button submit;
  private SharedPreferences prefs;
  private String device;
  private volatile boolean busy;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    prefs=getSharedPreferences("qpro_keys",MODE_PRIVATE);
    device=prefs.getString("device_id","");
    if(device.isEmpty()){
      device=UUID.randomUUID().toString();
      prefs.edit().putString("device_id",device).apply();
    }
    LinearLayout page=new LinearLayout(this);
    page.setOrientation(1);page.setGravity(Gravity.CENTER);
    page.setPadding(32,32,32,32);page.setBackgroundColor(0xff070a0d);
    TextView heading=new TextView(this);
    heading.setText("QELVORIFLIX PRO");heading.setTextSize(25);
    heading.setTextColor(0xff4caeff);heading.setGravity(Gravity.CENTER);
    page.addView(heading);
    TextView subtitle=new TextView(this);
    subtitle.setText("Acesso com Key de 2, 4 ou 8 semanas");
    subtitle.setTextColor(Color.WHITE);subtitle.setGravity(Gravity.CENTER);
    page.addView(subtitle);
    keyEntry=new EditText(this);
    keyEntry.setSingleLine(true);keyEntry.setTextColor(Color.WHITE);
    keyEntry.setHintTextColor(0xffaab3bf);keyEntry.setHint("Insira sua Key QPRO-...");
    page.addView(keyEntry);
    submit=new Button(this);submit.setText("ATIVAR KEY");page.addView(submit);
    status=new TextView(this);status.setGravity(Gravity.CENTER);
    status.setTextColor(Color.WHITE);status.setText("É necessária uma Key válida.");
    page.addView(status);setContentView(page);
    submit.setOnClickListener(v->verify(keyEntry.getText().toString().trim(),true));
    String previous=prefs.getString("key","");
    if(!previous.isEmpty()){keyEntry.setText(previous);verify(previous,false);}
  }
  private void verify(String key,boolean activate) {
    if(busy)return;
    if(!key.startsWith("QPRO-")){status.setText("Key inválida");return;}
    busy=true;submit.setEnabled(false);status.setText("Consultando servidor...");
    new Thread(()->{
      boolean approved=false;long expires=0;
      String reason="Sem conexão com o servidor.";
      try{
        HttpURLConnection c=(HttpURLConnection)new URL(SERVER+"/api/license/"+(activate?"activate":"status")).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        JSONObject request=new JSONObject();
        request.put("key",key);request.put("device_id",device);
        try(OutputStream out=c.getOutputStream()){
          out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code=c.getResponseCode();
        InputStream in=code<400?c.getInputStream():c.getErrorStream();
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(InputStream stream=in){byte[] buffer=new byte[2048];int n;
          while((n=stream.read(buffer))!=-1){
            if(result.size()+n>16384)throw new IOException("Resposta longa");
            result.write(buffer,0,n);
          }
        }
        JSONObject json=new JSONObject(result.toString("UTF-8"));
        expires=json.optLong("expires_at",0);
        approved=code==200 && json.optBoolean("valid") && expires>System.currentTimeMillis()/1000L;
        reason=json.optString("error","Key inválida ou expirada.");
      }catch(Exception e){reason="Não foi possível validar no servidor.";}
      final boolean ok=approved;final String error=reason;final long expiration=expires;
      handler.post(()->{
        busy=false;submit.setEnabled(true);
        if(ok){
          prefs.edit().putString("key",key).apply();
          long seconds=Math.max(0,expiration-System.currentTimeMillis()/1000L);
          status.setText((seconds/86400)+" dias, "+((seconds%86400)/3600)+" horas, "+((seconds%3600)/60)+" minutos");
          try{
            Intent main=new Intent();
            main.setClassName(getPackageName(),"com.th.lisoprime.MainActivity");
            startActivity(main);finish();
          }catch(Exception e){status.setText("Erro ao abrir aplicativo protegido.");}
        }else{
          prefs.edit().remove("key").apply();status.setText(error);
        }
      });
    }).start();
  }
}
