# Thinking Slider — 22 秒 9:16 日间模式 UI 进化视频（Remotion）

同一枚滑块从 **M3 积木拼装 → 液态玻璃 → 五档 THINKING 滑块**的进阶过程，
在 ULTRA 档以全彩霓虹闪电律动小方块收束。
纯代码生成（Remotion + React），冷浅灰白日间底色（#F5F7FA），无旁白无字幕、无音轨
（音乐在抖音端配），画面中只出现标准参数名与档位名
（OPACITY / LIQUID GLASS / THINKING / OFF / LOW / MID / HIGH / ULTRA）。

**抖音发布文案建议**：人们应常常不敢涉及UI而感到困扰

---

## 成片

| 项目 | 值 |
| --- | --- |
| 文件 | `output.mp4` |
| 尺寸 / 帧率 | 1080 × 1920（9:16 竖屏）@ **60fps** |
| 时长 | 22.0 s（1320 帧） |
| 编码 | H.264（**CRF 8 视觉无损**，PNG 中间帧）· 无音轨 |
| 底色 | 冷浅灰白 #F5F7FA（日间模式） |
| 大小 | ≈ 15.7 MB（约 5.7 Mbps） |

关键帧截图见 `keyframes/`（三场景 + 闪电瞬间 + 收束）。

## 设计语言

### Material 3（场景 1）
- **官方基线亮色 tonal 色板**（source #6750A4）：`primary #6750A4`、
  `surfaceContainerHighest #E6E0E9`、`inverseSurface #322F35` value indicator
- **积木式拼装**：轨道块 → 填充块 → 手柄块 → 标签 → 数值气泡，逐块 spring 落位，
  落地 squash 回弹，手柄落地时已落位组件轻微震颤（snap 手感）
- M3 滑块规范特征：圆端轨道、圆形手柄 + 白色 stop-indicator 圆点、深色药丸形数值指示器

### Liquid Glass（场景 2）
- 液态玻璃胶囊：backdrop blur + 半透明白 + 1.5px 高光描边 + 顶部镜面反光
- 玻璃球手柄：**放大折射内景（1.18×）+ feDisplacementMap 液体扭曲 +
  RGB 通道拆分色散（±1.6px）+ 左上高光弧 + 镜面耀斑 + 呼吸**
- 液体波纹填充随速度起伏；黏滞 squash & stretch + 果冻回弹
- 环境光场（M3 container tonal 光斑）作为玻璃折射的"背后内容"，无粒子等杂元素

### 五档 THINKING 滑块（场景 3）
- 广泛使用的"强度分档"控件形态：宽胶囊轨道 + 4 枚档位刻度点 +
  5 个档位标签（OFF / LOW / MID / HIGH / ULTRA）+ 药丸手柄逐档扫过
- **颜色逐档渐变**：冷灰 → 蓝 → 紫 → 品红 → ULTRA 全彩循环（fill / 手柄 /
  大号档位词 / 刻度高亮全部随档位联动）
- **ULTRA 霓虹闪电律动小方块**：9×2 全彩霓虹方格，闪电以不规则节律自左向右扫过
  （确定性 PRNG 种子驱动，白热核心 + 双层辉光 + 余晖衰减），收尾渐归平静
- Claude Code 氛围致敬但零元素抄袭：无终端图形、无原文案，仅取"律动方块"神韵

## 场景时间轴（帧号 @60fps）

| 场景 | 绝对帧 | 内容 |
| --- | --- | --- |
| Scene 1 M3 积木拼装 | 0 – 323 | 轨道块落位 → 填充块滑入 → 手柄块弹落（squash+震颤）→ OPACITY 标签 → 50% 数值气泡；随后 50→85→30→50 拖动演示 |
| 转场 fade 24f | 300 – 323 | 与 Scene 2 交叉淡入 |
| Scene 2 液态玻璃 | 300 – 689 | M3 滑块溶解（blur+上浮）→ 玻璃胶囊材质化（折射/色散/高光/环境光场）→ 黏滞滑动 92→24→60%（果冻回弹） |
| 转场 dissolve 30f | 660 – 689 | 自定义"溶解 + 自上方滑入" |
| Scene 3 五档 THINKING | 660 – 1319 | 胶囊展宽变形 + 玻璃球回位成药丸手柄 → LOW → MID → HIGH → ULTRA 逐档扫过（连续变色）→ 300f 起霓虹方格爆发，318–596f 不规则闪电扫掠 → 616f 起风暴平息，呼吸收束 |

`TransitionSeries` 总时长 = 324 + 390 + 660 − 24 − 30 = **1320 帧 = 22.0 s**。

## 运行

```bash
npm install

# 交互预览（Remotion Studio）
npm run dev

# 渲染成片（无声；CRF 0 近无损）
npm run render          # -> output.mp4

# 逐场景渲染静帧自检（每个场景都是独立 composition）
npx remotion still Scene1 keyframes/s1.png --frame=145
npx remotion still Scene3 keyframes/s3.png --frame=520 --image-format=png
npx remotion compositions
```

## 项目结构

```
src/
  constants.ts              全部设计常量（M3/日间色板、五档色阶、布局、时长）
  util.ts                   interpolate 封装、spring、衰减回弹、确定性 PRNG、档位配色
  Video.tsx                 主时间轴：TransitionSeries + 转场（无 Audio，无声成片）
  Root.tsx                  注册 ThinkingSliderDay + Scene1..3 四个 composition
  transitions/
    dissolveFromTop.tsx     自定义转场（custom presentation 契约）
  scenes/
    Scene1Blocks.tsx        M3 积木拼装与拖动演示
    Scene2LiquidGlass.tsx   溶解 + 液态玻璃材质化 + 黏滞演示
    Scene3Thinking.tsx      五档滑块扫过 + ULTRA 霓虹闪电收束
  components/
    Stage.tsx               1080×1920 逻辑舞台（STAGE_SCALE 统一缩放）
    M3BlockSlider.tsx       M3 亮色滑块（积木式逐块落位）
    GlassSliderDay.tsx      日间液态玻璃滑块总装（胶囊 + 玻璃球 + 标签 + SVG 滤镜）
    GlassThumb 内建于 GlassSliderDay（折射/色散/高光/果冻）
    CapsuleFill.tsx         彩色液体波纹填充（真实轨道与折射内景共用）
    ThinkingSlider.tsx      五档滑块（刻度点/药丸手柄/档位联动配色/彩虹渐变）
    LightningGrid.tsx       霓虹闪电律动方格（种子驱动，逐帧可复现）
    AmbientBlobs.tsx        环境光场（玻璃折射背景，缓慢漂移）
    ParamLabel.tsx          标准参数名标签
keyframes/                  关键帧截图
.agents/skills/             Remotion 官方 Agent Skills（npx skills add remotion-dev/skills）
.mcp.json                   remotion-mcp 服务器配置
```

## 规范落实

- 所有动画由 `useCurrentFrame() + interpolate() / spring()` 驱动，**零 CSS transition/animation**，
  逐帧渲染确定可复现（闪电节律 / 光斑漂移 / 闪烁全部由固定种子生成）。
- 场景拼装用 `TransitionSeries`，1→2 为 `fade()`，2→3 为自定义溶解转场。
- 玻璃效果沿用仓库验证方案："SVG filter（feDisplacementMap 湍流扭曲）+ backdrop-filter +
  通道拆分色散"，保留边缘折射与 RGB 色散；无任何外部生成素材。
- 颜色 / 尺寸 / 时长全部集中在 `src/constants.ts` 的常量对象中。
- 视觉自检流程：逐场景渲染静帧 → VLM 审帧（构图/裁切/伪影/玻璃质感/M3 规范/霓虹辉光）
  → 修复复审 → 全片渲染 → ffprobe 质检。
