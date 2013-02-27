package org.alfresco.module.russeltools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.io.IOUtils;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public class Dispatch3DR extends AbstractWebScript {	
	private static final String ADL_3DR_ROOT = "https://3dr.adlnet.gov/api/rest/";
	private static final String ADL_SEARCH = ADL_3DR_ROOT + "Search/%s/json/?ID=00-00-00";
	private static final String ADL_METADATA = ADL_3DR_ROOT + "/%s/Metadata/json?ID=00-00-00";
	private static final String ADL_UPLOAD = ADL_3DR_ROOT + "/UploadModel?ID=00-00-00";
	
	@Override 
	public void execute(WebScriptRequest incomingAlfresco, WebScriptResponse outgoingAlfresco) throws IOException {

		byte[] body = null;
		String action = incomingAlfresco.getParameter("action");
		String targetURL = "";
		String httpVerb = "GET";
		if (action.equalsIgnoreCase("search"))
			targetURL = String.format(ADL_SEARCH, incomingAlfresco.getParameter("terms"));
		else if (action.equalsIgnoreCase("metadata"))
			targetURL = String.format(ADL_METADATA, incomingAlfresco.getParameter("id"));
		else if (action.equalsIgnoreCase("upload")) {
			body = IOUtils.toByteArray(incomingAlfresco.getContent().getInputStream());
			targetURL = ADL_UPLOAD;
			httpVerb = "POST";
		} else
			throw new WebScriptException("invalid action");
		
		URLConnection connection = new URL(targetURL).openConnection();
		connection.setRequestProperty("Accept-Charset", "UTF-8");
		if (httpVerb.equalsIgnoreCase("POST")) {
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
			OutputStream outgoingPost = connection.getOutputStream();
			outgoingPost.write(body);
			outgoingPost.flush();
			outgoingPost.close();
		}
		
		outgoingAlfresco.getWriter().write(IOUtils.toString(connection.getInputStream()));
		outgoingAlfresco.getWriter().flush();
		outgoingAlfresco.getWriter().close();
	}
}

