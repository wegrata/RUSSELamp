package org.alfresco.module.russeltools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.extensions.webscripts.servlet.WebScriptServletRequest;


public class ZIPImport extends AbstractWebScript {	
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
	public void execute(WebScriptRequest incoming, WebScriptResponse outgoing)
			throws IOException {
		
		try {
			JSONObject obj = new JSONObject();
			String sType = incoming.getParameter("store_type");
			String sId = incoming.getParameter("store_id");
			String nId = incoming.getParameter("node_id");
			NodeRef node = getNodeRef(sType, sId, nId);
			
			String[] acc = importZIPAssets(node);
			String assets = "";
			
			for (int x=0;x<acc.length;x++)
				assets += "," + acc[x];
			if (assets!="")
				assets = "[" + assets.substring(1) + "]"; 
			
 			obj.put("importedIDs", assets);
			obj.put("numberOfEntries", acc.length);
			
	    	outgoing.getWriter().write(obj.toString());
	    	outgoing.getWriter().flush();
	    	outgoing.getWriter().close();
	     } catch(JSONException e) {
	    	throw new WebScriptException("Unable to serialize JSON");
	     }
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
	
	private String[] importZIPAssets(NodeRef node) {
        int tempByte = 0;
        
		ContentService contentService = registry.getContentService();
    	ContentReader reader = contentService.getReader(node, ContentModel.PROP_CONTENT);
        InputStream originalInputStream = reader.getContentInputStream();
        File tempZipFile = null;
        ZipFile zf = null;
        try {
			tempZipFile = File.createTempFile("russel", "russel");
		} catch (IOException e1) {
			new WebScriptException("Couldn't create temp file for SCORM import");
		}
        try {
	        OutputStream tempZipFileOStream = new FileOutputStream(tempZipFile);
	        while ((tempByte=originalInputStream.read())!=-1) {
	        	tempZipFileOStream.write(tempByte);
	        }
	        tempZipFileOStream.close();
	        zf = new ZipFile(tempZipFile);
        } catch (IOException e) {
        	new WebScriptException("Couldn't create zip file for SCORM import");
        }
        
        NodeRef userHome = registry.getNodeService().getPrimaryParent(node).getParentRef();
        NodeRef zipEntryRef = null;
        String[] acc = new String[zf.size()];
        ContentWriter cw = null;
        ZipEntry ze = null;
        int idCounter = 0;

//        QName CUSTOM_ASPECT_QNAME = QName.createQName("custom.model", "MyAspect");
//        QName PROP_QNAME_MY_PROPERTY = QName.createQName("custom.model", "myProperty");
//        Map<QName,Serializable> aspectValues = new HashMap<QName,Serializable>();
//        aspectValues.put(PROP_QNAME_MY_PROPERTY, value);
//        nodeService.addAspect(nodeRef, CUSTOM_ASPECT_QNAME, aspectValues);
        
        for (Enumeration<? extends ZipEntry> x=zf.entries();x.hasMoreElements();) {
        	ze = x.nextElement();
        	try {
		        zipEntryRef = registry.getFileFolderService().create(userHome,
		        													 ze.getName(),
		        													 ContentModel.TYPE_CONTENT).getNodeRef();
        	} catch (FileExistsException e) {
        		boolean created = false;
        		int i = 1;
        		while (!created) {
	        		try {
	        			String zipName = ze.getName();
	        			if (zipName.indexOf(".")!=-1) 
	        				zipName = zipName.substring(0, zipName.lastIndexOf(".")) + "-" + i + zipName.substring(zipName.lastIndexOf("."));
	        				
	        			zipEntryRef = registry.getFileFolderService().create(userHome,
	        																 zipName,
	        																 ContentModel.TYPE_CONTENT).getNodeRef();
	        			created = true;
	        		} catch (Exception e2) {
	        			if (i<100000)
	        				i++;
	        			else
	        				new WebScriptException("Failed to create item -100000 bounded ids - " + ze.getName());
	        		}
        		}
			}
	        acc[idCounter++] = zipEntryRef.getId();
	        try {
				cw = contentService.getWriter(zipEntryRef, ContentModel.PROP_CONTENT, true);
				InputStream zipInput = zf.getInputStream(ze);
				FileNameMap fnm = URLConnection.getFileNameMap();
				cw.setMimetype(fnm.getContentTypeFor(ze.getName()));
				cw.putContent(zipInput);
				zipInput.close();
	    	} catch (IOException e) {
				new WebScriptException("Failed import on SCORM - " + e);
			}
        }
        try {
			zf.close();
		} catch (IOException e) {
			new WebScriptException("Couldn't close zip file for SCORM import");
		}
        return acc;
	}
}