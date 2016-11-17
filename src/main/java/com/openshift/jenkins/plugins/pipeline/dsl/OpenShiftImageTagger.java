package com.openshift.jenkins.plugins.pipeline.dsl;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftImageTagger;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collection;
import java.util.Map;

public class OpenShiftImageTagger extends OpenShiftBaseStep implements IOpenShiftImageTagger {
	
	// started the process with this class of moving from testTag/Stream to srcTag/Stream and from prodTag/Stream to destTag/Stream
    protected final String srcTag;
    protected final String destTag;
    protected final String srcStream;
    protected final String destStream;
    protected String destinationNamespace;
    protected String destinationAuthToken;
    protected String alias;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftImageTagger(String srcStream, String srcTag, String destStream, String destTag) {
        this.srcStream = srcStream != null ? srcStream.trim() : null;
        this.srcTag = srcTag != null ? srcTag.trim() : null;
        this.destStream = destStream != null ? destStream.trim() : null;
        this.destTag = destTag != null ? destTag.trim() : null;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getAlias() {
		return alias;
	}

	@DataBoundSetter public void setAlias(String alias) {
		this.alias = alias != null ? alias.trim() : null;
	}
	
	@Deprecated
	public String getTestTag() {
		return srcTag;
	}
	
	public String getSrcTag() {
		return srcTag;
	}

	@Deprecated
	public String getProdTag() {
		return destTag;
	}
	
	public String getDestTag() {
		return destTag;
	}
	
	@Deprecated
	public String getTestStream() {
		return srcStream;
	}
	
	public String getSrcStream() {
		return srcStream;
	}

	@Deprecated
	public String getProdStream() {
		return destStream;
	}
	
	public String getDestStream() {
		return destStream;
	}

	public String getDestinationNamespace() {
		return this.destinationNamespace;
	}
	
	@DataBoundSetter public void setDestinationNamespace(String destinationNamespace) {
		this.destinationNamespace = destinationNamespace != null ? destinationNamespace.trim() : null;
	}
	
	public String getDestinationAuthToken() {
		return this.destinationAuthToken;
	}
	
	@DataBoundSetter public void setDestinationAuthToken(String destinationAuthToken) {
		this.destinationAuthToken = destinationNamespace != null ? destinationAuthToken.trim() : null;
	}
	
	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftImageTaggerExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftTag";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("sourceStream") &&
            	!arguments.containsKey("destinationStream") &&
            	!arguments.containsKey("sourceTag") &&
            	!arguments.containsKey("destinationTag") &&
            	!arguments.containsKey("destStream") &&
            	!arguments.containsKey("destTag") &&
            	!arguments.containsKey("srcTag") &&
            	!arguments.containsKey("destStream") &&
            	!arguments.containsKey("srcStream"))
            	throw new IllegalArgumentException("need to specify sourceStream, sourceTag, destinationStream, destinationTag");
            
            Object srcStream = arguments.get("sourceStream");
            Object srcTag = arguments.get("sourceTag");
            Object destStream = arguments.get("destinationStream");
            Object destTag = arguments.get("destinationTag");
            
            if (srcStream == null || srcStream.toString().trim().length() == 0)
            	srcStream = arguments.get("srcStream");
            if (srcTag == null || srcTag.toString().trim().length() == 0)
            	srcTag = arguments.get("srcTag");
            if (destStream == null || destStream.toString().trim().length() == 0)
            	destStream = arguments.get("destStream");
            if (destTag == null || destTag.toString().trim().length() == 0)
            	destTag = arguments.get("destTag");
            
            if (srcStream == null || srcStream.toString().trim().length() == 0 ||
            	srcTag == null || srcTag.toString().trim().length() == 0 ||
            	destStream == null || destStream.toString().trim().length() == 0 ||
            	destTag == null || destTag.toString().trim().length() == 0)
            	throw new IllegalArgumentException("need to specify sourceStream, sourceTag, destinationStream, destinationTag");
            
            OpenShiftImageTagger step = 
            		new OpenShiftImageTagger(srcStream.toString(),
            								 srcTag.toString(),
            								 destStream.toString(),
            								 destTag.toString());
            if(arguments.containsKey("alias")) {
                Object alias = arguments.get("alias");
                if (alias != null) {
                    step.setAlias(alias.toString());
                }
            }
            if (arguments.containsKey("destinationNamespace")) {
                Object destinationNamespace = arguments.get("destinationNamespace");
                if (destinationNamespace != null) {
                    step.setDestinationNamespace(destinationNamespace.toString());
                }
            }
            if (arguments.containsKey("destinationAuthToken")) {
                Object destinationAuthToken = arguments.get("destinationAuthToken");
                if (destinationAuthToken != null) {
                    step.setDestinationAuthToken(destinationAuthToken.toString());
                }
            }
            
            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }

	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		return true;
	}

	@Override
	public Action getProjectAction(AbstractProject<?, ?> project) {
		return null;
	}

	@Override
	public Collection<? extends Action> getProjectActions(
			AbstractProject<?, ?> project) {
		return null;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return null;
	}    
    
}

