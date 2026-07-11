package ru.tzfviewer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public final class PointCloudView extends GLSurfaceView {
    interface MeasureListener { void onMeasure(float length,float deltaZ); }
    interface TransformListener { void onTransform(float[] values); }
    interface OrientationListener { void onOrientation(float yaw,float pitch); }
    private final CloudRenderer renderer;
    private final ScaleGestureDetector scale;
    private float lastX,lastY;
    private boolean measureMode;
    private MeasureListener measureListener;
    private TransformListener transformListener;
    private OrientationListener orientationListener;
    private ValueAnimator cameraAnimator;
    private float uiYaw=25f,uiPitch=-18f;

    public PointCloudView(Context context){this(context,null);}
    public PointCloudView(Context context,AttributeSet attrs){
        super(context);setEGLContextClientVersion(2);renderer=new CloudRenderer();setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float f=d.getScaleFactor();queueEvent(()->renderer.zoom=Math.max(.35f,Math.min(8f,renderer.zoom*f)));requestRender();return true;}});
    }

    void setTransformListener(TransformListener listener){transformListener=listener;}
    void setOrientationListener(OrientationListener listener){orientationListener=listener;if(listener!=null)listener.onOrientation(uiYaw,uiPitch);}
    void setCloud(float[] xyz){queueEvent(()->renderer.setCloud(xyz));requestRender();}
    void setSecondaryCloud(float[] xyz){queueEvent(()->renderer.setSecondaryCloud(xyz));requestRender();}
    void setSecondaryTransform(float[] v){float[] copy=v.clone();queueEvent(()->renderer.setTransform(copy));requestRender();}
    void setPrimaryVisible(boolean v){queueEvent(()->renderer.primaryVisible=v);requestRender();}
    void setSecondaryVisible(boolean v){queueEvent(()->renderer.secondaryVisible=v);requestRender();}
    void fitView(){queueEvent(()->renderer.zoom=1f);requestRender();}
    void setPreset(float yaw,float pitch){
        cancelCameraAnimation();
        final float startYaw=uiYaw,startPitch=uiPitch;
        final float deltaYaw=ViewCubeMath.shortestYawDelta(startYaw,yaw);
        final float targetPitch=ViewCubeMath.clampPitch(pitch);
        cameraAnimator=ValueAnimator.ofFloat(0f,1f);
        cameraAnimator.setDuration(280);
        cameraAnimator.addUpdateListener(a->{float t=(float)a.getAnimatedValue();float eased=t*t*(3f-2f*t);applyOrientation(startYaw+deltaYaw*eased,startPitch+(targetPitch-startPitch)*eased);});
        cameraAnimator.start();
    }
    void rotateCamera(float deltaYaw,float deltaPitch){cancelCameraAnimation();applyOrientation(uiYaw+deltaYaw,uiPitch+deltaPitch);}
    private void applyOrientation(float yaw,float pitch){uiYaw=ViewCubeMath.normalizeYaw(yaw);uiPitch=ViewCubeMath.clampPitch(pitch);float y=uiYaw,p=uiPitch;queueEvent(()->{renderer.yaw=y;renderer.pitch=p;});requestRender();notifyOrientation();}
    private void notifyOrientation(){if(orientationListener!=null)orientationListener.onOrientation(uiYaw,uiPitch);}
    private void cancelCameraAnimation(){if(cameraAnimator!=null){cameraAnimator.cancel();cameraAnimator=null;}}
    void toggleProjection(){queueEvent(()->renderer.orthographic=!renderer.orthographic);requestRender();}
    void setMeasureMode(boolean enabled,MeasureListener listener){measureMode=enabled;measureListener=listener;queueEvent(renderer::clearMeasure);requestRender();}

    @Override public boolean onTouchEvent(MotionEvent e){
        scale.onTouchEvent(e);
        float x=e.getX(),y=e.getY();
        if(e.getPointerCount()>1){lastX=x;lastY=y;queueEvent(renderer::endGizmo);requestRender();return true;}
        if(scale.isInProgress())return true;
        if(measureMode&&e.getActionMasked()==MotionEvent.ACTION_UP){queueEvent(()->{float[] m=renderer.pickPoint(x,y);if(m!=null&&measureListener!=null)post(()->measureListener.onMeasure(m[0],m[1]));});return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){cancelCameraAnimation();lastX=x;lastY=y;queueEvent(()->renderer.beginGizmo(x,y));requestRender();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=x-lastX,dy=y-lastY;lastX=x;lastY=y;queueEvent(()->{float[] t=renderer.moveGesture(dx,dy,x,y);float cameraYaw=renderer.yaw,cameraPitch=renderer.pitch;post(()->{if(t!=null&&transformListener!=null)transformListener.onTransform(t);uiYaw=ViewCubeMath.normalizeYaw(cameraYaw);uiPitch=ViewCubeMath.clampPitch(cameraPitch);notifyOrientation();});});requestRender();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){queueEvent(renderer::endGizmo);requestRender();}
        return true;
    }

    private static final class CloudRenderer implements GLSurfaceView.Renderer {
        private static final String VS="uniform mat4 uMvp;uniform float uSize;attribute vec3 aPosition;void main(){gl_Position=uMvp*vec4(aPosition,1.0);gl_PointSize=uSize;}";
        private static final String FS="precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=uColor;}";
        private final float[] projection=new float[16],view=new float[16],commonModel=new float[16],primaryMv=new float[16],primaryMvp=new float[16],inversePrimaryMvp=new float[16];
        private final float[] local=new float[16],secondaryModel=new float[16],secondaryMv=new float[16],secondaryMvp=new float[16],gridMvp=new float[16];
        private final float[] transform=new float[4],projectIn=new float[4],projectOut=new float[4],gizmoPivot=new float[3];
        private final TransformGizmo gizmo=new TransformGizmo();
        private final float[] measure=new float[6];private int measureCount;
        private final FloatBuffer measureBuffer=direct(6),gizmoBuffer=direct(512);
        private FloatBuffer points,secondaryPoints,gridBuffer;private float[] cloud;
        private int pointCount,secondaryCount,gridCount,width,height,program,position,matrix,color,size;
        private float cx,cy,cz,span=1f,secondaryCx,secondaryCy,secondaryCz;
        private boolean inverseMvpValid;
        boolean primaryVisible=true,secondaryVisible=true,orthographic;
        float yaw=25f,pitch=-18f,zoom=1f;

        static FloatBuffer direct(int floats){return ByteBuffer.allocateDirect(floats*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
        void setTransform(float[] v){System.arraycopy(v,0,transform,0,Math.min(4,v.length));}
        void setCloud(float[] xyz){cloud=xyz;pointCount=xyz.length/3;float[] b=bounds(xyz);cx=(b[0]+b[3])*.5f;cy=(b[1]+b[4])*.5f;cz=(b[2]+b[5])*.5f;span=Math.max(1f,Math.max(b[3]-b[0],Math.max(b[4]-b[1],b[5]-b[2])));points=direct(xyz.length);points.put(xyz).position(0);buildGrid();}
        void setSecondaryCloud(float[] xyz){secondaryCount=xyz.length/3;float[] b=bounds(xyz);secondaryCx=(b[0]+b[3])*.5f;secondaryCy=(b[1]+b[4])*.5f;secondaryCz=(b[2]+b[5])*.5f;secondaryPoints=direct(xyz.length);secondaryPoints.put(xyz).position(0);}
        static float[] bounds(float[] a){float[] b={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};for(int i=0;i<a.length;i+=3){b[0]=Math.min(b[0],a[i]);b[3]=Math.max(b[3],a[i]);b[1]=Math.min(b[1],a[i+1]);b[4]=Math.max(b[4],a[i+1]);b[2]=Math.min(b[2],a[i+2]);b[5]=Math.max(b[5],a[i+2]);}return b;}
        void buildGrid(){float raw=span/10f,pow=(float)Math.pow(10,Math.floor(Math.log10(raw))),n=raw/pow,step=(n<2?1:n<5?2:5)*pow;int half=10;gridCount=(half*2+1)*4+6;gridBuffer=direct(gridCount*3);float extent=step*half;for(int i=-half;i<=half;i++){float p=i*step;put(gridBuffer,cx-extent,p+cy,0,cx+extent,p+cy,0);put(gridBuffer,p+cx,cy-extent,0,p+cx,cy+extent,0);}put(gridBuffer,cx-extent,0,0,cx+extent,0,0);put(gridBuffer,0,cy-extent,0,0,cy+extent,0);put(gridBuffer,0,0,-extent*.25f,0,0,extent*.25f);gridBuffer.position(0);}
        static void put(FloatBuffer b,float...v){b.put(v);}

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig cfg){GLES20.glClearColor(.015f,.025f,.04f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);program=link(VS,FS);position=GLES20.glGetAttribLocation(program,"aPosition");matrix=GLES20.glGetUniformLocation(program,"uMvp");color=GLES20.glGetUniformLocation(program,"uColor");size=GLES20.glGetUniformLocation(program,"uSize");}
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);if(points==null)return;setMatrices();GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(position);GLES20.glUniform1f(size,2.2f);
            if(gridBuffer!=null){draw(gridBuffer,gridCount,GLES20.GL_LINES,gridMvp,.20f,.28f,.34f,1);drawAxes();}
            if(primaryVisible)draw(points,pointCount,GLES20.GL_POINTS,primaryMvp,.12f,.78f,1,1);
            if(secondaryVisible&&secondaryPoints!=null){draw(secondaryPoints,secondaryCount,GLES20.GL_POINTS,secondaryMvp,1,.58f,.12f,1);drawGizmo();}
            if(measureCount==2){measureBuffer.position(0);measureBuffer.put(measure).position(0);GLES20.glLineWidth(3);draw(measureBuffer,2,GLES20.GL_LINES,primaryMvp,1,1,1,1);}
            GLES20.glDisableVertexAttribArray(position);
        }
        void setMatrices(){float aspect=(float)width/Math.max(1,height);if(orthographic)Matrix.orthoM(projection,0,-2*aspect/zoom,2*aspect/zoom,-2/zoom,2/zoom,.1f,20);else Matrix.perspectiveM(projection,0,45,aspect,.1f,20);float yr=(float)Math.toRadians(yaw),pr=(float)Math.toRadians(pitch),d=orthographic?4f:3.5f/zoom;float ex=(float)(d*Math.cos(pr)*Math.sin(yr)),ey=(float)(-d*Math.sin(pr)),ez=(float)(d*Math.cos(pr)*Math.cos(yr));Matrix.setLookAtM(view,0,ex,ey,ez,0,0,0,0,1,0);Matrix.setIdentityM(commonModel,0);Matrix.scaleM(commonModel,0,2/span,2/span,2/span);Matrix.translateM(commonModel,0,-cx,-cy,-cz);Matrix.multiplyMM(primaryMv,0,view,0,commonModel,0);Matrix.multiplyMM(primaryMvp,0,projection,0,primaryMv,0);inverseMvpValid=Matrix.invertM(inversePrimaryMvp,0,primaryMvp,0);System.arraycopy(primaryMvp,0,gridMvp,0,16);Matrix.setIdentityM(local,0);Matrix.translateM(local,0,transform[0],transform[1],transform[2]);Matrix.translateM(local,0,secondaryCx,secondaryCy,secondaryCz);Matrix.rotateM(local,0,transform[3],0,0,1);Matrix.translateM(local,0,-secondaryCx,-secondaryCy,-secondaryCz);Matrix.multiplyMM(secondaryModel,0,commonModel,0,local,0);Matrix.multiplyMM(secondaryMv,0,view,0,secondaryModel,0);Matrix.multiplyMM(secondaryMvp,0,projection,0,secondaryMv,0);updateGizmoPivot();if(inverseMvpValid)gizmo.updateScale(gizmoPivot,primaryMvp,inversePrimaryMvp,width,height);}
        void draw(FloatBuffer b,int count,int mode,float[] m,float r,float g,float bl,float a){b.position(0);GLES20.glUniformMatrix4fv(matrix,1,false,m,0);GLES20.glUniform4f(color,r,g,bl,a);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,12,b);GLES20.glDrawArrays(mode,0,count);}
        void drawAxes(){float e=span*.7f;FloatBuffer b=gizmoBuffer;b.position(0);put(b,0,0,0,e,0,0);b.position(0);draw(b,2,GLES20.GL_LINES,gridMvp,1,.18f,.18f,1);b.position(0);put(b,0,0,0,0,e,0);b.position(0);draw(b,2,GLES20.GL_LINES,gridMvp,.18f,1,.3f,1);b.position(0);put(b,0,0,0,0,0,e);b.position(0);draw(b,2,GLES20.GL_LINES,gridMvp,.2f,.45f,1,1);}
        void drawGizmo(){float s=gizmo.worldScale(),x=gizmoPivot[0],y=gizmoPivot[1],z=gizmoPivot[2];FloatBuffer b=gizmoBuffer;GLES20.glLineWidth(5);drawGizmoLine(b,x,y,z,x+s,y,z,TransformGizmo.X,1,.15f,.15f);drawGizmoLine(b,x,y,z,x,y+s,z,TransformGizmo.Y,.15f,1,.3f);drawGizmoLine(b,x,y,z,x,y,z+s,TransformGizmo.Z,.2f,.5f,1);b.position(0);float c=s*.12f;put(b,x-c,y-c,z,x+c,y+c,z,x-c,y+c,z,x+c,y-c,z);b.position(0);float xy=gizmo.activeHandle()==TransformGizmo.XY?1f:.72f;draw(b,4,GLES20.GL_LINES,primaryMvp,xy,xy,xy,1);b.position(0);int seg=48;for(int i=0;i<seg;i++){double a=i*Math.PI*2/seg,n=(i+1)*Math.PI*2/seg;put(b,x+(float)Math.cos(a)*s*.75f,y+(float)Math.sin(a)*s*.75f,z,x+(float)Math.cos(n)*s*.75f,y+(float)Math.sin(n)*s*.75f,z);}b.position(0);float hi=gizmo.activeHandle()==TransformGizmo.RZ?1f:.75f;draw(b,seg*2,GLES20.GL_LINES,primaryMvp,1,hi,.15f,1);}
        void drawGizmoLine(FloatBuffer b,float x1,float y1,float z1,float x2,float y2,float z2,int handle,float r,float g,float bl){b.position(0);put(b,x1,y1,z1,x2,y2,z2);b.position(0);float boost=gizmo.activeHandle()==handle?1f:.78f;draw(b,2,GLES20.GL_LINES,primaryMvp,Math.min(1,r*boost+.2f*(1-boost)),Math.min(1,g*boost+.2f*(1-boost)),Math.min(1,bl*boost+.2f*(1-boost)),1);}
        void updateGizmoPivot(){gizmoPivot[0]=secondaryCx+transform[0];gizmoPivot[1]=secondaryCy+transform[1];gizmoPivot[2]=secondaryCz+transform[2];}
        void beginGizmo(float x,float y){if(secondaryPoints==null||!secondaryVisible||!inverseMvpValid){gizmo.endDrag();return;}updateGizmoPivot();gizmo.beginDrag(x,y,transform,gizmoPivot,primaryMvp,inversePrimaryMvp,width,height);}
        float[] moveGesture(float dx,float dy,float x,float y){if(gizmo.activeHandle()!=TransformGizmo.NONE){float[] next=gizmo.updateDrag(x,y,inversePrimaryMvp,width,height);if(next!=null){setTransform(next);return transform.clone();}return null;}yaw=ViewCubeMath.normalizeYaw(yaw+dx*.35f);pitch=ViewCubeMath.clampPitch(pitch+dy*.35f);return null;}
        void endGizmo(){gizmo.endDrag();}
        void clearMeasure(){measureCount=0;}
        float[] pickPoint(float sx,float sy){if(cloud==null)return null;int best=-1;float bd=2500;for(int i=0;i<cloud.length;i+=3){projectIn[0]=cloud[i];projectIn[1]=cloud[i+1];projectIn[2]=cloud[i+2];projectIn[3]=1;Matrix.multiplyMV(projectOut,0,primaryMvp,0,projectIn,0);if(projectOut[3]<=0)continue;float x=(projectOut[0]/projectOut[3]*.5f+.5f)*width,y=(1-(projectOut[1]/projectOut[3]*.5f+.5f))*height,d=(x-sx)*(x-sx)+(y-sy)*(y-sy);if(d<bd){bd=d;best=i;}}if(best<0)return null;if(measureCount>=2)measureCount=0;System.arraycopy(cloud,best,measure,measureCount*3,3);measureCount++;if(measureCount<2)return null;float dx=measure[3]-measure[0],dy=measure[4]-measure[1],dz=measure[5]-measure[2];return new float[]{(float)Math.sqrt(dx*dx+dy*dy+dz*dz),Math.abs(dz)};}
        static int link(String v,String f){int vs=compile(GLES20.GL_VERTEX_SHADER,v),fs=compile(GLES20.GL_FRAGMENT_SHADER,f),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));return p;}
        static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    }
}
