package dev.chustatsu;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Rounded table surfaces with a local framebuffer blur when shaders are available. */
final class PanelStyle {
    private static final String VERTEX = "#version 120\n"
        + "varying vec2 localPos;\n"
        + "void main(){ localPos=gl_MultiTexCoord0.xy; gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; }\n";
    private static final String FRAGMENT = "#version 120\n"
        + "uniform sampler2D scene; uniform vec2 screenSize; uniform vec2 panelSize; uniform float tintAmount;\n"
        + "varying vec2 localPos;\n"
        + "void main(){ vec2 center=panelSize*0.5; vec2 d=abs(localPos-center)-(center-vec2(6.0));\n"
        + "float edge=length(max(d,0.0))+min(max(d.x,d.y),0.0)-6.0; if(edge>0.0)discard;\n"
        + "vec3 blurred=vec3(0.0); for(int i=-1;i<=1;i++) for(int j=-1;j<=1;j++)\n"
        + "blurred+=texture2D(scene,(gl_FragCoord.xy+vec2(float(i),float(j))*3.0)/screenSize).rgb;\n"
        + "blurred/=9.0; float highlight=0.035*(1.0-localPos.y/max(1.0,panelSize.y));\n"
        + "vec3 color=mix(blurred,vec3(0.055,0.075,0.095),0.12+0.18*tintAmount)+highlight;\n"
        + "gl_FragColor=vec4(color,(0.38+0.22*tintAmount)*(1.0-smoothstep(-1.0,0.0,edge))); }\n";
    private static int program;
    private static int sceneTexture;
    private static int textureWidth, textureHeight;
    private static boolean shaderFailed;

    private PanelStyle() {}

    static void panel(int left, int top, int right, int bottom, int opacity, boolean glass) {
        if (right <= left || bottom <= top) return;
        if (glass && !ChuStatsuConfig.lowPerformanceMode) blur(left, top, right, bottom, opacity);
        int alpha = clamp(opacity) * 255 / 100;
        if (glass) alpha = Math.round(alpha * .30f);
        rounded(left, top, right, bottom, 6, (alpha << 24) | 0x101820);
    }

    static void rounded(float left, float top, float right, float bottom, float radius, int argb) {
        if (right <= left || bottom <= top || (argb >>> 24) == 0) return;
        float r = Math.max(0, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
        int previousProgram = GLContext.getCapabilities().OpenGL20
            ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (previousProgram != 0) GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            float red = ((argb >> 16) & 255) / 255f;
            float green = ((argb >> 8) & 255) / 255f;
            float blue = (argb & 255) / 255f;
            float alpha = ((argb >>> 24) & 255) / 255f;
            GL11.glColor4f(red, green, blue, alpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f((left + right) / 2, (top + bottom) / 2);
            outline(left, top, right, bottom, r, -0.5f, red, green, blue, alpha, false);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            outline(left, top, right, bottom, r, -0.5f, red, green, blue, alpha, true);
            GL11.glEnd();
        } finally {
            if (previousProgram != 0) GL20.glUseProgram(previousProgram);
            GL11.glPopAttrib();
        }
    }

    private static void outline(float left, float top, float right, float bottom, float radius,
                                float inset, float red, float green, float blue, float alpha,
                                boolean feather) {
        float[] cx = {left + radius, right - radius, right - radius, left + radius};
        float[] cy = {top + radius, top + radius, bottom - radius, bottom - radius};
        for (int corner = 0; corner < 4; corner++) {
            for (int step = 0; step <= 24; step++) {
                double angle = Math.toRadians(180 + corner * 90 + step * 90.0 / 24);
                float cosine = (float) Math.cos(angle), sine = (float) Math.sin(angle);
                GL11.glColor4f(red, green, blue, alpha);
                GL11.glVertex2f(cx[corner] + cosine * Math.max(0, radius + inset),
                    cy[corner] + sine * Math.max(0, radius + inset));
                if (feather) {
                    GL11.glColor4f(red, green, blue, 0);
                    GL11.glVertex2f(cx[corner] + cosine * (radius + .5f),
                        cy[corner] + sine * (radius + .5f));
                }
            }
        }
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glVertex2f(cx[0] - Math.max(0, radius + inset), cy[0]);
        if (feather) {
            GL11.glColor4f(red, green, blue, 0);
            GL11.glVertex2f(cx[0] - radius - .5f, cy[0]);
        }
    }

    private static boolean blur(int l, int t, int r, int b, int opacity) {
        if (shaderFailed || opacity <= 0 || !GLContext.getCapabilities().OpenGL20) return false;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (program == 0) initialize();
            Minecraft mc = Minecraft.getInstance();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (textureWidth != mc.width || textureHeight != mc.height) {
                textureWidth = mc.width;
                textureHeight = mc.height;
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0,
                    textureWidth, textureHeight, 0);
            } else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                textureWidth, textureHeight);
            GL20.glUseProgram(program);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "scene"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "screenSize"), textureWidth, textureHeight);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "panelSize"), r - l, b - t);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "tintAmount"), clamp(opacity) / 100f);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(l, t);
            GL11.glTexCoord2f(r - l, 0); GL11.glVertex2f(r, t);
            GL11.glTexCoord2f(r - l, b - t); GL11.glVertex2f(r, b);
            GL11.glTexCoord2f(0, b - t); GL11.glVertex2f(l, b);
            GL11.glEnd();
            return true;
        } catch (RuntimeException error) {
            shaderFailed = true;
            ChuStatsu.LOGGER.warn("Glass background unavailable; using rounded panel", error);
            return false;
        } finally {
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
        }
    }

    private static void initialize() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        try {
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 2048));
            sceneTexture = GL11.glGenTextures();
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String message = GL20.glGetShaderInfoLog(shader, 2048);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    private static int clamp(int opacity) { return Math.max(0, Math.min(100, opacity)); }
}
