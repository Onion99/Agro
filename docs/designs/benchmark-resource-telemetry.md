# Benchmark Backend 资源遥测设计

## 1. 目标

SettingScreen 的 Benchmark 资源卡片只展示可由操作系统或 Backend 可靠提供的真实指标，禁止以运行状态、JVM heap 或固定常量推导硬件利用率。

本设计解决以下问题：

- “Engine Compute” 不再在运行时固定显示 95%。
- Android/Desktop 不再用 JVM heap 冒充模型 Backend 的进程内存。
- iOS 不再返回固定的 `256/4096 MB`。
- 正式跑测期间周期采样，保留内存峰值和相对跑测基线的增量。
- 删除不能代表真实 KV Cache 字节占用的 Token 容量进度条。

## 2. 指标语义

`ProcessResourceSnapshot` 是单次 OS 快照，`ProcessResourceTracker` 将连续快照聚合为 `ProcessResourceUsage`：

| 字段 | 语义 |
| --- | --- |
| `currentResidentMemoryBytes` | 当前 App 进程常驻内存/工作集 |
| `peakResidentMemoryBytes` | 本次正式 Benchmark 采样窗口内的最高常驻内存 |
| `peakResidentMemoryDeltaBytes` | `peak - 正式跑测起始基线`，最小为 0 |
| `currentCpuLoadPercent` | 相邻样本间进程 CPU 时间增量，经逻辑处理器数量归一化后的负载 |
| `peakCpuLoadPercent` | 本次正式 Benchmark 采样窗口内的最高归一化 CPU 负载 |
| `totalPhysicalMemoryBytes` | 设备物理内存，仅用于进度比例，不作为进程可用上限 |

CPU 百分比按 `CPU 时间增量 / 墙钟时间增量 / 逻辑处理器数` 计算并限制在 `0..100%`。因此 100% 表示该进程在采样间隔内占满全部逻辑处理器，而不是单核满载。

## 3. Backend 展示规则

| Backend | 计算指标 |
| --- | --- |
| CPU | 展示真实的 App 进程 CPU 当前值/峰值 |
| GPU | 驱动未提供可靠跨平台计数器时显示“等待开发” |
| NPU | 驱动未提供可靠跨平台计数器时显示“等待开发” |

禁止把进程 CPU 负载标为 GPU/NPU 利用率，也禁止用 `isRunning` 合成计算百分比。将来只有在具体平台接入可信的驱动/API 计数器后，才能为对应 Accelerator 填充计算利用率。

内存是独立于 Backend 标签的 App 进程级指标，UI 统一标为“应用内存占用”，展示进程内存峰值与正式跑测增量。它可能包含主机可见的 native/加速器分配，但不能拆分或归因到 CPU、GPU、NPU，也不包含设备私有显存；因此禁止显示为“CPU/GPU/NPU 消耗内存”。

## 4. 平台数据源

| 平台 | 进程内存 | 物理内存 | 进程 CPU 时间 |
| --- | --- | --- | --- |
| Android | `/proc/self/status` 的 `VmRSS`；读取失败时回退 `Debug.getPss()` | `/proc/meminfo` 的 `MemTotal` | `android.os.Process.getElapsedCpuTime()` |
| Windows Desktop | `GetProcessMemoryInfo().WorkingSetSize` | `OperatingSystemMXBean.totalMemorySize` | `ProcessHandle.Info.totalCpuDuration()` |
| Linux Desktop | `/proc/self/status` 的 `VmRSS` | `OperatingSystemMXBean.totalMemorySize` | `ProcessHandle.Info.totalCpuDuration()` |
| macOS Desktop | Mach `task_info(MACH_TASK_BASIC_INFO).resident_size` | `OperatingSystemMXBean.totalMemorySize` | `ProcessHandle.Info.totalCpuDuration()` |
| iOS | Mach `task_info(MACH_TASK_BASIC_INFO).resident_size` | `NSProcessInfo.physicalMemory` | `getrusage(RUSAGE_SELF)` 的 user + system CPU time |

平台 API 不可用时字段返回 `null`，UI 显示不可用；不得回退为固定数字或 JVM heap。

## 5. 采样生命周期

1. 打开 Benchmark 标签但尚未跑测时，只抓取一次当前进程快照。
2. 首次 Warmup 不计入正式资源窗口，避免 shader/JIT、引擎重建和冷启动分页污染跑测增量。
3. Warmup 完成后，以当前进程内存建立新基线，并每 200ms 采样一次。
4. 推理 Flow 结束时先停止采样 Job，再补抓一次终态样本，随后发布最终吞吐指标。
5. 取消或异常路径在 `NonCancellable` 清理区停止采样并补抓终态样本，不允许残留采样协程。
6. StateFlow 更新使用原子 `update`，避免流式文本与资源采样并发更新时互相覆盖。

## 6. 为什么删除 KV Cache 进度

原实现使用 `prefillTokenCount + decodeTokenCount` 除以 `maxTokens`，该比例只描述本轮 Token 数相对上下文容量，不是 KV Cache 的已分配字节、显存占用或峰值内存。KV Cache 还受层数、head 数、维度、精度、共享/分页策略和 Backend 内存布局影响。在 LiteRT-LM 未暴露真实 KV Cache 字节计数器前，不在硬件资源卡片展示该指标。

## 7. 验证

- `ProcessResourceTrackerTest` 覆盖内存峰值/增量、逻辑处理器 CPU 归一化和不可用字段传播。
- `PlatformProcessResourceSnapshotTest` 验证当前平台返回真实且为正的进程、物理内存与 CPU 计数器。
- Desktop 测试必须在 JDK 21 下运行；iOS 平台实现需要在 macOS CI/开发机完成最终 App 链接验证。
