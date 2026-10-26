package dev.beli.skylyrics.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** The rows and controls used on the settings screen. All drawing is custom so it can match the mod's look. */
final class Controls {
	private Controls() {
	}

	/** What a row needs to draw one frame. */
	record Frame(GuiGraphicsExtractor g, Font font, int mx, int my, float dt, boolean hoverAllowed) {
	}

	/** A card on a tab: a small title and a group of rows. */
	record Card(String title, List<Row> rows) {
		Card(String title, Row... rows) {
			this(title, List.of(rows));
		}
	}

	/** One line inside a card. Position is assigned by the screen each frame. */
	abstract static class Row {
		final String label;
		@Nullable Supplier<String> desc;
		int x, y, w;
		boolean descClipped;
		private BooleanSupplier shownIf = () -> true;

		Row(String label, @Nullable String desc) {
			this.label = label;
			this.desc = desc == null ? null : () -> desc;
		}

		/** A description that changes, like a status line. */
		Row describe(Supplier<String> text) {
			this.desc = text;
			return this;
		}

		/** Only show this row while {@code condition} is true. */
		Row when(BooleanSupplier condition) {
			this.shownIf = condition;
			return this;
		}

		boolean shown() {
			return shownIf.getAsBoolean();
		}

		int height() {
			return desc == null ? 24 : 32;
		}

		boolean hovered(Frame f) {
			return f.hoverAllowed() && Ui.inside(f.mx(), f.my(), x, y, w, height());
		}

		/** Width the control on the right needs, so the label can be clipped before it. */
		abstract int controlWidth(Font font);

		abstract void renderControl(Frame f);

		void render(Frame f) {
			int textW = w - 20 - controlWidth(f.font()) - 8;
			if (desc == null) {
				f.g().text(f.font(), Ui.visual(Ui.clip(f.font(), label, textW)), x + 10, y + 8, Ui.TEXT, false);
			} else {
				String d = desc.get();
				f.g().text(f.font(), Ui.visual(Ui.clip(f.font(), label, textW)), x + 10, y + 7, Ui.TEXT, false);
				descClipped = f.font().width(d) > textW;
				f.g().text(f.font(), Ui.visual(Ui.clip(f.font(), d, textW)), x + 10, y + 18, Ui.MUTED, false);
			}
			renderControl(f);
		}

		boolean click(double mx, double my) {
			return false;
		}

		void drag(double mx, double my) {
		}

		void release() {
		}

		/** Keyboard input while the mouse is over this row. */
		boolean key(KeyEvent event) {
			return false;
		}

		int centerY() {
			return y + height() / 2;
		}
	}

	static final class Toggle extends Row {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private float anim = -1;

		Toggle(String label, @Nullable String desc, BooleanSupplier get, Consumer<Boolean> set) {
			super(label, desc);
			this.get = get;
			this.set = set;
		}

		@Override
		int controlWidth(Font font) {
			return 22;
		}

		@Override
		void renderControl(Frame f) {
			boolean on = get.getAsBoolean();
			anim = anim < 0 ? (on ? 1 : 0) : Ui.approach(anim, on ? 1 : 0, f.dt(), 14);
			int tx = x + w - 10 - 22;
			int ty = centerY() - 6;
			int track = Ui.lerp(anim, hovered(f) ? Ui.CONTROL_HOVER : Ui.TRACK, Ui.ACCENT);
			Ui.rect(f.g(), tx, ty, 22, 12, 6, track);
			int kx = tx + 2 + Math.round(anim * 10);
			Ui.rect(f.g(), kx, ty + 2, 8, 8, 4, 0xFFFFFFFF);
		}

		@Override
		boolean click(double mx, double my) {
			set.accept(!get.getAsBoolean());
			return true;
		}

		@Override
		boolean key(KeyEvent event) {
			if (event.isSelection() || event.isConfirmation()) {
				return click(0, 0);
			}
			return false;
		}
	}

	static final class Slider extends Row {
		static final int WIDTH = 96;
		private final double min, max, step;
		private final DoubleSupplier get;
		private final DoubleConsumer set;
		private final DoubleFunction<String> format;
		boolean dragging;
		private float knobAnim;

		Slider(String label, @Nullable String desc, double min, double max, double step,
				DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format) {
			super(label, desc);
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
		}

		@Override
		int controlWidth(Font font) {
			return WIDTH + 8 + Math.max(font.width(format.apply(min)), font.width(format.apply(max)));
		}

		private int sx() {
			return x + w - 10 - WIDTH;
		}

		@Override
		void renderControl(Frame f) {
			double v = get.getAsDouble();
			float t = (float) Mth.clamp((v - min) / (max - min), 0, 1);
			int sx = sx();
			int cy = centerY();
			boolean hot = dragging || hovered(f) && Ui.inside(f.mx(), f.my(), sx - 4, y, WIDTH + 8, height());
			knobAnim = Ui.approach(knobAnim, hot ? 1 : 0, f.dt(), 16);
			Ui.rect(f.g(), sx, cy - 1, WIDTH, 3, 1, Ui.TRACK);
			int filled = Math.round(t * WIDTH);
			Ui.rect(f.g(), sx, cy - 1, filled, 3, 1, Ui.ACCENT);
			int kw = 6 + Math.round(knobAnim * 2);
			int kh = 10 + Math.round(knobAnim * 2);
			Ui.rect(f.g(), sx + filled - kw / 2, cy - kh / 2, kw, kh, 2, 0xFFFFFFFF);
			String text = format.apply(v);
			f.g().text(f.font(), Ui.visual(text), sx - 8 - f.font().width(text), cy - 4, dragging ? Ui.TEXT : Ui.MUTED, false);
		}

		@Override
		boolean click(double mx, double my) {
			if (!Ui.inside(mx, my, sx() - 6, y, WIDTH + 12, height())) {
				return false;
			}
			dragging = true;
			drag(mx, my);
			return true;
		}

		@Override
		void drag(double mx, double my) {
			if (dragging) {
				double t = Mth.clamp((mx - sx()) / WIDTH, 0, 1);
				apply(min + t * (max - min));
			}
		}

		@Override
		void release() {
			dragging = false;
		}

		@Override
		boolean key(KeyEvent event) {
			if (event.isLeft() || event.isRight()) {
				apply(get.getAsDouble() + (event.isRight() ? step : -step));
				return true;
			}
			return false;
		}

		private void apply(double value) {
			double snapped = Math.round((value - min) / step) * step + min;
			set.accept(Mth.clamp(snapped, min, max));
		}
	}

	/** A row of options where exactly one is picked, like "Rise | Fade | Pop". */
	static final class Segmented extends Row {
		private final String[] options;
		private final IntSupplier get;
		private final IntConsumer set;
		private float animX = Float.NaN, animW;

		Segmented(String label, @Nullable String desc, String[] options, IntSupplier get, IntConsumer set) {
			super(label, desc);
			this.options = options;
			this.get = get;
			this.set = set;
		}

		private int optionWidth(Font font, int i) {
			return font.width(options[i]) + 12;
		}

		@Override
		int controlWidth(Font font) {
			int total = 4;
			for (int i = 0; i < options.length; i++) {
				total += optionWidth(font, i);
			}
			return total;
		}

		@Override
		void renderControl(Frame f) {
			Font font = f.font();
			int total = controlWidth(font);
			int bx = x + w - 10 - total;
			int by = centerY() - 8;
			Ui.rect(f.g(), bx, by, total, 16, 4, Ui.CONTROL);
			int selected = get.getAsInt();
			int ox = bx + 2;
			for (int i = 0; i < options.length; i++) {
				int ow = optionWidth(font, i);
				if (i == selected) {
					if (Float.isNaN(animX)) {
						animX = ox;
						animW = ow;
					}
					animX = Ui.approach(animX, ox, f.dt(), 18);
					animW = Ui.approach(animW, ow, f.dt(), 18);
				}
				ox += ow;
			}
			Ui.rect(f.g(), Math.round(animX), by + 2, Math.round(animW), 12, 3, Ui.ACCENT);
			ox = bx + 2;
			for (int i = 0; i < options.length; i++) {
				int ow = optionWidth(font, i);
				boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), ox, by, ow, 16);
				int color = i == selected ? 0xFFFFFFFF : hover ? Ui.TEXT : Ui.MUTED;
				f.g().text(font, Ui.visual(options[i]), ox + 6, by + 4, color, false);
				ox += ow;
			}
		}

		@Override
		boolean click(double mx, double my) {
			Font font = net.minecraft.client.Minecraft.getInstance().font;
			int total = controlWidth(font);
			int ox = x + w - 10 - total + 2;
			for (int i = 0; i < options.length; i++) {
				int ow = optionWidth(font, i);
				if (Ui.inside(mx, my, ox, centerY() - 9, ow, 18)) {
					set.accept(i);
					return true;
				}
				ox += ow;
			}
			return false;
		}

		@Override
		boolean key(KeyEvent event) {
			if (event.isLeft() || event.isRight()) {
				int n = options.length;
				set.accept(Math.floorMod(get.getAsInt() + (event.isRight() ? 1 : -1), n));
				return true;
			}
			return false;
		}
	}

	/** A button showing one value; the left half goes back, the right half goes forward. */
	static final class Cycle extends Row {
		/** Width of the arrows, like the font's ‹ (3 pixels wide, 5 tall). */
		private static final int CHEVRON_W = 3;
		private final List<String> labels;
		private final IntSupplier get;
		private final IntConsumer set;

		Cycle(String label, @Nullable String desc, List<String> labels, IntSupplier get, IntConsumer set) {
			super(label, desc);
			this.labels = labels;
			this.get = get;
			this.set = set;
		}

		@Override
		int controlWidth(Font font) {
			int widest = 0;
			for (String l : labels) {
				widest = Math.max(widest, font.width(l));
			}
			return widest + 32;
		}

		@Override
		void renderControl(Frame f) {
			Font font = f.font();
			int bw = controlWidth(font);
			int bx = x + w - 10 - bw;
			int by = centerY() - 8;
			boolean hover = hovered(f) && Ui.inside(f.mx(), f.my(), bx, by, bw, 16);
			Ui.rect(f.g(), bx, by, bw, 16, 4, hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
			boolean leftHalf = f.mx() < bx + bw / 2;
			chevron(f.g(), bx + 6, by + 5, false, hover && leftHalf ? Ui.ACCENT : Ui.MUTED);
			chevron(f.g(), bx + bw - 6 - CHEVRON_W, by + 5, true, hover && !leftHalf ? Ui.ACCENT : Ui.MUTED);
			String value = labels.get(Mth.clamp(get.getAsInt(), 0, labels.size() - 1));
			f.g().centeredText(font, Ui.visual(value), bx + bw / 2, by + 4, Ui.TEXT);
		}

		/**
		 * A small ‹ or › drawn as pixels, top-left corner at x, y. The font's ‹ and › are mirrored in
		 * right-to-left languages, which made both arrows point inwards in Hebrew.
		 */
		private static void chevron(GuiGraphicsExtractor g, int x, int y, boolean right, int color) {
			int mid = y + CHEVRON_W - 1;
			for (int c = 0; c < CHEVRON_W; c++) {
				int fromTip = right ? CHEVRON_W - 1 - c : c;
				g.fill(x + c, mid - fromTip, x + c + 1, mid - fromTip + 1, color);
				g.fill(x + c, mid + fromTip, x + c + 1, mid + fromTip + 1, color);
			}
		}

		@Override
		boolean click(double mx, double my) {
			Font font = net.minecraft.client.Minecraft.getInstance().font;
			int bw = controlWidth(font);
			int bx = x + w - 10 - bw;
			if (!Ui.inside(mx, my, bx, centerY() - 9, bw, 18)) {
				return false;
			}
			step(mx < bx + bw / 2 ? -1 : 1);
			return true;
		}

		private void step(int dir) {
			set.accept(Math.floorMod(get.getAsInt() + dir, labels.size()));
		}

		@Override
		boolean key(KeyEvent event) {
			if (event.isLeft() || event.isRight()) {
				step(event.isRight() ? 1 : -1);
				return true;
			}
			return false;
		}
	}

	/** Little colour squares to pick from. */
	static final class Swatches extends Row {
		private static final int SIZE = 12;
		private static final int GAP = 5;
		private final int[] colors;
		private final IntSupplier selected;
		private final IntConsumer pick;

		Swatches(String label, @Nullable String desc, int[] colors, IntSupplier selected, IntConsumer pick) {
			super(label, desc);
			this.colors = colors;
			this.selected = selected;
			this.pick = pick;
		}

		@Override
		int controlWidth(Font font) {
			return colors.length * (SIZE + GAP) - GAP + 2;
		}

		@Override
		void renderControl(Frame f) {
			int sx = x + w - 10 - controlWidth(f.font()) + 1;
			int sy = centerY() - SIZE / 2;
			int current = selected.getAsInt();
			for (int i = 0; i < colors.length; i++) {
				int cx = sx + i * (SIZE + GAP);
				boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), cx - 2, sy - 2, SIZE + 4, SIZE + 4);
				if ((colors[i] & 0xFFFFFF) == current) {
					Ui.outline(f.g(), cx - 2, sy - 2, SIZE + 4, SIZE + 4, 4, 0xFFFFFFFF);
				} else if (hover) {
					Ui.outline(f.g(), cx - 2, sy - 2, SIZE + 4, SIZE + 4, 4, Ui.MUTED);
				}
				Ui.rect(f.g(), cx, sy, SIZE, SIZE, 3, 0xFF000000 | colors[i]);
			}
		}

		@Override
		boolean click(double mx, double my) {
			int sx = x + w - 10 - controlWidth(net.minecraft.client.Minecraft.getInstance().font) + 1;
			int sy = centerY() - SIZE / 2;
			for (int i = 0; i < colors.length; i++) {
				if (Ui.inside(mx, my, sx + i * (SIZE + GAP) - 2, sy - 3, SIZE + 4, SIZE + 6)) {
					pick.accept(colors[i] & 0xFFFFFF);
					return true;
				}
			}
			return false;
		}
	}

	record Button(Supplier<String> text, Runnable action, BooleanSupplier enabled, boolean primary) {
		Button(String text, Runnable action) {
			this(() -> text, action, () -> true, false);
		}
	}

	/** One or more buttons on the right. */
	static final class Buttons extends Row {
		private final List<Button> buttons;

		Buttons(String label, @Nullable String desc, Button... buttons) {
			super(label, desc);
			this.buttons = List.of(buttons);
		}

		private static int width(Font font, Button b) {
			return Math.max(font.width(b.text().get()) + 16, 20);
		}

		@Override
		int controlWidth(Font font) {
			int total = 0;
			for (Button b : buttons) {
				total += width(font, b) + 4;
			}
			return total - 4;
		}

		@Override
		void renderControl(Frame f) {
			int bx = x + w - 10 - controlWidth(f.font());
			for (Button b : buttons) {
				int bw = width(f.font(), b);
				boolean hover = hovered(f) && Ui.inside(f.mx(), f.my(), bx, centerY() - 8, bw, 16);
				drawButton(f, bx, centerY() - 8, bw, 16, b.text().get(), hover, b.primary(), b.enabled().getAsBoolean());
				bx += bw + 4;
			}
		}

		@Override
		boolean click(double mx, double my) {
			Font font = net.minecraft.client.Minecraft.getInstance().font;
			int bx = x + w - 10 - controlWidth(font);
			for (Button b : buttons) {
				int bw = width(font, b);
				if (Ui.inside(mx, my, bx, centerY() - 9, bw, 18) && b.enabled().getAsBoolean()) {
					b.action().run();
					return true;
				}
				bx += bw + 4;
			}
			return false;
		}
	}

	/** A label with read-only text on the right. */
	static final class Info extends Row {
		private final Supplier<String> value;

		Info(String label, @Nullable String desc, Supplier<String> value) {
			super(label, desc);
			this.value = value;
		}

		@Override
		int controlWidth(Font font) {
			return Math.min(160, font.width(value.get()));
		}

		@Override
		void renderControl(Frame f) {
			String text = Ui.clip(f.font(), value.get(), 160);
			f.g().text(f.font(), Ui.visual(text), x + w - 10 - f.font().width(text), centerY() - 4, Ui.MUTED, false);
		}
	}

	/** Plain text that wraps over several lines, for the About tab. */
	static final class Paragraph extends Row {
		private final Supplier<String> text;
		private List<net.minecraft.util.FormattedCharSequence> lines = List.of();
		private int linesWidth = -1;
		private String linesText = "";

		Paragraph(Supplier<String> text) {
			super("", null);
			this.text = text;
		}

		@Override
		int height() {
			return Math.max(1, lines.size()) * 10 + 12;
		}

		/** Called before layout so height() is right. */
		void measure(Font font, int width) {
			String t = text.get();
			if (width != linesWidth || !t.equals(linesText)) {
				lines = font.split(net.minecraft.network.chat.Component.literal(t), width - 20);
				linesWidth = width;
				linesText = t;
			}
		}

		@Override
		int controlWidth(Font font) {
			return 0;
		}

		@Override
		void render(Frame f) {
			int ly = y + 7;
			for (var line : lines) {
				f.g().text(f.font(), line, x + 10, ly, Ui.MUTED, false);
				ly += 10;
			}
		}

		@Override
		void renderControl(Frame f) {
		}
	}

	static void drawButton(Frame f, int x, int y, int w, int h, String text, boolean hover, boolean primary, boolean enabled) {
		int bg;
		int fg;
		if (!enabled) {
			bg = Ui.CONTROL;
			fg = Ui.DIM;
		} else if (primary) {
			bg = hover ? Ui.lerp(0.25f, Ui.ACCENT, 0xFFFFFFFF) : Ui.ACCENT;
			fg = 0xFFFFFFFF;
		} else {
			bg = hover ? Ui.CONTROL_HOVER : Ui.CONTROL;
			fg = hover ? 0xFFFFFFFF : Ui.TEXT;
		}
		Ui.rect(f.g(), x, y, w, h, 4, bg);
		f.g().centeredText(f.font(), Ui.visual(text), x + w / 2, y + (h - 8) / 2, fg);
	}
}
