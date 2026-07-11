package ru.tzfviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

final class OrientationCubeView extends View {
    interface Listener { void view(float yaw, float pitch); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    OrientationCubeView(Context c, Listener l) { super(c); listener=l; setMinimumHeight(150); }
    @Override protected void onDraw(Canvas c) {
        float w=getWidth(), h=getHeight(), s=Math.min(w,h)*.62f, x=(w-s)*.5f, y=(h-s)*.28f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(34,57,75)); c.drawRect(x,y,x+s,y+s,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setColor(Color.rgb(80,210,255)); c.drawRect(x,y,x+s,y+s,paint);
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(s*.22f); paint.setColor(Color.WHITE);
        c.drawText("ВЕРХ",w*.5f,y+s*.52f,paint); paint.setTextSize(s*.16f);
        c.drawText("С",w*.5f,y-s*.08f,paint); c.drawText("Ю",w*.5f,y+s*1.15f,paint); c.drawText("З",x-s*.12f,y+s*.55f,paint); c.drawText("В",x+s*1.12f,y+s*.55f,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e) { if(e.getAction()!=MotionEvent.ACTION_UP)return true; float x=e.getX()/getWidth(), y=e.getY()/getHeight(); if(y<.2f)listener.view(180,0); else if(y>.8f)listener.view(0,0); else if(x<.2f)listener.view(-90,0); else if(x>.8f)listener.view(90,0); else listener.view(0,-90); return true; }
}
