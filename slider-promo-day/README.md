# Liquid Glass Slider — Day Edition · 22 秒 9:16 白天版 UI 进化视频（Remotion）

姊妹篇：[`slider-promo`](../slider-promo)（30s 纯黑版）。
同一套"参数名叙事"的白天版：纯白 void 背景上，一枚滑块从**积木拼装 → 液态玻璃 → 五档神经玻璃**逐级进化。
纯代码生成（Remotion + React），无旁白无字幕无音轨，
画面中只出现标准参数名（Opacity / Brightness / Blur / Liquid Glass / Neural Glass）。

**抖音发布文案建议**：人们应常常不敢涉及UI而感到困扰

---

## 成片

| 项目 | 值 |
| --- | --- |
| 文件 | `output.mp4` |
| 尺寸 / 帧率 | 1080 × 1920（9:16 竖屏）@ **60fps** |
| 时长 | **22.0 s**（1320 帧） |
| 编码 | H.264 **CRF 10（视觉无损）** + x264 `slow` 预设，PNG 无损帧管线，无音轨（抖音内配乐） |
| 大小 | ≈ 1.7 MB（95% 纯白画面的压缩红利；UI 元素边缘逐帧核验无压制损失） |

关键帧截图见 `keyframes/`（每场景 3–5 张 + 转场帧 + 收尾帧）。

## 场景时间轴（帧号 @60fps，绝对帧）

| 场景 | 绝对帧 | 内容 |
| --- | --- | --- |
| Scene 1 积木拼装 | 0 – 389 | 8 块带 Stud 凸点的轨道积木依次坠落卡合（spring + 落地挤压）→ 端板弹入 → 方块手柄（带双凸点）加速坠落、落地冲击环 → `Opacity` 标签；演示 8→100→0→50→68→50，积木被滑块逐块"点亮"（离散填色 + 经过的积木微鼓包） |
| 转场 fade 24f | 366 – 389 | 与 Scene 2 交叉淡入 |
| Scene 2 液态玻璃 | 366 – 695 | 积木停留后溶解，玻璃胶囊从"积木线"熔胀而出（scaleY spring 0.16→1）；方块手柄 ↔ 玻璃球交接（折射 + 边缘虹彩）；三段材质拍子：`BRIGHTNESS`（填充亮至白金）→ `BLUR`（内部液体高斯失焦再回敛）→ `LIQUID GLASS`（黏滞回滑 + 果冻 ringdown） |
| 转场 dissolve-rise 30f | 666 – 695 | 自定义"溶解 + 自下方升起"（blur + 位移 + 淡入） |
| Scene 3 五档神经玻璃 | 666 – 1319 | 胶囊展宽 460→600，分隔线 + 5 个档位方块弹入；方块电平表（5×3 小格）与 1–5 编号徽章组装；手柄逐档右滑：**OPACITY → BRIGHTNESS → BLUR → LIQUID GLASS → NEURAL GLASS**，主题色由石墨灰逐档升温至琥珀金；L5 点火：琥珀辉光泛起、胶囊内 12×2 LED 矩阵与舱外 26 颗小方块开始"闪电律动"（行波 + 确定性随机回燃）、终端风 `> neural_glass --level 5` + 块状光标闪烁；完成脉冲 + 呼吸收尾，结束帧保持点亮不淡出 |

`TransitionSeries` 总时长 = 390 + 330 + 654 − 24 − 30 = **1320 帧**。

## 运行

```bash
npm install

# 交互预览（Remotion Studio）
npm run dev

# 渲染成片（CRF 10 视觉无损，PNG 帧管线，无声）
npm run render          # -> output.mp4

# 逐场景渲染静帧自检（每个场景都是独立 composition）
npx remotion still Scene1 keyframes/scene1-check.png --frame=140
npx remotion compositions
```

## 项目结构

```
src/
  constants.ts            全部设计常量（颜色/布局/时长/五档主题色，单一可调入口）
  util.ts                 interpolate 封装、spring、衰减回弹、确定性 PRNG、hex 调色
  Video.tsx               主时间轴：TransitionSeries + fade / dissolve-rise 转场（无音轨）
  Root.tsx                注册 UIEvolutionDay + Scene1..3 四个 composition
  transitions/
    dissolveRise.tsx      自定义转场（custom presentation 契约，白天版"上升溶解"）
  scenes/
    Scene1Blocks.tsx      积木拼装与演示（scene1Value 纯函数时间线）
    Scene2Glass.tsx       熔胀成胶囊 + 三段材质拍子（scene2Value 纯函数时间线）
    Scene3Levels.tsx      五档滑块、逐档换色、L5 闪电律动与收束
  components/
    Stage.tsx             1080×1920 逻辑舞台（STAGE_SCALE 统一缩放）
    BlocksTrack.tsx       带 Stud 凸点的轨道积木（离散点亮 + 经过微鼓包）
    BlockThumb.tsx        方块手柄（白面描边 + 双凸点 + 落地挤压）
    GlassCapsule.tsx      白天版液态玻璃胶囊（波纹液体填充 + 内部模糊 + 高光/阴影）
    GlassBall.tsx         玻璃球手柄（折射/边缘虹彩/高光/呼吸/果冻，主题色染色）
    FilterDefsDay.tsx     SVG 滤镜（湍流扭曲位移，白天版独立参数）
    LevelMeter.tsx        5×3 方块电平表（逐列点亮 + L5 脉冲）
    LedStrip.tsx          胶囊内 12×2 LED 矩阵（闪电律动）
    NeonField.tsx         舱外 26 颗闪电律动小方块（行波 + 确定性回燃）
keyframes/                关键帧截图
```

## 设计规范落实

- 所有动画由 `useCurrentFrame() + interpolate() / spring()` 驱动，**零 CSS transition/animation**，逐帧渲染确定可复现（闪电方块由固定种子 PRNG + 行波函数生成，逐帧可复现）。
- 入场 `Easing.out(Easing.cubic)`，退场 `Easing.in`；场景拼装用 `TransitionSeries`，1→2 为 `fade()`，2→3 为自定义"上升溶解"转场。
- 白天版玻璃方案：纯白背景上以"发丝级描边 + 柔和投影 + 内部染色液体 + 顶部高光弧"表达玻璃进深（iOS 浅色 Liquid Glass 思路），保留 `feDisplacementMap` 湍流折射与红/蓝边缘虹彩，无任何外部素材。
- 五档主题色为单一路径暖化色阶：`#7D8590 → #A89A85 → #C9A96A → #F0B24A → #FFB020`（deep 变体保证白底文字对比度），避开常见霓虹青紫与 Claude 橙，末档为琥珀暖光。
- 颜色 / 尺寸 / 时长全部集中在 `src/constants.ts`。
- 视觉自检流程：逐场景静帧渲染 → 审帧（构图/居中/配色/伪影，共修复 7 处：下落缓动、积木对比度、Stud 贴合、弹簧下坠越界、标签居中、色散偏粉、徽章对比度）→ 全片渲染 → ffprobe 质检（22.000s / 60fps / 1320 帧）+ 成片抽帧复核。

## 与 30s 黑夜版（slider-promo）的关系

- 复用其"标准参数名叙事 + 纯函数时间线 + TransitionSeries 拼装"的工程范式；
- 新增积木拼装语言（Stud 凸点）、五档位交互隐喻（类似思考程度调节）、L5 闪电律动小方块与终端风点睛；
- 背景由纯黑 void 反转为纯白 void，玻璃表达由"发光"改为"描边 + 投影 + 内染色"。

## 抖音发布建议

- 文案：人们应常常不敢涉及UI而感到困扰
- 视频**无声**，建议在抖音 App 内配热门 BGM；L5 点火（约第 16.8s）与节拍_drop 对齐效果最佳。
- 60fps 上传可保留全部帧；如需二创，CRF 10 母带导入剪映无画质损失。
