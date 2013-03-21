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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.io.IOUtils;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public class DispatchFLR extends AbstractWebScript {
	
	// ***************** BEGIN CUSTOMIZATION AREA ******************************************************************//
	// By default, a RUSSEL installation uses the Russel.Project@adlnet.gov account on FLR Sandbox.  You can change your
	// installation to use a different account by editing these parameters.
	//
		// The following string constant should be replaced with the desired FLR publish URL.
		private static final String ADL_FLR_PUBLISH_URL = "http://sandbox.learningregistry.org/publish";
		// The following string constant should be replaced with your Base64-encoded login credentials on the ADL_FLR_PUBLISH_URL.
		//private static final String ADL_FLR_AUTHORIZATION = "b2ZmaWNlQGVkdXdvcmtzLmNvbTplZHUyMDEzd29ya3M=";
		private static final String ADL_FLR_AUTHORIZATION = "cnVzc3NlbC5wcm9qZWN0QGFkbG5ldC5nb3Y6UnVzc2VsRkxSIQ==";
		
	//
	// ***************** END CUSTOMIZATION AREA ********************************************************************//

	
	// ***************** EDIT BELOW AT YOUR OWN RISK ***************************************************************//
	@Override 
	public void execute(WebScriptRequest incomingAlfresco, WebScriptResponse outgoingAlfresco) throws IOException {

		InputStream body = incomingAlfresco.getContent().getInputStream();
		
		String targetURL = ADL_FLR_PUBLISH_URL;
		
		URLConnection connection = new URL(targetURL).openConnection();
		connection.setRequestProperty("Accept-Charset", "UTF-8");

		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type","application/json;charset=UTF-8");
		connection.setRequestProperty("Authorization", "Basic " + ADL_FLR_AUTHORIZATION);
		OutputStream outgoingPost = connection.getOutputStream();

		IOUtils.copy(body, outgoingPost);
		outgoingPost.flush();
		outgoingPost.close();
		
		InputStream incomingResponse = connection.getInputStream();
		outgoingAlfresco.getWriter().write(new String (IOUtils.toByteArray(incomingResponse), "UTF-8"));
		
		incomingResponse.close();
		outgoingAlfresco.getWriter().flush();
		outgoingAlfresco.getWriter().close();
	}
}
