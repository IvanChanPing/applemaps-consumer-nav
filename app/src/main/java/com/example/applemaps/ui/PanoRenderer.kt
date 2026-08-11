package com.example.applemaps.ui

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.applemaps.map.LookAroundRawFace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * GLES2 Look Around renderer. It projects six original calibrated Apple HEIC faces directly when
 * available, and retains the equirectangular JPEG path as a compatibility fallback.
 * Implements docs/STREET_VIEW_ROADMAP.md PART B exactly (shader math, texture upload, GL lifecycle).
 * [PanoMath] is a pure-Kotlin mirror of the fragment shader (unit-tested in PanoMathTest).
 * [PanoGlRenderer] is the GLSurfaceView.Renderer; [PanoGlView] is the Compose host that wires
 * EGL setup + Activity lifecycle (pause/resume) per the AOSP GLSurfaceView contract.
 * The raw-face path preserves the former box projector's two projection modes: wrapped angular
 * bands for the four side cameras and calibrated 3-axis rotation for the two polar cameras.
 * [PanoMathTest] checks a real calibration over a full-sphere 2° grid; final visual inspection is
 * performed in Look Around on a physical device by dragging behind and pitching to both poles.
 */
object PanoMath {
    /** Bounded first-frame face size used while a selected place remains on the normal map screen. */
    const val PRELOAD_RAW_FACE_SIZE = 1024

    /** Equirect (u,v) for NDC (px,py); fovY/yaw/pitch in RADIANS. Mirrors the fragment shader 1:1 (B.2). */
    fun ndcToUv(px: Float, py: Float, aspect: Float, fovY: Float, yaw: Float, pitch: Float): Pair<Float, Float> {
        val t = tan(fovY / 2f)
        var dx = px * aspect * t; var dy = py * t; var dz = -1f
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        dx /= len; dy /= len; dz /= len
        val cp = cos(pitch); val sp = sin(pitch)
        val d1y = cp * dy - sp * dz; val d1z = sp * dy + cp * dz
        val d1x = dx
        val cy = cos(yaw); val sy = sin(yaw)
        val d2x = cy * d1x - sy * d1z; val d2y = d1y; val d2z = sy * d1x + cy * d1z
        val lon = atan2(d2x, -d2z)
        val lat = asin(d2y.coerceIn(-1f, 1f))
        val pi = Math.PI.toFloat()
        val u = 0.5f + lon / (2f * pi)
        val v = 0.5f - lat / pi
        return u to v
    }

    /** Horizontal FOV in degrees for a vertical FOV in degrees at the given aspect (B.6). */
    fun fovXdeg(fovYdeg: Float, aspect: Float): Float {
        val fovXrad = 2f * atan(tan(Math.toRadians(fovYdeg.toDouble()) / 2.0) * aspect)
        return Math.toDegrees(fovXrad).toFloat()
    }

    /** Returns the safe progressive HD width only after GL proves it can accept the distinct 4K texture. */
    fun progressiveHdWidth(maxTextureSize: Int, hasDistinctHd: Boolean): Int? =
        if (hasDistinctHd && maxTextureSize >= 4096) 4096 else null

    /** Bounds six simultaneous raw-face textures so their combined software/GPU allocation stays predictable. */
    fun rawFaceTargetSize(maxTextureSize: Int): Int = when {
        maxTextureSize >= 4096 -> 1536
        maxTextureSize >= 2048 -> 1024
        else -> 0
    }

    /**
     * Returns calibrated texture coordinates for one original Apple face, or null outside it.
     * Faces 0..3 are wrapped side bands; faces 4..5 are rotated polar patches. This mirrors
     * [RAW_FRAGMENT_SHADER] and the former box's Look Around reprojection contract.
     */
    fun rawFaceUv(
        faceIndex: Int,
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        fovS: Float,
        fovH: Float,
        cy: Float,
        yaw: Float,
        pitch: Float,
        roll: Float,
    ): Pair<Float, Float>? {
        require(faceIndex in 0..5)
        val u: Float
        val v: Float
        if (faceIndex < 4) {
            val lon = atan2(worldX, -worldZ)
            val unwrappedDelta = lon - yaw
            val delta = atan2(sin(unwrappedDelta), cos(unwrappedDelta))
            u = 0.5f + delta / fovS
            val theta = Math.PI.toFloat() * 0.5f - asin(worldY.coerceIn(-1f, 1f))
            val thetaStart = Math.PI.toFloat() * 0.5f - fovH * 0.5f - cy
            v = (theta - thetaStart) / fovH
        } else {
            val equiX = -worldZ
            val equiY = worldX
            val equiZ = -worldY

            val cr = cos(roll); val sr = sin(roll)
            val rollX = equiX
            val rollY = cr * equiY - sr * equiZ
            val rollZ = sr * equiY + cr * equiZ

            val cp = cos(pitch); val sp = sin(pitch)
            val pitchX = cp * rollX + sp * rollZ
            val pitchY = rollY
            val pitchZ = -sp * rollX + cp * rollZ

            val correctedYaw = -yaw
            val cyaw = cos(correctedYaw); val syaw = sin(correctedYaw)
            val rotatedX = cyaw * pitchX - syaw * pitchY
            val rotatedY = syaw * pitchX + cyaw * pitchY
            val rotatedZ = pitchZ

            u = 0.5f + atan2(rotatedY, rotatedX) / fovS
            v = 0.5f + asin(rotatedZ.coerceIn(-1f, 1f)) / fovH
        }
        return if (u in 0f..1f && v in 0f..1f) u to v else null
    }
}

/** One decoded, original Apple HEIC face paired with its server-provided camera calibration. */
data class RawPanoFace(val calibration: LookAroundRawFace, val bitmap: Bitmap)

private const val VERTEX_SHADER = """
attribute vec2 aPos;
varying vec2 vNdc;
void main() { vNdc = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }
"""

private const val FRAGMENT_SHADER = """
precision highp float;
varying vec2 vNdc;
uniform float uAspect;      // viewportW / viewportH
uniform float uTanHalfFov;  // tan(fovY * 0.5)
uniform float uYaw;         // radians, + = look right (relative to image center)
uniform float uPitch;       // radians, + = look up
uniform sampler2D uTex;
const float PI = 3.14159265358979;
void main() {
    vec3 d = normalize(vec3(vNdc.x * uAspect * uTanHalfFov, vNdc.y * uTanHalfFov, -1.0));
    float cp = cos(uPitch), sp = sin(uPitch);
    d = vec3(d.x, cp * d.y - sp * d.z, sp * d.y + cp * d.z);
    float cy = cos(uYaw), sy = sin(uYaw);
    d = vec3(cy * d.x - sy * d.z, d.y, sy * d.x + cy * d.z);
    float lon = atan(d.x, -d.z);
    float lat = asin(clamp(d.y, -1.0, 1.0));
    vec2 uv = vec2(0.5 + lon / (2.0 * PI), 0.5 - lat / PI);
    gl_FragColor = texture2D(uTex, uv);
}
"""

private const val RAW_FRAGMENT_SHADER = """
precision highp float;
varying vec2 vNdc;
uniform float uAspect;
uniform float uTanHalfFov;
uniform float uYaw;
uniform float uPitch;
uniform sampler2D uFace0;
uniform sampler2D uFace1;
uniform sampler2D uFace2;
uniform sampler2D uFace3;
uniform sampler2D uFace4;
uniform sampler2D uFace5;
uniform vec4 uFaceLens[6]; // fovS, fovH, cx, cy
uniform vec3 uFacePose[6]; // yaw, pitch, roll

vec3 projectSideFace(vec3 worldDir, vec4 lens, vec3 pose) {
    const float PI = 3.14159265358979;
    float lon = atan(worldDir.x, -worldDir.z);
    float delta = lon - pose.x;
    delta = atan(sin(delta), cos(delta));
    float u = 0.5 + delta / lens.x;
    float theta = PI * 0.5 - asin(clamp(worldDir.y, -1.0, 1.0));
    float thetaStart = PI * 0.5 - lens.y * 0.5 - lens.w;
    float v = (theta - thetaStart) / lens.y;
    float inside = float(u >= 0.0 && u <= 1.0 && v >= 0.0 && v <= 1.0);
    return vec3(inside, u, v);
}

vec3 projectPolarFace(vec3 worldDir, vec4 lens, vec3 pose) {
    vec3 equiDir = vec3(-worldDir.z, worldDir.x, -worldDir.y);

    float cr = cos(pose.z), sr = sin(pose.z);
    vec3 rollDir = vec3(
        equiDir.x,
        cr * equiDir.y - sr * equiDir.z,
        sr * equiDir.y + cr * equiDir.z
    );

    float cp = cos(pose.y), sp = sin(pose.y);
    vec3 pitchDir = vec3(
        cp * rollDir.x + sp * rollDir.z,
        rollDir.y,
        -sp * rollDir.x + cp * rollDir.z
    );

    float correctedYaw = -pose.x;
    float cy = cos(correctedYaw), sy = sin(correctedYaw);
    vec3 rotated = vec3(
        cy * pitchDir.x - sy * pitchDir.y,
        sy * pitchDir.x + cy * pitchDir.y,
        pitchDir.z
    );

    float u = 0.5 + atan(rotated.y, rotated.x) / lens.x;
    float v = 0.5 + asin(clamp(rotated.z, -1.0, 1.0)) / lens.y;
    float inside = float(u >= 0.0 && u <= 1.0 && v >= 0.0 && v <= 1.0);
    return vec3(inside, u, v);
}

void main() {
    vec3 d = normalize(vec3(vNdc.x * uAspect * uTanHalfFov, vNdc.y * uTanHalfFov, -1.0));
    float cp = cos(uPitch), sp = sin(uPitch);
    d = vec3(d.x, cp * d.y - sp * d.z, sp * d.y + cp * d.z);
    float cy = cos(uYaw), sy = sin(uYaw);
    d = vec3(cy * d.x - sy * d.z, d.y, sy * d.x + cy * d.z);
    vec3 p0 = projectSideFace(d, uFaceLens[0], uFacePose[0]);
    vec3 p1 = projectSideFace(d, uFaceLens[1], uFacePose[1]);
    vec3 p2 = projectSideFace(d, uFaceLens[2], uFacePose[2]);
    vec3 p3 = projectSideFace(d, uFaceLens[3], uFacePose[3]);
    vec3 p4 = projectPolarFace(d, uFaceLens[4], uFacePose[4]);
    vec3 p5 = projectPolarFace(d, uFaceLens[5], uFacePose[5]);
    float best = -1.0;
    vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
    if (p0.x > 0.0) { color = texture2D(uFace0, p0.yz); best = 0.0; }
    if (p1.x > 0.0 && best < 0.0) { color = texture2D(uFace1, p1.yz); best = 1.0; }
    if (p2.x > 0.0 && best < 0.0) { color = texture2D(uFace2, p2.yz); best = 2.0; }
    if (p3.x > 0.0 && best < 0.0) { color = texture2D(uFace3, p3.yz); best = 3.0; }
    if (p4.x > 0.0 && best < 0.0) { color = texture2D(uFace4, p4.yz); best = 4.0; }
    if (p5.x > 0.0 && best < 0.0) { color = texture2D(uFace5, p5.yz); }
    gl_FragColor = color;
}
"""

/** GLES2 renderer that draws one equirectangular bitmap as a full-screen pano (B.3–B.5). Camera fields
 *  are @Volatile so the UI thread (Compose drag/pinch handlers) can write them and the GL thread reads
 *  them in onDrawFrame without extra synchronization (plain float writes are safe per B.5). */
class PanoGlRenderer(private val onTextureLimitKnown: (Int) -> Unit = {}) : GLSurfaceView.Renderer {
    @Volatile var yawRad = 0f       // relative to image center (B.1)
    @Volatile var pitchRad = 0f
    @Volatile var fovYRad = Math.toRadians(65.0).toFloat()
    @Volatile var maxTex = 0        // 0 until the GL thread publishes the actual bounded device limit
    @Volatile private var pendingBitmap: Bitmap? = null   // set via setBitmap, consumed on GL thread
    private var retainedBitmap: Bitmap? = null            // last uploaded — context-loss re-upload (B.5)
    @Volatile private var pendingRawFaces: List<RawPanoFace>? = null
    private var retainedRawFaces: List<RawPanoFace>? = null

    private var program = 0
    private var texId = 0
    private var rawProgram = 0
    private val rawTexIds = IntArray(6)
    private var aPosLoc = 0
    private var uAspectLoc = 0; private var uTanHalfFovLoc = 0; private var uYawLoc = 0; private var uPitchLoc = 0
    private var vpW = 1; private var vpH = 1
    private val quadBuf: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    /** Queue a bitmap for upload on the GL thread; caller must also call glView.requestRender(). */
    fun setBitmap(b: Bitmap) { pendingRawFaces = null; retainedRawFaces = null; pendingBitmap = b }

    /** Queue six original HEIC faces for calibrated on-device projection on the GL thread. */
    fun setRawFaces(faces: List<RawPanoFace>) {
        require(faces.map { it.calibration.index } == (0..5).toList())
        pendingBitmap = null; retainedBitmap = null; pendingRawFaces = faces
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Re-entered whenever the EGL context is created OR recreated after context loss (B.5) —
        // must re-query limits, recompile shaders, and re-upload the retained bitmap if any.
        val maxTexArr = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexArr, 0)
        maxTex = if (maxTexArr[0] > 0) minOf(4096, maxTexArr[0]) else 2048
        onTextureLimitKnown(maxTex)

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        program = linkProgram(vs, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        rawProgram = linkProgram(vs, compileShader(GLES20.GL_FRAGMENT_SHADER, RAW_FRAGMENT_SHADER))
        if (program == 0 && rawProgram == 0) return
        if (program != 0) {
            aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
            uAspectLoc = GLES20.glGetUniformLocation(program, "uAspect")
            uTanHalfFovLoc = GLES20.glGetUniformLocation(program, "uTanHalfFov")
            uYawLoc = GLES20.glGetUniformLocation(program, "uYaw")
            uPitchLoc = GLES20.glGetUniformLocation(program, "uPitch")
        }

        texId = 0
        rawTexIds.fill(0)
        retainedBitmap?.let { pendingBitmap = it }   // context lost resources are gone — re-upload
        retainedRawFaces?.let { pendingRawFaces = it }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h); vpW = w; vpH = h
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0 && rawProgram == 0) return

        pendingRawFaces?.let { faces ->
            uploadRawFaces(faces)
            retainedRawFaces = faces
            pendingRawFaces = null
        }
        if (rawProgram != 0 && rawTexIds.all { it != 0 }) {
            drawRawFaces()
            return
        }
        if (program == 0) return

        pendingBitmap?.let { bmp ->
            if (texId == 0) {
                val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); texId = ids[0]
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            retainedBitmap = bmp
            pendingBitmap = null
        }
        if (texId == 0) return

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuf)
        GLES20.glUniform1f(uAspectLoc, vpW.toFloat() / vpH.toFloat())
        GLES20.glUniform1f(uTanHalfFovLoc, tan(fovYRad / 2f))
        GLES20.glUniform1f(uYawLoc, yawRad)
        GLES20.glUniform1f(uPitchLoc, pitchRad)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosLoc)
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("PanoGL", "compile failed: " + GLES20.glGetShaderInfoLog(id))
            GLES20.glDeleteShader(id); return 0
        }
        return id
    }

    private fun linkProgram(vertex: Int, fragment: Int): Int {
        if (vertex == 0 || fragment == 0) return 0
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertex); GLES20.glAttachShader(linked, fragment); GLES20.glLinkProgram(linked)
        val status = IntArray(1); GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != 0) return linked
        Log.e("PanoGL", "link failed: " + GLES20.glGetProgramInfoLog(linked))
        GLES20.glDeleteProgram(linked)
        return 0
    }

    private fun uploadRawFaces(faces: List<RawPanoFace>) {
        if (rawTexIds.any { it != 0 }) {
            GLES20.glDeleteTextures(6, rawTexIds, 0)
            rawTexIds.fill(0)
        }
        GLES20.glGenTextures(6, rawTexIds, 0)
        faces.forEach { face ->
            val unit = face.calibration.index
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rawTexIds[unit])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, face.bitmap, 0)
        }
    }

    private fun drawRawFaces() {
        GLES20.glUseProgram(rawProgram)
        val aPos = GLES20.glGetAttribLocation(rawProgram, "aPos")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadBuf)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(rawProgram, "uAspect"), vpW.toFloat() / vpH.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(rawProgram, "uTanHalfFov"), tan(fovYRad / 2f))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(rawProgram, "uYaw"), yawRad)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(rawProgram, "uPitch"), pitchRad)
        val faces = retainedRawFaces ?: return
        val lens = FloatArray(24); val pose = FloatArray(18)
        faces.forEach { face ->
            val i = face.calibration.index
            lens[i * 4] = face.calibration.fovS; lens[i * 4 + 1] = face.calibration.fovH
            lens[i * 4 + 2] = face.calibration.cx; lens[i * 4 + 3] = face.calibration.cy
            pose[i * 3] = face.calibration.yaw; pose[i * 3 + 1] = face.calibration.pitch; pose[i * 3 + 2] = face.calibration.roll
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rawTexIds[i])
            GLES20.glUniform1i(GLES20.glGetUniformLocation(rawProgram, "uFace$i"), i)
        }
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(rawProgram, "uFaceLens[0]"), 6, lens, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(rawProgram, "uFacePose[0]"), 6, pose, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
    }
}

/** panoGlSurfaceView — fullscreen black GL surface hosting the equirect pano render; the visible content
 *  is entirely the shader output (no other chrome). Wraps GLSurfaceView setup (B.5: EGL v2 context before
 *  setRenderer, preserve-on-pause, RENDERMODE_WHEN_DIRTY) and forwards Activity lifecycle pause/resume. */
@Composable
fun PanoGlView(
    modifier: Modifier = Modifier,
    onTextureLimitKnown: (Int) -> Unit = {},
    onReady: (GLSurfaceView, PanoGlRenderer) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glViewRef: GLSurfaceView? = null
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val mainHandler = Handler(Looper.getMainLooper())
            val renderer = PanoGlRenderer { limit -> mainHandler.post { onTextureLimitKnown(limit) } }
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)          // must precede setRenderer (B.5)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glViewRef = this
                onReady(this, renderer)
            }
        },
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
