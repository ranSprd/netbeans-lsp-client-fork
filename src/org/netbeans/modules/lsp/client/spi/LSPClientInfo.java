package org.netbeans.modules.lsp.client.spi;

import org.eclipse.lsp4j.ClientInfo;
import org.netbeans.modules.lsp.client.ConnectToLanguageServerParameters;

/**
 * Handling of LSP client informations.
 * 
 * @author ranSprd
 */
public class LSPClientInfo {

    private final String clientName;

    public LSPClientInfo() {
        this.clientName = org.openide.util.NbBundle.getMessage(ConnectToLanguageServerParameters.class, "OpenIDE-Module-Name");
    }

    public String getClientName() {
        return clientName;
    }
    /** generate informations about the client which is send to the LSP server */
    public ClientInfo generateClientInfo() {
        return new ClientInfo(clientName, "1");
    }
    
}
