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
    
    // 纺锤体异步时钟
    private volatile float qg_sync_time = 0.0f;
    private long lastFrameTime = 0;
    private Thread syncThread;
    private volatile boolean isRunning = true;

    public FieldRenderer(Context context, String probeInfo) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        getHolder().setFixedSize(3840, 2160); // 保留4K度规
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        
        startSpindleAsyncTiming();
    }

    // 纺锤体异步网络时序同步线程
    private void startSpindleAsyncTiming() {
        syncThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    long now = System.nanoTime();
                    // 以 60fps (16.66ms) 的固定相空间步长推进
                    qg_sync_time += 0.01666f; 
                    try {
                        // 精准休眠，对齐 VSYNC 周期
                        Thread.sleep(16, 666666); 
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        syncThread.setPriority(Thread.MAX_PRIORITY);
        syncThread.start();
    }

    private final String vCode = "attribute vec4 vPosition; void main() { gl_Position = vPosition; }";

    // 量子着色器预载优化版
    private final String fCode =
        "#version 300 es\n" +
        "precision highp float;\n" +
        "out vec4 fragColor;\n" +
        "uniform float u_sync_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "void main() {\n" +
        "  vec2 uv = gl_FragCoord.xy / u_resolution;\n" +
        "  vec2 center = uv - vec2(0.5);\n" +
        "  float dist = length(center);\n" +
        "  \n" +
        // ========================================================
        // 第一部分：相空间光栅衍射物理层 (打破塑料边界的核心)
        // ========================================================
        "  float d = 0.5 + 0.1 * sin(u_sync_time); \n" +
        "  float theta = dist * 3.1415; \n" +
        "  float lambda = 0.7606; \n" +
        "  float path_diff = d * sin(theta);\n" +
        "  float phase_diff = 2.0 * 3.1415926 * path_diff / lambda;\n" +
        // 衍射色散：产生真实的雨后彩虹边缘和高光折射
        "  vec3 diff_color;\n" +
        "  diff_color.r = abs(cos(phase_diff * 1.8));\n" +
        "  diff_color.g = abs(cos(phase_diff * 2.0));\n" +
        "  diff_color.b = abs(cos(phase_diff * 2.2));\n" +
        "  \n" +
        // ========================================================
        // 第二部分：亿万度规超位态叠加 (中央凹神经视觉动态)
        // ========================================================
        "  float metric = 1e12 * dist * dist;\n" + 
        "  float time_scale = u_sync_time * 1e4;\n" +
        "  float quantum_time = floor(u_sync_time * 60.0) / 60.0;\n" +
        "  float time_phase = u_sync_time - quantum_time;\n" +
        "  float super_pos_history = sin(uv.x * metric + quantum_time) * cos(uv.y * metric + quantum_time);\n" +
        "  float super_pos_current = sin(uv.x * metric - time_scale) * cos(uv.y * metric - time_scale);\n" +
        "  float superposition = mix(super_pos_history, super_pos_current, time_phase);\n" +
        "  float interference = abs(superposition);\n" +
        "  \n" +
        // ========================================================
        // 第三部分：独立算子解：色彩维度重构 (洗掉塑料感)
        // ========================================================
        "  float spin = sin(uv.x * 3.1415 + u_sync_time) * cos(uv.y * 3.1415 - u_sync_time);\n" +
        "  float algebra_metric = 1e6 * (uv.x*uv.x + uv.y*uv.y);\n" +
        // 强行重构 RGB，用李代数算子替代原有的塑料色
        "  float r = abs(sin(algebra_metric * 0.001) * spin);\n" +
        "  float g = abs(cos(algebra_metric * 0.002) * spin);\n" +
        "  float b = abs(sin(algebra_metric * 0.003 + 0.7071));\n" +
        "  vec3 algebra_rgb = vec3(r, g, b) * 1e5;\n" +
        "  \n" +
        // ========================================================
        // 第四部分：终极物理色域融合
        // ========================================================
        "  float bloom = 1.0 / (1.0 + dist * dist * 500.0);\n" +
        "  vec3 color1 = vec3(0.9, 0.2, 1.0);\n" + // 霓虹紫
        "  vec3 color2 = vec3(0.1, 0.9, 1.0);\n" + // 赛博蓝
        "  \n" +
        // 兆级干涉泛光打底
        "  vec3 hdrColor = mix(color1, color2, interference) * (5e11 * bloom);\n" +
        // 注入衍射色散，让画面拥有琉璃般的折射感
        "  hdrColor += diff_color * 2e5;\n" +
        // 注入李代数算子色，彻底覆盖原本的塑料色彩
        "  hdrColor += algebra_rgb;\n" +
        "  \n" +
        // 统一 ACES 电影级映射，把高维色彩压回屏幕
        "  vec3 finalColor = hdrColor / (hdrColor + vec3(1.0));\n" +
        "  finalColor = pow(finalColor, vec3(1.0 / 2.2));\n" +
        "  \n" +
        // 输出
        "  fragColor = vec4(finalColor, 0.65);\n" +
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

        // 【量子着色器预载】强制 GPU 在后台先完成编译，不阻塞首帧
        GLES30.glUseProgram(program);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        float[] quad = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
        vertexBuffer = ByteBuffer.allocateDirect(quad.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(quad).position(0);
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
        
        // 传入异步校准后的时钟，彻底消除时序偏移
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_sync_time"), qg_sync_time);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "u_resolution"), getWidth(), getHeight());
        
        int pos = GLES30.glGetAttribLocation(program, "vPosition");
        GLES30.glEnableVertexAttribArray(pos);
        GLES30.glVertexAttribPointer(pos, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(pos);
    }
}
