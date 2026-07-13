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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PointCloudView extends GLSurfaceView {
    interface PerformanceListener { void onFrameRate(float fps); }
    private volatile PerformanceListener performanceListener;
    void setPerformanceListener(PerformanceListener listener){performanceListener=listener;}
    interface MeasureListener { void onMeasure(float length,float deltaZ); }
    interface TransformListener { void onTransform(String targetNodeId,float[] values); }
    interface OrientationListener { void onOrientation(float yaw,float pitch); }
    private final CloudRenderer renderer;
    private final ScaleGestureDetector scale;
    private float lastX,lastY;
    private boolean discardNextSinglePointerMove;
    private boolean twoFingerGesture;
    private float lastMidX,lastMidY;
    private boolean measureMode;
    private MeasureListener measureListener;
    private TransformListener transformListener;
    private Runnable transformCommitListener;
    private OrientationListener orientationListener;
    private ValueAnimator cameraAnimator;
    private float uiYaw=25f,uiPitch=-18f;

    public PointCloudView(Context context){this(context,null);}
    public PointCloudView(Context context,AttributeSet attrs){
        super(context);setEGLContextClientVersion(2);renderer=new CloudRenderer(fps->{PerformanceListener listener=performanceListener;if(listener!=null)post(()->listener.onFrameRate(fps));});setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float f=d.getScaleFactor();queueEvent(()->renderer.zoom=Math.max(.35f,Math.min(8f,renderer.zoom*f)));requestRender();return true;}});
    }

    void setTransformListener(TransformListener listener){transformListener=listener;}
    void setTransformCommitListener(Runnable listener){transformCommitListener=listener;}
    void setAutoPerformanceMode(boolean enabled){setRenderMode(enabled?RENDERMODE_CONTINUOUSLY:RENDERMODE_WHEN_DIRTY);queueEvent(renderer::resetPerformanceSample);if(!enabled)requestRender();}
    void setOrientationListener(OrientationListener listener){orientationListener=listener;if(listener!=null)listener.onOrientation(uiYaw,uiPitch);}
    void setSceneCloud(String id,float[] xyz,float[] transform,int color,boolean visible){float[] points=xyz.clone(),pose=transform.clone();queueEvent(()->renderer.setSceneCloud(id,points,pose,color,visible));requestRender();}
    void appendSceneCloud(String id,float[] xyz,float[] transform,int color,boolean visible,boolean reset){float[] points=xyz.clone(),pose=transform.clone();queueEvent(()->renderer.appendSceneCloud(id,points,pose,color,visible,reset));requestRender();}
    void setSceneCloudVisible(String id,boolean visible){queueEvent(()->renderer.setSceneCloudVisible(id,visible));requestRender();}
    void setSceneCloudTransform(String id,float[] transform){float[] pose=transform.clone();queueEvent(()->renderer.setSceneCloudTransform(id,pose));requestRender();}
    void setActiveSceneTarget(String targetNodeId,String[] scanIds,float[] worldTransform){String[] ids=scanIds.clone();float[] pose=worldTransform==null?null:worldTransform.clone();queueEvent(()->renderer.setActiveSceneTarget(targetNodeId,ids,pose));requestRender();}
    void removeSceneCloud(String id){queueEvent(()->renderer.removeSceneCloud(id));requestRender();}
    void retainSceneClouds(String[] ids){String[] keep=ids.clone();queueEvent(()->renderer.retainSceneClouds(keep));requestRender();}
    void fitView(){queueEvent(renderer::fitVisible);requestRender();}
    void setPointSize(float size){queueEvent(()->renderer.pointSize=Math.max(1f,Math.min(5f,size)));requestRender();}
    void setGridVisible(boolean visible){queueEvent(()->renderer.gridVisible=visible);requestRender();}
    void setClipBounds(float[] lower,float[] upper,boolean enabled){float[] lo=lower.clone(),hi=upper.clone();queueEvent(()->renderer.setClip(lo,hi,enabled));requestRender();}
    void enableDefaultClip(boolean enabled){queueEvent(()->renderer.enableDefaultClip(enabled));requestRender();}
    void setZClipFractions(float lower,float upper){queueEvent(()->renderer.setZFractions(lower,upper));requestRender();}
    void setClipControlsVisible(boolean visible){queueEvent(()->renderer.clipControlsVisible=visible);requestRender();}
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
    void restoreOrientation(float yaw,float pitch){cancelCameraAnimation();applyOrientation(yaw,pitch);}
    void rotateCamera(float deltaYaw,float deltaPitch){cancelCameraAnimation();applyOrientation(uiYaw+deltaYaw,uiPitch+deltaPitch);}
    private void applyOrientation(float yaw,float pitch){uiYaw=ViewCubeMath.normalizeYaw(yaw);uiPitch=ViewCubeMath.clampPitch(pitch);float y=uiYaw,p=uiPitch;queueEvent(()->{renderer.yaw=y;renderer.pitch=p;});requestRender();notifyOrientation();}
    private void notifyOrientation(){if(orientationListener!=null)orientationListener.onOrientation(uiYaw,uiPitch);}
    private void cancelCameraAnimation(){if(cameraAnimator!=null){cameraAnimator.cancel();cameraAnimator=null;}}
    void toggleProjection(){queueEvent(()->renderer.orthographic=!renderer.orthographic);requestRender();}
    void setMeasureMode(boolean enabled,MeasureListener listener){measureMode=enabled;measureListener=listener;queueEvent(renderer::clearMeasure);requestRender();}

    @Override public boolean onTouchEvent(MotionEvent e){
        scale.onTouchEvent(e);
        float x=e.getX(),y=e.getY();
        if(e.getPointerCount()>1){cancelCameraAnimation();float midX=(e.getX(0)+e.getX(1))*.5f,midY=(e.getY(0)+e.getY(1))*.5f;if(twoFingerGesture){float dx=midX-lastMidX,dy=midY-lastMidY;queueEvent(()->renderer.panByPixels(dx,dy));}else{queueEvent(renderer::cancelGizmo);twoFingerGesture=true;}lastMidX=midX;lastMidY=midY;lastX=x;lastY=y;discardNextSinglePointerMove=true;requestRender();return true;}
        if(scale.isInProgress())return true;
        if(twoFingerGesture){twoFingerGesture=false;lastX=x;lastY=y;discardNextSinglePointerMove=true;return true;}
        if(discardNextSinglePointerMove){lastX=x;lastY=y;if(e.getActionMasked()==MotionEvent.ACTION_MOVE)discardNextSinglePointerMove=false;if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)discardNextSinglePointerMove=false;return true;}
        if(measureMode&&e.getActionMasked()==MotionEvent.ACTION_UP){queueEvent(()->{float[] m=renderer.pickPoint(x,y);if(m!=null&&measureListener!=null)post(()->measureListener.onMeasure(m[0],m[1]));});return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){cancelCameraAnimation();lastX=x;lastY=y;queueEvent(()->renderer.beginGizmo(x,y));requestRender();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=x-lastX,dy=y-lastY;lastX=x;lastY=y;queueEvent(()->{TransformUpdate update=renderer.moveGesture(dx,dy,x,y);float cameraYaw=renderer.yaw,cameraPitch=renderer.pitch;post(()->{if(update!=null&&transformListener!=null)transformListener.onTransform(update.targetNodeId,update.worldTransform);uiYaw=ViewCubeMath.normalizeYaw(cameraYaw);uiPitch=ViewCubeMath.clampPitch(cameraPitch);notifyOrientation();});});requestRender();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){queueEvent(()->{if(renderer.endGizmo())post(()->{if(transformCommitListener!=null)transformCommitListener.run();});});requestRender();}
        return true;
    }

    private static final class CloudRenderer implements GLSurfaceView.Renderer {
        private static final String VS="uniform mat4 uMvp;uniform mat4 uWorld;uniform float uSize;attribute vec3 aPosition;varying vec3 vWorld;void main(){vWorld=(uWorld*vec4(aPosition,1.0)).xyz;gl_Position=uMvp*vec4(aPosition,1.0);gl_PointSize=uSize;}";
        private static final String FS="precision mediump float;uniform vec4 uColor;uniform vec3 uClipMin;uniform vec3 uClipMax;uniform float uClip;varying vec3 vWorld;void main(){if(uClip>.5&&(any(lessThan(vWorld,uClipMin))||any(greaterThan(vWorld,uClipMax))))discard;gl_FragColor=uColor;}";
        private final PerformanceListener performanceListener;
        private final float[] projection=new float[16],view=new float[16],commonModel=new float[16],primaryMv=new float[16],primaryMvp=new float[16],inversePrimaryMvp=new float[16];
        private final float[] local=new float[16],gridMvp=new float[16],sceneModel=new float[16],sceneMv=new float[16],sceneMvp=new float[16],sceneWorld=new float[16];
        private final float[] transform=new float[4],projectIn=new float[4],projectOut=new float[4],gizmoPivot=new float[3];
        private final TransformGizmo gizmo=new TransformGizmo();
        private final float[] measure=new float[6];private int measureCount;
        private final FloatBuffer measureBuffer=direct(6),gizmoBuffer=direct(512);
        private FloatBuffer gridBuffer;
        private final Map<String,SceneCloud> sceneClouds=new LinkedHashMap<>();
        private final Set<String> activeSceneIds=new HashSet<>();
        private String activeTargetNodeId;
        private int gridCount,width,height,program,position,matrix,color,size,world,clipMin,clipMax,clip;
        private final float[] clipLower=new float[3],clipUpper=new float[3],identity=new float[16],sceneFrame=new float[6];private boolean clipping,clipControlsVisible=true;
        private float cx,cy,cz,span=1f,panX,panY,panZ;
        private boolean inverseMvpValid,frameLockedForGizmo;
        private long fpsStarted; private int fpsFrames;
        boolean orthographic,gridVisible=true;
        float yaw=25f,pitch=-18f,zoom=1f,pointSize=2f;

        CloudRenderer(PerformanceListener performanceListener){this.performanceListener=performanceListener;}

        static FloatBuffer direct(int floats){return ByteBuffer.allocateDirect(floats*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
        void setSceneCloud(String id,float[] xyz,float[] pose,int argb,boolean visible){appendSceneCloud(id,xyz,pose,argb,visible,true);}
        void setClip(float[] lower,float[] upper,boolean enabled){System.arraycopy(lower,0,clipLower,0,3);System.arraycopy(upper,0,clipUpper,0,3);clipping=enabled;}
        void enableDefaultClip(boolean enabled){if(enabled){for(int i=0;i<3;i++){float center=(sceneFrame[i]+sceneFrame[i+3])*.5f,half=(sceneFrame[i+3]-sceneFrame[i])*.35f;clipLower[i]=center-half;clipUpper[i]=center+half;}clipControlsVisible=true;}clipping=enabled;}
        void setZFractions(float lower,float upper){float min=sceneFrame[2],range=Math.max(.001f,sceneFrame[5]-min);clipLower[2]=min+range*Math.max(0,Math.min(1,lower));clipUpper[2]=min+range*Math.max(0,Math.min(1,upper));clipping=true;}
        void appendSceneCloud(String id,float[] xyz,float[] pose,int argb,boolean visible,boolean reset){SceneCloud c=sceneClouds.get(id);if(c==null){c=new SceneCloud();sceneClouds.put(id,c);}if(reset){c.chunks.clear();c.localBounds=null;}FloatBuffer buffer=direct(xyz.length);buffer.put(xyz).position(0);c.chunks.add(buffer);c.localBounds=SceneBounds.merge(c.localBounds,SceneBounds.of(xyz));System.arraycopy(pose,0,c.pose,0,Math.min(4,pose.length));c.r=((argb>>16)&255)/255f;c.g=((argb>>8)&255)/255f;c.b=(argb&255)/255f;c.visible=visible;updateSceneFrame();}
        void setSceneCloudVisible(String id,boolean visible){SceneCloud c=sceneClouds.get(id);if(c!=null){c.visible=visible;updateSceneFrame();}}
        void setSceneCloudTransform(String id,float[] pose){SceneCloud c=sceneClouds.get(id);if(c!=null){System.arraycopy(pose,0,c.pose,0,Math.min(4,pose.length));if(frameLockedForGizmo)updateActivePivot();else updateSceneFrame();}}
        void setActiveSceneTarget(String targetNodeId,String[] ids,float[] pose){activeTargetNodeId=targetNodeId;activeSceneIds.clear();java.util.Collections.addAll(activeSceneIds,ids);if(targetNodeId==null||pose==null){activeTargetNodeId=null;activeSceneIds.clear();java.util.Arrays.fill(transform,0);gizmo.endDrag();}else System.arraycopy(pose,0,transform,0,Math.min(4,pose.length));updateActivePivot();}
        void removeSceneCloud(String id){sceneClouds.remove(id);updateSceneFrame();}
        void retainSceneClouds(String[] ids){Set<String> keep=new HashSet<>();java.util.Collections.addAll(keep,ids);sceneClouds.keySet().retainAll(keep);activeSceneIds.retainAll(keep);updateSceneFrame();}
        static float[] bounds(float[] a){return SceneBounds.of(a);}
        void updateSceneFrame(){float[] frame=null;for(SceneCloud c:sceneClouds.values())if(c.visible&&c.localBounds!=null)frame=SceneBounds.merge(frame,SceneBounds.transformed(c.localBounds,c.pose));if(frame==null)return;System.arraycopy(frame,0,sceneFrame,0,6);cx=(frame[0]+frame[3])*.5f;cy=(frame[1]+frame[4])*.5f;cz=(frame[2]+frame[5])*.5f;span=Math.max(1f,Math.max(frame[3]-frame[0],Math.max(frame[4]-frame[1],frame[5]-frame[2])));buildGrid();updateActivePivot();}
        void fitVisible(){updateSceneFrame();zoom=1f;panX=panY=panZ=0f;}
        void updateActivePivot(){if(activeSceneIds.isEmpty())return;gizmoPivot[0]=transform[0];gizmoPivot[1]=transform[1];gizmoPivot[2]=transform[2];}
        void buildGrid(){float raw=span/10f,pow=(float)Math.pow(10,Math.floor(Math.log10(raw))),n=raw/pow,step=(n<2?1:n<5?2:5)*pow;int half=10;gridCount=(half*2+1)*4+6;gridBuffer=direct(gridCount*3);float extent=step*half;for(int i=-half;i<=half;i++){float p=i*step;put(gridBuffer,cx-extent,p+cy,0,cx+extent,p+cy,0);put(gridBuffer,p+cx,cy-extent,0,p+cx,cy+extent,0);}put(gridBuffer,cx-extent,0,0,cx+extent,0,0);put(gridBuffer,0,cy-extent,0,0,cy+extent,0);put(gridBuffer,0,0,-extent*.25f,0,0,extent*.25f);gridBuffer.position(0);}
        static void put(FloatBuffer b,float...v){b.put(v);}

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig cfg){GLES20.glClearColor(.015f,.025f,.04f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);program=link(VS,FS);position=GLES20.glGetAttribLocation(program,"aPosition");matrix=GLES20.glGetUniformLocation(program,"uMvp");world=GLES20.glGetUniformLocation(program,"uWorld");color=GLES20.glGetUniformLocation(program,"uColor");size=GLES20.glGetUniformLocation(program,"uSize");clipMin=GLES20.glGetUniformLocation(program,"uClipMin");clipMax=GLES20.glGetUniformLocation(program,"uClipMax");clip=GLES20.glGetUniformLocation(program,"uClip");Matrix.setIdentityM(identity,0);}
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);if(sceneClouds.isEmpty())return;setMatrices();GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(position);GLES20.glUniform1f(size,pointSize);GLES20.glUniform3fv(clipMin,1,clipLower,0);GLES20.glUniform3fv(clipMax,1,clipUpper,0);GLES20.glUniform1f(clip,clipping?1:0);
            if(gridVisible&&gridBuffer!=null){draw(gridBuffer,gridCount,GLES20.GL_LINES,gridMvp,.20f,.28f,.34f,1);drawAxes();}
            for(SceneCloud c:sceneClouds.values())if(c.visible)for(FloatBuffer chunk:c.chunks)drawScene(chunk,chunk.capacity()/3,c);
            if(clipping&&clipControlsVisible){GLES20.glDisable(GLES20.GL_DEPTH_TEST);drawClipBox();GLES20.glEnable(GLES20.GL_DEPTH_TEST);}
            if(activeTargetNodeId!=null&&!activeSceneIds.isEmpty()){GLES20.glDisable(GLES20.GL_DEPTH_TEST);drawGizmo();GLES20.glEnable(GLES20.GL_DEPTH_TEST);}
            if(measureCount==2){measureBuffer.position(0);measureBuffer.put(measure).position(0);GLES20.glLineWidth(3);draw(measureBuffer,2,GLES20.GL_LINES,primaryMvp,1,1,1,1);}
            long now=System.nanoTime();if(fpsStarted==0)fpsStarted=now;if(++fpsFrames>=30){float fps=fpsFrames*1_000_000_000f/Math.max(1,now-fpsStarted);fpsFrames=0;fpsStarted=now;if(performanceListener!=null)performanceListener.onFrameRate(fps);}
            GLES20.glDisableVertexAttribArray(position);
        }
        void setMatrices(){float aspect=(float)width/Math.max(1,height);if(orthographic)Matrix.orthoM(projection,0,-2*aspect/zoom,2*aspect/zoom,-2/zoom,2/zoom,.1f,20);else Matrix.perspectiveM(projection,0,45,aspect,.1f,20);float yr=(float)Math.toRadians(yaw),pr=(float)Math.toRadians(pitch),d=orthographic?4f:3.5f/zoom;float ex=(float)(d*Math.cos(pr)*Math.sin(yr)),ey=(float)(-d*Math.cos(pr)*Math.cos(yr)),ez=(float)(-d*Math.sin(pr));Matrix.setLookAtM(view,0,ex+panX,ey+panY,ez+panZ,panX,panY,panZ,0,0,1);Matrix.setIdentityM(commonModel,0);Matrix.scaleM(commonModel,0,2/span,2/span,2/span);Matrix.translateM(commonModel,0,-cx,-cy,-cz);Matrix.multiplyMM(primaryMv,0,view,0,commonModel,0);Matrix.multiplyMM(primaryMvp,0,projection,0,primaryMv,0);inverseMvpValid=Matrix.invertM(inversePrimaryMvp,0,primaryMvp,0);System.arraycopy(primaryMvp,0,gridMvp,0,16);updateGizmoPivot();if(inverseMvpValid)gizmo.updateScale(gizmoPivot,primaryMvp,inversePrimaryMvp,width,height);}
        void draw(FloatBuffer b,int count,int mode,float[] m,float r,float g,float bl,float a){b.position(0);GLES20.glUniform1f(clip,0);GLES20.glUniformMatrix4fv(matrix,1,false,m,0);GLES20.glUniformMatrix4fv(world,1,false,identity,0);GLES20.glUniform4f(color,r,g,bl,a);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,12,b);GLES20.glDrawArrays(mode,0,count);}
        void drawScene(FloatBuffer b,int count,SceneCloud c){prepareSceneMatrices(c);b.position(0);GLES20.glUniform1f(clip,clipping?1:0);GLES20.glUniformMatrix4fv(matrix,1,false,sceneMvp,0);GLES20.glUniformMatrix4fv(world,1,false,sceneWorld,0);GLES20.glUniform4f(color,c.r,c.g,c.b,1);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,12,b);GLES20.glDrawArrays(GLES20.GL_POINTS,0,count);}
        void prepareSceneMatrices(SceneCloud c){Matrix.setIdentityM(local,0);Matrix.translateM(local,0,c.pose[0],c.pose[1],c.pose[2]);Matrix.rotateM(local,0,c.pose[3],0,0,1);Matrix.multiplyMM(sceneModel,0,commonModel,0,local,0);Matrix.multiplyMM(sceneMv,0,view,0,sceneModel,0);Matrix.multiplyMM(sceneMvp,0,projection,0,sceneMv,0);Matrix.setIdentityM(sceneWorld,0);Matrix.translateM(sceneWorld,0,c.pose[0],c.pose[1],c.pose[2]);Matrix.rotateM(sceneWorld,0,c.pose[3],0,0,1);}
        void drawAxes(){float e=span*.7f;FloatBuffer b=gizmoBuffer;b.position(0);put(b,0,0,0,e,0,0);b.position(0);draw(b,2,GLES20.GL_LINES,gridMvp,1,.18f,.18f,1);b.position(0);put(b,0,0,0,0,e,0);b.position(0);draw(b,2,GLES20.GL_LINES,gridMvp,.18f,1,.3f,1);b.position(0);put(b,0,0,0,0,0,e);b.position(0);draw(b,2,GLES20.GL_LINES,gridMvp,.2f,.45f,1,1);}
        void drawGizmo(){float s=gizmo.worldScale(),x=gizmoPivot[0],y=gizmoPivot[1],z=gizmoPivot[2];FloatBuffer b=gizmoBuffer;GLES20.glLineWidth(5);drawGizmoLine(b,x,y,z,x+s,y,z,TransformGizmo.X,1,.15f,.15f);drawGizmoLine(b,x,y,z,x,y+s,z,TransformGizmo.Y,.15f,1,.3f);drawGizmoLine(b,x,y,z,x,y,z+s,TransformGizmo.Z,.2f,.5f,1);b.position(0);float c=s*.12f;put(b,x-c,y-c,z,x+c,y+c,z,x-c,y+c,z,x+c,y-c,z);b.position(0);float xy=gizmo.activeHandle()==TransformGizmo.XY?1f:.72f;draw(b,4,GLES20.GL_LINES,primaryMvp,xy,xy,xy,1);b.position(0);int seg=48;for(int i=0;i<seg;i++){double a=i*Math.PI*2/seg,n=(i+1)*Math.PI*2/seg;put(b,x+(float)Math.cos(a)*s*.75f,y+(float)Math.sin(a)*s*.75f,z,x+(float)Math.cos(n)*s*.75f,y+(float)Math.sin(n)*s*.75f,z);}b.position(0);float hi=gizmo.activeHandle()==TransformGizmo.RZ?1f:.75f;draw(b,seg*2,GLES20.GL_LINES,primaryMvp,1,hi,.15f,1);}
        void drawClipBox(){float x0=clipLower[0],y0=clipLower[1],z0=clipLower[2],x1=clipUpper[0],y1=clipUpper[1],z1=clipUpper[2],mx=(x0+x1)*.5f,my=(y0+y1)*.5f,mz=(z0+z1)*.5f,h=span*.08f;FloatBuffer b=gizmoBuffer;b.position(0);put(b,x0,y0,z0,x1,y0,z0,x1,y0,z0,x1,y1,z0,x1,y1,z0,x0,y1,z0,x0,y1,z0,x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z1,x1,y1,z1,x1,y1,z1,x0,y1,z1,x0,y1,z1,x0,y0,z1,x0,y0,z0,x0,y0,z1,x1,y0,z0,x1,y0,z1,x1,y1,z0,x1,y1,z1,x0,y1,z0,x0,y1,z1,x0,my,mz,x0-h,my,mz,x1,my,mz,x1+h,my,mz,mx,y0,mz,mx,y0-h,mz,mx,y1,mz,mx,y1+h,mz,mx,my,z0,mx,my,z0-h,mx,my,z1,mx,my,z1+h);b.position(0);GLES20.glLineWidth(3);draw(b,36,GLES20.GL_LINES,primaryMvp,1,.78f,.12f,1);}
        void drawGizmoLine(FloatBuffer b,float x1,float y1,float z1,float x2,float y2,float z2,int handle,float r,float g,float bl){b.position(0);put(b,x1,y1,z1,x2,y2,z2);b.position(0);float boost=gizmo.activeHandle()==handle?1f:.78f;draw(b,2,GLES20.GL_LINES,primaryMvp,Math.min(1,r*boost+.2f*(1-boost)),Math.min(1,g*boost+.2f*(1-boost)),Math.min(1,bl*boost+.2f*(1-boost)),1);}
        void updateGizmoPivot(){if(!activeSceneIds.isEmpty())updateActivePivot();}
        void beginGizmo(float x,float y){if(activeTargetNodeId==null||activeSceneIds.isEmpty()||!inverseMvpValid){gizmo.endDrag();frameLockedForGizmo=false;return;}updateGizmoPivot();frameLockedForGizmo=gizmo.beginDrag(x,y,transform,gizmoPivot,primaryMvp,inversePrimaryMvp,width,height);}
        TransformUpdate moveGesture(float dx,float dy,float x,float y){if(gizmo.activeHandle()!=TransformGizmo.NONE){float[] next=gizmo.updateDrag(x,y,inversePrimaryMvp,width,height);if(next!=null&&activeTargetNodeId!=null){System.arraycopy(next,0,transform,0,Math.min(4,next.length));return new TransformUpdate(activeTargetNodeId,transform.clone());}return null;}yaw=ViewCubeMath.normalizeYaw(yaw+dx*.35f);pitch=ViewCubeMath.clampPitch(pitch+dy*.35f);return null;}
        boolean endGizmo(){boolean committed=frameLockedForGizmo;gizmo.endDrag();if(frameLockedForGizmo){frameLockedForGizmo=false;updateSceneFrame();}return committed;}
        void cancelGizmo(){gizmo.endDrag();frameLockedForGizmo=false;}
        void resetPerformanceSample(){fpsStarted=0;fpsFrames=0;}
        void panByPixels(float dx,float dy){if(width<=0||height<=0)return;float yr=(float)Math.toRadians(yaw),pr=(float)Math.toRadians(pitch),d=orthographic?4f:3.5f/zoom;float unitsPerPixel=(orthographic?4f/zoom:2f*d*(float)Math.tan(Math.toRadians(22.5)))/height;float fx=-(float)(Math.cos(pr)*Math.sin(yr)),fy=(float)(Math.cos(pr)*Math.cos(yr)),fz=(float)Math.sin(pr);float rx=fy,ry=-fx,rl=(float)Math.hypot(rx,ry);if(rl<1e-5f)return;rx/=rl;ry/=rl;float ux=-ry*fz,uy=rx*fz,uz=rx*fy-ry*fx;panX+=-rx*dx*unitsPerPixel+ux*dy*unitsPerPixel;panY+=-ry*dx*unitsPerPixel+uy*dy*unitsPerPixel;panZ+=uz*dy*unitsPerPixel;}
        void clearMeasure(){measureCount=0;}
        float[] pickPoint(float sx,float sy){float bd=2500;float[] best=null;for(SceneCloud scene:sceneClouds.values())if(scene.visible){double radians=Math.toRadians(scene.pose[3]),cos=Math.cos(radians),sin=Math.sin(radians);for(FloatBuffer chunk:scene.chunks)for(int i=0;i<chunk.capacity();i+=3){float lx=chunk.get(i),ly=chunk.get(i+1),lz=chunk.get(i+2);float wx=(float)(cos*lx-sin*ly)+scene.pose[0],wy=(float)(sin*lx+cos*ly)+scene.pose[1],wz=lz+scene.pose[2];projectIn[0]=wx;projectIn[1]=wy;projectIn[2]=wz;projectIn[3]=1;Matrix.multiplyMV(projectOut,0,primaryMvp,0,projectIn,0);if(projectOut[3]<=0)continue;float x=(projectOut[0]/projectOut[3]*.5f+.5f)*width,y=(1-(projectOut[1]/projectOut[3]*.5f+.5f))*height,d=(x-sx)*(x-sx)+(y-sy)*(y-sy);if(d<bd){bd=d;best=new float[]{wx,wy,wz};}}}if(best==null)return null;if(measureCount>=2)measureCount=0;System.arraycopy(best,0,measure,measureCount*3,3);measureCount++;if(measureCount<2)return null;float dx=measure[3]-measure[0],dy=measure[4]-measure[1],dz=measure[5]-measure[2];return new float[]{(float)Math.sqrt(dx*dx+dy*dy+dz*dz),Math.abs(dz)};}
        static int link(String v,String f){int vs=compile(GLES20.GL_VERTEX_SHADER,v),fs=compile(GLES20.GL_FRAGMENT_SHADER,f),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));return p;}
        static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
        private static final class SceneCloud{final java.util.List<FloatBuffer> chunks=new java.util.ArrayList<>();float[] localBounds;boolean visible=true;float r=.2f,g=.8f,b=1f;final float[] pose=new float[4];}
    }

    private static final class TransformUpdate{
        final String targetNodeId;final float[] worldTransform;
        TransformUpdate(String targetNodeId,float[] worldTransform){this.targetNodeId=targetNodeId;this.worldTransform=worldTransform;}
    }
}
