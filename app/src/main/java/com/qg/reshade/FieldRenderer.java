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
        
        // 嘉嘉的 4K 野心：强行把缓冲区拉到 4K 分辨率！
        // 手机屏幕可能只有 1080P 或 2K，但 GPU 会渲染 4K 然后向下采样（SSAA）
        // 这会极大加重 GPU 负担，但视觉效果会极其锐利！如果手机发热严重，可以改回 1080, 2400
        getHolder().setFixedSize(3840, 2160); 
        
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    private final String vCode = 
        "attribute vec4 vPosition; void main() { gl_Position = vPosition; }";

    // 亿万量纲、超位态叠加、4K 核心 Shader
    private final String fCode =
        "#version 300 es\n" +
        "precision highp float;\n" + // 必须用 highp，不然亿万量纲会直接溢出变成 NaN！
        "out vec4 fragColor;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "void main() {\n" +
        "  vec2 uv = gl_FragCoord.xy / u_resolution;\n" +
        "  vec2 center = uv - vec2(0.5);\n" +
        "  float dist = length(center);\n" +
        "  \n" +
        // 1. 构造亿万量纲的度规空间 (Metric Space)
        "  float metric = 1e8 * dist * dist; \n" +
        "  float time_scale = u_time * 1e4;\n" +
        "  \n" +
        // 2. 超位态叠加 (Super Position)
        "  float super_pos = sin(uv.x * metric + time_scale) * cos(uv.y * metric + time_scale);\n" +
        "  float anti_pos = cos(uv.x * metric - time_scale) * sin(uv.y * metric - time_scale);\n" +
        "  float interference = abs(super_pos + anti_pos);\n" +
        "  \n" +
        // 3. 制造极致的 HDR 泛光与亿万高光
        "  float bloom = 1.0 / (1.0 + dist * dist * 1000.0);\n" +
        "  vec3 color1 = vec3(0.8, 0.2, 1.0); // 霓虹紫\n" +
        "  vec3 color2 = vec3(0.2, 0.9, 1.0); // 赛博蓝\n" +
        "  vec3 hdrColor = mix(color1, color2, interference) * (1e6 * bloom);\n" +
        "  \n" +
        // 4. 色差与扫描线（故障艺术）
        "  float chroma = dist * 0.1 * sin(u_time * 5.0);\n" +
        "  hdrColor += vec3(chroma, 0.0, -chroma);\n" +
        "  \n" +
        // 5. ACES 电影级映射 (把亿万量纲压缩回 0-1 的可见光范围)
        "  vec3 finalColor = hdrColor / (hdrColor + vec3(1.0));\n" +
        "  finalColor = pow(finalColor, vec3(1.0 / 2.2));\n" +
        "  \n" +
        // 保持透明度叠加
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
        // 使用 4K 设定的视口
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
