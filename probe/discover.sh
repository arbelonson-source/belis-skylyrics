#!/bin/bash
# discover.sh CPFILE : looks up where the 1.21.11 names our mod uses went in this game version.
# Prints class-name searches over every jar on the client compile classpath, then filtered javap of key classes.
cpfile="$1"
cp=$(paste -sd: "$cpfile")
jars=$(grep '\.jar$' "$cpfile")
all=$(mktemp)
for j in $jars; do unzip -Z1 "$j" 2>/dev/null | grep '\.class$' | grep -v '\$[0-9]' | sed "s#^#$(basename "$j")!#"; done > "$all"
echo "== classpath jars (basenames) =="
for j in $jars; do basename "$j"; done | grep -iE 'minecraft|fabric-api|rendering|key|lwjgl|sdl|glfw|blaze|joml' | tr '\n' ' '; echo
search() { echo "== classes matching /$1/ =="; grep -E "$1" "$all" | sed 's#\.class$##' | head -${2:-40}; }
search '/[A-Za-z]*Graphics[A-Za-z]*\.class' 30
search 'GuiGraphics|GuiRender|gui/GuiGraphics' 10
search '(LightTexture|Lightmap|LightCoords|FullBright)' 20
search 'FilterMode|textures/Filter' 10
search 'GpuBufferSlice' 10
search 'fabric/api/client/rendering/v1/[^/]*/?[A-Za-z]*\.class' 60
search 'fabric/api/client/(keybinding|keymapping|key)' 20
search 'org/lwjgl/(glfw|sdl)/[A-Z][A-Za-z]*\.class' 8
search 'net/minecraft/client/(gui/)?[A-Za-z]*Screen(Manager|Stack|s)\.class' 20
search 'client/resources/language/' 10
search 'ConfirmLinkScreen' 5
jp() { echo "== javap $1 ${2:+(filtered /$2/)} =="; if [ -n "$2" ]; then javap -public -cp "$cp" "$1" 2>&1 | grep -E "$2|^(public |Error)" | head -${3:-60}; else javap -public -cp "$cp" "$1" 2>&1 | head -${3:-80}; fi; }
jp net.minecraft.client.gui.screens.Screen 'render|extract|Graphics|minecraft' 30
jp net.minecraft.client.Minecraft 'screen|Screen|resize|RenderTarget|gui|options|gameRenderer|Window' 50
jp net.minecraft.client.Options 'hide|Gui|gui' 15
jp net.minecraft.client.gui.Gui 'Overlay|overlay|Boss|boss|Message' 20
jp net.minecraft.client.renderer.GameRenderer 'Camera|camera' 15
jp net.minecraft.client.resources.language.I18n '' 20
jp net.minecraft.client.gui.screens.ConfirmLinkScreen 'confirmLink' 10
jp com.mojang.blaze3d.vertex.PoseStack 'mulPose|rotate' 10
jp com.mojang.blaze3d.platform.InputConstants\$Type '' 20
jp net.minecraft.client.KeyMapping 'KeyMapping\(|Category' 20
jp com.mojang.blaze3d.systems.RenderSystem 'Sampler|sampler' 10
jp net.minecraft.client.renderer.LevelRenderer 'renderLevel|render|extract|Sky|sky' 30
jp net.minecraft.client.gui.Font 'drawInBatch|width|split|lineHeight|prepareText' 30
jp net.minecraft.client.renderer.RenderPipelines 'GUI|TEXT' 40
jp net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry '' 20
jp net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement '' 20
for c in $(grep -E 'fabric/api/client/rendering/v1/(level|world)/[A-Za-z]*(Events|Context)\.class' "$all" | sed 's#.*!##; s#\.class$##; s#/#.#g' | sort -u); do jp "$c" '' 60; done
for c in $(grep -E 'fabric/api/client/(keybinding|keymapping)/v1/[A-Za-z]*\.class' "$all" | sed 's#.*!##; s#\.class$##; s#/#.#g' | sort -u); do jp "$c" '' 20; done
for c in $(grep -E '/[A-Za-z]*Graphics[A-Za-z]*\.class' "$all" | grep 'net/minecraft/client/gui' | sed 's#.*!##; s#\.class$##; s#/#.#g' | sort -u | head -3); do jp "$c" 'fill|blit|drawString|text|pose|guiWidth|submit|enableScissor|nextStratum|Text' 70; done
