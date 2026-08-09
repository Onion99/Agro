# Gemma4 Native Lottie Prompt Specification

**Version:** 1.4.0  
**Updated:** 2026-08-09  
**Owner:** `ChatViewModel.LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`

## Purpose

Gemma4 4B must generate the complete Native Lottie JSON. The client does not select an animation template, infer a `kind`, calculate geometry from a `seed`, or build layers from an intent object. `LottieAnimationSpecParser` only sanitizes malformed model output, validates safety, and extracts metadata for rendering.

The model must output exactly one JSON object. The legacy `lottie_animation_spec` intent envelope is no longer accepted.

## Smallest Working Animation

Use this one-layer breathing circle as the baseline pattern:

```json
{
  "v": "5.7.4",
  "fr": 30,
  "ip": 0,
  "op": 60,
  "w": 240,
  "h": 240,
  "nm": "Breathing Circle",
  "ddd": 0,
  "loop": true,
  "assets": [],
  "layers": [
    {
      "ddd": 0,
      "ind": 1,
      "ty": 4,
      "nm": "Circle Layer",
      "sr": 1,
      "ks": {
        "o": { "a": 0, "k": 100 },
        "r": { "a": 0, "k": 0 },
        "p": { "a": 0, "k": [120, 120, 0] },
        "a": { "a": 0, "k": [0, 0, 0] },
        "s": { "a": 1, "k": [
          { "t": 0, "s": [90, 90, 100], "e": [100, 100, 100] },
          { "t": 30, "s": [100, 100, 100], "e": [90, 90, 100] },
          { "t": 60, "s": [90, 90, 100], "e": [90, 90, 100] }
        ]
      }
    },
    "ao": 0,
      "shapes": [
        {
          "ty": "gr",
          "nm": "Circle Group",
          "it": [
            { "ty": "el", "nm": "Circle Path", "p": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [100, 100] }, "d": 1 },
            { "ty": "fl", "nm": "Circle Fill", "c": { "a": 0, "k": [0.12, 0.65, 0.95, 1] }, "o": { "a": 0, "k": 100 }, "r": 1 },
            { "ty": "tr", "p": { "a": 0, "k": [0, 0] }, "a": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [100, 100] }, "r": { "a": 0, "k": 0 }, "o": { "a": 0, "k": 100 } }
          ]
        }
      ],
      "ip": 0,
      "op": 60,
      "st": 0,
      "bm": 0
    }
  ]
}
```

At 30 FPS, `op - ip = 60` is two seconds. The scale is 90% at frame 0, 100% at frame 30, and 90% at frame 60, so the loop returns to its starting state.

## Field Meaning

### Root

| Field | Meaning |
| --- | --- |
| `v` | Lottie/exporter version. Use `5.7.4`. |
| `fr` | Frames per second. Use 30 for simple UI motion or 60 for very smooth motion. |
| `ip` / `op` | Composition start/end frames. Duration is `(op - ip) / fr` seconds. Keep the span at most 180 frames. |
| `w` / `h` | Canvas dimensions in composition units. Use 240x240 by default; keep them in 64..512. |
| `nm` | Human-readable animation title. |
| `ddd` | 3D flag. Must be `0`; this route is 2D. |
| `loop` | Optional app metadata. Use `true` for seamless activity and `false` for one-shot events. |
| `assets` | Must be an empty array. No images, fonts, precompositions, or external files. |
| `layers` | Non-empty ordered array. Later layers draw over earlier layers. |

### Layer and Transform

| Field | Meaning |
| --- | --- |
| `ind` | Unique positive layer id. |
| `ty` | Layer type. Use `4` for a vector shape layer. |
| `sr` | Time stretch. Use `1`. |
| `ip` / `op` / `st` | Layer visibility interval and start offset, in frames. |
| `bm` | Blend mode. Use `0` for normal blending. |
| `ao` | Auto-orientation. Use `0`. |
| `ks.o` | Layer opacity, 0..100. |
| `ks.r` | Layer rotation in degrees. |
| `ks.p` | Absolute position `[x,y,z]`; center is normally `[w/2,h/2,0]`. |
| `ks.a` | Anchor point `[x,y,z]`; a simple centered layer uses `[0,0,0]`. |
| `ks.s` | Scale `[x,y,z]` in percentages. Use `[100,100,100]`, never `[1,1,1]`. |

### Shape Items

- `ty="gr"` is a group. Its `it` array contains geometry, style, and a final `tr` group transform.
- `ty="el"` is an ellipse/circle. `p` is local position and `s` is local `[width,height]`.
- `ty="rc"` is a rectangle. `p` is local center, `s` is `[width,height]`, and `r` is corner radius.
- `ty="sh"` is a custom path. `ks.k` contains vertices `v`, in-tangents `i`, out-tangents `o`, and closed flag `c`.
- `ty="fl"` is a fill. `c` is normalized RGBA, each channel 0..1; `o` is opacity 0..100.
- `ty="st"` is a stroke. `c` is normalized RGBA, `o` is opacity, `w` is width, `lc` is line cap (`1` butt, `2` round, `3` square), and `lj` is line join.
- `ty="tm"` is Trim Path. `s` is start percentage, `e` is end percentage, and `o` is offset degrees. Animate `e` from 0 to 100 for draw-on motion.
- `ty="tr"` is a group transform with local position `p`, anchor `a`, percentage scale `s`, rotation `r`, and opacity `o`. Put it after visible items in `it`.

## Keyframes and Timing

- Static property: `{ "a": 0, "k": value }`.
- Animated property: `{ "a": 1, "k": [ ...chronological keyframes... ] }`.
- `t` is an absolute frame number, not milliseconds.
- `s` is the segment start value; `e` is the segment end value. Include both for clear interpolation.
- Scalar values use `[value]`; vectors use `[x,y]` or `[x,y,z]`.
- `h=1` means hold/step. Omit it or use `h=0` for interpolation.
- Optional keyframe `i` and `o` are Bezier easing handles. They are different from opacity and Trim Path offset `o`.
- Convert time with `frame = round(milliseconds * fr / 1000)` and clamp to the composition/layer interval.

## Animation Guidance

1. Choose one subject and one action: pulse, rotate, move, fade, draw, or reveal.
2. Start with one layer and one primitive. Add layers only when they clarify the action.
3. Build geometry first, style second, and timing third.
4. Use a readable sequence: entrance/draw `0%..25%`, main action `25%..75%`, settle `75%..100%`.
5. Use 2..4 keyframes per animated property. Avoid random movement and avoid animating every property at once.
6. Pulse uses scale or opacity; rotation uses `r`; movement uses `p`; fading uses `o`; drawing uses Trim Path `e`.
7. A loop must have matching first and last visual values. A one-shot must end in a stable readable pose.
8. Keep the artwork inside roughly 8%..92% of the canvas so strokes and overshoot are not clipped.

## Forbidden Output

Do not output `lottie_animation_spec`, URLs, file paths, images, fonts, text layers, scripts, HTML, CSS, expressions, masks, effects, 3D layers, base64, `.lottie` packages, executable content, comments, or trailing commas. Keep `assets` empty, use at most 32 layers, and keep the JSON below 256 KiB.

## Implementation References

- Prompt: `composeApp/src/commonMain/kotlin/org/onion/agro/viewmodel/ChatViewModel.kt`
- Parser and validator: `composeApp/src/commonMain/kotlin/org/onion/agro/lottie/LottieAnimationSpecParser.kt`
- Message boundary: `composeApp/src/commonMain/kotlin/org/onion/agro/message/LottieMessageParser.kt`
