package org.netbeans.modules.lsp.client.log;

import org.netbeans.spi.editor.hints.Severity;

/**
 *
 * @author ran
 */
public class LogLine {
    
    public static LogLine error(String message) {
        return new LogLine(Severity.ERROR, message);
    }
    
    public static LogLine warning(String message) {
        return new LogLine(Severity.WARNING, message);
    }
    
    public static LogLine info(String message) {
        return new LogLine(Severity.HINT, message);
    }
    
    
    private final Severity level;
    private final long timestamp;
    private final String message;

    private LogLine(Severity level, long timestamp, String message) {
        this.level = level;
        this.timestamp = timestamp;
        this.message = message;
    }

    private LogLine(Severity level, String message) {
        this(level, System.currentTimeMillis(), message);
    }

    public Severity getLevel() {
        return level;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return level + " " + timestamp + " {" + message + '}';
    }
    
    
}
