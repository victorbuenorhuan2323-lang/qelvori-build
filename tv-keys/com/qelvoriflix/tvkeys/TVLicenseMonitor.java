package com.qelvoriflix.tvkeys;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TVLicenseMonitor implements Application.ActivityLifecycleCallbacks {
    public static final class Result {
        final boolean valid; final String status, message; final long expiration;
        Result(boolean ok,String code,String msg,long expiry){
            valid=ok;status=code;message=msg;expiration=expiry;
        }
    }
    static Result call(String server,String op,String key,String deviceId){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(server+"/api/v1/"+op).openConnection();
            c.setConnectTimeout(70000);
            c.setReadTimeout(70000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setRequestProperty("Cache-Control","no-store");
            JSONObject request=new JSONObject();
            request.put("key",key);request.put("device_id",deviceId);
            try(OutputStream o=c.getOutputStream()){
                o.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            int http=c.getResponseCode();
            InputStream in=http>=400?c.getErrorStream():c.getInputStream();
            if(in==null)return new Result(false,"offline","Servidor indisponível.",0);
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            try(InputStream src=in){
                byte[] block=new byte[2048];int n;
                while((n=src.read(block))!=-1) {
                    if(buf.size()+n>16384)throw new java.io.IOException("Response too large");
                    buf.write(block,0,n);
                }
            }
            JSONObject obj=new JSONObject(buf.toString("UTF-8"));
            long exp=obj.optLong("expires_at",0);
            String status=obj.optString("status","error");
            boolean ok=http==200&&obj.optBoolean("valid")&&
                "active".equals(status)&&exp>System.currentTimeMillis()/1000;
            String msg=obj.optString("message",ok?"Licença ativa":"Key inválida.");
            return new Result(ok,status,msg,exp);
        }catch(Exception ex){
            return new Result(false,"offline",
                "Não foi possível validar. Verifique a internet e tente novamente.",0L);
        }finally{
            if(c!=null)c.disconnect();
        }
    }
    private static volatile TVLicenseMonitor instance;
    private final Application app;
    private final SharedPreferences prefs;
    private final String server;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private boolean running=false;
    private boolean blocked=false;
    private long lastSuccess=android.os.SystemClock.elapsedRealtime();
    private long lastAttempt=0L;
    private final Runnable pulse=new Runnable(){
        public void run(){
            if(blocked)return;
            if(running) {
                long now=android.os.SystemClock.elapsedRealtime();
                long expiry=prefs.getLong("expires_at",0L);
                if(expiry>0L&&expiry<=System.currentTimeMillis()/1000L){
                    stopAccess();return;
                }
                if(now-lastAttempt>=30000L)verify();
            }
            handler.postDelayed(this,3000);
        }
    };
    private TVLicenseMonitor(Application app,String server,String prefsName){
        this.app=app;this.server=server;
        this.prefs=app.getSharedPreferences(prefsName,0);
        app.registerActivityLifecycleCallbacks(this);
        handler.post(pulse);
    }
    static synchronized void attach(Application app,String server,String prefsName) {
        if(instance==null)instance=new TVLicenseMonitor(app,server,prefsName);
        else {
            instance.blocked=false;
            instance.lastSuccess=android.os.SystemClock.elapsedRealtime();
            instance.handler.removeCallbacks(instance.pulse);
            instance.handler.post(instance.pulse);
        }
    }
    private void verify(){
        if(!busy.compareAndSet(false,true))return;
        lastAttempt=android.os.SystemClock.elapsedRealtime();
        String key=prefs.getString("key","");
        String device=prefs.getString("device_id","");
        if(key.isEmpty()||device.isEmpty()){busy.set(false);stopAccess();return;}
        new Thread(()->{
            Result r=call(server,"verify",key,device);
            handler.post(()->{
                busy.set(false);
                if(blocked)return;
                if(r.valid){
                    lastSuccess=android.os.SystemClock.elapsedRealtime();
                    prefs.edit().putLong("expires_at",r.expiration).apply();
                } else if("revoked".equals(r.status) || "expired".equals(r.status) ||
                    "invalid_key".equals(r.status) || "device_mismatch".equals(r.status) ||
                    "not_activated".equals(r.status)) {
                    prefs.edit().remove("key").remove("expires_at").apply();
                    stopAccess();
                }else if(android.os.SystemClock.elapsedRealtime()-lastSuccess>150000L){
                    stopAccess();
                }
            });
        },"TVLicenseRefresh").start();
    }
    private void stopAccess(){
        if(blocked)return;
        blocked=true;running=false;
        handler.removeCallbacks(pulse);
        Intent gate=new Intent();
        gate.setClassName(app.getPackageName(),"com.qelvoriflix.tvkeys.TVKeyGateActivity");
        gate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try{app.startActivity(gate);}catch(Exception ignored){}
    }
    public void onActivityResumed(Activity a){
        if(a instanceof TVKeyGateActivity){running=false;return;}
        running=true;
        TVVipTextPatch.onActivityResumed(a);
        if(android.os.SystemClock.elapsedRealtime()-lastAttempt>=30000L)verify();
    }
    public void onActivityPaused(Activity a){
        TVVipTextPatch.onActivityPaused(a);
    }
    public void onActivityCreated(Activity a,Bundle b){}
    public void onActivityStarted(Activity a){}
    public void onActivityStopped(Activity a){}
    public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){}
}
