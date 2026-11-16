package dev.beli.skylyrics.render;

/**
 * Everything a laid-out top bar line depends on. Two keys are equal only when the layout would come out the same:
 * a different text, font object, style, text scale, language (the game's own text ordering), GUI scale or resource
 * reload (a font that changed under the same name) gives a new layout. The line's width is not part of it: measuring
 * the whole line each frame just to build a key cost more than the layout it saved.
 *
 * @param font the game's Font object
 * @param style the text's Style
 * @param language the game's current Language object, replaced when the language changes
 * @param reloads how many resource reloads have been seen
 */
record LineKey(String text, Object font, Object style, float scale, Object language, double guiScale, long reloads) {}
