package com.openshift.jenkins.support;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.IStoppable;
import com.openshift.restclient.capability.resources.IPodLogRetrievalAsync;
import com.openshift.restclient.model.IPod;

import hudson.model.TaskListener;

/**
 * A manager class to watch logs
 * from a pod and restart if disconnected
 * 
 * @author jeff.cantrill
 *
 */
public class PodLogWatcher implements IStoppable{

    private final TaskListener listener;
    private final boolean needToFollow;
    private final IPod pod;
    private final Duration maxTimeReconnect; 
    private final ExecutorService threadService;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicReference<IStoppable> stop = new AtomicReference<IStoppable>(null);
    @SuppressWarnings("rawtypes")
    private final AtomicReference<Future> task = new AtomicReference<Future>(null);
    
    private final String container;
    private final long startTimeMillis;

    /**
     * 
     * @param listener         A listener that will receive the log messages
     * @param follow           True if the watcher should follow the logs
     * @param pod              The pod from which to retrieve logs
     * @param startTime        The time in millis the build started
     * @param maxTimeReconnect The maximum duration to reconnect before watcher gives up.
     */
    public PodLogWatcher(TaskListener listener, boolean follow, IPod pod, long startTime, Duration maxTimeReconnect) {
        this.listener = listener;
        this.needToFollow = follow;
        this.pod = pod;
        this.maxTimeReconnect = maxTimeReconnect;
        this.startTimeMillis = startTime;
        this.threadService = Executors.newSingleThreadExecutor();
        this.container = pod.getContainers().iterator().next().getName();
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
            stop.set(pod.accept(new CapabilityVisitor<IPodLogRetrievalAsync, IStoppable>() {
                @Override
                public IStoppable visit(IPodLogRetrievalAsync capability) {
                    return capability.start(new IPodLogRetrievalAsync.IPodLogListener() {
                        @Override
                        public void onOpen() {
                        }
                        
                        @Override
                        public void onMessage(String message) {
                            listener.getLogger().print(message);
                        }
                        
                        @Override
                        public void onClose(int code, String reason) {
                            restart();
                        }
                        
                        @Override
                        public void onFailure(IOException e) {
                            restart();
                        }
                    }, new IPodLogRetrievalAsync.Options()
                            .follow(needToFollow)
                            .container(container));
                }
            }, null));
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
