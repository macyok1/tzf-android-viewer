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
    private final CloudRenderer renderer;
    private final ScaleGestureDetector scale;
    private float lastX;
    private float lastY;

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

    @Override public boolean onTouchEvent(MotionEvent event) {
        scale.onTouchEvent(event);
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
        private final float[] mvp = new float[16];
        private int program;
        private int position;
        private int matrix;
        private FloatBuffer points;
        private int pointCount;
        private float cx, cy, cz, span = 1f;
        float yaw = 25f, pitch = -18f, zoom = 1f;

        void setCloud(float[] xyz) {
            pointCount = xyz.length / 3;
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

        @Override public void onSurfaceCreated(javax.microedition.khronos.egl.EGLConfig config) {
            GLES20.glClearColor(.025f, .04f, .07f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            program = link(VERTEX, FRAGMENT);
            position = GLES20.glGetAttribLocation(program, "aPosition");
            matrix = GLES20.glGetUniformLocation(program, "uMvp");
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            Matrix.perspectiveM(projection, 0, 45f, (float) width / Math.max(1, height), .1f, 20f);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (points == null || pointCount == 0) return;
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
            GLES20.glDisableVertexAttribArray(position);
        }

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
