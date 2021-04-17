package org.netbeans.modules.lsp.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.openide.filesystems.FileObject;

/**
 *
 * @author ran
 */
public class CodeLensImpl implements LSPWorkingPool.BackgroundTask {


    @Override
    public void run(LSPBindings bindings, FileObject file) {
        try { 
            String uri = Utils.toURI(file);
            TextDocumentService docService = bindings.getTextDocumentService();

            CodeLensParams clp = new CodeLensParams();
            clp.setTextDocument(new TextDocumentIdentifier(uri));
            
            List<? extends CodeLens> codeLens = docService.codeLens(clp).get();
            
            System.out.println(codeLens);
            
            for(CodeLens cl : codeLens) {
                CodeLens resolved = docService.resolveCodeLens(cl).get();
                System.out.println( resolved);
                
            }

        } catch (ExecutionException ex) {
//                LOG.log(Level.WARNING, "Can't load data from TextDocumentService", ex);
//                setKeys(Collections.emptyList());
        } catch (InterruptedException ex) {
            //try again:
            LSPWorkingPool.addBackgroundTask(file, this);
        }
    }
    
}
