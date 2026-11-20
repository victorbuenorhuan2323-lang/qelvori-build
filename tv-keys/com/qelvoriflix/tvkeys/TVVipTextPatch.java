package com.qelvoriflix.tvkeys;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Targeted presentation-only label replacement on the account screen.
 * This does not alter billing, subscription state or network permissions.
 * Handles native TextView and Compose semantics-based UI.
 */
public final class TVVipTextPatch {
    private static final WeakHashMap<Activity, TVVipTextPatch> ACTIVE=new WeakHashMap<>();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Activity activity;
    private final Layer layer;
    private boolean resumed=true;
    private final Runnable poll=new Runnable() {
        public void run() {
            if(!resumed||activity.isFinishing()||activity.isDestroyed())return;
            try {refresh();} catch(Throwable ignored){layer.setTarget(null);}
            ui.postDelayed(this,2400);
        }
    };

    private TVVipTextPatch(Activity a) {
        activity=a;
        layer=new Layer(a);
        layer.setFocusable(false);
        layer.setClickable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ViewGroup decor=(ViewGroup)a.getWindow().getDecorView();
        decor.addView(layer,new ViewGroup.LayoutParams(-1,-1));
        ui.post(poll);
    }
    public static void onActivityResumed(Activity a) {
        if(a==null)return;
        TVVipTextPatch old=ACTIVE.get(a);
        if(old!=null) {old.resumed=true;old.ui.post(old.poll);return;}
        ACTIVE.put(a,new TVVipTextPatch(a));
    }
    public static void onActivityPaused(Activity a) {
        TVVipTextPatch p=ACTIVE.get(a);
        if(p!=null) {
            p.resumed=false;
            p.ui.removeCallbacks(p.poll);
            p.layer.setTarget(null);
        }
    }
    private void refresh() throws Exception {
        ViewGroup decor=(ViewGroup)activity.getWindow().getDecorView();
        Rect rect=new Rect();
        if(replaceNativeText(decor)) {layer.setTarget(null);return;}
        boolean found=searchCompose(decor,rect);
        if(found)layer.setTarget(rect);
        else layer.setTarget(null);
    }
    private boolean replaceNativeText(View v) {
        boolean matched=false;
        if(v instanceof TextView) {
            TextView t=(TextView)v;
            CharSequence current=t.getText();
            if(current!=null&&current.toString().contains("Plano Gratuito")) {
                t.setText(current.toString().replace("Plano Gratuito","Plano VIP"));
                matched=true;
            }
        }
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) {
                View child=g.getChildAt(i);
                if(child==layer)continue;
                if(replaceNativeText(child))matched=true;
            }
        }
        return matched;
    }
    private boolean searchCompose(View root,Rect target) {
        if(root==layer)return false;
        if(root.getClass().getName().contains("AndroidComposeView")) {
            if(searchSemantics(root,target))return true;
            if(searchProvider(root,target))return true;
        }
        if(root instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++)
                if(searchCompose(g.getChildAt(i),target))return true;
        }
        return false;
    }
    private boolean searchSemantics(View compose,Rect rect) {
        try {
            Method getOwner=compose.getClass().getMethod("getSemanticsOwner");
            Object owner=getOwner.invoke(compose);
            if(owner==null)return false;
            Class<?> kt=Class.forName("androidx.compose.ui.semantics.SemanticsOwnerKt");
            Map<?,?> nodes=null;
            for(Method m:kt.getDeclaredMethods()) {
                if(!m.getName().equals("getAllSemanticsNodesToMap"))continue;
                if(!Map.class.isAssignableFrom(m.getReturnType()))continue;
                Class<?>[] p=m.getParameterTypes();
                if(p.length<1||!p[0].isInstance(owner))continue;
                Object[] args=new Object[p.length];args[0]=owner;
                boolean supported=true;
                for(int i=1;i<p.length;i++) {
                    if(p[i]==boolean.class||p[i]==Boolean.class)args[i]=Boolean.TRUE;
                    else {supported=false;break;}
                }
                if(!supported)continue;
                m.setAccessible(true);
                Object value=m.invoke(null,args);
                if(value instanceof Map){nodes=(Map<?,?>)value;break;}
            }
            if(nodes==null)return false;
            for(Object node:nodes.values()) {
                if(node==null)continue;
                Object cfg=invokeNoArgs(node,"getConfig");
                if(cfg==null)cfg=invokeNoArgs(node,"getConfig$ui_release");
                String summary=String.valueOf(cfg);
                if(!summary.contains("Plano Gratuito"))continue;
                if(rectForNode(node,compose,rect))return true;
            }
        }catch(Throwable ignored){}
        return false;
    }
    private Object invokeNoArgs(Object target,String name) {
        try{
            Method m=target.getClass().getMethod(name);return m.invoke(target);
        }catch(Throwable ignored) {return null;}
    }
    private static Float floatMethod(Object obj,String name){
        try {
            Object v=obj.getClass().getMethod(name).invoke(obj);
            return v instanceof Number?((Number)v).floatValue():null;
        }catch(Throwable ignored){return null;}
    }
    private boolean rectForNode(Object node,View compose,Rect target) {
        try {
            Object bounds=invokeNoArgs(node,"getBoundsInWindow");
            if(bounds==null)return false;
            Float l=floatMethod(bounds,"getLeft"), t=floatMethod(bounds,"getTop");
            Float r=floatMethod(bounds,"getRight"), b=floatMethod(bounds,"getBottom");
            if(l==null||t==null||r==null||b==null)return false;
            float w=r-l,h=b-t;
            // Reject merged content cards; only cover the title itself.
            if(w<40||w>600||h<12||h>105)return false;
            int[] decorLoc=new int[2];
            activity.getWindow().getDecorView().getLocationInWindow(decorLoc);
            target.set(Math.round(l)-decorLoc[0],Math.round(t)-decorLoc[1],
                Math.round(r)-decorLoc[0],Math.round(b)-decorLoc[1]);
            return true;
        }catch(Throwable ignored){return false;}
    }
    private boolean searchProvider(View compose,Rect target) {
        try {
            AccessibilityNodeProvider provider=compose.getAccessibilityNodeProvider();
            if(provider==null)return false;
            Rect bounds=new Rect();
            for(int id=-1;id<900;id++){
                AccessibilityNodeInfo info=null;
                try{info=provider.createAccessibilityNodeInfo(id);}catch(Throwable ignored){}
                if(info==null)continue;
                CharSequence raw=info.getText();
                if(raw!=null&&raw.toString().contains("Plano Gratuito")) {
                    info.getBoundsInScreen(bounds);
                    int[] loc=new int[2];
                    activity.getWindow().getDecorView().getLocationOnScreen(loc);
                    target.set(bounds.left-loc[0],bounds.top-loc[1],
                        bounds.right-loc[0],bounds.bottom-loc[1]);
                    if(target.width()>40&&target.width()<600&&target.height()>12&&target.height()<105)return true;
                }
            }
        }catch(Throwable ignored){}
        return false;
    }
    private static final class Layer extends View {
        private final Paint fill=new Paint(3), ink=new Paint(3);
        private Rect target;
        private Layer(Activity a){
            super(a);
            fill.setColor(Color.rgb(17,23,35));
            ink.setColor(Color.rgb(248,250,255));
            ink.setTypeface(android.graphics.Typeface.create("sans-serif-medium",1));
        }
        void setTarget(Rect r){
            target=r==null?null:new Rect(r);
            invalidate();
        }
        @Override public void onDraw(Canvas c){
            super.onDraw(c);
            if(target==null)return;
            float pad=8*getResources().getDisplayMetrics().density;
            float x=target.left,y=target.top;
            float height=target.height();
            // The old text is masked without modifying other UI controls.
            c.drawRoundRect(new RectF(x-pad,y-pad,target.right+pad,target.bottom+pad),
                3,3,fill);
            ink.setTextSize(Math.min(height*0.81f,27*getResources().getDisplayMetrics().scaledDensity));
            Paint.FontMetrics fm=ink.getFontMetrics();
            float baseline=y+height/2f-(fm.ascent+fm.descent)/2f;
            c.drawText("Plano VIP",x,baseline,ink);
        }
    }
}
