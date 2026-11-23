package dev.beli.skylyrics;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;

/**
 * Collects what the mod writes to the game log ("SkyLyrics" logger, every level) while open, for tests that check what a
 * log line may say: players paste latest.log in public when they ask for help.
 */
public final class LogCapture implements AutoCloseable {
	private final List<String> lines = new CopyOnWriteArrayList<>();
	private final org.apache.logging.log4j.core.Logger logger;
	private final Level oldLevel;
	private final AbstractAppender appender;
	private final String name;

	public LogCapture() {
		this("SkyLyrics");
	}

	/** Collects what the logger of this name writes (PrivateFile's is "skylyrics", in small letters). */
	public LogCapture(String name) {
		this.name = name;
		logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(name);
		oldLevel = logger.getLevel();
		appender = new AbstractAppender("SkyLyricsTestCapture" + System.nanoTime(), null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				StringBuilder line = new StringBuilder(event.getLevel() + " " + event.getMessage().getFormattedMessage());
				for (Throwable t = event.getThrown(); t != null; t = t.getCause() == t ? null : t.getCause()) {
					line.append(" | ").append(t);
				}
				lines.add(line.toString());
			}
		};
		appender.start();
		logger.addAppender(appender);
		Configurator.setLevel(name, Level.ALL); // the test setup's default would drop everything below ERROR
	}

	/** Every line so far, as "LEVEL message", with any exception's text after " | ". */
	public List<String> lines() {
		return List.copyOf(lines);
	}

	@Override
	public void close() {
		logger.removeAppender(appender);
		Configurator.setLevel(name, oldLevel);
		appender.stop();
	}
}
