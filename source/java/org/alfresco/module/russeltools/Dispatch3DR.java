/*
Copyright (c) 2012-2013, Eduworks Corporation. All rights reserved.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 
02110-1301 USA
*/
package org.alfresco.module.russeltools;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.io.IOUtils;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public class Dispatch3DR extends AbstractWebScript {	

	// ***************** BEGIN CUSTOMIZATION AREA ******************************************************************//
	// By default, a RUSSEL installation uses the Russel.Project@adlnet.gov account on 3DR.  You can change your
	// installation to use a different account by editing these parameters.
	//
		// The following string constant should be replaced with your ADL 3DR API Key. 
		private static final String ADL_3DR_API_KEY = "D5-7A-EA";
		// The following string constant should be replaced with your ADL 3DR Base64-encoded login credentials.
		private static final String ADL_3DR_AUTHORIZATION = "cnVzc2VsLnByb2plY3RAYWRsbmV0LmdvdjpSdXNzZWwzRFIh";
	//
	// ***************** END CUSTOMIZATION AREA ********************************************************************//

	
	// ***************** EDIT BELOW AT YOUR OWN RISK ***************************************************************//
	private static final String ADL_3DR_ROOT = "https://3dr.adlnet.gov/api/rest/";
	private static final String ADL_SEARCH = ADL_3DR_ROOT + "Search/%s/json?ID="+ADL_3DR_API_KEY;
	private static final String ADL_METADATA = ADL_3DR_ROOT + "/%s/Metadata/json?ID="+ADL_3DR_API_KEY;
	private static final String ADL_UPLOAD = ADL_3DR_ROOT + "/UploadModel?ID="+ADL_3DR_API_KEY;
	private static final String ADL_REVIEWS = ADL_3DR_ROOT + "/%s/Reviews/json?ID="+ADL_3DR_API_KEY;
	

	
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
		else if (action.equalsIgnoreCase("reviews"))
			targetURL = String.format(ADL_REVIEWS, incomingAlfresco.getParameter("id"));
		else if (action.equalsIgnoreCase("uploadReview")) {
			httpVerb = "POST";
			targetURL = String.format(ADL_REVIEWS, incomingAlfresco.getParameter("id"));
			body = IOUtils.toByteArray(incomingAlfresco.getContent().getInputStream());
		} else if (action.equalsIgnoreCase("upload")) {
			body = IOUtils.toByteArray(incomingAlfresco.getContent().getInputStream());
			targetURL = ADL_UPLOAD;
			httpVerb = "POST";
		} else
			throw new WebScriptException("invalid action");
		
		URLConnection connection = new URL(targetURL).openConnection();
		connection.setRequestProperty("Accept-Charset", "UTF-8");
		if (httpVerb.equalsIgnoreCase("POST")) {
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type","text/plain;charset=UTF-8");
			connection.setRequestProperty("Authorization", "Basic " + ADL_3DR_AUTHORIZATION);
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

