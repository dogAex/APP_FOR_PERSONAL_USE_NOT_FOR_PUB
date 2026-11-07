package com.qg.reshade;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class FieldRenderer extends GLSurfaceView implements GLSurfaceView.Renderer {
    private int program;
    private FloatBuffer vertexBuffer;
    private long startTime;
    private String probeInfo;

    public FieldRenderer(Context context, String probeInfo) {
        super(context);
        this.probeInfo = probeInfo;
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    private final String vCode = "attribute vec4 vPosition; void main() { gl_Position = vPosition; }";

    private final String fCode =
        "#version 300 es\n" +
        "precision highp float;\n" +
        "out vec4 fragColor;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "void main() {\n" +
        "  vec2 uv = gl_FragCoord.xy / u_resolution;\n" +
        "  vec2 center = uv - vec2(0.5);\n" +
        "  float dist = length(center);\n" +
        "  vec3 baseColor = vec3(0.1, 0.2, 0.4); \n" +
        "  float scanline = sin(uv.y * 800.0 + u_time * 10.0) * 0.04;\n" +
        "  baseColor += scanline;\n" +
        "  float boson = sin(uv.x * 30.0 + u_time) * cos(uv.y * 30.0 + u_time);\n" +
        "  float fermion = sin(uv.x * 30.0 + u_time + 1.57) * cos(uv.y * 30.0 + u_time + 1.57);\n" +
        "  float interference = abs(boson - fermion);\n" +
        "  float bloom = 1.0 / (1.0 + dist * dist * 30.0);\n" +
        "  vec3 bloomColor = vec3(0.2, 0.8, 1.0) * bloom * 1.5;\n" +
        "  float chroma = dist * 0.05 * sin(u_time * 2.0);\n" +
        "  vec3 chromaColor = vec3(chroma, 0.0, -chroma);\n" +
        "  vec3 finalColor = baseColor + bloomColor + vec3(interference * 0.1) + chromaColor;\n" +
        "  finalColor = finalColor / (finalColor + vec3(1.0)); \n" +
        "  finalColor = pow(finalColor, vec3(1.0 / 2.2)); \n" +
        "  fragColor = vec4(finalColor, 0.8);\n" +
        "}";

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int vShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER);
        GLES30.glShaderSource(vShader, vCode);
        GLES30.glCompileShader(vShader);

        int fShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER);
        GLES30.glShaderSource(fShader, fCode);
        GLES30.glCompileShader(fShader);

        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vShader);
        GLES30.glAttachShader(program, fShader);
        GLES30.glLinkProgram(program);

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        float[] quad = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
        vertexBuffer = ByteBuffer.allocateDirect(quad.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(quad).position(0);
        startTime = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(program);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_time"), (System.currentTimeMillis() - startTime) / 1000.0f);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "u_resolution"), getWidth(), getHeight());
        int pos = GLES30.glGetAttribLocation(program, "vPosition");
        GLES30.glEnableVertexAttribArray(pos);
        GLES30.glVertexAttribPointer(pos, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(pos);
    }
}