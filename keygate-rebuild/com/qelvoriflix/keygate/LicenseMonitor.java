package com.qelvoriflix.keygate;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LicenseMonitor implements Application.ActivityLifecycleCallbacks {
    private static volatile LicenseMonitor singleton;
    private final Application app;
    private final SharedPreferences prefs;
    private final String server;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean checking=new AtomicBoolean(false);
    private volatile boolean blocked=false;
    private volatile boolean active=false;
    private long lastOk=System.currentTimeMillis();
    private final Runnable task=new Runnable() {
        public void run() {
            if(blocked) return;
            if(active) {
                String key=prefs.getString("key","");
                if(key.isEmpty()) {block("A licença precisa ser ativada.");return;}
                long expires=prefs.getLong("expires_at",0L);
                if(expires>0L && expires<=System.currentTimeMillis()/1000L) {
                    block("Esta Key expirou.");return;
                }
                long now=System.currentTimeMillis();
                if(now-lastOk>=29000L)checkInBackground(key);
            }
            main.postDelayed(this,3000L);
        }
    };
    public static final class Check {
        public final boolean valid;
        public final String status;
        public final String message;
        public final long expiresAt;
        private Check(boolean v,String s,String m,long e) {
            valid=v;status=s;message=m;expiresAt=e;
        }
    }
    public static Check call(String base,String operation,String key,String device) {
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(base+"/api/v1/"+operation).openConnection();
            c.setConnectTimeout(9000);c.setReadTimeout(9000);
            c.setUseCaches(false);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setRequestProperty("Cache-Control","no-store");
            JSONObject body=new JSONObject();
            body.put("key",key);body.put("device_id",device);
            try(OutputStream out=c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code=c.getResponseCode();
            InputStream stream=code>=400?c.getErrorStream():c.getInputStream();
            if(stream==null) return new Check(false,"server_error","Servidor indisponível.",0);
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(InputStream in=stream) {
                byte[] bytes=new byte[2048];int n;
                while((n=in.read(bytes))!=-1) {
                    if(data.size()+n>32768)throw new IOException("Response too large");
                    data.write(bytes,0,n);
                }
            }
            JSONObject obj=new JSONObject(data.toString("UTF-8"));
            String status=obj.optString("status","server_error");
            long expiry=obj.optLong("expires_at",0L);
            boolean valid=code==200 && obj.optBoolean("valid",false)
                && "active".equals(status) && expiry>System.currentTimeMillis()/1000L;
            String msg=obj.optString("message","");
            if(!valid && msg.isEmpty())msg="Licença inválida. Verifique sua conexão.";
            return new Check(valid,status,msg,expiry);
        } catch(Exception error) {
            return new Check(false,"offline",
               "Servidor indisponível. Conecte-se à internet para validar a licença.",0L);
        } finally {
            if(c!=null)c.disconnect();
        }
    }
    private LicenseMonitor(Application a,String base,String name) {
        app=a;server=base;prefs=a.getSharedPreferences(name,0);
        lastOk=System.currentTimeMillis();
        a.registerActivityLifecycleCallbacks(this);
        main.postDelayed(task,3000L);
    }
    public static synchronized void attach(Application a,String base,String name) {
        if(singleton==null)singleton=new LicenseMonitor(a,base,name);
        else {
            singleton.blocked=false;
            singleton.lastOk=System.currentTimeMillis();
            singleton.main.removeCallbacks(singleton.task);
            singleton.main.postDelayed(singleton.task,3000L);
        }
    }
    private void checkInBackground(String key) {
        if(!checking.compareAndSet(false,true))return;
        String device=prefs.getString("device_id","");
        new Thread(()->{
            Check result=call(server,"verify",key,device);
            main.post(()->{
                checking.set(false);
                if(blocked)return;
                if(result.valid) {
                    lastOk=System.currentTimeMillis();
                    prefs.edit().putLong("expires_at",result.expiresAt)
                       .putLong("last_valid_at",lastOk).apply();
                } else {
                    if("revoked".equals(result.status)||"expired".equals(result.status)
                      ||"invalid_key".equals(result.status))
                      prefs.edit().remove("key").remove("expires_at").apply();
                    block(result.message);
                }
            });
        },"QelvoriflixLicenseCheck").start();
    }
    private void block(String reason) {
        if(blocked)return;
        blocked=true;active=false;
        main.removeCallbacks(task);
        Intent gate=new Intent();
        gate.setClassName(app.getPackageName(),"com.qelvoriflix.keygate.KeyGateActivity");
        gate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {app.startActivity(gate);}catch(Exception ignored){}
    }
    @Override public void onActivityResumed(Activity activity) {
        if(activity instanceof KeyGateActivity) {active=false;return;}
        active=true;
        if(System.currentTimeMillis()-lastOk>=29000 && !blocked)
            checkInBackground(prefs.getString("key",""));
    }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivityCreated(Activity a,Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
