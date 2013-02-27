package org.alfresco.module.russeltools;

import java.io.IOException;

import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public class IECompatibility extends AbstractWebScript {	
	private ServiceRegistry registry;
	private Repository repository;	
	private static final String NODE = "node";
	
	// for Spring injection 
	public void setRepository(Repository repository) {
		this.repository = repository;
	}

	// for Spring injection 
	public void setServiceRegistry(ServiceRegistry registry) {
		this.registry = registry;
	}
	
	@Override
	public void execute(final WebScriptRequest incoming, final WebScriptResponse outgoing)
			throws IOException {
		
		
	}
}
