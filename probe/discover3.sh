#!/bin/bash
# discover3.sh CPFILE : third round (26.3 Hud, hideGui, main render target; camera matrices for the mixin).
cpfile="$1"
cp=$(paste -sd: "$cpfile")
all=$(mktemp)
for j in $(grep '\.jar$' "$cpfile"); do unzip -Z1 "$j" 2>/dev/null | grep '\.class$' | grep -v '\$[0-9]' | sed "s#^#$(basename "$j" | sed 's/-[0-9a-f]\{10\}-/-/')!#"; done > "$all"
search() { echo "== classes /$1/ =="; grep -E "$1" "$all" | grep -vE '^(oshi|lwjgl-vulkan)' | sed 's#\.class$##' | head -${2:-30}; }
jp() { echo "== javap $1 ${2:+/$2/} =="; if [ -n "$2" ]; then javap ${3:--public} -cp "$cp" "$1" 2>&1 | grep -E "$2|^(public |abstract |class |final |Error)" | head -50; else javap ${3:--public} -cp "$cp" "$1" 2>&1 | grep -v '^Compiled' | head -80; fi; }
search '/(RenderTarget|MainTarget|[A-Za-z]*RenderTargets?)\.class' 20
jp net.minecraft.client.gui.Hud ''
jp net.minecraft.client.Minecraft '[Tt]arget|gameRenderer|options|[Hh]ide|gameDirectory|mouseHandler|level|player|getTextureManager'
jp net.minecraft.client.renderer.GameRenderer '[Tt]arget|[Hh]ide|[Gg]ui|[Cc]amera'
jp net.minecraft.client.Options '[Hh]ide|[Hh]ud|[Gg]ui' -p
jp net.minecraft.client.renderer.state.level.CameraRenderState '' -p
jp net.minecraft.client.renderer.LevelRenderer 'renderLevel|void render\(' -p
jp net.minecraft.client.gui.screens.ConfirmLinkScreen 'confirmLinkNow'
jp net.minecraft.client.gui.components.Button 'builder|Button\('
jp net.minecraft.client.gui.screens.options.controls.KeyBindsScreen 'KeyBindsScreen\('
jp net.minecraft.client.MouseHandler 'getScaled'
