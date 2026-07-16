package ru.tzfviewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OrientationCubeView extends View {
    interface Listener {
        void onPreset(float yaw, float pitch);
        void onRotate(float deltaYaw, float deltaPitch);
    }

    private static final float EDGE_ZONE = .22f;
    private static final int[][] FACE_DATA = {
            { 1,0,0,  0,-1,0, 0,0,1}, {-1,0,0,  0,1,0, 0,0,1},
            {0,0, 1,  1,0,0, 0,1,0}, {0,0,-1,  1,0,0, 0,-1,0},
            {0,-1,0,  1,0,0, 0,0,1}, {0,1,0, -1,0,0, 0,0,1}
    };
    private static final String[] LABELS = {"ПРАВО", "ЛЕВО", "ВЕРХ", "НИЗ", "ПЕРЕД", "ЗАД"};

    private static final String[] ENGLISH_LABELS = {"RIGHT", "LEFT", "TOP", "BOT", "FRONT", "BACK"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Face> visibleFaces = new ArrayList<>(3);
    private final int touchSlop;
    private Listener listener;
    private float yaw = 25f, pitch = -18f, downX, downY, lastX, lastY;
    private boolean dragging, cancelled;
    private Hit activeHit;

    public OrientationCubeView(Context context) { this(context, null); }
    public OrientationCubeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    void setListener(Listener listener) { this.listener = listener; }
    void setOrientation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        this.yaw = ViewCubeMath.normalizeYaw(yaw);
        this.pitch = ViewCubeMath.clampPitch(pitch);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buildFaces();
        for (Face face : visibleFaces) drawFace(canvas, face);
    }

    private void buildFaces() {
        visibleFaces.clear();
        float[] eye = ViewCubeMath.eyeDirection(yaw, pitch);
        float[] right = normalize(new float[]{-eye[1], eye[0], 0f});
        float[] up = normalize(cross(eye, right));
        float size = Math.min(getWidth(), getHeight()) * .245f;
        float cx = getWidth() * .5f, cy = getHeight() * .5f;
        for (int i = 0; i < FACE_DATA.length; i++) {
            int[] d = FACE_DATA[i];
            float facing = d[0] * eye[0] + d[1] * eye[1] + d[2] * eye[2];
            if (facing <= .015f) continue;
            Face face = new Face(i, d, facing);
            for (int corner = 0; corner < 4; corner++) {
                float u = (corner == 1 || corner == 2) ? 1f : -1f;
                float v = corner >= 2 ? 1f : -1f;
                float wx = d[0] + u * d[3] + v * d[6];
                float wy = d[1] + u * d[4] + v * d[7];
                float wz = d[2] + u * d[5] + v * d[8];
                face.x[corner] = cx + dot(wx, wy, wz, right) * size;
                face.y[corner] = cy - dot(wx, wy, wz, up) * size;
            }
            visibleFaces.add(face);
        }
        visibleFaces.sort(Comparator.comparingDouble(f -> f.depth));
    }

    private void drawFace(Canvas canvas, Face face) {
        Path path = face.path();
        Hit selected = activeHit;
        boolean active = selected != null && selected.face == face.index;
        int base = 45 + Math.round(face.depth * 55f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? Color.rgb(54, 174, 214) : Color.rgb(base / 2, base, Math.min(155, base + 35)));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(active ? 5f : 3f);
        paint.setColor(active ? Color.WHITE : Color.rgb(80, 218, 240));
        canvas.drawPath(path, paint);
        float area = Math.abs(cross2(face.x[1]-face.x[0], face.y[1]-face.y[0], face.x[3]-face.x[0], face.y[3]-face.y[0]));
        if (area > getWidth() * getHeight() * .025f) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(10f, Math.min(getWidth(), getHeight()) * .082f));
            canvas.drawText(ENGLISH_LABELS[face.index], average(face.x), average(face.y) - (paint.ascent()+paint.descent())*.5f, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (event.getPointerCount() > 1) { cancelInteraction(); return true; }
        float x = event.getX(), y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            buildFaces(); downX = lastX = x; downY = lastY = y; dragging = false; cancelled = false;
            activeHit = hitTest(x, y); invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (cancelled) return true;
            if (!dragging && distance(x-downX, y-downY) > touchSlop) { dragging = true; activeHit = null; }
            if (dragging && listener != null) listener.onRotate((x-lastX)*.35f, (y-lastY)*.35f);
            else { buildFaces(); activeHit = hitTest(x, y); }
            lastX=x; lastY=y; invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (!cancelled && !dragging && activeHit != null && listener != null) {
                float[] angles = activeHit.angles();
                if (angles != null) listener.onPreset(angles[0], angles[1]);
                performClick();
            }
            cancelInteraction(); return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { cancelInteraction(); return true; }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void cancelInteraction() { dragging=false; cancelled=true; activeHit=null; invalidate(); }

    private Hit hitTest(float px, float py) {
        for (int i=visibleFaces.size()-1; i>=0; i--) {
            Face f=visibleFaces.get(i);
            float ax=f.x[1]-f.x[0], ay=f.y[1]-f.y[0], bx=f.x[3]-f.x[0], by=f.y[3]-f.y[0];
            float det=cross2(ax,ay,bx,by); if(Math.abs(det)<1e-4f) continue;
            float dx=px-f.x[0],dy=py-f.y[0];
            float u=cross2(dx,dy,bx,by)/det, v=cross2(ax,ay,dx,dy)/det;
            if(u<0||u>1||v<0||v>1) continue;
            return f.hit(u,v);
        }
        return null;
    }

    private static final class Face {
        final int index; final int[] d; final float depth; final float[] x=new float[4],y=new float[4];
        Face(int index,int[] d,float depth){this.index=index;this.d=d;this.depth=depth;}
        Path path(){Path p=new Path();p.moveTo(x[0],y[0]);for(int i=1;i<4;i++)p.lineTo(x[i],y[i]);p.close();return p;}
        Hit hit(float u,float v){
            int[] direction={d[0],d[1],d[2]};
            if(u<EDGE_ZONE)add(direction,d,3,-1); else if(u>1-EDGE_ZONE)add(direction,d,3,1);
            if(v<EDGE_ZONE)add(direction,d,6,-1); else if(v>1-EDGE_ZONE)add(direction,d,6,1);
            return new Hit(index,direction[0],direction[1],direction[2]);
        }
        static void add(int[] out,int[] d,int offset,int sign){out[0]+=d[offset]*sign;out[1]+=d[offset+1]*sign;out[2]+=d[offset+2]*sign;}
    }

    private static final class Hit {
        final int face,x,y,z; Hit(int face,int x,int y,int z){this.face=face;this.x=x;this.y=y;this.z=z;}
        float[] angles(){return ViewCubeMath.directionToAngles(x,y,z);}
    }

    private static float dot(float x,float y,float z,float[] b){return x*b[0]+y*b[1]+z*b[2];}
    private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static float[] normalize(float[] v){float n=(float)Math.sqrt(dot(v[0],v[1],v[2],v));if(n<1e-6f)return new float[]{1,0,0};return new float[]{v[0]/n,v[1]/n,v[2]/n};}
    private static float cross2(float ax,float ay,float bx,float by){return ax*by-ay*bx;}
    private static float average(float[] v){return (v[0]+v[1]+v[2]+v[3])*.25f;}
    private static float distance(float x,float y){return (float)Math.sqrt(x*x+y*y);}
}
