# Liquid Glass Slider — 30 秒 9:16 极简 UI 进化视频（Remotion）

同一枚滑块从**基础拼装 → 三层景深 → 液态玻璃 → 收束定格**的进阶过程。
纯代码生成（Remotion + React），纯黑 void 背景，无旁白无字幕，
画面中只出现标准参数名（Opacity / Brightness / Blur / Blur Intensity / Liquid Glass）。

**抖音发布文案建议**：人类应常常不敢设计ui而感到困扰

---

## 成片

| 项目 | 值 |
| --- | --- |
| 文件 | `output.mp4` |
| 尺寸 / 帧率 | 1080 × 1920（9:16 竖屏）@ 30fps |
| 时长 | 30.0 s（900 帧） |
| 编码 | H.264（CRF 18）+ AAC 48 kHz 立体声 BGM（-14 LUFS） |
| 大小 | ≈ 1.4 MB |

关键帧截图见 `keyframes/`（每场景 2–5 张 + 转场帧）。

## 场景时间轴（帧号 @30fps）

| 场景 | 绝对帧 | 内容 |
| --- | --- | --- |
| Scene 1 基础滑块 | 0 – 179 | 轨道线从中心展开 → 端点 spring 弹入 → 白色手柄滑至中央 → `Opacity` 标签；随后 50%→100%→0%→50% 演示（interpolate + 回弹） |
| 转场 fade 15f | 165 – 179 | 与 Scene 2 交叉淡入 |
| Scene 2 三级滑块 | 165 – 419 | 后方 40px 处弹入第二/三层轨道（#222/#111），细竖线连接暗示纵深；级联滑动 70/45/90%（15 帧错峰）；整体上浮 -10px |
| 转场 dissolve 20f | 400 – 419 | 自定义“溶解 + 自上方滑入”（blur + 位移 + 淡入） |
| Scene 3 液态玻璃 | 400 – 719 | 三层融化为一枚半透明胶囊（backdrop-filter blur）；玻璃球手柄：放大折射内景、RGB 边缘色散、左上高光弧、60 帧呼吸；黏滞滑动 + squash & stretch + 果冻回弹；液体波纹填充随速度起伏；≤18 颗微粒漂浮 |
| Scene 4 定格收束 | 720 – 899 | 硬切无缝延续（phase 偏移 320）；1.0→1.03→1.0 完成脉冲；粒子缓慢淡出；末 30 帧整体呼吸（opacity 1→0.95→1） |

`TransitionSeries` 总时长 = 180 + 255 + 320 + 180 − 15 − 20 = **900 帧**。

## 运行

```bash
npm install

# 交互预览（Remotion Studio）
npm run dev

# 渲染成片（含 public/bgm.mp3 音轨）
npm run render          # -> output.mp4

# 逐场景渲染静帧自检（每个场景都是独立 composition）
npx remotion still Scene1 keyframes/s1.png --frame=120
npx remotion render Scene1 out/s1 --frames=8,50,80,170 --image-format=png
npx remotion compositions
```

## 项目结构

```
src/
  constants.ts            全部设计常量（颜色/布局/时长，单一可调入口）
  util.ts                 interpolate 封装、spring、衰减回弹、确定性 PRNG
  Video.tsx               主时间轴：TransitionSeries + 转场 + BGM
  Root.tsx                注册 UIEvolution + Scene1..4 五个 composition
  transitions/
    dissolveFromTop.tsx   自定义转场（custom presentation 契约）
  scenes/
    Scene1BasicSlider.tsx 基础滑块拼装与演示
    Scene2ThreeLayer.tsx  三层景深与级联
    Scene3LiquidGlass.tsx 融化 + 液态玻璃材质化
    Scene4Finale.tsx      定格、脉冲、呼吸收束
  components/
    Stage.tsx             1080×1920 逻辑舞台（STAGE_SCALE 统一缩放）
    SliderTrack.tsx       扁平轨道 + 填充 + 端点
    SliderThumb.tsx       扁平手柄
    ThreeLayerStack.tsx   三层堆叠 + 纵深连接线
    GlassSlider.tsx       液态玻璃滑块总装（胶囊 + 玻璃球 + 标签 + SVG 滤镜）
    CapsuleContent.tsx    液体波纹填充（真实轨道与折射内景共用）
    GlassThumb.tsx        玻璃球：折射/色散/高光/呼吸/果冻
    ParamLabel.tsx        标准参数名标签
    ParticleField.tsx     确定性漂浮粒子（种子驱动，逐帧可复现）
public/bgm.mp3            合成氛围 BGM（100 BPM half-time，与场景同步）
keyframes/                关键帧截图
.agents/skills/           Remotion 官方 Agent Skills（npx skills add remotion-dev/skills）
.mcp.json                 remotion-mcp 服务器配置（渲染/静帧/脚手架能力）
```

## 规范落实

- 所有动画由 `useCurrentFrame() + interpolate() / spring()` 驱动，**零 CSS transition/animation**，逐帧渲染确定可复现（粒子/星尘由固定种子生成）。
- 入场 `Easing.out(Easing.cubic)`，退场 `Easing.in`；场景拼装用 `TransitionSeries`，1→2 为 `fade()`，2→3 为自定义溶解转场，3→4 为同状态硬切（像素级验证：相邻帧均差 0.005）。
- 玻璃效果采用“SVG filter（feDisplacementMap 湍流扭曲）+ backdrop-filter + 通道拆分色散”组合方案，保留边缘折射与 RGB 色散；无任何外部生成素材。
- 颜色 / 尺寸 / 时长全部集中在 `src/constants.ts` 的常量对象中。
- 视觉自检流程：逐场景渲染静帧 → VLM 审帧（构图/裁切/伪影/玻璃质感）→ 全片渲染 → 抽帧复查 + ffprobe 质检，全部通过。

## Agent 能力配置说明

- 已安装 Remotion 官方 Agent Skills（`.agents/skills/`，12 个，含 best-practices / markup / render 等），作为编码与渲染的行为准则。
- `.mcp.json` 配置了社区 `remotion-mcp` 服务器（Claude Code / Cursor 等可据此直接渲染 MP4 / 静帧 / GIF 并以 job ID 追踪长任务）。
- 社区 Skill `launch-video`（broomva）安装失败：仓库不存在或已转私有（GitHub 返回认证要求）。其“纯黑 void 背景、内容悬浮、无渐变基底、spring 动画”的液态玻璃视觉原则已按其公开描述在本项目中直接落实，未调用任何外部素材生成管线。

## 音频

`public/bgm.mp3` 为 numpy/scipy 纯合成的极简氛围乐（100 BPM half-time：
柔和底鼓 + sub bass + 玻璃质感 FM 拨音 + 高频星尘 + riser + 卷积混响 + 轻侧链），
与视觉节点（元素出现 0.0/0.55/1.17/1.83s、层级 6.0/6.5s、级联 8.0/8.5/9.0s、
溶解 13.3s、液态滑动 16.0–22.6s、定格脉冲 24.0s）逐一对齐，响度 -14 LUFS / -1.5 dBTP。
若需无声版本，删除 `src/Video.tsx` 中的 `<Audio>` 行后重新 `npm run render` 即可。
