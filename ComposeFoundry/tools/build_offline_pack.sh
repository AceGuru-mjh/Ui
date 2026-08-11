#!/usr/bin/env bash
# 生成 ComposeFoundry 真实 Compose 重型渲染离线包（B 类）。
#
# 前置（在装有 Android SDK 的机器 / Git Bash / WSL 中运行）：
#   - 设置环境变量 ANDROID_HOME 指向 Android SDK（脚本会选用其 build-tools 下最新版 d8）
#   - 具备 curl、unzip、bash
#   - 网络可访问 Maven Central（repo1.maven.org）
#
# 步骤：
#   1) 先构建 Compose 渲染插件，得到它的 classes.jar：
#        ./gradlew :plugins:plugin-compose-dsl:assembleRelease
#        产出 plugins/plugin-compose-dsl/build/intermediates/aar_main_jar/release/classes.jar
#   2) 运行本脚本，把 compose 库 AAR + 插件 classes.jar 收集进 FoundrySDK/<id>/libs/*.dex
#
# 用法：
#   ./build_offline_pack.sh <sdkId> <version> <entryClass> <maven坐标...>
# 示例：
#   ./build_offline_pack.sh com.foundry.sdk.compose_runtime 1.6.0 \
#     com.foundry.plugin.composedsl.ComposeDslRenderEngine \
#     androidx.compose.runtime:compose-runtime:1.6.0 \
#     androidx.compose.ui:ui:1.6.0 \
#     androidx.compose.foundation:foundation:1.6.0 \
#     androidx.compose.material3:material3:1.2.0 \
#     androidx.compose.animation:animation:1.6.0
set -euo pipefail

SDK_ID="${1:?用法: $0 <sdkId> <version> <entryClass> <坐标 group:artifact:version ...>}"
VER="${2:?}"
ENTRY="${3:?}"
shift 3

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/FoundrySDK/$SDK_ID"
LIBS="$OUT/libs"
mkdir -p "$LIBS"

# 选取 build-tools 里最新版本的 d8
BT_DIR="$ANDROID_HOME/build-tools"
D8="$BT_DIR/$(ls "$BT_DIR" | sort -V | tail -1)/d8"
[ -x "$D8" ] || { echo "错误: 找不到 d8，请设置 ANDROID_HOME 指向 Android SDK"; exit 1; }

# 1) 内置 Compose 渲染插件（来自 plugin-compose-dsl 构建产物）
PLUGIN_JAR="$ROOT/plugins/plugin-compose-dsl/build/intermediates/aar_main_jar/release/classes.jar"
if [ -f "$PLUGIN_JAR" ]; then
  "$D8" --output "$LIBS/plugin-composedsl.dex" "$PLUGIN_JAR"
  echo "已打包插件: $LIBS/plugin-composedsl.dex"
else
  echo "警告: 找不到 $PLUGIN_JAR，请先执行 ./gradlew :plugins:plugin-compose-dsl:assembleRelease"
fi

# 2) 每个 Maven 坐标: group:artifact:version -> 下载 AAR -> classes.jar -> d8
for coord in "$@"; do
  IFS=':' read -r g a v <<< "$coord"
  path="${g//.//}/$a/$v/$a-$v.aar"
  url="https://repo1.maven.org/maven2/$path"
  tmp="$(mktemp -d)"
  echo "下载 $url"
  curl -fL "$url" -o "$tmp/a.aar"
  unzip -o "$tmp/a.aar" classes.jar -d "$tmp" >/dev/null
  "$D8" --output "$LIBS/$a.dex" "$tmp/classes.jar"
  rm -rf "$tmp"
  echo "已打包: $LIBS/$a.dex"
done

# 3) 生成 foundry-pack.json（libraries 列出 libs/ 下所有 dex，相对 pack 根）
mapfile -t DEXES < <(ls "$LIBS" | sed 's/.*/"libs\/&"/')
LIBS_JSON="$(IFS=,; echo "${DEXES[*]}")"
cat > "$OUT/foundry-pack.json" <<JSON
{
  "id": "$SDK_ID",
  "version": "$VER",
  "type": "VIEW_RENDERER",
  "entryClass": "$ENTRY",
  "libraries": [ $LIBS_JSON ],
  "minCoreVersion": "0.2.0",
  "minSdk": 26
}
JSON

echo ""
echo "离线包已生成: $OUT (libs: $(ls "$LIBS" | wc -l) 个 dex)"
echo "下一步（真机）: adb push $OUT /storage/emulated/0/Android/data/<pkg>/files/FoundrySDK/"
echo "  :renderer 进程会经 LocalSdkScanner 扫描该目录，按 libraries[] 用 DexClassLoader 加载并渲染。"
