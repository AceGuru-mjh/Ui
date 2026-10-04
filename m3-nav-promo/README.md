# Material 3 圆形导航栏 → 液态玻璃 — 30 秒 9:16 UI 进化视频（Remotion）

一枚 **Material 3 圆形导航栏**（环形轨道 + 5 个圆形导航项 + FAB Menu 式中心枢纽）
从**基础拼装 → 同心层级 → 液态玻璃 → 收束定格**的进阶过程。
纯代码生成（Remotion + React），纯黑 void 背景，无旁白无字幕、无水印，
画面仅出现导航图标（Material Symbols 规范路径）。

**抖音发布文案建议**：人类应常常不敢设计ui而感到困扰

---

## 成片

| 项目 | 值 |
| --- | --- |
| 文件 | `output.mp4` |
| 尺寸 / 帧率 | 1080 × 1920（9:16 竖屏）@ 30fps |
| 时长 | 30.0 s（900 帧） |
| 编码 | H.264（CRF 18）+ AAC 48 kHz 立体声 BGM（-14 LUFS） |
| 大小 | ≈ 4.0 MB |

关键帧截图见 `keyframes/`（每场景 4–6 张 + 转场帧）。

## 设计语言

### Material 3（场景 1–2）
- **官方基线暗色 tonal 色板**（source #6750A4）：`primary #D0BCFF`、
  `primaryContainer #4F378B`、`secondaryContainer #4A4458`、
  surface-container 阶梯（#211F26 / #2B2930）、outline-variant 描边
- **Material Symbols 图标**（home / search / plus / favorite / person / star / mail / play / chat）
- **M3 Expressive 动效**：错峰弹性出场（staggered spring）、移动收拢药丸 +
  停驻绽放光晕的选中指示器、circle↔squircle 形变脉冲、hub 的 M3 press ripple
- **FAB Menu 式中心枢纽**（Material 3 Expressive 官方圆形导航组件精神，
  取代 speed dial）：紫色 primary-container FAB + 辉光 + 到达脉冲
- 同心环层级：tonal elevation 阶梯（外环 high / 内环 container）+ 流动虚线辐条

### Liquid Glass（场景 3–4）
- 液态玻璃圆球：**放大折射内景（1.16×）+ feDisplacementMap 液体扭曲 +
  RGB 通道拆分色散（±1.6px）+ 左上高光弧 + 镜面耀斑 + 60 帧呼吸**
- 玻璃中心透镜（1.3× 放大、±2.2px 色散）作为 FAB hub 的玻璃形态
- 熔化轨道：双谐波正弦波纹圆环，振幅随指示器速度响应
- 液态物理：黏滞 squash & stretch、果冻回弹、经过项涟漪、内环被 hub 吸收合并
- 环境光场（M3 primary/tertiary/secondary tonal 光斑）作为玻璃折射的"背后内容"
- ≤18 颗确定性漂浮微粒（种子驱动，tonal 着色）

## 场景时间轴（帧号 @30fps）

| 场景 | 绝对帧 | 内容 |
| --- | --- | --- |
| Scene 1 M3 基础拼装 | 0 – 179 | 环形轨道扫描展开 → 5 个导航项错峰弹性弹出 → 选中指示器在 Home 绽放 → 沿环巡游 Search、Heart（切向挤压 + 果冻到达 + 路过涟漪） |
| 转场 fade 15f | 165 – 179 | 与 Scene 2 交叉淡入 |
| Scene 2 同心层级 | 165 – 419 | 内环 4 项弹出（低 tonal 层级）→ 中心 FAB hub 弹出 + 辉光 → 流动虚线辐条连接层级 → 外环巡游 Person、Home；内环 15 帧错峰级联跟随；hub 双次 M3 ripple；整体上浮 −10px 悬浮 |
| 转场 dissolve 20f | 400 – 419 | 自定义"溶解 + 自上方滑入"（blur + 位移 + 淡入） |
| Scene 3 液态玻璃 | 400 – 719 | 内环被 hub 液态吸收（blobs merging）→ 全部圆形材质熔化为玻璃（折射/色散/高光/背景模糊）→ 玻璃指示器黏滞巡游 Search、Heart、Person → 轨道波纹随速度起伏 |
| Scene 4 收束定格 | 720 – 899 | 硬切无缝延续（phase 偏移 320）；玻璃指示器最后一跳回到 Home；1.0→1.03→1.0 完成脉冲；镜面高光沿玻璃环扫整圈；粒子淡出；末 30 帧整体呼吸（opacity 1→0.95→1） |

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
npx remotion still Scene3 keyframes/s3.png --frame=140 --image-format=png
npx remotion compositions
```

## 项目结构

```
src/
  constants.ts            M3 token、布局、时长（单一可调入口）
  icons.tsx               Material Symbols 规范图标路径
  util.ts                 interpolate 封装、spring、衰减回弹、确定性 PRNG
  Video.tsx               主时间轴：TransitionSeries + 转场 + BGM
  Root.tsx                注册 UIEvolution + Scene1..4 五个 composition
  components/
    Stage.tsx             1080×1920 逻辑舞台（1.25 展示缩放）
    NavItem.tsx           M3 tonal 圆形导航项（选中 crossfade 至容器色）
    ActiveIndicator.tsx   移动收拢/停驻绽放的选中指示器 + M3 ripple
    GlassCircle.tsx       液态玻璃圆（折射/色散/高光）+ 共享 SVG 滤镜
    GlassNavRing.tsx      玻璃导航环总装（波纹轨道/玻璃项/玻璃 hub/巡游 blob）
    AmbientField.tsx      玻璃背后的 M3 tonal 环境光场
    ParticleField.tsx     确定性漂浮微粒
    BackdropSnapshot.tsx  玻璃折射源的完整"背后世界"
    ringGeometry.ts       环形坐标 / 液态波纹路径
  scenes/
    Scene1M3Basic.tsx     基础拼装
    Scene2Hierarchy.tsx   同心层级 + 级联
    Scene3LiquidGlass.tsx 液态玻璃熔化 + 黏滞巡游
    Scene4Finale.tsx      收束定格
  transitions/
    dissolveFromTop.tsx   自定义转场
```

## 工程规范

- 全动画由 `useCurrentFrame() + interpolate()/spring()` 驱动，**零 CSS transition/animation**（逐帧确定性渲染）
- 位移用独立 `translate`/`scale`/`rotate` 属性（不写进 transform 混合）
- 颜色 / 布局 / 时长全部收敛在 `constants.ts` 常量对象
- 每个场景独立 composition，可单独 `remotion still` 静帧自检
- BGM 为纯代码合成（Python），与视觉节点（拼装 0–2s / 层级 5.5–7s / 级联 8–10s /
  riser→溶解 13–14s / 液态巡游 16–22s / 定格脉冲 24s）逐段对齐

## Skills 与 MCP

- `.agents/skills/`：Remotion 官方 Agent Skills
  （`remotion-best-practices` 行为准则、`remotion-docs` API 查询、
  markup / transitions / render 参考链）
- `.mcp.json`：`remotion-mcp` 服务器配置（逐场景静帧渲染自检、job 追踪）
- launch-video 仓库当前不可达（已按其公开视觉原则落实：
  纯黑 void、内容悬浮、无渐变基底、spring 动效）
