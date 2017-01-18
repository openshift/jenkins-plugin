package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IService;
import hudson.Launcher;
import hudson.model.TaskListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

public interface IOpenShiftServiceVerifier extends IOpenShiftPlugin {
    String DISPLAY_NAME = "Verify OpenShift Service";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getSvcName();

    String getRetryCount();

    String getRetryCount(Map<String, String> overrides);

    default String getSvcName(Map<String, String> overrides) {
        return getOverride(getSvcName(), overrides);
    }

    default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        listener.getLogger().println(String.format(MessageConstants.START_SERVICE_VERIFY, DISPLAY_NAME, getSvcName(overrides), getNamespace(overrides)));

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
        String spec = null;

        if (client != null) {
            // get Service
            IService svc = client.get(ResourceKind.SERVICE, getSvcName(overrides), getNamespace(overrides));
            String ip = svc.getClusterIP();
            int port = svc.getPort();
            spec = ip + ":" + port;
            int tryCount = 0;
            if (chatty)
                listener.getLogger().println("\nOpenShiftServiceVerifier retry " + getRetryCount(overrides));
            listener.getLogger().println(String.format(MessageConstants.SERVICE_CONNECTING, spec));
            while (tryCount < Integer.parseInt(getRetryCount(overrides))) {
                tryCount++;
                if (chatty)
                    listener.getLogger().println("\nOpenShiftServiceVerifier attempt connect to " + spec + " attempt " + tryCount);
                InetSocketAddress address = new InetSocketAddress(ip, port);
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(address, 2500);
                    listener.getLogger().println(String.format(MessageConstants.EXIT_SERVICE_VERIFY_GOOD, DISPLAY_NAME, spec));
                    return true;
                } catch (IOException e) {
                    if (chatty) e.printStackTrace(listener.getLogger());
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e1) {
                    }
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        if (chatty)
                            e.printStackTrace(listener.getLogger());
                    }
                }
            }


        } else {
            return false;
        }

        listener.getLogger().println(String.format(MessageConstants.EXIT_SERVICE_VERIFY_BAD, DISPLAY_NAME, spec));

        return false;
    }
}
