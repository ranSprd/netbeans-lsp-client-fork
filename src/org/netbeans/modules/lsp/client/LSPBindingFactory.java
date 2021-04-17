/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.lsp.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.event.ChangeListener;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.DocumentHighlightCapabilities;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolKindCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.util.Preconditions;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.progress.BaseProgressUtils;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.lsp.client.bindings.LanguageClientImpl;
import org.netbeans.modules.lsp.client.bindings.TextDocumentSyncServerCapabilityHandler;
import org.netbeans.modules.lsp.client.options.MimeTypeInfo;
import org.netbeans.modules.lsp.client.spi.LSPClientInfo;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider;
import org.netbeans.modules.lsp.client.spi.ServerRestarter;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.OnStop;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author lahvac 
 * @author ran
 */
public class LSPBindingFactory {
    
    private static final int LSP_KEEP_ALIVE_MINUTES = 10;
    private static final Logger LOG = Logger.getLogger(LSPBindingFactory.class.getName());
    
    private static final Map<LSPBindings,Long> lspKeepAlive = new IdentityHashMap<>();
    private static final Map<URI, Map<String, WeakReference<LSPBindings>>> project2MimeType2Server = new HashMap<>();
    private static final Map<FileObject, Map<String, LSPBindings>> workspace2Extension2Server = new HashMap<>();
    
    private static final RequestProcessor WORKER = new RequestProcessor(LanguageClientImpl.class.getName(), 1, false, true);
    
    private static final ChangeSupport ideChangeSupport = new ChangeSupport(LSPBindingFactory.class);

    private static final LSPClientInfo CLIENT_INFO = new LSPClientInfo();
    

    static {
        //Don't perform null checks. The servers may not adhere to the specification, and send illegal nulls.
        Preconditions.enableNullChecks(false);

        // Remove LSP Servers from strong reference tracking, that have not
        // been accessed more than LSP_KEEP_ALIVE_MINUTES minutes
        WORKER.scheduleAtFixedRate(
            () -> {
                synchronized (LSPBindings.class) {
                    long tooOld = System.currentTimeMillis() - (LSP_KEEP_ALIVE_MINUTES * 60L * 1000L);
                    Iterator<Map.Entry<LSPBindings, Long>> iterator = lspKeepAlive.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<LSPBindings, Long> entry = iterator.next();
                        if (entry.getValue() < tooOld) {
                            //@todo close the server...
                            try {
                                entry.getKey().shutdown();
                            } catch (Exception e) {
                            }
                            iterator.remove();
                        }
                    }
                }
            },
            Math.max(LSP_KEEP_ALIVE_MINUTES / 2, 1),
            Math.max(LSP_KEEP_ALIVE_MINUTES / 2, 1),
            TimeUnit.MINUTES);
    }
    
    
    public static void addChangeListener(ChangeListener l) {
        ideChangeSupport.addChangeListener(WeakListeners.change(l, ideChangeSupport));
    }
    

    public static synchronized LSPBindings getBindingForFile(FileObject file) {
        for (Map.Entry<FileObject, Map<String, LSPBindings>> e : workspace2Extension2Server.entrySet()) {
            if (FileUtil.isParentOf(e.getKey(), file)) {
                LSPBindings bindings = e.getValue().get(file.getExt());

                if (bindings != null) {
                    return bindings;
                }

                break;
            }
        }

        String mimeType = FileUtil.getMIMEType(file);

        if (mimeType == null) {
            return null;
        }

        return getBindingsImpl(FileOwnerQuery.getOwner(file), file, mimeType);
    }
    
    
    /**
     * 
     * connects/start a new LSP server - call will block until initializiation is finished
     * 
     * @param project
     * @param file
     * @param mimeType
     * 
     * @return LSP server binding
     */
    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    private static synchronized LSPBindings getBindingsImpl(Project project, FileObject file, String mimeType) {
        FileObject dir;

        if (project == null) {
            dir = file.getParent();
        } else {
            dir = project.getProjectDirectory();
        }

        URI uri = dir.toURI();

        LSPBindings bindings = null;
        WeakReference<LSPBindings> bindingsReference = project2MimeType2Server.computeIfAbsent(uri, p -> new HashMap<>())
                                       .get(mimeType);

        if(bindingsReference != null) {
            bindings = bindingsReference.get();
        }

        if (bindings != null && !bindings.isProcessAlive()) {
            bindings = null;
        }

        if (bindings == null) {
            bindings = buildBindings(project, mimeType, dir, uri);
            if (bindings != null) {
                project2MimeType2Server.computeIfAbsent(uri, p -> new HashMap<>())
                    .put(mimeType, new WeakReference<>(bindings));
                WORKER.post(() -> ideChangeSupport.fireChange());
            }
        }

        if(bindings != null) {
            lspKeepAlive.put(bindings, System.currentTimeMillis());
        }

        return bindings != null ? bindings : null;
    }
    
    
    public static void ensureServerRunning(Project project, String mimeType) {
        getBindingsImpl(project, project.getProjectDirectory(), mimeType);
    }
    
    
    @NbBundle.Messages("LBL_Connecting=Connecting to language server")
    public static void addBindings(FileObject root, int port, String... extensions) {
        BaseProgressUtils.showProgressDialogAndRun(() -> {
            try {
                Socket s = new Socket(InetAddress.getLocalHost(), port);
                LanguageClientImpl lc = new LanguageClientImpl();
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(lc, in, new OutputStream() {
                    @Override
                    public void write(int w) throws IOException {
                        out.write(w);
                        if (w == '\n')
                            out.flush();
                    }
                });
                launcher.startListening();
                LanguageServer server = launcher.getRemoteProxy();
                InitializeResult result = initServer(null, server, root);
                LSPBindings bindings = new LSPBindings(server, result, null);

                lc.setBindings(bindings);
                
                workspace2Extension2Server.put(root, Arrays.stream(extensions)
                    .collect(Collectors.toMap(k -> k, v -> bindings)));
                WORKER.post(() -> ideChangeSupport.fireChange());
            } catch (InterruptedException | ExecutionException | IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }, Bundle.LBL_Connecting());
    }
    
    
    public static synchronized Set<LSPBindings> getAllBindings() {
        Set<LSPBindings> allBindings = Collections.newSetFromMap(new IdentityHashMap<>());

        project2MimeType2Server.values()
                               .stream()
                               .flatMap(n -> n.values().stream())
                               .map(bindingRef -> bindingRef.get())
                               .filter(binding -> binding != null)
                               .forEach(allBindings::add);
        workspace2Extension2Server.values()
                                  .stream()
                                  .flatMap(n -> n.values().stream())
                                  .forEach(allBindings::add);

        return allBindings;
    }
    
    
    @SuppressWarnings({"AccessingNonPublicFieldOfAnotherObject", "ResultOfObjectAllocationIgnored"})
    private static LSPBindings buildBindings(Project project, String mt, FileObject dir, URI baseUri) {
        MimeTypeInfo mimeTypeInfo = new MimeTypeInfo(mt);
        ServerRestarter restarter = () -> {
            synchronized (LSPBindings.class) {
                WeakReference<LSPBindings> bRef = project2MimeType2Server.getOrDefault(baseUri, Collections.emptyMap()).remove(mt);
                LSPBindings b = bRef != null ? bRef.get() : null;

                if (b != null) {
                    lspKeepAlive.remove(b);
                    b.shutdownAndKill();
                }
            }
        };
        
        for (LanguageServerProvider provider : MimeLookup.getLookup(mt).lookupAll(LanguageServerProvider.class)) {
            final Lookup lkp = project != null ? Lookups.fixed(project, mimeTypeInfo, restarter) : Lookups.fixed(mimeTypeInfo, restarter);
            LanguageServerProvider.LanguageServerDescription desc = provider.startServer(lkp);

            if (desc != null) {
                LSPBindings b = LanguageServerProviderAccessor.getINSTANCE().getBindings(desc);
                if (b != null) {
                    return b;
                }
                try {
                    LanguageClientImpl lci = new LanguageClientImpl();
                    InputStream in = LanguageServerProviderAccessor.getINSTANCE().getInputStream(desc);
                    OutputStream out = LanguageServerProviderAccessor.getINSTANCE().getOutputStream(desc);
                    Process p = LanguageServerProviderAccessor.getINSTANCE().getProcess(desc);
                    Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(lci, in, out);
                    launcher.startListening();
                    LanguageServer server = launcher.getRemoteProxy();
                    InitializeResult result = initServer(p, server, dir); //XXX: what if a different root is expected????
                    b = new LSPBindings(server, result, LanguageServerProviderAccessor.getINSTANCE().getProcess(desc));
                    // Register cleanup via LSPReference#run
                    new LSPBindings.LSPReference(b, Utilities.activeReferenceQueue());
                    lci.setBindings(b);
                    LanguageServerProviderAccessor.getINSTANCE().setBindings(desc, b);
                    TextDocumentSyncServerCapabilityHandler.refreshOpenedFilesInServers();
                    return b;
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.WARNING, null, ex);
                }
            }
        }
        return null;
    }
    
    @SuppressWarnings("deprecation")
    private static InitializeResult initServer(Process p, LanguageServer server, FileObject root) throws InterruptedException, ExecutionException {
       InitializeParams initParams = new InitializeParams();
       initParams.setRootUri(Utils.toURI(root));
       final File rootFile = FileUtil.toFile(root);
       if (rootFile != null) {
           initParams.setRootPath(rootFile.getAbsolutePath()); //some servers still expect root path
       }
       initParams.setWorkspaceFolders(Arrays.asList(root).stream()
               .map(fileObject -> Utils.toURI(root))
               .map(uri -> new WorkspaceFolder( uri))
               .collect(Collectors.toList()) ); //?

        // The process Id of the parent process that started the server. Is null if
	// the process has not been started by another process. If the parent
	// process is not alive then the server should exit (see exit notification)
	// its process.       
        // Either we set our ID or NULL
        // initParams.setProcessId( null);
       Integer pid = (int) ProcessHandle.current().pid();
       initParams.setProcessId( pid);
       
       initParams.setClientInfo( CLIENT_INFO.generateClientInfo()); 
//       initParams.setTrace( TraceValue.Message);
       
       SymbolCapabilities symbolCapabilities = new SymbolCapabilities(
               new SymbolKindCapabilities(Arrays.asList(SymbolKind.values())));
       symbolCapabilities.setDynamicRegistration(Boolean.TRUE);
       
       TextDocumentClientCapabilities tdcc = new TextDocumentClientCapabilities();
       DocumentSymbolCapabilities dsc = new DocumentSymbolCapabilities();
       dsc.setDynamicRegistration(Boolean.TRUE); //?
       dsc.setSymbolKind( symbolCapabilities.getSymbolKind());
       dsc.setHierarchicalDocumentSymbolSupport(true);
       dsc.setTagSupport( symbolCapabilities.getTagSupport()); //?
       dsc.setLabelSupport(Boolean.FALSE); //?
       
       tdcc.setDocumentSymbol(dsc);
       DocumentHighlightCapabilities dh = new DocumentHighlightCapabilities(); //?
       dh.setDynamicRegistration(Boolean.TRUE); //?
       tdcc.setDocumentHighlight( dh); //?
       
       CompletionCapabilities completionCapabilities = new CompletionCapabilities();
       completionCapabilities.setContextSupport(Boolean.TRUE);
       tdcc.setCompletion(completionCapabilities);
       
       WorkspaceClientCapabilities wcc = new WorkspaceClientCapabilities();
       wcc.setWorkspaceEdit(new WorkspaceEditCapabilities());
       wcc.getWorkspaceEdit().setDocumentChanges(true);
       wcc.getWorkspaceEdit().setResourceOperations(Arrays.asList(ResourceOperationKind.Create, ResourceOperationKind.Delete, ResourceOperationKind.Rename));
       wcc.setSymbol( symbolCapabilities); //?

       initParams.setCapabilities(new ClientCapabilities(wcc, tdcc, null));
       CompletableFuture<InitializeResult> initResult = server.initialize(initParams);
       while (true) {
           try {
               InitializeResult result = initResult.get(100, TimeUnit.MILLISECONDS);
               if (result != null) {
                   server.initialized( new InitializedParams());
               }
               return result;
           } catch (TimeoutException ex) {
               if (p != null && !p.isAlive()) {
                   //@todo endless loop?? 
                   LOG.log(Level.WARNING, "no answer from LSP server, it seems dead.");
                   InitializeResult emptyResult = new InitializeResult();
                   emptyResult.setCapabilities(new ServerCapabilities());
                   return emptyResult;
               }
           }
       }
    }
    
    
    @OnStop
    public static class Cleanup implements Runnable {

        @Override
        @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
        public void run() {
            for (Map<String, WeakReference<LSPBindings>> mime2Bindings : project2MimeType2Server.values()) {
                for (WeakReference<LSPBindings> bRef : mime2Bindings.values()) {
                    LSPBindings b = bRef != null ? bRef.get() : null;
                    if (b != null) {
                        b.killRunningServerProcess();
                    }
                }
            }
            for (Map<String, LSPBindings> mime2Bindings : workspace2Extension2Server.values()) {
                for (LSPBindings b : mime2Bindings.values()) {
                    if (b != null) {
                        b.killRunningServerProcess();
                    }
                }
            }
        }
    }
    
    
}
