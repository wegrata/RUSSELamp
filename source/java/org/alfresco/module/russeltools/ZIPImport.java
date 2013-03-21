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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;


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
	public void execute(final WebScriptRequest incoming, final WebScriptResponse outgoing)
			throws IOException {
		try {
			JSONObject obj = new JSONObject();
			String sType = incoming.getParameter("store_type");
			String sId = incoming.getParameter("store_id");
			String nId = incoming.getParameter("node_id");
			NodeRef node = getNodeRef(sType, sId, nId);
			
			Vector<String> acc = importZIPAssets(node);
 			obj.put("importedIDs", acc);
			
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
	
	private Vector<String> importZIPAssets(NodeRef node) {
        byte[] tempBytes;
        
		ContentService contentService = registry.getContentService();
    	ContentReader reader = contentService.getReader(node, ContentModel.PROP_CONTENT);
        InputStream originalInputStream = reader.getContentInputStream();
        File tempZipFile = null;
        ZipFile zf = null;
        try {
			tempZipFile = File.createTempFile("russel", "russel");
		} catch (IOException e1) {
			new WebScriptException("Couldn't create temp file for import");
		}
        try {
	        OutputStream tempZipFileOStream = new FileOutputStream(tempZipFile);
	        tempBytes = new byte[(int)reader.getSize()];
	        originalInputStream.read(tempBytes);
	        tempZipFileOStream.write(tempBytes);
	        tempZipFileOStream.close();
	        originalInputStream.close();
	        zf = new ZipFile(tempZipFile);
        } catch (IOException e) {
        	new WebScriptException("Couldn't create zip file for import");
        }
        
        FileInfo zipEntryInfo = null;
        Vector<String> acc = new Vector<String>();
        ContentWriter cw = null;
        ZipEntry ze = null;

        for (Enumeration<? extends ZipEntry> x=zf.entries();x.hasMoreElements();) {
        	ze = x.nextElement();
        	String zipName = ze.getName();
    		if (zipName.lastIndexOf("/")!=-1)
				zipName = zipName.substring(zipName.lastIndexOf("/")+1);
    		if (!zipName.isEmpty()&&!ze.isDirectory()&&checkAsset(zipName, ze.getSize())) {
    			int i = 0;
    			boolean created = false;
    			UserTransaction userT = null;
    			while (!created) {
	    			try {
	    				userT = registry.getTransactionService().getNonPropagatingUserTransaction();
	    				zipName = ze.getName();
	    				if (zipName.lastIndexOf("/")!=-1)
	        				zipName = zipName.substring(zipName.lastIndexOf("/")+1);
	    				
	    				if (i!=0&&zipName.lastIndexOf(".")!=-1) 
	        				zipName = zipName.substring(0, zipName.lastIndexOf(".")) + "-" + i + zipName.substring(zipName.lastIndexOf("."));
	    				else if (i!=0)
	    					zipName = zipName + "-" + i;
	    				userT.begin();
	            		zipEntryInfo = registry.getFileFolderService().create(repository.getUserHome(repository.getPerson()),
																		      zipName,
																		      ContentModel.TYPE_CONTENT);
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
		    				new WebScriptException("Failed to create item -100000 bounded ids - " + ze.getName());
					}
    			}
		        acc.add(zipEntryInfo.getNodeRef().getId() + ";" + zipName);
		        QName titledAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "titled");
		        QName authorAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "author");
		        QName versionAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "versionable");
		        QName russelAspect = QName.createQName("russel.russelMetadata", "metaTest");
		        //QName PROP_QNAME_MY_PROPERTY = QName.createQName("custom.model", "myProperty");
		        Map<QName,Serializable> aspectValues = new HashMap<QName,Serializable>();
		        //aspectValues.put(PROP_QNAME_MY_PROPERTY, value);
		        registry.getNodeService().addAspect(zipEntryInfo.getNodeRef(), titledAspect, aspectValues);
		        registry.getNodeService().addAspect(zipEntryInfo.getNodeRef(), authorAspect, aspectValues);
		        registry.getNodeService().addAspect(zipEntryInfo.getNodeRef(), versionAspect, aspectValues);
		        registry.getNodeService().addAspect(zipEntryInfo.getNodeRef(), russelAspect, aspectValues);
		        try {
					cw = contentService.getWriter(zipEntryInfo.getNodeRef(), ContentModel.PROP_CONTENT, true);
					InputStream zipInput = zf.getInputStream(ze);
					cw.setMimetype(URLConnection.getFileNameMap().getContentTypeFor(ze.getName()));
					cw.putContent(zipInput);
					zipInput.close();
		    	} catch (IOException e) {
					new WebScriptException("Failed import on - " + e);
				}
        	}
        }
        try {
			zf.close();
		} catch (IOException e) {
			new WebScriptException("Couldn't close zip file for import");
		}
        return acc;
	}
	
	private boolean checkAsset(String filename, long fileKSize) {
		String ext = "";
		boolean keepAsset = false;
		if (filename.lastIndexOf(".")!=-1) {
			fileKSize = fileKSize / 1024;
			ext = filename.substring(filename.lastIndexOf(".")+1);
			if (ext.equalsIgnoreCase("fla")||ext.equalsIgnoreCase("7z")||ext.equalsIgnoreCase("deb")||ext.equalsIgnoreCase("gz")||ext.equalsIgnoreCase("wmv")||
				ext.equalsIgnoreCase("pkg")||ext.equalsIgnoreCase("rar")||ext.equalsIgnoreCase("rpm")||ext.equalsIgnoreCase("sit")||ext.equalsIgnoreCase("sitx")||
				ext.equalsIgnoreCase("zip")||ext.equalsIgnoreCase("zipx")||ext.equalsIgnoreCase("csv")||ext.equalsIgnoreCase("dat")||ext.equalsIgnoreCase("efx")||
				ext.equalsIgnoreCase("epub")||ext.equalsIgnoreCase("gbr")||ext.equalsIgnoreCase("ged")||ext.equalsIgnoreCase("ibooks")||ext.equalsIgnoreCase("sdf")||
				ext.equalsIgnoreCase("tar")||ext.equalsIgnoreCase("tax2010")||ext.equalsIgnoreCase("vcf")||ext.equalsIgnoreCase("accdb")||ext.equalsIgnoreCase("db")||
				ext.equalsIgnoreCase("dbf")||ext.equalsIgnoreCase("mdb")||ext.equalsIgnoreCase("sql")||ext.equalsIgnoreCase("app")||ext.equalsIgnoreCase("bat")||
				ext.equalsIgnoreCase("cgi")||ext.equalsIgnoreCase("com")||ext.equalsIgnoreCase("exe")||ext.equalsIgnoreCase("gadget")||ext.equalsIgnoreCase("jar")||
				ext.equalsIgnoreCase("msi")||ext.equalsIgnoreCase("pif")||ext.equalsIgnoreCase("vb")||ext.equalsIgnoreCase("wsf")||ext.equalsIgnoreCase("fnt")||
				ext.equalsIgnoreCase("fon")||ext.equalsIgnoreCase("otf")||ext.equalsIgnoreCase("ttf")||ext.equalsIgnoreCase("3dm")||ext.equalsIgnoreCase("3ds")||
				ext.equalsIgnoreCase("dwg")||ext.equalsIgnoreCase("dxf")||ext.equalsIgnoreCase("max")||ext.equalsIgnoreCase("obj")||ext.equalsIgnoreCase("ai")||
				ext.equalsIgnoreCase("eps")||ext.equalsIgnoreCase("ps")||ext.equalsIgnoreCase("svg")||ext.equalsIgnoreCase("indd")||ext.equalsIgnoreCase("pct")||
				ext.equalsIgnoreCase("pdf")||ext.equalsIgnoreCase("xlr")||ext.equalsIgnoreCase("xls")||ext.equalsIgnoreCase("xlsx")||ext.equalsIgnoreCase("doc")||
				ext.equalsIgnoreCase("docx")||ext.equalsIgnoreCase("log")||ext.equalsIgnoreCase("msg")||ext.equalsIgnoreCase("odt")||ext.equalsIgnoreCase("pages")||
				ext.equalsIgnoreCase("rtf")||ext.equalsIgnoreCase("tex")||ext.equalsIgnoreCase("txt")||ext.equalsIgnoreCase("wpd")||ext.equalsIgnoreCase("wps")||
				ext.equalsIgnoreCase("3g2")||ext.equalsIgnoreCase("3gp")||ext.equalsIgnoreCase("asf")||ext.equalsIgnoreCase("asx")||ext.equalsIgnoreCase("avi")||
				ext.equalsIgnoreCase("flv")||ext.equalsIgnoreCase("mov")||ext.equalsIgnoreCase("mp4")||ext.equalsIgnoreCase("mpg")||ext.equalsIgnoreCase("rm")||
				ext.equalsIgnoreCase("srt")||ext.equalsIgnoreCase("swf")||ext.equalsIgnoreCase("vob")||ext.equalsIgnoreCase("rpf")||ext.equalsIgnoreCase("rlk")||
				ext.equalsIgnoreCase("rlr"))
				keepAsset = true;
			else if ((fileKSize>10)&&((ext.equalsIgnoreCase("aif")||ext.equalsIgnoreCase("iff")||ext.equalsIgnoreCase("m3u")||ext.equalsIgnoreCase("m4a")||
					 ext.equalsIgnoreCase("mid")||ext.equalsIgnoreCase("mp3")||ext.equalsIgnoreCase("mpa")||ext.equalsIgnoreCase("ra")||ext.equalsIgnoreCase("swa")||
					 ext.equalsIgnoreCase("wav")||ext.equalsIgnoreCase("wma"))))
				keepAsset = true;
			else if ((fileKSize>50)&&((ext.equalsIgnoreCase("gif")||ext.equalsIgnoreCase("giff")||ext.equalsIgnoreCase("jpeg")||ext.equalsIgnoreCase("jpg")||ext.equalsIgnoreCase("png"))))
				keepAsset = true;
			else if ((fileKSize>100)&&((ext.equalsIgnoreCase("key")||ext.equalsIgnoreCase("pps")||ext.equalsIgnoreCase("ppt")||ext.equalsIgnoreCase("pptx")||ext.equalsIgnoreCase("yuv")||
					 ext.equalsIgnoreCase("psd")||ext.equalsIgnoreCase("dds"))))
				keepAsset = true;
			else if ((fileKSize>200)&&((ext.equalsIgnoreCase("pspimage")||ext.equalsIgnoreCase("tga")||ext.equalsIgnoreCase("tif")||ext.equalsIgnoreCase("tiff"))))
				keepAsset = true;
			else if ((fileKSize>400)&&((ext.equalsIgnoreCase("bmp")||ext.equalsIgnoreCase("dng"))))
				keepAsset = true;
		}
		return keepAsset;
	}
}