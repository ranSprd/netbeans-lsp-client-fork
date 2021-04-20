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
package org.netbeans.modules.lsp.client.model;

import java.util.Optional;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DocumentFormattingOptions;
import org.eclipse.lsp4j.DocumentHighlightOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingOptions;
import org.eclipse.lsp4j.DocumentSymbolOptions;
import org.eclipse.lsp4j.FoldingRangeProviderOptions;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.ReferenceOptions;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.netbeans.modules.lsp.client.Utils;

/**
 *
 * @author ran
 */
public class LSPServerCapabilities {

    private final ServerCapabilities serverCapabilities;

    public LSPServerCapabilities(ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
    }

    /**
     * if <either> contains a boolean then return it, otherwise true if there is a alternative data object.
     *
     * @param either
     * @return
     */
    private boolean translateAvailibility(Either<Boolean, ?> either) {
        return either != null && ((either.isLeft() && Utils.isTrue(either.getLeft())) || either.isRight());
    }

    /**
     * The server provides find references support.
     */
    public boolean hasReferenceSupport() {
        Either<Boolean, ReferenceOptions> either = serverCapabilities.getReferencesProvider();
        if (either != null) {
            if (either.isLeft()) {
                return either.getLeft();
            }
            ReferenceOptions refOptions = either.getRight();
            if (refOptions != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The server provides rename support. RenameOptions may only be specified if the client states that it supports `prepareSupport` in its initial
     * `initialize` request.
     */
    public boolean hasRenameSupport() {
        Either<Boolean, RenameOptions> hasRename = serverCapabilities.getRenameProvider();
        return translateAvailibility(hasRename);
    }

    public CompletionOptions getCompletionProvider() {
        return serverCapabilities.getCompletionProvider();
    }

    
    /**
     * The server provides document symbol support.
     *
     * @return
     */
    public boolean hasDocumentSymbolSupport() {
        Either<Boolean, DocumentSymbolOptions> documentSymbol = serverCapabilities.getDocumentSymbolProvider();
        return translateAvailibility(documentSymbol);
    }

    public boolean hasDocumentFormattingSupport() {
        Either<Boolean, DocumentFormattingOptions> docFormatting = serverCapabilities.getDocumentFormattingProvider();
        return translateAvailibility(docFormatting);
    }

    public boolean hasDocumentRangeFormattingSupport() {
        Either<Boolean, DocumentRangeFormattingOptions> docRangeFormatting = serverCapabilities.getDocumentRangeFormattingProvider();
        return translateAvailibility(docRangeFormatting);
    }

    /**
     * The server provides document highlight support.
     */
    public boolean hasDocumentHighlightSupport() {
        Either<Boolean, DocumentHighlightOptions> docHighlighting = serverCapabilities.getDocumentHighlightProvider();
        return translateAvailibility(docHighlighting);
    }

    public Either<TextDocumentSyncKind, TextDocumentSyncOptions> getTextDocumentSync() {
        return serverCapabilities.getTextDocumentSync();
    }

    public Either<Boolean, CodeActionOptions> getCodeActionProvider() {
        return serverCapabilities.getCodeActionProvider();
    }

    public boolean hasCodeActionSupport() {
        Either<Boolean, CodeActionOptions> codeAction = serverCapabilities.getCodeActionProvider();
        return translateAvailibility(codeAction);
    }

    /**
     * The server provides folding provider support.
     *
     * @return
     */
    public boolean hasFoldingRangeSupport() {
        Either<Boolean, FoldingRangeProviderOptions> folding = serverCapabilities.getFoldingRangeProvider();
        return translateAvailibility(folding);
    }

    public Optional<HoverOptions> getHoverProviderOptions() {
        Either<Boolean, HoverOptions> hover = serverCapabilities.getHoverProvider();
        if (hover!=null && hover.isRight()) {
            return Optional.ofNullable(hover.getRight());
        }
        return Optional.empty();
    }
    
    public boolean hasHoverSupport() {
        Either<Boolean, HoverOptions> hoverSupport = serverCapabilities.getHoverProvider();
        return translateAvailibility(hoverSupport);
    }

    public SignatureHelpOptions getSignatureHelpProviderOptions() {
        SignatureHelpOptions options = serverCapabilities.getSignatureHelpProvider();
        return options;
    }

    public SemanticTokensWithRegistrationOptions getSemanticTokensProvider() {
        return serverCapabilities.getSemanticTokensProvider();
    }

    
    

}
