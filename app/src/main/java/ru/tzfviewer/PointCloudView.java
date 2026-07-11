package ru.tzfviewer;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class PointCloudView extends GLSurfaceView {
    interface MeasureListener { void onMeasure(float length, float deltaZ); }
    private final CloudRenderer renderer;
    private final ScaleGestureDetector scale;
    private float lastX;
    private float lastY;
    private boolean measureMode;
    private MeasureListener measureListener;

    PointCloudView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new CloudRenderer();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                renderer.zoom = Math.max(0.35f, Math.min(4.5f,
                        renderer.zoom * detector.getScaleFactor()));
                requestRender();
                return true;
            }
        });
    }

    void setCloud(float[] xyz) {
        queueEvent(() -> renderer.setCloud(xyz));
        requestRender();
    }
    void setSecondaryCloud(float[] xyz) { queueEvent(() -> renderer.setSecondaryCloud(xyz)); requestRender(); }
    void moveSecondary(float x,float y,float z) { queueEvent(() -> { renderer.secondaryX+=x; renderer.secondaryY+=y; renderer.secondaryZ+=z; }); requestRender(); }
    void rotateSecondary(float x,float y,float z) { queueEvent(() -> { renderer.secondaryPitch+=x; renderer.secondaryYaw+=y; renderer.secondaryRoll+=z; }); requestRender(); }
    void setMeasureMode(boolean enabled, MeasureListener listener) {
        measureMode = enabled; measureListener = listener;
        queueEvent(() -> renderer.clearMeasure());
    }
    void setPreset(float yaw, float pitch) { queueEvent(() -> { renderer.yaw=yaw; renderer.pitch=pitch; }); requestRender(); }
    void toggleProjection() { queueEvent(() -> renderer.orthographic=!renderer.orthographic); requestRender(); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scale.onTouchEvent(event);
        if (measureMode && event.getActionMasked() == MotionEvent.ACTION_UP) {
            final float x=event.getX(), y=event.getY();
            queueEvent(() -> { float[] m=renderer.pick(x,y); if(m!=null && measureListener!=null) post(() -> measureListener.onMeasure(m[0],m[1])); });
            return true;
        }
        if (event.getPointerCount() == 1) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                renderer.yaw += (event.getX() - lastX) * 0.45f;
                renderer.pitch = Math.max(-89f, Math.min(89f,
                        renderer.pitch + (event.getY() - lastY) * 0.45f));
                lastX = event.getX();
                lastY = event.getY();
                requestRender();
            }
        }
        return true;
    }

    private static final class CloudRenderer implements GLSurfaceView.Renderer {
        private static final String VERTEX =
                "uniform mat4 uMvp; attribute vec3 aPosition;" +
                "void main(){ gl_Position=uMvp*vec4(aPosition,1.0); gl_PointSize=2.2; }";
        private static final String FRAGMENT =
                "precision mediump float; void main(){ gl_FragColor=vec4(0.10,0.78,1.0,1.0); }";
        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] modelView = new float[16];
        private final float[] secondaryMvp = new float[16];
        private final float[] secondaryModelView = new float[16];
        private final float[] mvp = new float[16];
        private int program;
        private int position;
        private int matrix;
        private FloatBuffer points;
        private FloatBuffer secondaryPoints;
        private int secondaryCount;
        private float[] cloud;
        private int surfaceWidth, surfaceHeight;
        private final float[] measure = new float[6];
        private final float[] pickInput = new float[4];
        private final float[] pickOutput = new float[4];
        private int measureCount;
        boolean orthographic;
        float secondaryX, secondaryY, secondaryZ;
        float secondaryPitch, secondaryYaw, secondaryRoll;
        private int pointCount;
        private float cx, cy, cz, span = 1f;
        float yaw = 25f, pitch = -18f, zoom = 1f;

        void setCloud(float[] xyz) {
            pointCount = xyz.length / 3;
            cloud = xyz;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < xyz.length; i += 3) {
                minX = Math.min(minX, xyz[i]); maxX = Math.max(maxX, xyz[i]);
                minY = Math.min(minY, xyz[i + 1]); maxY = Math.max(maxY, xyz[i + 1]);
                minZ = Math.min(minZ, xyz[i + 2]); maxZ = Math.max(maxZ, xyz[i + 2]);
            }
            cx = (minX + maxX) * .5f; cy = (minY + maxY) * .5f; cz = (minZ + maxZ) * .5f;
            span = Math.max(1f, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
            points = ByteBuffer.allocateDirect(xyz.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            points.put(xyz).position(0);
        }
        void setSecondaryCloud(float[] xyz) { secondaryCount=xyz.length/3; secondaryPoints=ByteBuffer.allocateDirect(xyz.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer(); secondaryPoints.put(xyz).position(0); }

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                               javax.microedition.khronos.egl.EGLConfig config) {
            GLES20.glClearColor(.025f, .04f, .07f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            program = link(VERTEX, FRAGMENT);
            position = GLES20.glGetAttribLocation(program, "aPosition");
            matrix = GLES20.glGetUniformLocation(program, "uMvp");
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            surfaceWidth=width; surfaceHeight=height;
            setProjection(width,height);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (points == null || pointCount == 0) return;
            setProjection(surfaceWidth, surfaceHeight);
            Matrix.setIdentityM(model, 0);
            Matrix.scaleM(model, 0, 2f * zoom / span, 2f * zoom / span, 2f * zoom / span);
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f);
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f);
            Matrix.translateM(model, 0, -cx, -cy, -cz);
            Matrix.setLookAtM(view, 0, 0f, 0f, 3.5f, 0f, 0f, 0f,
                    0f, 1f, 0f);
            Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0);
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, points);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCount);
            if(secondaryPoints!=null){ System.arraycopy(modelView,0,secondaryModelView,0,16); Matrix.translateM(secondaryModelView,0,secondaryX,secondaryY,secondaryZ); Matrix.rotateM(secondaryModelView,0,secondaryPitch,1,0,0); Matrix.rotateM(secondaryModelView,0,secondaryYaw,0,1,0); Matrix.rotateM(secondaryModelView,0,secondaryRoll,0,0,1); Matrix.multiplyMM(secondaryMvp,0,projection,0,secondaryModelView,0); GLES20.glUniformMatrix4fv(matrix,1,false,secondaryMvp,0); GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,12,secondaryPoints); GLES20.glDrawArrays(GLES20.GL_POINTS,0,secondaryCount); GLES20.glUniformMatrix4fv(matrix,1,false,mvp,0); }
            if(measureCount==2){ FloatBuffer b=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer(); b.put(measure).position(0); GLES20.glLineWidth(3f); GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,12,b); GLES20.glDrawArrays(GLES20.GL_LINES,0,2); }
            GLES20.glDisableVertexAttribArray(position);
        }
        void clearMeasure(){measureCount=0;}
        float[] pick(float sx,float sy){ if(cloud==null) return null; int best=-1; float bestD=2500f; for(int i=0;i<cloud.length;i+=3){ pickInput[0]=cloud[i];pickInput[1]=cloud[i+1];pickInput[2]=cloud[i+2];pickInput[3]=1f; Matrix.multiplyMV(pickOutput,0,mvp,0,pickInput,0); if(pickOutput[3]<=0)continue; float px=(pickOutput[0]/pickOutput[3]*.5f+.5f)*surfaceWidth, py=(1-(pickOutput[1]/pickOutput[3]*.5f+.5f))*surfaceHeight; float d=(px-sx)*(px-sx)+(py-sy)*(py-sy); if(d<bestD){bestD=d;best=i;} } if(best<0)return null; System.arraycopy(cloud,best,measure,measureCount*3,3); measureCount++; if(measureCount<2)return null; float dx=measure[3]-measure[0],dy=measure[4]-measure[1],dz=measure[5]-measure[2]; return new float[]{(float)Math.sqrt(dx*dx+dy*dy+dz*dz),Math.abs(dz)}; }
        private void setProjection(int w,int h){float a=(float)w/Math.max(1,h); if(orthographic) Matrix.orthoM(projection,0,-2*a,2*a,-2,2,.1f,20f); else Matrix.perspectiveM(projection,0,45f,a,.1f,20f);}

        private static int link(String vertex, String fragment) {
            int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);
            return program;
        }

        private static int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
            return shader;
        }
    }
}
