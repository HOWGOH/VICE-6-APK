package com.example.game.render

import android.opengl.GLES30

class ShaderProgram(vertexSrc: String, fragmentSrc: String) {
  val programId: Int

  init {
    val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
    val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
    programId = GLES30.glCreateProgram()
    GLES30.glAttachShader(programId, vs)
    GLES30.glAttachShader(programId, fs)
    GLES30.glLinkProgram(programId)
    GLES30.glDeleteShader(vs)
    GLES30.glDeleteShader(fs)
  }

  fun use() {
    GLES30.glUseProgram(programId)
  }

  fun uniform(name: String): Int = GLES30.glGetUniformLocation(programId, name)

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)
    return shader
  }

  companion object {
    val WORLD_VERTEX_SHADER =
      """
      #version 300 es
      layout(location = 0) in vec3 aPos;
      layout(location = 1) in vec3 aNormal;
      layout(location = 2) in vec4 aColor;
      layout(location = 3) in float aEmissive;

      uniform mat4 uMVP;
      uniform mat4 uModel;
      uniform vec4 uTint;
      uniform float uExtraEmissive;

      out vec3 vWorldPos;
      out vec3 vNormal;
      out vec4 vColor;
      out float vEmissive;

      void main() {
        vec4 world = uModel * vec4(aPos, 1.0);
        vWorldPos = world.xyz;
        vNormal = normalize(mat3(uModel) * aNormal);
        vColor = aColor * uTint;
        vEmissive = clamp(aEmissive + uExtraEmissive, 0.0, 1.0);
        gl_Position = uMVP * vec4(aPos, 1.0);
      }
      """
        .trimIndent()

    val WORLD_FRAGMENT_SHADER =
      """
      #version 300 es
      precision mediump float;

      in vec3 vWorldPos;
      in vec3 vNormal;
      in vec4 vColor;
      in float vEmissive;

      uniform vec3 uCameraPos;
      uniform vec3 uSunDir;
      uniform vec3 uAmbientColor;
      uniform vec3 uSunColor;
      uniform float uNeonBoost;
      uniform vec3 uFogColor;
      uniform float uFogStart;
      uniform float uFogEnd;

      out vec4 fragColor;

      void main() {
        vec3 n = normalize(vNormal);
        float ndl = max(dot(n, normalize(uSunDir)), 0.0);
        vec3 lit = vColor.rgb * (uAmbientColor + uSunColor * ndl);
        vec3 emissiveColor = vColor.rgb * (1.18 + uNeonBoost * 0.35);
        vec3 finalRgb = mix(lit, emissiveColor, vEmissive);

        float dist = length(uCameraPos - vWorldPos);
        float fogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
        finalRgb = mix(finalRgb, uFogColor, fogFactor * 0.88);
        fragColor = vec4(finalRgb, vColor.a);
      }
      """
        .trimIndent()

    val SKY_VERTEX_SHADER =
      """
      #version 300 es
      layout(location = 0) in vec2 aPos;
      out vec2 vUv;
      void main() {
        vUv = aPos * 0.5 + 0.5;
        gl_Position = vec4(aPos, 0.9999, 1.0);
      }
      """
        .trimIndent()

    val SKY_FRAGMENT_SHADER =
      """
      #version 300 es
      precision mediump float;
      in vec2 vUv;
      uniform vec3 uSkyBottom;
      uniform vec3 uSkyMid;
      uniform vec3 uSkyTop;
      out vec4 fragColor;
      void main() {
        float t = clamp(vUv.y, 0.0, 1.0);
        vec3 col = t < 0.45
          ? mix(uSkyBottom, uSkyMid, t / 0.45)
          : mix(uSkyMid, uSkyTop, (t - 0.45) / 0.55);
        fragColor = vec4(col, 1.0);
      }
      """
        .trimIndent()
  }
}
