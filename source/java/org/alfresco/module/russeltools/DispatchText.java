package org.alfresco.module.russeltools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public class DispatchText extends AbstractWebScript {
	private ServiceRegistry registry;
	private Repository repository;	
	
	// for Spring injection 
	public void setRepository(Repository repository) {
		this.repository = repository;
	}

	// for Spring injection 
	public void setServiceRegistry(ServiceRegistry registry) {
		this.registry = registry;
	}
	
	@Override 
	public void execute(WebScriptRequest incomingAlfresco, WebScriptResponse outgoingAlfresco) throws IOException {

		InputStream body = incomingAlfresco.getContent().getInputStream();
		
		String targetURL = incomingAlfresco.getParameter("targetURL");
		String httpType = incomingAlfresco.getParameter("httpType");
		
		URLConnection connection = new URL(targetURL).openConnection();
		connection.setRequestProperty("Accept-Charset", "UTF-8");
		if (httpType.equalsIgnoreCase("POST")) {
			connection.setDoOutput(true);
			OutputStream outgoingPost = connection.getOutputStream();
			connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
			int bodySize = body.available();
			for (int bodyIndex=0;bodyIndex<bodySize;bodyIndex++)
				outgoingPost.write((byte)body.read());
			outgoingPost.flush();
			outgoingPost.close();
		}
		
		InputStream incomingResponse = connection.getInputStream();
		int readData;
		while ((readData=incomingResponse.read())!=-1)
			outgoingAlfresco.getWriter().write(readData);
		outgoingAlfresco.getWriter().flush();
		outgoingAlfresco.getWriter().close();
	}
}
