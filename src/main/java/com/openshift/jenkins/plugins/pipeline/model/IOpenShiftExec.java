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

public interface IOpenShiftExec extends IOpenShiftPlugin {

	String DISPLAY_NAME = "OpenShift Exec";

	String getPod();

	String getContainer();

	String getCommand();

	List<Argument> getArguments();

	String getWaitTime();

	default String getDisplayName() {
		return DISPLAY_NAME;
	}

	String getWaitTime(Map<String, String> overrides);

	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) {
		listener.getLogger().println(String.format(MessageConstants.START_EXEC, DISPLAY_NAME, getNamespace(overrides)));
		List<String> fullCommand = new ArrayList<>();
		fullCommand.add(getCommand());
		getArguments().forEach( a -> fullCommand.add( a.getValue() ) );

		IPodExec.Options options = new IPodExec.Options();
		if ( !getContainer().isEmpty() ) {
			options.container( getContainer() );
		}

		// get oc client
		IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
		if ( client == null ) {
			listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_BAD, DISPLAY_NAME, "Unable to obtain rest client"));
			return false;
		}

		int remainingWaitTime = Integer.parseInt( getWaitTime( overrides ) );

		final int LOOP_DELAY = 1000;

		IPod p = null;
		for ( int retries = 10; p == null; retries-- ) {
			try {
				p = client.get( ResourceKind.POD, getPod(), getNamespace());
			} catch ( Exception e ) {
				if ( retries == 1 || remainingWaitTime < 0 ) {
					listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_BAD, DISPLAY_NAME, "Unable to find pod: " + getPod()));
					e.printStackTrace( listener.getLogger() );
					return false;
				}
				try {
					Thread.sleep( LOOP_DELAY );
					remainingWaitTime -= LOOP_DELAY;
				} catch ( InterruptedException ie ) {}
			}
		}

		final CountDownLatch latch = new CountDownLatch(1);

		IStoppable exec = p.accept(new CapabilityVisitor<IPodExec, IStoppable>() {
			@Override
			public IStoppable visit(IPodExec capability) {
				return capability.start( new IPodExec.IPodExecOutputListener() {

					@Override
					public void onOpen() {
						listener.getLogger().println( "Connection opened for exec operation" );
					}

					@Override
					public void onStdOut(String message) {
						listener.getLogger().println( "stdout> " + message );
					}

					@Override
					public void onStdErr(String message) {
						listener.getLogger().println( "stderr> " + message );
					}

					@Override
					public void onExecErr(String message) {
						listener.error( "Error during exec: " + message );
					}

					@Override
					public void onClose(int code, String reason) {
						listener.getLogger().printf( "Connection closed for exec operation [%d]: %s\n", code, reason  );
						latch.countDown();
					}

					@Override
					public void onFailure(IOException e) {
						listener.error( "Failure during exec: " + e.getMessage() );
						e.printStackTrace();
						latch.countDown();
					}

				}, options, fullCommand.toArray(new String[]{}) );
			}
		}, null);

		try {
			latch.await( Math.max( remainingWaitTime, 1 ), TimeUnit.MILLISECONDS );
		} catch ( InterruptedException ie ) {}

		listener.getLogger().println(String.format(MessageConstants.EXIT_EXEC_GOOD, DISPLAY_NAME));
		return true;
	}


}
