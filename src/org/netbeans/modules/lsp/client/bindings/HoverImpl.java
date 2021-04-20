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
package org.netbeans.modules.lsp.client.bindings;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.LSPWorkingPool;
import org.netbeans.modules.lsp.client.Utils;
import org.openide.filesystems.FileObject;

/**
 *
 * @author ranSprd
 */
public class HoverImpl implements LSPWorkingPool.BackgroundTask, CaretListener, PropertyChangeListener  {

    private final JTextComponent component;
    private Document document;
    private int latestCaretPos;
    

    public HoverImpl(JTextComponent component) {
        this.component = component;
        try {
            document = component.getDocument();
            latestCaretPos = component.getCaretPosition();
            component.addCaretListener(this);
            component.addPropertyChangeListener(this);
        } catch (Exception e) {
        }
        
    }

    @Override
    public void run(LSPBindings bindings, FileObject file) {
        // hier mal noch optimieren, läuft ein task bereits oder gibt es ein Ergebnis für die gleiche Anfrage welches nicht zu
        // alt ist - vielleicht das alles in den BackgroundTask einbauen
        try { 
            Optional<Hover> hoverResult = call(bindings, file, document, latestCaretPos);

            if (hoverResult.isPresent()) {
                System.out.println("hover\n" +hoverResult);
            }
        }
         catch (InterruptedException | TimeoutException ex) {
            //try again:
            LSPWorkingPool.addBackgroundTask(file, this);
        }
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        latestCaretPos = e.getDot();
        LSPWorkingPool.ASYNC.post(() -> {
            FileObject file = NbEditorUtilities.getFileObject(document);

            if (file != null) {
                LSPWorkingPool.rescheduleBackgroundTask(file, this);
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == null || "document".equals(evt.getPropertyName())) {
            document = component.getDocument();
        }
    }
    
    /**
     * call the hover endpoint of LSP server, blocks until the response is recived.
     * 
     * @param bindings
     * @param file
     * @param document
     * @param offset
     * @return
     * @throws InterruptedException
     * @throws TimeoutException 
     */ 
    public static Optional<Hover> call(LSPBindings bindings, FileObject file, Document document, int offset) throws InterruptedException, TimeoutException {
        if (!bindings.getInitResult().getCapabilities().hasHoverSupport()) {
            return Optional.empty();
        }
        String uri = Utils.toURI(file);
        TextDocumentService docService = bindings.getTextDocumentService();

        try {
            HoverParams params = new HoverParams(new TextDocumentIdentifier(uri), Utils.createPosition(document, offset));
            return Optional.ofNullable(docService.hover(params).get(500, TimeUnit.MILLISECONDS)); // blocks until call is received
        } catch (BadLocationException | ExecutionException ex) {
        }

        return Optional.empty();

    }
    
    public static String getSimpleHoverContent( Hover hover) {
        if (hover != null) {
            if (hover.getContents().isRight()) {
                return hover.getContents().getRight().getValue();
            }
        }
        return null;
    }
    
}
