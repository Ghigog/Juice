package com.juice.ui.liquid

/**
 * AGSL shader that turns the particle field into liquid.
 *
 * The field is drawn first: every particle stamps a soft blob into a RenderNode with additive
 * blending. Red = juice, green = speed (churn), blue = foam. This shader runs as a RenderEffect
 * on that node: it thresholds the field into a surface and shades it.
 */
internal object LiquidShaders {
    const val COMPOSITE = """
uniform shader field;
uniform float2 size;     // view size in px
uniform float px;        // px per dp
uniform float corner;    // glass corner radius in dp
uniform float3 acc;      // flavour accent
uniform float3 deep;     // flavour deep tone
uniform float3 cream;

float T(float2 p) { half4 q = field.eval(p); return float(q.r + q.b); }

half4 main(float2 p) {
    half4 s = field.eval(p);
    float t = float(s.r + s.b);
    const float th = 0.2;
    float e = smoothstep(th - 0.03, th + 0.03, t);
    if (e < 0.003) { return half4(0.0); }
    float ff = smoothstep(0.3, 0.65, float(s.b) / max(t, 0.001));

    // softened field for shading, so single particles never show as dimples
    float R = 5.0 * px;
    float Rd = 3.6 * px;
    float tb = (t * 4.0
        + 2.0 * (T(p + float2(R, 0.0)) + T(p - float2(R, 0.0)) + T(p + float2(0.0, R)) + T(p - float2(0.0, R)))
        + T(p + float2(Rd, Rd)) + T(p + float2(-Rd, Rd)) + T(p + float2(Rd, -Rd)) + T(p + float2(-Rd, -Rd))) / 16.0;

    // distance to the glass, in dp
    float cr = corner * px;
    float2 q = abs(p - size * 0.5) - (size * 0.5 - cr);
    float wd = -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cr) / px;
    float wallFade = smoothstep(4.0, 20.0, wd);
    tb += (1.0 - wallFade) * 0.15 * step(th, t);

    float G = 8.0 * px;
    float gx = T(p + float2(G, 0.0)) - T(p - float2(G, 0.0));
    float gy = T(p + float2(0.0, G)) - T(p - float2(0.0, G));
    float bump = 0.9 * wallFade * mix(1.0, 0.2, smoothstep(th + 0.08, th + 0.35, tb));
    float3 n = normalize(float3(-gx * bump, -gy * bump, 1.0));
    float3 L = normalize(float3(-0.45, -0.6, 0.75));   // light from the top left (y is down)
    float diff = dot(n, L);
    float rl = max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0001);
    float spec = pow(rl, 70.0) * 1.4 + pow(rl, 8.0) * 0.12;

    float depth = smoothstep(th, th + 0.4, tb);
    float3 body = mix(acc, deep, depth * 0.55);
    float rim = (1.0 - smoothstep(th, th + 0.07, tb)) * wallFade;
    float churn = smoothstep(0.04, 0.3, float(s.g));
    float3 liquid = body * (0.78 + 0.3 * diff) + cream * (rim * 0.4 + churn * 0.18) + float3(spec * 0.6);
    float3 froth = mix(cream, acc, 0.22) * (0.86 + 0.18 * diff) + float3(spec * 0.15);
    float3 col = min(mix(liquid, froth, ff), float3(1.0));
    float a = e * mix(mix(0.7, 0.94, depth), 0.97, ff);
    return half4(half3(col * a), half(a));
}
"""
}
