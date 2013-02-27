package org.alfresco.module.russeltools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;


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
			String postJSON = "";
			InputStream postStream = incomingAlfresco.getContent().getInputStream();
			int postCharacter;
			while ((postCharacter=postStream.read())!=-1) {
				postJSON += (char)postCharacter;
			}
			postStream.close();
			File fileHandle = File.createTempFile("russel", "russel");
			ZipOutputStream zipStream = new ZipOutputStream(new FileOutputStream(fileHandle));
			createZipEntries(new JSONObject(postJSON), zipStream);
			zipStream.close();
			
			byte[] outputBytes = new byte[(int)fileHandle.length()];
			FileInputStream fis = new FileInputStream(fileHandle);
			outgoingAlfresco.setContentType("application/zip; charset=UTF-8");
			outgoingAlfresco.setContentEncoding("UTF-8");
			outgoingAlfresco.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");
			OutputStream outStream = outgoingAlfresco.getOutputStream();
			fis.read(outputBytes);
			outStream.write(outputBytes);
			fis.close();
			outStream.close();
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
        byte[] tempBytes;
        JSONArray rawZipEntries;
        try {
        	rawZipEntries = post.getJSONArray("toZip");
        } catch (JSONException e) {
        	throw new WebScriptException("malformed json: needs a \"toZip\" key");
        }
        ContentService contentService = registry.getContentService();
        if (rawZipEntries.length()==0)
        	throw new WebScriptException("nothing to zip");
        try {
	    	for (int entryIndex=0; entryIndex<rawZipEntries.length(); entryIndex++) {
	    		JSONObject entry;
	    		try {
		        	entry = new JSONObject(rawZipEntries.get(entryIndex));
	    		} catch (JSONException e) {
	    			throw new WebScriptException("Out of bound in JSON array");
	    		}
	    		
	        	if (getJSONKey(entry, "id")!="") {
	        		NodeRef nodeHandle = getNodeRef(getJSONKey(entry, "id"));
	        		if (nodeHandle!=null) {
	        			ContentReader reader = contentService.getReader(nodeHandle, ContentModel.PROP_CONTENT);
	        			InputStream contentStream = reader.getContentInputStream();
	        			tempBytes = new byte[(int)reader.getSize()];
	        			contentStream.read(tempBytes);
	        			zipContainer.putNextEntry(new ZipEntry(getJSONKey(entry, "location") + registry.getNodeService().getProperty(nodeHandle, ContentModel.PROP_NAME).toString()));		        			
	        			zipContainer.write(tempBytes);
	        			zipContainer.closeEntry();
	        			contentStream.close();
	        		}
	        	} else {
	        		String filename = getJSONKey(entry, "filename");
	        		String filecontents = getJSONKey(entry, "filecontent");
	        		zipContainer.putNextEntry(new ZipEntry(filename));
	        		zipContainer.write(filecontents.getBytes());
        			zipContainer.closeEntry();
	        	}
	    	}
        } catch (IOException e) {
        	throw new WebScriptException("Could not write to zip file"); 
        }
	}
}