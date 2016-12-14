package com.openshift.jenkins.plugins.pipeline;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

@Extension
public class OpenShiftItemListener extends ItemListener {

    public OpenShiftItemListener() {
    }

    private void updateRootURLConfig() {
        if (Jenkins.getInstance().getRootUrl() != null
                && JenkinsLocationConfiguration.get().getUrl() == null) {
            JenkinsLocationConfiguration.get().setUrl(
                    Jenkins.getInstance().getRootUrl());
        }
    }

    @Override
    public void onCreated(Item item) {
        updateRootURLConfig();
        super.onCreated(item);
    }

    @Override
    public void onCopied(Item src, Item item) {
        updateRootURLConfig();
        super.onCopied(src, item);
    }

    @Override
    public void onLoaded() {
        updateRootURLConfig();
        super.onLoaded();
    }

    @Override
    public void onDeleted(Item item) {
        updateRootURLConfig();
        super.onDeleted(item);
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        updateRootURLConfig();
        super.onRenamed(item, oldName, newName);
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName,
            String newFullName) {
        updateRootURLConfig();
        super.onLocationChanged(item, oldFullName, newFullName);
    }

    @Override
    public void onUpdated(Item item) {
        updateRootURLConfig();
    }

}
