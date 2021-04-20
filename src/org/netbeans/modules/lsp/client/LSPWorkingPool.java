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

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.netbeans.modules.lsp.client.bindings.LanguageClientImpl;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author lahvac 
 * @author ranSprd
 * 
 */
public class LSPWorkingPool {
 
    private static final RequestProcessor WORKER = new RequestProcessor(LanguageClientImpl.class.getName(), 1, false, true);
    public static final RequestProcessor  ASYNC = new RequestProcessor(LanguageClientImpl.class.getName()+"-ASYNC", 1, false, false);
    
    private static final int DELAY = 500;

    private static final Map<FileObject, Map<BackgroundTask, RequestProcessor.Task>> backgroundTasks = new WeakHashMap<>();

    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    public static synchronized void addBackgroundTask(FileObject file, BackgroundTask task) {
        RequestProcessor.Task req = WORKER.create(() -> {
            LSPBindings bindings = LSPBindingFactory.getBindingForFile(file);

            if (bindings == null)
                return ;

            task.run(bindings, file);
        });

        backgroundTasks.computeIfAbsent(file, f -> new LinkedHashMap<>()).put(task, req);
        scheduleBackgroundTask(req);
    }
    
    
    public static synchronized void removeBackgroundTask(FileObject file, BackgroundTask task) {
        RequestProcessor.Task req = backgroundTasksMapFor(file).remove(task);

        if (req != null) {
            req.cancel();
        }
    }
    
    public static void runOnBackground(Runnable r) {
        WORKER.post(r);
    }

    private static void scheduleBackgroundTask(RequestProcessor.Task req) {
        WORKER.post(req, DELAY);
    }

    public static synchronized void rescheduleBackgroundTask(FileObject file, BackgroundTask task) {
        RequestProcessor.Task req = backgroundTasksMapFor(file).get(task);

        if (req != null) {
            WORKER.post(req, DELAY);
        }
    }

    public static synchronized void scheduleBackgroundTasks(FileObject file) {
        backgroundTasksMapFor(file).values().stream().forEach(LSPWorkingPool::scheduleBackgroundTask);
    }

    private static Map<BackgroundTask, RequestProcessor.Task> backgroundTasksMapFor(FileObject file) {
        return backgroundTasks.computeIfAbsent(file, f -> new IdentityHashMap<>());
    }
    
    
    
    public interface BackgroundTask {
        public void run(LSPBindings bindings, FileObject file);
    }
    
}
