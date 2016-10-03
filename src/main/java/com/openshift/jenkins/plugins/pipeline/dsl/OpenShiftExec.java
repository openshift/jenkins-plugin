package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.Argument;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.GlobalConfig;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftExec;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OpenShiftExec extends OpenShiftBaseStep implements IOpenShiftExec {

    protected String pod;
    protected String container;
    protected String command;
    protected List<Argument> arguments = new ArrayList<>();
    protected String waitTime;

    @DataBoundConstructor
    public OpenShiftExec( String pod ) {
        this.pod = pod;
    }

    @DataBoundSetter
    public void setContainer(String container) {
        this.container = container;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    @DataBoundSetter
    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
    }

    @DataBoundSetter
    public void setWaitTime(String waitTime) {
        this.waitTime = waitTime;
    }

    public String getPod() {
        return pod;
    }

    public String getContainer() {
        return container;
    }

    public String getCommand() {
        return command;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public String getWaitTime() {
        return waitTime;
    }

    public String getWaitTime(Map<String,String> overrides) {
        String val = getOverride(getWaitTime(), overrides);
        if (val.length() > 0)
            return val;
        return Long.toString(GlobalConfig.getBuildWait());
    }

	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftExecExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftExec";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        private Object requiredArgument( Map<String, Object> arguments, String arg ) {
            Object o = arguments.get( arg );
            if ( o == null ) {
                throw new IllegalArgumentException( "Missing required argument: " + arg );
            }
            return o;
        }

        private String argumentAsString(Map<String, Object> arguments, String arg, String def ) {
            Object o = arguments.get( arg );
            if ( o == null ) {
                return def;
            }
            return o.toString();
        }

        private String argumentAsString(Map<String, Object> arguments, String arg ) throws IllegalArgumentException {
            return requiredArgument(arguments, arg ).toString();
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            OpenShiftExec step = new OpenShiftExec( argumentAsString( arguments, "pod" ) );
            step.setContainer( argumentAsString( arguments, "container", "" ) );

            Object commandObject = requiredArgument( arguments, "command" );
            if ( commandObject instanceof String ) { // command: "date"
                step.setCommand( commandObject.toString() );
            } else if ( commandObject instanceof List ) { // command : [ "echo", "hello", ... ]
                List commandList = (List)commandObject;
                Iterator i = commandList.iterator();
                if ( ! i.hasNext() ) {
                    throw new IllegalArgumentException( "Command list cannot be empty" );
                }
                step.setCommand( i.next().toString() );
                List<Argument> commandArgs = new ArrayList<>();
                while ( i.hasNext() ) {
                    commandArgs.add( new Argument( i.next().toString() ) );
                }
                step.setArguments(commandArgs);
            } else {
                throw new IllegalArgumentException( "Unrecognized command syntax. It created type: " + commandObject.getClass().getName() );
            }

            Object argumentsObject = arguments.get( "arguments" );
            if ( argumentsObject != null ) {
                if ( argumentsObject instanceof List ) {
                    List<Argument> commandArgs = new ArrayList<>();
                    for ( Object o : (List)argumentsObject ) {
                        String arg;
                        if ( o instanceof Map ) {
                            Object aObject = ((Map)o).get( "value" );
                            if ( aObject == null ) {
                                throw new IllegalArgumentException( "Expected value entry in arguments map: " + o.toString() );
                            }
                            arg = aObject.toString();
                        } else {
                            arg = o.toString();
                        }
                        commandArgs.add( new Argument( arg ) );
                    }
                    step.setArguments(commandArgs);
                } else {
                    throw new IllegalArgumentException( "Unrecognized arguments syntax. It created type: " + argumentsObject.getClass().getName() );
                }
            }

            step.setWaitTime(argumentAsString( arguments, "waitTime", null));
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
