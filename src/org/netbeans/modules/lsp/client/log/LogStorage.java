package org.netbeans.modules.lsp.client.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;
import org.netbeans.spi.editor.hints.Severity;

/**
 *
 * @author ranSprd
 */
public enum LogStorage {
    
    ALL;
    
    
    private int maxCapacity = 5000;
    private boolean enabled = false;
    private boolean consoleLogging = false;
    private final List<LogLine> lines = new ArrayList<>();
    private final LogTableModel tableModel = new LogTableModel();
    
    public void clear() {
        lines.clear();
        tableModel.fireTableDataChanged();
    }
    
    public void enableLogging() {
        enabled = true;
    }
    
    public void disableLogging() {
        enabled = false;
    }
    
    public void toggleLogging() {
        enabled = !enabled;
    }

    public void setLoggingEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * collect and print logging messages
     * @return false if logging is disabled and no log message will be logged
     */
    public boolean isLoggingEnabled() {
        return enabled;
    }

    /**
     * log messages additionally on system.out - only if logging is enabled 
     * @return true/false
     */
    public boolean isConsoleLogging() {
        return consoleLogging;
    }

    public void setConsoleLogging(boolean consoleLogging) {
        this.consoleLogging = consoleLogging;
    }
    
    
    
    /** register/print a log message (or throw away if logging is disabled) */
    public LogStorage add(LogLine line) {
        
        if (!enabled) {
            return this;
        }
        
        int lastLineIndex = lines.size();
        
        // prevent adding the same log line several times
        if (!lines.isEmpty()) {
            LogLine lastLine = lines.get(lastLineIndex-1);
            if (Objects.equals(lastLine.getMessage(), line.getMessage())) {
                return this;
            }
        }
        
        lines.add(line);
        if (lastLineIndex < 2) {
            tableModel.fireTableDataChanged();
        } else {
            tableModel.fireTableRowsInserted(lastLineIndex, lastLineIndex);
        }
        
        if (consoleLogging) {
            System.out.println(line);
        }
        
        return this;
    }
    
    public LogStorage error(String message) {
        return add( LogLine.error(message));
    }
    public LogStorage warning(String message) {
        return add( LogLine.warning(message));
    }
    public LogStorage info(String message) {
        return add( LogLine.info(message));
    }

    public List<LogLine> getLines() {
        return lines;
    }

    public LogTableModel getTableModel() {
        return tableModel;
    }
    
    
    public static class LogTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return ALL.lines.size();
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0: return "severity";
                case 1: return "timestamp";
                case 2: return "message";
                default : return "raw";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch(columnIndex) {
                case 0: return Severity.class;
                case 1: return Long.class;
                case 2: return String.class;
                default : return Object.class;
            }
        }
        
        

        
        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < ALL.lines.size()) {
                LogLine logLine = ALL.lines.get(rowIndex);
                if (logLine != null) {
                    switch (columnIndex) {
                        case 0 : return logLine.getLevel();
                        case 1 : return logLine.getTimestamp();
                        case 2 : return logLine.getMessage();
                        default : return "column " +columnIndex;
                        
                    }
                }
            }
            return "row " +rowIndex;
        }
        
    }
    
}
