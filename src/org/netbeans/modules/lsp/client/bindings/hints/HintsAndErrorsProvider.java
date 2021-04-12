package org.netbeans.modules.lsp.client.bindings.hints;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.text.Document;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.Utils;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;
import static org.netbeans.spi.editor.hints.LazyFixList.PROP_COMPUTED;
import static org.netbeans.spi.editor.hints.LazyFixList.PROP_FIXES;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author ranSprd
 */
public class HintsAndErrorsProvider {
    
    private static final Map<DiagnosticSeverity, Severity> severityMap = new EnumMap<>(DiagnosticSeverity.class);
    
    static {
        severityMap.put(DiagnosticSeverity.Error, Severity.ERROR);
        severityMap.put(DiagnosticSeverity.Hint, Severity.HINT);
        severityMap.put(DiagnosticSeverity.Information, Severity.HINT);
        severityMap.put(DiagnosticSeverity.Warning, Severity.WARNING);
    }
    
    private final LSPBindings bindings;
    private final Map<String, ListMerger> cachedLists = new HashMap<>();

    public HintsAndErrorsProvider(LSPBindings bindings) {
        this.bindings = bindings;
    }
    
    public List<ErrorDescription> consume(PublishDiagnosticsParams diagnostics, FileObject file, Document doc) {
//        System.out.println("pdp version " +diagnostics.getVersion() +"\t "+diagnostics.getDiagnostics().size() +" \t" +diagnostics.getUri());
        List<ErrorDescription> errorDescriptions = diagnostics.getDiagnostics().stream()
                .map(d -> createHintsAndErrors(doc, file, diagnostics.getUri(), d))
                .collect(Collectors.toList());
        return errorDescriptions;
    }

    private ErrorDescription createHintsAndErrors(Document doc, FileObject file, String uri, org.eclipse.lsp4j.Diagnostic d) {
        LazyFixList fixList = new DiagnosticFixList(uri, d);
        return ErrorDescriptionFactory.createErrorDescription(severityMap.get(d.getSeverity()), d.getMessage(), fixList, file, Utils.getOffset(doc, d.getRange().getStart()), Utils.getOffset(doc, d.getRange().getEnd()));
    }
    
    
    private ListMerger get(PublishDiagnosticsParams diagnostics) {
        int v = (diagnostics.getVersion() == null)?-1:diagnostics.getVersion();

        ListMerger listMerger = cachedLists.get(diagnostics.getUri());
        if (listMerger == null) {
            listMerger = new ListMerger(v);
            cachedLists.put(diagnostics.getUri(), listMerger);
        } else if (listMerger.version < v) {
            // incoming data for a newer version, throw the old stuff away
            listMerger = new ListMerger(v);
            cachedLists.put(diagnostics.getUri(), listMerger);
        } else if (listMerger.version > v) {
            // uuupppps, that is crazy
        }
        
        return listMerger;
    }
    
    /** merge list of the same version */
    private class ListMerger {

        private final int version;
        private final Set<org.eclipse.lsp4j.Diagnostic> allDiagnosticItems = new HashSet<>();

        public ListMerger(int version) {
            this.version = version;
        }

        public int getVersion() {
            return version;
        }
        
        public Stream<Diagnostic> combinedStream(List<Diagnostic> list) {
            allDiagnosticItems.addAll(list);
            return allDiagnosticItems.stream();
        }

    }
    
    private final class DiagnosticFixList implements LazyFixList {

        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private final String fileUri;
        private final Diagnostic diagnostic;
        private List<Fix> fixes;
        private boolean computing;
        private boolean computed;

        public DiagnosticFixList(String fileUri, Diagnostic diagnostic) {
            this.fileUri = fileUri;
            this.diagnostic = diagnostic;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
            pcs.addPropertyChangeListener(l);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
            pcs.removePropertyChangeListener(l);
        }

        @Override
        public boolean probablyContainsFixes() {
            return true;
        }

        @Override
        public synchronized List<Fix> getFixes() {
            if (!computing && !computed) {
                computing = true;
                bindings.runOnBackground(() -> {
                    try {
                        List<Either<Command, CodeAction>> commands =
                                bindings.getTextDocumentService().codeAction(new CodeActionParams(new TextDocumentIdentifier(fileUri),
                                        diagnostic.getRange(),
                                        new CodeActionContext(Collections.singletonList(diagnostic)))).get();
                        List<Fix> newFixes = commands.stream()
                                                  .map(cmd -> new CommandBasedFix(cmd))
                                                  .collect(Collectors.toList());
                        synchronized (this) {
                            this.fixes = Collections.unmodifiableList(newFixes);
                            this.computed = true;
                            this.computing = false;
                        }
                        pcs.firePropertyChange(PROP_COMPUTED, null, null);
                        pcs.firePropertyChange(PROP_FIXES, null, null);
                    } catch (InterruptedException | ExecutionException ex) {
//                        Exceptions.printStackTrace(ex);
                    }
                });
            }
            return fixes;
        }

        @Override
        public synchronized boolean isComputed() {
            return computed;
        }

        private class CommandBasedFix implements Fix {

            private final Either<Command, CodeAction> cmd;

            public CommandBasedFix(Either<Command, CodeAction> cmd) {
                this.cmd = cmd;
            }

            @Override
            public String getText() {
                return cmd.isLeft() ? cmd.getLeft().getTitle() : cmd.getRight().getTitle();
            }

            @Override
            public ChangeInfo implement() throws Exception {
                Utils.applyCodeAction(bindings, cmd);
                return null;
            }
        }
        
    }
    
}
