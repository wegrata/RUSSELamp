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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
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
	public void execute(final WebScriptRequest incomingAlfresco, final WebScriptResponse outgoing)
			throws IOException {
		
		String action = incomingAlfresco.getParameter("action");
		String sType = incomingAlfresco.getParameter("store_type");
		String sId = incomingAlfresco.getParameter("store_id");
		String nId = incomingAlfresco.getParameter("node_id");
		String zipName = incomingAlfresco.getParameter("zipName");
		File fileHandle = null;
		if (action.equalsIgnoreCase("update")) {
			try {
				updateNode(sType, sId, nId, new JSONObject(IOUtils.toString(incomingAlfresco.getContent().getInputStream())));
			} catch (JSONException e) {
				throw new WebScriptException("invalid json structure");
			}
		} else if (action.equalsIgnoreCase("create")) {
			try {
				nId = createNode(new JSONObject(IOUtils.toString(incomingAlfresco.getContent().getInputStream())));
			} catch (JSONException e) {
				throw new WebScriptException("invalid json structure");
			}
		} else if (action.equalsIgnoreCase("get")) {
			fileHandle = File.createTempFile("russel", "russel");
			OutputStream os = new FileOutputStream(fileHandle);
			InputStream contentStream = registry.getFileFolderService().getReader(getNodeRef(sType, sId, nId)).getContentInputStream();
			os.write(IOUtils.toByteArray(contentStream));
			contentStream.close();
			os.flush();
			os.close();
			registry.getFileFolderService().delete(getNodeRef(sType, sId, nId));
		} else
			throw new WebScriptException("invalid action");
		
		if (action.equalsIgnoreCase("get")) {
			outgoing.setContentType("application/zip; charset=UTF-8");
			outgoing.setContentEncoding("UTF-8");
			outgoing.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");
			OutputStream response = outgoing.getOutputStream();
			response.write(IOUtils.toByteArray(new FileInputStream(fileHandle)));
			response.flush();
			response.close();
		} else
			outgoing.getWriter().write("{\"id\":\"" + nId + "\"}");
	}
	
	public void updateNode(String sType, String sId, String nId, JSONObject postData) {
		try {
			ContentWriter cw = registry.getFileFolderService().getWriter(getNodeRef(sType, sId, nId));
			cw.putContent(postData.getString("nodeData"));
		} catch (JSONException e) {
			throw new WebScriptException("nodeData is missing");
		}
	}
	
	public String createNode(JSONObject postData) {
		int i = 0;
		boolean created = false;
		UserTransaction userT = null;
		FileInfo fileEntry = null;
		String filename = "";
		while (!created) {
			try {
				try {
					filename = postData.getString("filename");
				} catch (JSONException e2) {
					throw new WebScriptException("filename is missing");
				}
				userT = registry.getTransactionService().getNonPropagatingUserTransaction();
				if (i!=0&&filename.lastIndexOf(".")!=-1) 
					filename = filename.substring(0, filename.lastIndexOf(".")) + "-" + i + filename.substring(filename.lastIndexOf("."));
				else if (i!=0)
					filename = filename + "-" + i;
				userT.begin();
				fileEntry = registry.getFileFolderService().create(repository.getUserHome(repository.getPerson()),
    															   filename,
																   ContentModel.TYPE_CONTENT);
				registry.getFileFolderService().getWriter(fileEntry.getNodeRef()).putContent(postData.getString("filecontent"));
		        QName titledAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "titled");
		        QName authorAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "author");
		        QName versionAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "versionable");
		        QName russelAspect = QName.createQName("russel.russelMetadata", "metaTest");
		        //QName PROP_QNAME_MY_PROPERTY = QName.createQName("custom.model", "myProperty");
		        Map<QName,Serializable> aspectValues = new HashMap<QName,Serializable>();
		        //aspectValues.put(PROP_QNAME_MY_PROPERTY, value);
		        registry.getNodeService().addAspect(fileEntry.getNodeRef(), titledAspect, aspectValues);
		        registry.getNodeService().addAspect(fileEntry.getNodeRef(), authorAspect, aspectValues);
		        registry.getNodeService().addAspect(fileEntry.getNodeRef(), versionAspect, aspectValues);
		        registry.getNodeService().addAspect(fileEntry.getNodeRef(), russelAspect, aspectValues);
				userT.commit();
    			created = true;
        	} catch (Exception e) {
        		try {
					if (userT!=null)
						userT.rollback();
				} catch (Exception e1) {
					
				}
        		if (i<100000)
    				i++;
    			else
    				new WebScriptException("Failed to create item -100000 bounded ids - " + filename);
			}
		}
		return fileEntry.getNodeRef().getId();
	}
	
	protected NodeRef getNodeRef(String sType, String sId, String nId) {
		// look up the child
		NodeRef nodeRef = null;
		try {
			nodeRef = repository.findNodeRef(NODE, new String[]{sType, sId, nId});
		} catch (Exception ex) {
			throw new WebScriptException("Error unable to locate path " + sType + "://" + sId + "/" + nId);
		}
		if (nodeRef == null) {
			throw new WebScriptException("Object doesn't exist " + sType + "://" + sId + "/" + nId);
		}
		return nodeRef; 
	}
}
