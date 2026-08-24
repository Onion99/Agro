# Gemma4 4B Lottie Scene Prompt Specification

**Version:** 2.0.0
**Updated:** 2026-08-24
**Owner:** `LottieSceneContract`
**Consumer:** `ChatViewModel.LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`

## 1. Purpose

Gemma4 4B no longer generates Native Lottie/Bodymovin AST directly. It emits a shallow
`lottie_scene` plan containing objects, geometry, colors, and normalized motion tracks. The app then
uses `LottieSceneCompiler` to deterministically compile that plan into the Native Lottie JSON passed
to Compottie.

This split follows the model's reliable reasoning order:

1. decide which visible objects exist;
2. assign simple geometry and colors;
3. describe one clear motion for each object.

The model is explicitly forbidden from generating `layers`, `shapes`, `ty`, `ks`, `a`, `k`, `s`, or
`e`. Those fields require deep Bodymovin nesting and were the main source of structurally valid but
non-renderable output. The compiler is generic: it does not inspect user keywords, select a fixed
animation template, or map a `kind/style/seed` to canned artwork.

## 2. Response Envelope

The model must output exactly one raw JSON object:

```json
{
  "type": "lottie_scene",
  "schemaVersion": 1,
  "title": "Falling Water Drops",
  "duration": 2,
  "loop": true,
  "objects": [
    {
      "name": "Drop 1",
      "shape": "ellipse",
      "position": [75, 20],
      "size": [12, 34],
      "fill": "#38BDF8",
      "motion": {
        "position": [[0, 75, -20], [1, 75, 260]],
        "opacity": [[0, 0], [0.12, 100], [0.82, 100], [1, 0]]
      }
    },
    {
      "name": "Drop 2",
      "shape": "ellipse",
      "position": [165, 20],
      "size": [10, 28],
      "fill": "#60A5FA",
      "motion": {
        "position": [[0, 165, -45], [0.22, 165, -20], [1, 165, 260]],
        "opacity": [[0, 0], [0.22, 0], [0.32, 100], [0.86, 100], [1, 0]]
      }
    }
  ]
}
```

No Markdown fence, comments, prose, or second object may surround the payload. A prose prefix/suffix
is tolerated by the response extractor for recovery, but it is not part of the contract.

## 3. Root Fields

| Field | Required model value | Compiler behavior |
| --- | --- | --- |
| `type` | `lottie_scene` | Other envelope types are rejected. |
| `schemaVersion` | `1` | Missing is treated as 1; other versions are rejected. |
| `title` | Non-empty display title | Trimmed to 64 characters; missing becomes `Lottie Animation`. |
| `duration` | `2` or `3`, in seconds | Clamped to 1..4 seconds. |
| `loop` | Boolean | Defaults to `true`. |
| `objects` | 1..6 objects | Parser tolerates at most 12; an empty scene is rejected. |

The compiler owns the canvas (`240 x 240`), frame rate (`30`), composition start (`ip=0`), frame end,
2D flags, empty assets, layer indexes, transforms, and paint syntax.

## 4. Object Geometry

Every object has `name`, `shape`, and `position:[x,y]`.

| `shape` | Fields | Native output |
| --- | --- | --- |
| `ellipse` | `size:[width,height]`, normally `fill` | `el` geometry |
| `rect` | `size`, optional `roundness`, normally `fill` | `rc` geometry |
| `star` | `points`, `radius`, `innerRadius`, normally `fill` | `sr` geometry |
| `path` | local `vertices:[[x,y],...]`, `closed`, fill and/or stroke | `sh` path with zero tangents |

Colors use `#RRGGBB`. `fill:"none"` and `stroke:"none"` are accepted. An open path should use
`stroke` and `strokeWidth`; Trim Path automatically ensures a visible stroke. Sizes, coordinates,
stroke widths, star point counts, and radii are clamped to safe render bounds.

Aliases such as `circle`, `rectangle`, and `line` are accepted by the compiler only as recovery.
The prompt always teaches the four canonical shape names.

## 5. Motion Tracks

Track time is normalized progress from `0` to `1`, never frames or milliseconds:

| Track | Row form | Compiled property |
| --- | --- | --- |
| `position` | `[time,x,y]` | layer `ks.p` |
| `scale` | `[time,percent]` or `[time,xPercent,yPercent]` | layer `ks.s` |
| `rotation` | `[time,degrees]` | layer `ks.r` |
| `opacity` | `[time,0..100]` | layer `ks.o` |
| `trim` | `[time,0..100]` | shape `tm.e` |

Each animated track should contain 2..5 chronological rows. The compiler:

- sorts rows and removes duplicate times;
- clamps time and values;
- extends a missing start/end boundary using the nearest value;
- converts progress to `round(progress * frameCount)`;
- writes `a=1` and makes each keyframe's `e` equal the next keyframe's `s`;
- preserves a static property when all values are equal;
- adds a small 96% → 104% → 96% pulse to the first object only when the entire scene is static.

For a loop, scale, rotation, and opacity should normally match at progress 0 and 1. Translation may
end elsewhere when the object is invisible at both boundaries, as in falling particles.

## 6. Runtime Policy for Gemma4 4B

Lottie mode has deterministic sampling caps independent of looser chat settings:

- `temperature <= 0.25`;
- `topP <= 0.9`;
- `topK <= 20`;
- output budget `1536` tokens;
- request-scoped structured conversation with no durable response replay.

Persisted Lottie sessions migrate to the current scene instruction when reopened. This prevents an
old session snapshot from continuing to request Native Lottie after the protocol upgrade.

## 7. Safety and Validation

The scene plan rejects external resources and executable content, including URLs, file/data URIs,
base64, images, fonts, scripts, HTML/CSS, effects, masks, expressions, Native `layers/shapes/ks`, and
`.lottie` packages. The compiled JSON is then checked again by `LottieJsonValidator` for:

- payload size at most 256 KiB;
- empty external assets;
- 2D supported layer types;
- at most 32 layers;
- at least one drawable geometry and fill/stroke pair;
- absence of forbidden fields and values.

Invalid content becomes `ChatMessageContent.Unsupported`, preserving the original model payload for
inspection.

## 8. Legacy Native Lottie Compatibility

Native Lottie is no longer requested from the model, but existing history and externally supplied
Native JSON remain accepted. `LottieJsonSanitizer` continues its token/AST repair path and now also:

- extracts the first JSON object from accidental prose;
- recognizes anonymous property wrappers when group-level size plus a color vector provide enough
  evidence to recover an ellipse and fill;
- marks animated position/opacity/rotation properties as `a=1` when the model omitted the flag;
- repairs segment continuity when a keyframe repeated its own `s` as `e` instead of using the next
  keyframe value.

The legacy `lottie_animation_spec` kind/template envelope remains unsupported. It is different from
the generic `lottie_scene` object graph introduced here.

## 9. Verification

`LottieMessageParserTest` covers:

- compact falling-water scene → compiler → validator → `LottieComposition.parse`;
- stroked path plus normalized Trim Path → `LottieComposition.parse`;
- the reported malformed anonymous water-drop Native JSON → sanitizer → Compottie;
- prior malformed Native Lottie regression cases and empty-drawable rejection.

## 10. Implementation References

- Contract/prompt: `composeApp/src/commonMain/kotlin/org/onion/agro/lottie/LottieSceneContract.kt`
- Compiler: `composeApp/src/commonMain/kotlin/org/onion/agro/lottie/LottieSceneCompiler.kt`
- Route/parser/validator: `composeApp/src/commonMain/kotlin/org/onion/agro/lottie/LottieAnimationSpecParser.kt`
- Legacy repair: `composeApp/src/commonMain/kotlin/org/onion/agro/lottie/LottieJsonSanitizer.kt`
- Session policy: `composeApp/src/commonMain/kotlin/org/onion/agro/viewmodel/ChatViewModel.kt`
- Message boundary: `composeApp/src/commonMain/kotlin/org/onion/agro/message/LottieMessageParser.kt`
