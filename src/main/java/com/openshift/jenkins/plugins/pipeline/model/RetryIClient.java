package com.openshift.jenkins.plugins.pipeline.model;

import hudson.model.TaskListener;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.openshift.internal.restclient.DefaultClient;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.BadRequestException;
import com.openshift.restclient.IClient;
import com.openshift.restclient.IOpenShiftWatchListener;
import com.openshift.restclient.IResourceFactory;
import com.openshift.restclient.IWatcher;
import com.openshift.restclient.NotFoundException;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.UnsupportedVersionException;
import com.openshift.restclient.api.ITypeFactory;
import com.openshift.restclient.authorization.IAuthorizationContext;
import com.openshift.restclient.authorization.ResourceForbiddenException;
import com.openshift.restclient.authorization.UnauthorizedException;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.ICapability;
import com.openshift.restclient.model.IList;
import com.openshift.restclient.model.IResource;
import com.openshift.restclient.model.JSONSerializeable;

public class RetryIClient implements IClient {
    
    private static int MAX_RETRY = 3;

    private final IClient client;
    private final TaskListener listener;
    
    private OpenShiftException handleError(Throwable t, Object call) throws OpenShiftException {
        OpenShiftException ose = null;
        if ((t instanceof BadRequestException) ||
            (t instanceof ResourceForbiddenException) ||
            (t instanceof UnauthorizedException) ||
            (t instanceof NotFoundException)) {
            throw (OpenShiftException)t;                    
        }
        if (!(t instanceof OpenShiftException)) {
            ose = new OpenShiftException(t, "retry", call);
        } else {
            ose = (OpenShiftException)t;
        }
        // rc=409, conflict, object modified currently does not have a special case exception in
        // the rest client; for now, look for the expected text in the message
        if (ose.getMessage().contains("409") && ose.getMessage().contains("response code"))
            throw ose;
        if (ose.getCause() != null && ose.getCause().getMessage().contains("409") && ose.getCause().getMessage().contains("response code"))
            throw ose;
        
        listener.getLogger().println(String.format(MessageConstants.RETRY, t.getMessage()));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new OpenShiftException(e, "retry", call);
        }
        return ose;
    }
    
    private Object retry(Callable<Object> call) throws OpenShiftException {
        int retryCount = 0;
        OpenShiftException ose = null;
        while (retryCount < MAX_RETRY) {
            try {
                Object o = call.call();
                return o;
            } catch (Throwable t) {
                retryCount++;
                ose = handleError(t, call);
            }
        }
        if (ose.getCause() != null)
            listener.getLogger().println(String.format(MessageConstants.GIVE_UP_RETRY, ose.getCause().getMessage()));
        else 
            listener.getLogger().println(String.format(MessageConstants.GIVE_UP_RETRY, ose.getMessage()));
        throw ose;
    }
    
    private void retry(Runnable call) throws OpenShiftException {
        int retryCount = 0;
        OpenShiftException ose = null;
        while (retryCount < MAX_RETRY) {
            try {
                call.run();
                return;
            } catch (Throwable t) {
                retryCount++;
                ose = handleError(t, call);
            }
        }
        if (ose.getCause() != null)
            listener.getLogger().println(String.format(MessageConstants.GIVE_UP_RETRY, ose.getCause().getMessage()));
        else 
            listener.getLogger().println(String.format(MessageConstants.GIVE_UP_RETRY, ose.getMessage()));
        throw ose;
    }
    
    public RetryIClient(IClient c, TaskListener l) {
        client = c;
        listener = l;
    }
    
    // some of the error handling, core request building, and auth retrieval in restclient needs the impl class ... 
    // does not come into play with actual REST interactions
    public DefaultClient getDefaultClient() {
        return (DefaultClient)client;
    }

    @Override
    public <T extends ICapability> T getCapability(Class<T> capability) {
        return client.getCapability(capability);
    }

    @Override
    public boolean supports(Class<? extends ICapability> capability) {
        return client.supports(capability);
    }

    @Override
    public <T extends ICapability, R> R accept(CapabilityVisitor<T, R> visitor,
            R unsupportedCapabililityValue) {
        return client.accept(visitor, unsupportedCapabililityValue);
    }

    @Override
    public IWatcher watch(String namespace, IOpenShiftWatchListener listener,
            String... kinds) {
        return client.watch(namespace, listener, kinds);
    }

    @Override
    public IWatcher watch(IOpenShiftWatchListener listener, String... kinds) {
        return client.watch(listener, kinds);
    }

    @Override
    public <T extends IResource> List<T> list(String kind) {
        return (List<T>) retry(() -> client.list(kind));
    }

    @Override
    public <T extends IResource> List<T> list(String kind,
            Map<String, String> labels) {
        return (List<T>) retry(() -> client.list(kind, labels));
    }

    @Override
    public <T extends IResource> List<T> list(String kind, String namespace) {
        return (List<T>) retry(() -> client.list(kind, namespace));
    }

    @Override
    public <T extends IResource> List<T> list(String kind, String namespace,
            Map<String, String> labels) {
        return (List<T>) retry(() -> client.list(kind, namespace, labels));
    }

    @Override
    public <T extends IResource> List<T> list(String kind, String namespace,
            String labelQuery) {
        return (List<T>) retry(() -> client.list(kind, namespace, labelQuery));
    }

    @Override
    public <T extends IResource> T get(String kind, String name,
            String namespace) {
        return (T) retry(() -> client.get(kind, name, namespace));
    }

    @Override
    public IList get(String kind, String namespace) {
        return (IList) retry(() -> client.get(kind, namespace));
    }

    @Override
    public <T extends IResource> T create(T resource) {
        return (T) retry(() -> client.create(resource));
    }

    @Override
    public <T extends IResource> T create(T resource, String namespace) {
        return (T) retry(() -> client.create(resource, namespace));
    }

    @Override
    public <T extends IResource> T create(String kind, String namespace,
            String name, String subresource, IResource payload) {
        return (T) retry(() -> client.create(kind, namespace, name, subresource, payload));
    }

    @Override
    public Collection<IResource> create(IList list, String namespace) {
        return (Collection<IResource>) retry(() -> client.create(list, namespace));
    }

    @Override
    public <T extends IResource> T update(T resource) {
        return (T) retry(() -> client.update(resource));
    }

    @Override
    public <T extends IResource> void delete(T resource) {
        retry(() -> client.delete(resource));
    }

    @Override
    public <T extends IResource> T execute(String httpMethod, String kind,
            String namespace, String name, String subresource, IResource payload) {
        return (T) retry(() -> client.execute(httpMethod, kind, namespace, name, subresource, payload));
    }

    @Override
    public <T extends IResource> T execute(String httpMethod, String kind,
            String namespace, String name, String subresource,
            IResource payload, Map<String, String> params) {
        return (T) retry(() -> client.execute(httpMethod, kind, namespace, name, subresource, payload, params));
    }

    @Override
    public <T extends IResource> T execute(String httpMethod, String kind,
            String namespace, String name, String subresource,
            IResource payload, String subcontext) {
        return (T) retry(() -> client.execute(httpMethod, kind, namespace, name, subresource, payload, subcontext));
    }

    @Override
    public <T> T execute(ITypeFactory factory, String httpMethod, String kind,
            String namespace, String name, String subresource,
            String subContext, JSONSerializeable payload,
            Map<String, String> params) {
        return (T) retry(() -> client.execute(factory, httpMethod, kind, namespace, name, subresource, subContext, payload, params));
    }

    @Override
    public URL getBaseURL() {
        return client.getBaseURL();
    }

    @Override
    public String getResourceURI(IResource resource) {
        return client.getResourceURI(resource);
    }

    @Override
    public String getOpenShiftAPIVersion() throws UnsupportedVersionException {
        return client.getOpenShiftAPIVersion();
    }

    @Override
    public IAuthorizationContext getAuthorizationContext() {
        return client.getAuthorizationContext();
    }

    @Override
    public IResourceFactory getResourceFactory() {
        return client.getResourceFactory();
    }

    @Override
    public String getServerReadyStatus() {
        return (String) retry(() -> client.getServerReadyStatus());
    }

    @Override
    public IClient clone() {
        return new RetryIClient(client.clone(), listener);
    }

}
