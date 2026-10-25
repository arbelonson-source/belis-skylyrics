#!/bin/bash
# discover2.sh CPFILE : second round of lookups (where screen/hideGui/GLFW/RenderSystem/... went; mixin targets).
cpfile="$1"
cp=$(paste -sd: "$cpfile")
all=$(mktemp)
for j in $(grep '\.jar$' "$cpfile"); do unzip -Z1 "$j" 2>/dev/null | grep '\.class$' | grep -v '\$[0-9]' | sed "s#^#$(basename "$j" | sed 's/-[0-9a-f]\{10\}-/-/')!#"; done > "$all"
search() { echo "== classes /$1/ =="; grep -E "$1" "$all" | grep -vE '^(oshi|lwjgl-vulkan)' | sed 's#\.class$##' | head -${2:-40}; }
jp() { echo "== javap $1 ${2:+/$2/} =="; if [ -n "$2" ]; then javap ${3:--public} -cp "$cp" "$1" 2>&1 | grep -E "$2|^(public |abstract |class |final |Error)" | head -60; else javap ${3:--public} -cp "$cp" "$1" 2>&1 | grep -v '^Compiled' | head -70; fi; }
search '/(RenderSystem|SamplerCache|NativeImage|PoseStack|VertexConsumer|GraphicsResourceAllocator|InputConstants|Screenshot|BossHealthOverlay|LerpingBossEvent|FontTexture|GlyphRenderTypes|AbstractTexture|DynamicTexture|RenderTypes|SubmitNodeCollector|Language|FilterMode|GpuBufferSlice|RenderPipeline)\.class' 60
search 'net/minecraft/client/[A-Za-z/]*(ScreenManager|Screens|ScreenStack|Hud|HudState)\.class' 20
jp net.minecraft.client.Minecraft '[Ss]creen|[Gg]ui|[Hh]ud|[Oo]verlay|[Cc]amera|Target|resize|Window|getInstance'
jp net.minecraft.client.gui.Gui ''
jp net.minecraft.client.Options '[Hh]ide|[Hh]ud|[Gg]ui'
jp net.minecraft.client.resources.language.I18n ''
jp net.minecraft.locale.Language 'has|getInstance'
jp com.mojang.blaze3d.platform.InputConstants 'KEY_J|KEY_K|getKey|Key '
jp net.minecraft.util.LightCoordsUtil 'FULL|BRIGHT|static final'
jp net.minecraft.client.renderer.LightCoordsUtil 'FULL|BRIGHT'
for c in $(grep -E 'rendering/v1/level/LevelRenderEvents\$[A-Za-z]*\.class' "$all" | sed 's#.*!##; s#\.class$##; s#/#.#g' | sort -u | grep -E 'CollectSubmits|StartMain|EndMain|AfterSolid|AfterTranslucentFeatures'); do jp "$c" ''; done
jp net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ''
jp net.minecraft.client.renderer.SubmitNodeCollector 'submitText|submitCustomGeometry'
jp net.minecraft.client.renderer.OrderedSubmitNodeCollector 'submitText|submitCustomGeometry'
jp net.minecraft.client.gui.font.FontTexture '' -p
jp net.minecraft.client.gui.components.BossHealthOverlay 'events|Map' -p
jp net.minecraft.client.renderer.texture.AbstractTexture 'sampler|Sampler' -p
jp net.minecraft.client.renderer.texture.DynamicTexture 'DynamicTexture\('
jp net.minecraft.client.Camera 'Fluid|attributeProbe|position|rotation'
jp net.minecraft.client.Screenshot 'grab'
jp net.minecraft.client.renderer.rendertype.RenderTypes 'lightning|text'
jp net.minecraft.client.renderer.LevelRenderer 'render|extract' -p
jp com.mojang.blaze3d.systems.RenderSystem 'Sampler|isOnRenderThread'
jp net.minecraft.client.KeyMapping\$Category 'register|Category\('
jp net.minecraft.client.gui.screens.Screen 'minecraft|font|width|onClose|init|keyPressed|mouseClicked|mouseScrolled|isPauseScreen|renderBackground|extract'
