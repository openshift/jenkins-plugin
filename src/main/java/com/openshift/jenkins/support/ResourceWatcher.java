package com.openshift.jenkins.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.openshift.restclient.IClient;
import com.openshift.restclient.IOpenShiftWatchListener;
import com.openshift.restclient.IWatcher;
import com.openshift.restclient.capability.IStoppable;
import com.openshift.restclient.model.IResource;

/**
 * Watcher to manage watching of a specific resource kind
 * and reconnect when the server timeouts the watch.
 * @author jeff.cantrill
 *
 */
public class ResourceWatcher implements IStoppable{
    
    private final Duration maxTimeReconnect; 
    private final ExecutorService threadService;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicReference<IWatcher> stop = new AtomicReference<IWatcher>(null);
    private final IOpenShiftWatchListener listener;
    private final IClient client;
    private final String namespace;
    private final String kind;

    @SuppressWarnings("rawtypes")
    private final AtomicReference<Future> task = new AtomicReference<Future>(null);
    private final long startTimeMillis;
    
    /**
     * The callback handler is wrappered and will restart on error or disconnect before calling
     * the listener that is passed to the watcher
     * @param startTime          The time to use as the base for starting the watch
     * @param maxTimeReconnect   The duration to reconnect before giving up
     * @param client             The client to use to start the watch
     * @param namespace          The namespace to watch
     * @param kind               The resource kind to watch
     * @param listener           callback handler
     */
    public ResourceWatcher(long startTime, Duration maxTimeReconnect, IClient client, String namespace, String kind, IOpenShiftWatchListener listener) {
        this.maxTimeReconnect = maxTimeReconnect;
        this.startTimeMillis = startTime;
        this.client = client;
        this.namespace = namespace;
        this.kind = kind;
        this.threadService = Executors.newSingleThreadExecutor();
        this.listener = new IOpenShiftWatchListener(){

            @Override
            public void connected(List<IResource> resources) {
                listener.connected(resources);
            }

            @Override
            public void disconnected() {
                restart();
                listener.disconnected();
            }

            @Override
            public void received(IResource resource, ChangeType change) {
                listener.received(resource, change);
            }

            @Override
            public void error(Throwable err) {
                restart();
                listener.error(err);
            }
            
        };
    }

    /**
     * Begin the watch
     * @return  A handle to stop the watch if it can be stopped
     */
    public IStoppable watch() {
        if(stop.get() == null) {
            restart();
        }
        return this;
    }
    
    private void restart() {
        if(System.currentTimeMillis() - startTimeMillis < maxTimeReconnect.toMillis() && !stopping.get()) {
            task.set(threadService.submit(runner));
        }
    }
    
   private Runnable runner = new Runnable() {
        
        @Override
        public void run() {
            stop.set(client.watch(namespace, listener, kind));
        }
    };
    
    @Override
    public void stop() {
        stopping.set(true);
        if(task.get() != null) {
            task.get().cancel(true);
        }
        if(stop.get() != null){
            stop.get().stop();
            stop.set(null);
        }
        stopping.set(false);
    }
}
