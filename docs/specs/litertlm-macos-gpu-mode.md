# LiteRT-LM macOS GPU Mode

## Runtime path

The desktop macOS backend is `gpu`. LiteRT-LM creates a WebGPU environment and
Dawn selects the Apple Metal adapter underneath it. The expected initialization
sequence includes:

- WebGPU environment creation;
- an Apple adapter with `backend=Metal`;
- a default Metal device;
- a LiteRT `GpuEnvironment`.

These messages mean that Metal initialization succeeded. LiteRT-LM does not
select a separate `Metal` executor for the desktop path; the Metal device is the
native implementation used by WebGPU.

CPU XNNPACK messages can still appear because auxiliary embedding, end-of-audio,
or other small submodels may remain on CPU. They do not by themselves prove that
the main model left GPU mode.

## Top-K sampler ABI

Older `libLiteRtTopKWebGpuSampler` binaries export only:

- `LiteRtTopKWebGpuSampler_Create`;
- `LiteRtTopKWebGpuSampler_Destroy`;
- `LiteRtTopKWebGpuSampler_SampleToIdAndScoreBuffer`.

Newer LiteRT-LM sampler factory code also looks up configuration and input
capability functions. Those functions are optional extensions, so requiring them
causes the legacy WebGPU sampler to be rejected and sampling to fall back to
CPU. The compatibility patch keeps the three core functions mandatory and
returns `UNIMPLEMENTED` only if a caller explicitly requests an unavailable
optional update operation.

The patch is applied by `BuildNativeLibTask` before desktop Bazel builds from
`cpp/patches/lite-rt-lm-gpu-sampler-compatibility.patch`. The source operation is
idempotent so a workspace that already contains the fix is left unchanged.

## Failure handling

Desktop JNI must return the requested backend's native handle or propagate the
initialization error. It must not silently create a CPU engine after a failed GPU
initialization, because that leaves UI backend state inconsistent with the actual
executor. Higher-level recovery may explicitly rebuild the engine with `cpu` and
update the active backend state.
