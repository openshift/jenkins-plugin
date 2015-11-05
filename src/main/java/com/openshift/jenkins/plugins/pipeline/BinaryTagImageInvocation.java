package com.openshift.jenkins.plugins.pipeline;

import java.io.InputStream;
import java.io.SequenceInputStream;

import org.apache.commons.lang.StringUtils;

import com.openshift.internal.restclient.capability.resources.AbstractOpenShiftBinaryCapability;
import com.openshift.restclient.IClient;
import com.openshift.restclient.capability.resources.IPodLogRetrieval;
import com.trilead.ssh2.util.IOUtils;

public class BinaryTagImageInvocation extends AbstractOpenShiftBinaryCapability implements IPodLogRetrieval {

	private final String testTag;
	private final String prodTag;
	private final String nameSpace;
	private StringBuilder args;
	
	public BinaryTagImageInvocation(String testTag, String prodTag, String nameSpace, IClient client) {
		super(client);
		this.testTag = testTag;
		this.prodTag = prodTag;
		this.nameSpace = nameSpace;
	}
	
	@Override
	public boolean isSupported() {
		return true;
	}

	@Override
	public String getName() {
		return BinaryTagImageInvocation.class.getSimpleName();
	}

	@Override
	protected void cleanup() {
		if (getProcess() != null) {
			IOUtils.closeQuietly(getProcess().getInputStream());
			IOUtils.closeQuietly(getProcess().getErrorStream());
		}
	}

	@Override
	protected boolean validate() {
		return true;
	}

	@Override
	protected String[] buildArgs(String location) {
		args = new StringBuilder(location);
		String sec = " --insecure-skip-tls-verify=true ";
		if (Auth.useCert())
			sec = Auth.CERT_ARG;
		args.append(" -n ").append(nameSpace).append(" tag ")
			.append(sec)
			.append(" --server=").append(getClient().getBaseURL()).append(" ")
			.append(" ").append(testTag).append(" ")
			.append(" ").append(prodTag).append(" ");
		addToken(args);
		return StringUtils.split(args.toString(), " ");
	}
	
	public String getArgs() {
		return args.toString();
	}

	@Override
	public InputStream getLogs(boolean follow) {
		start();
		SequenceInputStream is = new SequenceInputStream(getProcess().getInputStream(), getProcess().getErrorStream());
		return is;
	}

}
