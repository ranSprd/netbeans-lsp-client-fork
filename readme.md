# Netbeans LSP Client (preview)

This is an fork of the [embedded Netbeans LSP client](https://github.com/apache/netbeans/tree/master/ide/lsp.client) 
which allows the usage of any [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) implementation.

Our motivation was driven by the idea of C# support for Netbeans. Unfortunately the current LSP client doesn't work
with [Omnisharp](https://github.com/OmniSharp/omnisharp-roslyn) which is the reference LSP server for C#.
Furthermore the plan is, to make our _LSP client_ implementation stable and give it back to the 
apache Netbeans community.

## current changes are

- upgrade eclipse [LSP4j libraries](https://github.com/eclipse/lsp4j) which supports the latest LSP spec (LSP 3.16.0) 
- implement missing code because of LSP upgrade
    - alternative _MarkOccurrences_ based on references if highlighting provider is not available
- make it work with Omnisharp
    - set PID
    - send initialized msg
- LSP Server logging UI

## build instructions

you need the following libraries in folder `release/modules/ext`

- org.eclipse.lsp4j.generator-0.13.0-SNAPSHOT.jar
- org.eclipse.lsp4j-0.13.0-SNAPSHOT.jar
- org.eclipse.lsp4j.jsonrpc-0.13.0-SNAPSHOT.jar
- gson-2.8.6.jar
- org.eclipse.xtend.lib-2.25.0.jar
- org.eclipse.xtext.xbase.lib-2.25.0.jar
- org.eclipse.xtend.lib.macro-2.25.0.jar

The LSP4j snapshots should be available here [https://oss.sonatype.org/content/repositories/snapshots/org/eclipse/lsp4j/org.eclipse.lsp4j/]