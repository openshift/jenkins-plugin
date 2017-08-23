package com.openshift.jenkins.plugins.pipeline;

import hudson.scm.SCMRevisionState;

public class ImageStreamRevisionState extends SCMRevisionState implements
        Comparable<ImageStreamRevisionState> {

    private final String commitId;

    public ImageStreamRevisionState(String commitId) {
        if (commitId == null)
            throw new IllegalArgumentException("nulls not allowed");
        this.commitId = commitId.trim();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((commitId == null) ? 0 : commitId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ImageStreamRevisionState other = (ImageStreamRevisionState) obj;
        // remove some code from auto-generated equals since we force commitId
        // to be set via constructor
        // if (commitId == null) {
        // if (other.commitId != null)
        // return false;
        /* } else */if (!commitId.equals(other.commitId))
            return false;
        return true;
    }

    @Override
    public int compareTo(ImageStreamRevisionState o) {
        if (o == null)
            return 1;
        // constructor enforces commitId to not be null
        return this.commitId.compareTo(o.commitId);
    }

    @Override
    public String toString() {
        return "ImageStreamRevisionState [commitId=" + commitId + "]";
    }

}
