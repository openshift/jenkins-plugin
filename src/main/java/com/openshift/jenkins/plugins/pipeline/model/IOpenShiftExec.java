package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.Argument;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.api.capabilities.IPodExec;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.IStoppable;
import com.openshift.restclient.model.IPod;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public interface IOpenShiftExec extends ITimedOpenShiftPlugin, IPodExec.IPodExecOutputListener {

    String DISPLAY_NAME = "OpenShift Exec";

    String getPod();
    
    default String getPod(Map<String, String> overrides) {
        return getOverride(getPod(), overrides);
    }

    String getContainer();
    
    default String getContainer(Map<String, String> overrides) {
        return getOverride(getContainer(), overrides);
    }

    String getCommand();
    
    default String getCommand(Map<String, String> overrides) {
        return getOverride(getCommand(), overrides);
    }

    List<Argument> getArguments();
    
    default List<Argument> getArguments(Map<String, String> overrides) {
        List<Argument> subs = new ArrayList<Argument>();
        List<Argument> args = getArguments();
        if (args != null)
            args.forEach(a -> subs.add(new Argument(getOverride(a.getValue(),overrides))));
        return subs;
    }

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    default long getGlobalTimeoutConfiguration() {
        return GlobalConfig.getExecWait();
    }

    default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));

        listener.getLogger().println(String.format(MessageConstants.START_EXEC, DISPLAY_NAME, getNamespace(overrides)));
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(getCommand(overrides));
        List<Argument> arguments = getArguments(overrides);
        if ( arguments != null ) {
            arguments.forEach(a -> fullCommand.add(a.getValue()));
        }

        IPodExec.Options options = new IPodExec.Options();
        if (!getContainer(overrides).isEmpty()) {
            options.container(getContainer(overrides));
        }

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
        if (client == null) {
            listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_BAD, DISPLAY_NAME, "Unable to obtain rest client"));
            return false;
        }

        long remainingWaitTime = getTimeout(listener, chatty, overrides);

        final int LOOP_DELAY = 1000;

        IPod p = null;
        for (int retries = 10; p == null; retries--) {
            try {
                p = client.get(ResourceKind.POD, getPod(overrides), getNamespace(overrides));
            } catch (Exception e) {
                if (retries == 1 || remainingWaitTime < 0) {
                    listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_BAD, DISPLAY_NAME, "Unable to find pod: " + getPod(overrides)));
                    e.printStackTrace(listener.getLogger());
                    return false;
                }
                try {
                    Thread.sleep(LOOP_DELAY);
                    remainingWaitTime -= LOOP_DELAY;
                } catch (InterruptedException ie) {
                }
            }
        }

        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicBoolean reportError = new AtomicBoolean(false);

        IStoppable exec = p.accept(new CapabilityVisitor<IPodExec, IStoppable>() {
            @Override
            public IStoppable visit(IPodExec capability) {
                return capability.start(new IPodExec.IPodExecOutputListener() {

                    @Override
                    public void onOpen() {
                        listener.getLogger().println("Connection opened for exec operation");
                        IOpenShiftExec.this.onOpen();
                    }

                    @Override
                    public void onStdOut(String message) {
                        listener.getLogger().println("stdout> " + message);
                        IOpenShiftExec.this.onStdOut(message);
                    }

                    @Override
                    public void onStdErr(String message) {
                        listener.getLogger().println("stderr> " + message);
                        IOpenShiftExec.this.onStdErr(message);
                    }

                    @Override
                    public void onExecErr(String message) {
                        reportError.set(true);
                        listener.error("Error during exec: " + message);
                        IOpenShiftExec.this.onExecErr(message);
                    }

                    @Override
                    public void onClose(int code, String reason) {
                        listener.getLogger().printf("Connection closed for exec operation [%d]: %s\n", code, reason);
                        IOpenShiftExec.this.onClose(code,reason);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(IOException e) {
                        reportError.set(true);
                        listener.error("Failure during exec: " + e.getMessage());
                        IOpenShiftExec.this.onFailure(e);
                        e.printStackTrace();
                        latch.countDown();
                    }

                }, options, fullCommand.toArray(new String[]{}));
            }
        }, null);

        boolean timeout = true;
        try {
            timeout = ! latch.await(Math.max(remainingWaitTime, 1), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
        }

        if ( timeout ) {
            listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_BAD_TIMED_OUT, DISPLAY_NAME ));
            return false;
        }

        if ( reportError.get() ) {
            listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_BAD_API_ERROR, DISPLAY_NAME ));
            return false;
        } else {
            listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_GOOD, DISPLAY_NAME));
            return true;
        }

    }

    default void onOpen() {}

    default void onStdOut(String message) {}

    default void onStdErr(String message) {}

    default void onExecErr(String message) {}

    default void onClose(int code, String reason) {}

    default void onFailure(IOException e) {}

}
