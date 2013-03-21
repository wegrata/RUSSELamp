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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.extensions.webscripts.servlet.FormData;


public class ZIPExport extends AbstractWebScript {	
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
	public void execute(final WebScriptRequest incomingAlfresco, final WebScriptResponse outgoingAlfresco)
			throws IOException {
		try {
			String zipName = incomingAlfresco.getParameter("zipName");
			JSONObject postJSON;
			InputStream postStream = incomingAlfresco.getContent().getInputStream();

			postJSON = new JSONObject(IOUtils.toString(postStream));
			postStream.close();
			
			File fileHandle = File.createTempFile("russel", "russel");
			ZipOutputStream zipStream = new ZipOutputStream(new FileOutputStream(fileHandle));
			createZipEntries(postJSON, zipStream);
			zipStream.close();
			
	        try {
	        	File projectWrapper;
	        	JSONObject rawZipProject = new JSONObject(postJSON.get("projectToZip").toString());
		    	if (getJSONKey(rawZipProject, "projectName")!="") {
		    		projectWrapper = File.createTempFile("russel", "russel");
					ZipOutputStream zipProject = new ZipOutputStream(new FileOutputStream(projectWrapper));
					zipProject.putNextEntry(new ZipEntry(zipName.substring(0, zipName.lastIndexOf(".")) + "SCORM.zip"));
					zipProject.write(IOUtils.toByteArray(new FileInputStream(fileHandle)));
					zipProject.putNextEntry(new ZipEntry(getJSONKey(rawZipProject, "projectName")));
					String nodeId = getJSONKey(rawZipProject, "projectNodeId");
					InputStream content = registry.getFileFolderService().getReader(getNodeRef(nodeId)).getContentInputStream();
					zipProject.write(IOUtils.toByteArray(content));
					zipProject.flush();
					zipProject.close();
					fileHandle = projectWrapper;
		    	}
	        } catch (JSONException e) {}
	        
			FileInputStream fis = new FileInputStream(fileHandle);
			
			int i = 0;
			boolean created = false;
			UserTransaction userT = null;
			FileInfo fileEntry = null;
			String filename = "";
			while (!created) {
				try {
					filename = zipName;
					userT = registry.getTransactionService().getNonPropagatingUserTransaction();
					if (i!=0&&filename.lastIndexOf(".")!=-1) 
						filename = filename.substring(0, filename.lastIndexOf(".")) + "-" + i + filename.substring(filename.lastIndexOf("."));
					else if (i!=0)
						filename = filename + "-" + i;
					userT.begin();
					fileEntry = registry.getFileFolderService().create(repository.getUserHome(repository.getPerson()),
	    															   filename,
																	   ContentModel.TYPE_CONTENT);
					registry.getFileFolderService().getWriter(fileEntry.getNodeRef()).putContent(fis);
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
			
			fis.close();
			outgoingAlfresco.getWriter().write("{\"id\":\"" + fileEntry.getNodeRef().getId() + "\"}");
	     } catch(JSONException e) {
	    	throw new WebScriptException("Unable to serialize JSON");
	     }
	}
	
	protected NodeRef getNodeRef(String nodeRef) {
		// look up the child
		NodeRef nodeHandle = null;
		if (nodeRef=="") return nodeHandle;
		String sType = nodeRef.substring(0, nodeRef.indexOf("://"));
		String sId = nodeRef.substring(nodeRef.indexOf("://")+"://".length(),nodeRef.lastIndexOf("/"));
		String nId = nodeRef.substring(nodeRef.lastIndexOf("/")+1);
		try {
			nodeHandle = repository.findNodeRef(NODE, new String[]{sType, sId, nId});
		} catch (Exception ex) {
			throw new WebScriptException("Error unable to locate path " + sType + "://" + sId + "/" + nId);
		}
		if (nodeHandle == null) {
			throw new WebScriptException("Object doesn't exist " + sType + "://" + sId + "/" + nId);
		}
		return nodeHandle; 
	}
	
	private String getJSONKey(JSONObject jsonObject, String key) {
		String value = "";
		try {
			value = jsonObject.getString(key);
		} catch (JSONException e) {}
		return value;
	}
	
	private void createZipEntries(JSONObject post, ZipOutputStream zipContainer) {
        JSONArray rawZipEntries;
        try {
        	rawZipEntries = post.getJSONArray("mediaToZip");
        } catch (JSONException e) {
        	throw new WebScriptException("malformed json: needs a \"mediaToZip\" key");
        }
        ContentService contentService = registry.getContentService();
        if (rawZipEntries.length()==0)
        	throw new WebScriptException("nothing to zip");

    	for (int entryIndex=0; entryIndex<rawZipEntries.length(); entryIndex++) {
    		JSONObject entry;
    		try {
	        	entry = new JSONObject(rawZipEntries.get(entryIndex).toString());
    		} catch (JSONException e) {
    			throw new WebScriptException("Out of bound in JSON array");
    		}
	    	if (getJSONKey(entry, "id")!="") {
	    		NodeRef nodeHandle = null;
	    		try {
	        		nodeHandle = getNodeRef(getJSONKey(entry, "id"));
	        		if (nodeHandle!=null) {
	        			ContentReader reader = contentService.getReader(nodeHandle, ContentModel.PROP_CONTENT);
	        			InputStream contentStream = reader.getContentInputStream();
	        			zipContainer.putNextEntry(new ZipEntry(getJSONKey(entry, "location") + registry.getNodeService().getProperty(nodeHandle, ContentModel.PROP_NAME).toString()));		        			
	        			zipContainer.write(IOUtils.toByteArray(contentStream));
	        			zipContainer.closeEntry();
	        			contentStream.close();
	        		}
	            } catch (IOException e) {
	            	//throw new WebScriptException("Could not write node " + ((nodeHandle!=null)?nodeHandle.getId():"null") + " to zip file"); 
	            }
	    	} else {
	    		try {
	        		zipContainer.putNextEntry(new ZipEntry(getJSONKey(entry, "filename")));
	        		zipContainer.write(getJSONKey(entry, "filecontent").getBytes());
	    			zipContainer.closeEntry();
	    	 	} catch (IOException e) {
	            	//throw new WebScriptException("Could not write post data " + getJSONKey(entry, "filename") + ", " + getJSONKey(entry, "filecontent") + " to zip file"); 
	            }
	    	}
    	}
	}
}