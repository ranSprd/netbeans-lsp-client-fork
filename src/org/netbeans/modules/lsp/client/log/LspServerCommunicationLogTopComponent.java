package org.netbeans.modules.lsp.client.log;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.netbeans.modules.lsp.client.log//LspServerCommunicationLog//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "LspServerCommunicationLogTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "output", openAtStartup = false)
@ActionID(category = "Window/Debug", id = "org.netbeans.modules.lsp.client.log.LspServerCommunicationLogTopComponent")
@ActionReference(path = "Menu/Window/Tools", position = 1100, separatorBefore = 1050)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_LspServerCommunicationLogAction",
        preferredID = "LspServerCommunicationLogTopComponent"
)
@Messages({
    "CTL_LspServerCommunicationLogAction=LSP Server Logs",
    "CTL_LspServerCommunicationLogTopComponent=LSP Logs",
    "HINT_LspServerCommunicationLogTopComponent=Communication Logs of LSP Server Connection"
})
public final class LspServerCommunicationLogTopComponent extends TopComponent {

    public LspServerCommunicationLogTopComponent() {
        initComponents();
        setName(Bundle.CTL_LspServerCommunicationLogTopComponent());
        setToolTipText(Bundle.HINT_LspServerCommunicationLogTopComponent());

    }
    
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        clearButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        logStateButton = new javax.swing.JToggleButton();
        consoleLogginButton = new javax.swing.JToggleButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setName(org.openide.util.NbBundle.getMessage(LspServerCommunicationLogTopComponent.class, "LoggingFrame.ComponentName")); // NOI18N

        jToolBar1.setRollover(true);

        org.openide.awt.Mnemonics.setLocalizedText(clearButton, org.openide.util.NbBundle.getMessage(LspServerCommunicationLogTopComponent.class, "LspServerCommunicationLogTopComponent.clearButton.text")); // NOI18N
        clearButton.setFocusable(false);
        clearButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        clearButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(clearButton);
        jToolBar1.add(jSeparator1);

        logStateButton.setSelected(LogStorage.ALL.isLoggingEnabled());
        org.openide.awt.Mnemonics.setLocalizedText(logStateButton, org.openide.util.NbBundle.getMessage(LspServerCommunicationLogTopComponent.class, "LspServerCommunicationLogTopComponent.logStateButton.text")); // NOI18N
        logStateButton.setFocusable(false);
        logStateButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        logStateButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        logStateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logStateButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(logStateButton);

        consoleLogginButton.setSelected(LogStorage.ALL.isConsoleLogging());
        org.openide.awt.Mnemonics.setLocalizedText(consoleLogginButton, org.openide.util.NbBundle.getMessage(LspServerCommunicationLogTopComponent.class, "LspServerCommunicationLogTopComponent.consoleLogginButton.text")); // NOI18N
        consoleLogginButton.setToolTipText(org.openide.util.NbBundle.getMessage(LspServerCommunicationLogTopComponent.class, "LspServerCommunicationLogTopComponent.consoleLogginButton.toolTipText")); // NOI18N
        consoleLogginButton.setEnabled(LogStorage.ALL.isLoggingEnabled());
        consoleLogginButton.setFocusable(false);
        consoleLogginButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        consoleLogginButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        consoleLogginButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                consoleLogginButtonItemStateChanged(evt);
            }
        });
        jToolBar1.add(consoleLogginButton);

        jTable1.setAutoCreateRowSorter(true);
        jTable1.setModel(LogStorage.ALL.getTableModel());
        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        jTable1.setFillsViewportHeight(true);
        jTable1.setShowGrid(true);
        jScrollPane2.setViewportView(jTable1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jToolBar1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jScrollPane2)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        LogStorage.ALL.clear();
    }//GEN-LAST:event_clearButtonActionPerformed

    private void logStateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logStateButtonActionPerformed
        consoleLogginButton.setEnabled(logStateButton.isSelected());
        LogStorage.ALL.setLoggingEnabled( logStateButton.isSelected());
    }//GEN-LAST:event_logStateButtonActionPerformed

    private void consoleLogginButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_consoleLogginButtonItemStateChanged
        LogStorage.ALL.setConsoleLogging( consoleLogginButton.isSelected());
    }//GEN-LAST:event_consoleLogginButtonItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton clearButton;
    private javax.swing.JToggleButton consoleLogginButton;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JTable jTable1;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JToggleButton logStateButton;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
