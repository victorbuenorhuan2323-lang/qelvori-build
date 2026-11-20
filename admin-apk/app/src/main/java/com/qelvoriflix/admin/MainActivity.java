package com.qelvoriflix.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.ArrayList;

public final class MainActivity extends Activity {
  private static final int BG=0xff080d15, PANEL=0xff172236, BLUE=0xff369eff, WHITE=0xfff4f8ff, MUTED=0xffa9bdd5;
  private final ExecutorService net=Executors.newSingleThreadExecutor();
  private final Handler main=new Handler(Looper.getMainLooper());
  private LinearLayout root, cards, generated;
  private EditText urlField, userField, passwordField, noteField;
  private Spinner period;
  private TextView feedback;
  private String baseUrl="", token="";
  private boolean showingDashboard=false, busy=false;
  private static final class Countdown {
    final TextView view;
    final long initialSeconds;
    final long startElapsed;
    Countdown(TextView view,long seconds) {
      this.view=view;
      this.initialSeconds=Math.max(0L,seconds);
      this.startElapsed=SystemClock.elapsedRealtime();
    }
  }
  private final ArrayList<Countdown> countdowns=new ArrayList<>();
  private static String timeString(long seconds) {
    long s=Math.max(0L,seconds);
    return "Restante: "+(s/86400)+"d "+((s%86400)/3600)+"h "+((s%3600)/60)+"min "+(s%60)+"s";
  }
  private void paintCountdowns() {
    long now=SystemClock.elapsedRealtime();
    for(Countdown row:countdowns) {
      long elapsedSeconds=Math.max(0L,(now-row.startElapsed)/1000L);
      row.view.setText(timeString(Math.max(0L,row.initialSeconds-elapsedSeconds)));
    }
  }
  private final Runnable countdownTimer=new Runnable() {
    @Override public void run() {
      if(!showingDashboard)return;
      paintCountdowns();
      main.postDelayed(this,1000L);
    }
  };
  private final Runnable refreshTimer=new Runnable() {
    @Override public void run() {
      if(showingDashboard) { refresh(); main.postDelayed(this,30000); }
    }
  };
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    loginScreen();
  }
  @Override protected void onPause() {
    main.removeCallbacks(refreshTimer);
    main.removeCallbacks(countdownTimer);
    super.onPause();
  }
  @Override protected void onResume() {
    super.onResume();
    if(showingDashboard) {
      main.removeCallbacks(refreshTimer);
      main.removeCallbacks(countdownTimer);
      main.postDelayed(refreshTimer,30000);
      main.post(countdownTimer);
    }
  }
  @Override protected void onDestroy() {
    main.removeCallbacks(refreshTimer);
    main.removeCallbacks(countdownTimer);
    net.shutdownNow();
    super.onDestroy();
  }
  private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
  private GradientDrawable bg(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d; }
  private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v; }
  private LinearLayout.LayoutParams marg(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p; }
  private TextView text(String value,int sp,int color,boolean bold) {
    TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
  }
  private void label(LinearLayout parent,String value,int sp,int color,boolean bold,int top) { parent.addView(text(value,sp,color,bold),marg(top)); }
  private Button button(LinearLayout parent,String title,boolean blue,int top,View.OnClickListener click) {
    Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(blue?BG:WHITE);
    b.setBackground(bg(blue?BLUE:0xff263955));parent.addView(b,marg(top));b.setOnClickListener(click);return b;
  }
  private Button deleteButton(LinearLayout parent,String title,int top,View.OnClickListener click) {
    Button b=new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextColor(0xfffff6f6);
    b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    b.setBackground(bg(0xff9e2434));
    parent.addView(b,marg(top));
    b.setOnClickListener(click);
    return b;
  }
  private EditText entry(LinearLayout parent,String hint,int type,int top) {
    EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(WHITE);
    e.setHintTextColor(MUTED);e.setHint(hint);e.setInputType(type);
    e.setPadding(dp(13),dp(13),dp(13),dp(13));e.setBackground(bg(0xff24324a));
    parent.addView(e,marg(top));return e;
  }
  private LinearLayout card(LinearLayout parent,int top) {
    LinearLayout v=column();v.setPadding(dp(17),dp(17),dp(17),dp(17));v.setBackground(bg(PANEL));
    parent.addView(v,marg(top));return v;
  }
  private void base() {
    ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
    root=column();root.setPadding(dp(18),dp(22),dp(18),dp(35));scroll.addView(root);
    setContentView(scroll);
    ImageView logo=new ImageView(this);
    logo.setImageResource(R.drawable.qelvoriflix_key_admin_premium);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(82),dp(82));
    iconParams.gravity=android.view.Gravity.CENTER_HORIZONTAL;
    root.addView(logo,iconParams);
    TextView brand=text("QELVORIFLIX",25,BLUE,true);
    brand.setGravity(android.view.Gravity.CENTER);
    root.addView(brand,marg(8));
    TextView caption=text("CENTRAL DE KEYS  •  ADMIN",12,MUTED,true);
    caption.setGravity(android.view.Gravity.CENTER);
    root.addView(caption,marg(3));
  }
  private void loginScreen() {
    showingDashboard=false;busy=false;token="";
    countdowns.clear();
    main.removeCallbacks(refreshTimer);
    main.removeCallbacks(countdownTimer);
    base();
    LinearLayout c=card(root,27);label(c,"Entrar no painel",22,WHITE,true,0);
    label(c,"Seu gerador de Keys e controle de acesso.",13,MUTED,false,7);
    SharedPreferences prefs=getSharedPreferences("admin",MODE_PRIVATE);
    label(c,"SERVIDOR HTTPS",12,MUTED,true,20);
    urlField=entry(c,"https://seu-servidor.com",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,7);
    urlField.setText(prefs.getString("host",""));
    label(c,"USUÁRIO",12,MUTED,true,14);
    userField=entry(c,"admin",InputType.TYPE_CLASS_TEXT,7);userField.setText(prefs.getString("user","admin"));
    label(c,"SENHA",12,MUTED,true,14);
    passwordField=entry(c,"Senha do painel",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,7);
    button(c,"Entrar",true,20,v->login());
    feedback=text("A senha não é armazenada no aplicativo.",12,MUTED,false);c.addView(feedback,marg(11));
  }
  private void error(String s) { runOnUiThread(()->{
    if(showingDashboard) Toast.makeText(this,s,Toast.LENGTH_LONG).show();
    else if(feedback!=null) feedback.setText(s);
  }); }
  private JSONObject request(String method,String path,JSONObject body,boolean auth) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+path).openConnection();
    try {
      c.setInstanceFollowRedirects(false);c.setConnectTimeout(9000);c.setReadTimeout(9000);
      c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");
      c.setRequestProperty("Cache-Control","no-store");
      if(auth)c.setRequestProperty("Authorization","Bearer "+token);
      if(body!=null){
        c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
      }
      int status=c.getResponseCode();
      InputStream in=status>=400?c.getErrorStream():c.getInputStream();
      if(in==null)throw new Exception("Resposta vazia do servidor ("+status+")");
      ByteArrayOutputStream out=new ByteArrayOutputStream();
      try(InputStream s=in){byte[] b=new byte[4096];int n;while((n=s.read(b))!=-1){
        if(out.size()+n>300000)throw new Exception("Resposta muito grande");out.write(b,0,n);
      }}
      JSONObject j=new JSONObject(out.toString("UTF-8"));
      if(status<200||status>=300)throw new Exception(j.optString("message","HTTP "+status));
      return j;
    } finally {c.disconnect();}
  }
  private void login() {
    if(busy)return;
    String u=urlField.getText().toString().trim().replaceAll("/+$","");
    try {
      URI parsed=new URI(u);
      if(!"https".equalsIgnoreCase(parsed.getScheme())||parsed.getHost()==null||
         parsed.getUserInfo()!=null||parsed.getRawQuery()!=null||parsed.getRawFragment()!=null||
         (parsed.getPath()!=null&&!parsed.getPath().isEmpty())) throw new Exception();
    } catch(Exception e){feedback.setText("Informe uma URL HTTPS válida, sem caminhos.");return;}
    String user=userField.getText().toString().trim(),pass=passwordField.getText().toString();
    if(user.isEmpty()||pass.isEmpty()){feedback.setText("Informe usuário e senha.");return;}
    baseUrl=u;busy=true;feedback.setText("Conectando...");
    net.execute(()->{
      try {
        JSONObject body=new JSONObject();body.put("username",user);body.put("password",pass);
        JSONObject answer=request("POST","/api/admin/login",body,false);
        if(!answer.optBoolean("ok"))throw new Exception(answer.optString("message","Login recusado."));
        String t=answer.getString("token");
        runOnUiThread(()->{
          token=t;busy=false;
          getSharedPreferences("admin",MODE_PRIVATE).edit().putString("host",u).putString("user",user).apply();
          dashboard();
        });
      } catch(Exception e){runOnUiThread(()->{busy=false;feedback.setText("Falha: "+e.getMessage());});}
    });
  }
  private void dashboard() {
    showingDashboard=true;base();
    label(root,"Painel administrador",21,WHITE,true,22);
    button(root,"Sair da conta",false,6,v->loginScreen());
    LinearLayout c=card(root,17);
    label(c,"Gerar nova Key",20,WHITE,true,0);
    label(c,"Escolha a duração",12,MUTED,false,12);
    period=new Spinner(this);
    String[] options={"2 semanas (14 dias)","4 semanas (28 dias)","8 semanas (56 dias)"};
    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
    period.setAdapter(adapter);c.addView(period,marg(5));
    noteField=entry(c,"Nome do cliente (opcional)",InputType.TYPE_CLASS_TEXT,10);
    button(c,"Gerar Key",true,12,v->create());
    label(c,"O prazo inicia na primeira ativação.",12,MUTED,false,5);
    generated=column();root.addView(generated,marg(0));
    label(root,"Licenças",20,WHITE,true,24);
    button(root,"Atualizar lista",false,5,v->refresh());
    cards=column();root.addView(cards,marg(8));
    refresh();
    main.removeCallbacks(refreshTimer);
    main.removeCallbacks(countdownTimer);
    main.postDelayed(refreshTimer,30000);
    main.post(countdownTimer);
  }
  private interface Done { void success(JSONObject v); }
  private void doCall(String method,String path,JSONObject input,Done next) {
    if(busy)return;busy=true;
    net.execute(()->{
      try {
        JSONObject j=request(method,path,input,true);
        runOnUiThread(()->{busy=false;if(showingDashboard)next.success(j);});
      } catch(Exception e){
        runOnUiThread(()->{busy=false;
          if(showingDashboard)new AlertDialog.Builder(this).setTitle("Erro de conexão")
             .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
        });
      }
    });
  }
  private void create() {
    int[] weeks={2,4,8};
    JSONObject body=new JSONObject();
    try {body.put("weeks",weeks[period.getSelectedItemPosition()]);body.put("note",noteField.getText().toString().trim());}
    catch(Exception e){return;}
    doCall("POST","/api/admin/keys",body,j->{
      String key=j.optString("key","");generated.removeAllViews();
      LinearLayout c=card(generated,15);label(c,"KEY GERADA — COPIE E GUARDE",13,BLUE,true,0);
      TextView t=text(key,18,WHITE,true);t.setTextIsSelectable(true);c.addView(t,marg(8));
      button(c,"Copiar Key",true,9,v->{
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
           .setPrimaryClip(ClipData.newPlainText("Qelvoriflix Key",key));
        Toast.makeText(this,"Key copiada",Toast.LENGTH_SHORT).show();
      });
      label(c,"A Key completa só é mostrada uma vez.",12,MUTED,false,6);
      noteField.setText("");refresh();
    });
  }
  private void refresh() {
    if(!showingDashboard||busy)return;
    doCall("GET","/api/admin/keys",null,j->{
      countdowns.clear();
      cards.removeAllViews();
      JSONArray a=j.optJSONArray("keys");
      if(a==null||a.length()==0){label(cards,"Nenhuma Key cadastrada.",14,MUTED,false,8);return;}
      for(int i=0;i<a.length();i++) {JSONObject k=a.optJSONObject(i);if(k!=null)drawKey(k);}
    });
  }
  private void drawKey(JSONObject k) {
    LinearLayout c=card(cards,10);
    String state=k.optString("status","?");int id=k.optInt("id");
    label(c,k.optString("hint","Key")+"   •   "+state.toUpperCase(),15,
          "revoked".equals(state)?0xffff7b86:WHITE,true,0);
    label(c,k.optInt("weeks")+" semanas  •  "+k.optString("note",""),13,MUTED,false,6);
    long sec=k.optLong("remaining_seconds",0);
    TextView remaining=text("active".equals(state)?timeString(sec):
      "unused".equals(state)?"Prazo começa na primeira ativação":
      "revoked".equals(state)?"Acesso revogado":
      "expired".equals(state)?"Licença expirada":"Estado desconhecido",
      14,BLUE,true);
    c.addView(remaining,marg(7));
    if("active".equals(state))countdowns.add(new Countdown(remaining,sec));
    label(c,"Dispositivo: "+(k.optBoolean("device_bound")?"vinculado":"livre"),12,MUTED,false,5);
    if("revoked".equals(state))
      button(c,"Restaurar Key",false,9,v->confirm(id,"restore","Restaurar esta Key?"));
    else button(c,"Revogar acesso",false,9,v->confirm(id,"revoke","Bloquear imediatamente esta Key?"));
    if(k.optBoolean("device_bound"))
      button(c,"Liberar aparelho",false,5,v->confirm(id,"reset-device","Liberar dispositivo vinculado?"));
    deleteButton(c,"Excluir Key definitivamente",10,
      v->confirmDelete(id,k.optString("hint","esta Key")));
  }
  private void confirmDelete(int id,String keyHint) {
    new AlertDialog.Builder(this)
      .setTitle("Excluir licença?")
      .setMessage("Excluir "+keyHint+" permanentemente do servidor?"+
        "\n\nEssa ação não pode ser desfeita. A licença deixará de existir e não poderá ser restaurada. "+
        "Para apenas suspender o acesso, use Revogar acesso.")
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Excluir definitivamente",(d,w)->
        doCall("DELETE","/api/admin/keys/"+id,null,j->{
          Toast.makeText(this,"Key excluída permanentemente.",Toast.LENGTH_SHORT).show();
          refresh();
        }))
      .show();
  }
  private void confirm(int id,String action,String message) {
    new AlertDialog.Builder(this).setTitle("Confirmar").setMessage(message)
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Confirmar",(d,w)->doCall("POST","/api/admin/keys/"+id+"/"+action,
          new JSONObject(),j->refresh()))
      .show();
  }
}
