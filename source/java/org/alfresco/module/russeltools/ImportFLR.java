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
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.UserTransaction;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


public class ImportFLR extends AbstractWebScript {	
	
	// ***************** BEGIN CUSTOMIZATION AREA ******************************************************************//
	// By default, a RUSSEL installation uses the Russel.Project@adlnet.gov account on FLR Sandbox.  You can change your
	// installation to use a different account by editing these parameters.
	//
		// The following string constant should be replaced with the desired FLR obtain URL.
		private static final String ADL_FLR_OBTAIN_URL = "http://sandbox.learningregistry.org/obtain";
	//
	// ***************** END CUSTOMIZATION AREA ********************************************************************//

	
	// ***************** EDIT BELOW AT YOUR OWN RISK ***************************************************************//
	private ServiceRegistry registry;
	private Repository repository;	
	private static final String FLR_RUSSEL_MIME_TYPE = "russel/flr";
	private static final String FLR_JSON_PARSE_FAIL = "JSON parse failure";
	private static final String FLR_XML_PARSE_FAIL = "XML parse failure";
	private static final String FLR_OUTPUT_FAIL = "Output stream failure";
	private static final String FLR_PARSE_SUCCESS = "Parse successful";
	private static final String FLR_METADATA_FAIL = "No metadata extracted";
	private static final String FLR_ADD_NODE_SUCCESS = "Node successful";
	private static final String FLR_ADD_NODE_FAIL = "Node failure";
	private static final String PAYLOAD_SCHEMA_NSDL_DC = "nsdl dc";
	private static final String PAYLOAD_SCHEMA_UNKNOWN = "unknown schema";
	private static final String PAYLOAD_SCHEMA_NOT_FOUND = "missing schema";
	
	private JSONArray badFiles;
	private JSONArray partialFiles;
	private JSONObject docOut = null;
	private JSONArray outDocs = null;
	private JSONObject outObj = null;
	private DocumentBuilderFactory dbFactory;
	private DocumentBuilder dBuilder;
	private String curDocURL = "";
	private String flrId = "";
	private String identifier = "";
	private String title = "";
	private String description = "";
	private String creator = "";
	private String language = "";
	private String date = "";
	private String access = "";
	private String pubs = "";
	private String keys = "";
	
	// for Spring injection 
	public void setRepository(Repository repository) {
		this.repository = repository;
	}

	// for Spring injection 
	public void setServiceRegistry(ServiceRegistry registry) {
		this.registry = registry;
	}
	
	private void initMetadata0(String init) {
		identifier = init;
		flrId = init;
		title = init;
		description = init;
		creator = init;
		language = init;
		date = init;
		access = init;
		pubs = init;
		keys = init;
	}
	
	@Override
	public void execute(WebScriptRequest incoming, WebScriptResponse outgoing)
			throws IOException {
		
		try {
			// Extract webscript parameters
			String targetURL = ADL_FLR_OBTAIN_URL;
						
			// Establish connection with the targetURL using httpType
			URLConnection connection = new URL(targetURL).openConnection();
			connection.setRequestProperty("Accept-Charset", "UTF-8");
			
			// Retrieve and format response from targetURL connection
			connection.setRequestProperty("Connection", "close");
			connection.connect();
			InputStream incomingResponse = connection.getInputStream();
			String jsonData = new String(IOUtils.toByteArray(incomingResponse), "UTF-8");
			incomingResponse.close();
			dbFactory = DocumentBuilderFactory.newInstance();
			dBuilder = dbFactory.newDocumentBuilder();
			
			//parse the JSON package received from FLR
			JSONObject incomingGetPacket = new JSONObject(jsonData);
			outObj = new JSONObject();		
			outDocs = new JSONArray();
			badFiles  = new JSONArray();
			partialFiles  = new JSONArray();
			JSONArray docs = getDocArray(incomingGetPacket);
			
			for (int i=0; i<docs.length(); i++) {
				
				String status = null;
				JSONObject doc = ((JSONObject) docs.get(i)); 
				curDocURL = getDocURL(doc);
				
				if ((curDocURL != null) && (curDocURL != "")) {
					
					status = pullMetadata(doc);
					if (status == FLR_METADATA_FAIL) {
						badFiles.put("FLR_METADATA_FAIL-"+curDocURL);
						title = curDocURL;
					}
					
					status = addFLRNode(doc);
					if (status == FLR_ADD_NODE_FAIL) {
						badFiles.put("FLR_ADD_NODE_FAIL-"+curDocURL);
					}						
					outDocs.put(docOut);
				} // validated doc

			} // for each doc in the import

			//prepare the FLRImport outgoing response to caller
			buildResponse(outgoing);
		} 
		catch(JSONException e) {
			throw new WebScriptException("JSON Exception: Unable to serialize JSON");
		} catch (ParserConfigurationException e) {
			throw new WebScriptException("Parser Configuration Exception: Unable to serialize JSON");
		}
		finally{ }

	}
	
	protected String pullMetadata(JSONObject doc) {
		String schema = null;
		String status = FLR_PARSE_SUCCESS;
		JSONObject rec = null;
		docOut = new JSONObject();
		
		initMetadata0("");
		JSONArray recs = getDocRecordsArray(doc);
		if (recs.length() >= 1) {
			try {
				rec = recs.getJSONObject(0);
			} catch (JSONException e1) {
				status = FLR_JSON_PARSE_FAIL;
				schema = PAYLOAD_SCHEMA_UNKNOWN;
			}
			
			if (rec != null) {
				flrId = getDocID(rec);
				//if ((flrId == "") || (flrId == null)) flrId = currDocId;
				keys = getKeys(rec);
				pubs = getPublisher(rec);
				schema = getPayloadSchema(rec);
				if (schema == PAYLOAD_SCHEMA_UNKNOWN) {
					title = curDocURL;
					description = "For more information about this FLR item, refer to the preserved original metadata of this node.";
				}
				else if (schema == PAYLOAD_SCHEMA_NOT_FOUND) {
					title = curDocURL;
					description = "For more information about this FLR item, refer to the preserved original metadata of this node.";
				}
				else if (schema == PAYLOAD_SCHEMA_NSDL_DC) {
					status = extractNsdlDc(rec);
				}

				try {
					docOut.put("FoundDoc", curDocURL);
					docOut.put("FoundDocRecs", recs.length());
					docOut.put("Schema", schema);
					docOut.put("flrID", flrId);
					docOut.put("Identifier", identifier);
					docOut.put("Title", title);
					docOut.put("Keys", keys);
					docOut.put("Publisher", pubs);						
				} catch (JSONException e) {
					status = FLR_OUTPUT_FAIL;
				}
			}
		}
		else status = FLR_METADATA_FAIL;
		
		return status;
	}
	
	protected String extractNsdlDc(JSONObject obj) {
		String status = FLR_PARSE_SUCCESS;
		Document XMLdoc = null;
		
		XMLdoc = getXMLRecord(obj);
		if (XMLdoc != null) {
			XMLdoc.getDocumentElement().normalize();
			identifier = getXMLValue("dc:identifier", XMLdoc);
			title = getXMLValue("dc:title", XMLdoc);
			description = getXMLValue("dc:description", XMLdoc);
			creator = getXMLValue("dc:creator", XMLdoc);
			language = getXMLValue("dc:language", XMLdoc);
			date = getXMLValue("dc:date", XMLdoc);
			access = getXMLValue("dct:accessRights", XMLdoc);							
		}
		else {
			status = FLR_XML_PARSE_FAIL;
		}
		return status;
	}
	
	protected String addFLRNode(JSONObject doc) {
		String status = FLR_ADD_NODE_SUCCESS;
		ContentService contentService = registry.getContentService();
		
		// Store original FLR JSON for this document
		byte[] tempBytes = (doc.toString()).getBytes(); 
        File tempFLRfile = null;
        try {
        	tempFLRfile = File.createTempFile("russel", "russel");
		} catch (IOException e1) {
			status = FLR_ADD_NODE_FAIL;
//			buildResponse();
			new WebScriptException("Couldn't create temp file for FLR document "+curDocURL);
		}
        try {
	        OutputStream tempFLRfileOStream = new FileOutputStream(tempFLRfile);
	        tempFLRfileOStream.write(tempBytes);
	        tempFLRfileOStream.close();
        } catch (IOException e) {
			status = FLR_ADD_NODE_FAIL;    
//			buildResponse();
        	new WebScriptException("Couldn't create FLR file output stream");
        }
    
        // Set up Alfresco node for the FLR document
        FileInfo curDoc = null;
        ContentWriter cw = null;	    		
		int x = 0;
		boolean created = false;
		UserTransaction userT = null;
		while (!created) {
			try {
				userT = registry.getTransactionService().getNonPropagatingUserTransaction();
				String rawFilename = title;
				String fileName = "";
				for (int filenameIndex=0;filenameIndex<rawFilename.length();filenameIndex++)
					if ((rawFilename.codePointAt(filenameIndex)>=48&&rawFilename.codePointAt(filenameIndex)<=57)||
						(rawFilename.codePointAt(filenameIndex)>=65&&rawFilename.codePointAt(filenameIndex)<=90)||
						(rawFilename.codePointAt(filenameIndex)>=97&&rawFilename.codePointAt(filenameIndex)<=122))
						fileName += rawFilename.charAt(filenameIndex);
				
				if (x!=0&&fileName.lastIndexOf(".")!=-1) 
    				fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "-" + x + fileName.substring(fileName.lastIndexOf("."));
				else if (x!=0)
					fileName = fileName + "-" + x;
				fileName += ".rlr";
				docOut.put("Filename", fileName);	
				userT.begin();
        		curDoc = registry.getFileFolderService().create(repository.getUserHome(repository.getPerson()),
														      fileName,
														      ContentModel.TYPE_CONTENT);
				userT.commit();
    			created = true;
        	} catch (Exception e) {
        		try {
					if (userT!=null)
						userT.rollback();
				} catch (Exception e1) {
					
				}
        		if (x<100000)
    				x++;
    			else {
    	    		status = FLR_ADD_NODE_FAIL;
//    				buildResponse();
					badFiles.put("FLR_ADD_NODE_FAIL-"+curDocURL);
    				//new WebScriptException("FLR_ADD_NODE_FAIL: Failed to create item -100000 bounded ids - " + curDocURL);
    			}
			}
		} // end while
        
        // Save metadata to the new Alfresco node for the FLR document
        QName titledAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "titled");
        QName authorAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "author");
        QName versionAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "versionable");
//        QName tagsAspect = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "taggable");
        QName russelAspect = QName.createQName("russel.russelMetadata", "metaTest");
        NodeService nodeService = registry.getNodeService();	        
        Map<QName, Serializable> titledProps = new HashMap<QName, Serializable>();
        titledProps.put(ContentModel.PROP_TITLE, title);
        titledProps.put(ContentModel.PROP_DESCRIPTION, description);
        nodeService.addAspect(curDoc.getNodeRef(), titledAspect, titledProps);
        Map<QName, Serializable> authorProps = new HashMap<QName, Serializable>();
        authorProps.put(ContentModel.PROP_AUTHOR, creator);
        nodeService.addAspect(curDoc.getNodeRef(), authorAspect, authorProps);			        
//        Map<QName,Serializable> tagProps = new HashMap<QName,Serializable>();	
//        tagProps.put(ContentModel.PROP_TAGS, keys);
//        nodeService.addAspect(curDoc.getNodeRef(), tagsAspect, tagProps);	
        Map<QName,Serializable> russelProps = new HashMap<QName,Serializable>();	
        russelProps.put(QName.createQName("russel.russelMetadata", "language"), language);
        russelProps.put(QName.createQName("russel.russelMetadata", "FLRtag"), curDocURL);	
        russelProps.put(QName.createQName("russel.russelMetadata", "FLRid"), flrId);	
        russelProps.put(QName.createQName("russel.russelMetadata", "publisher"), pubs);	
        nodeService.addAspect(curDoc.getNodeRef(), russelAspect, russelProps);
        Map<QName,Serializable> aspectProps = new HashMap<QName,Serializable>();		
        nodeService.addAspect(curDoc.getNodeRef(), versionAspect, aspectProps);
        try {
			cw = contentService.getWriter(curDoc.getNodeRef(), ContentModel.PROP_CONTENT, true);
			cw.setMimetype(FLR_RUSSEL_MIME_TYPE);
			cw.putContent(tempFLRfile);
    	} catch (ContentIOException e) {
    		status = FLR_ADD_NODE_FAIL;
			badFiles.put("FLR_ADD_NODE_FAIL-Failed saving payload to node on"+curDocURL);
			//new WebScriptException("Failed saving payload to node on - " + curDocURL);
			
		}
		return status;
	}
	
	protected void buildResponse(WebScriptResponse outgoing) {
		try {
			outObj.put("docCount", outDocs.length());
			outObj.put("badCount", badFiles.length());
			outObj.put("partialCount", partialFiles.length());
			outObj.put("docs", outDocs);
			outObj.put("bad", badFiles);
			outObj.put("partial", partialFiles);
			String outString = outObj.toString();
	    	outgoing.getWriter().write(outString);
			outgoing.getWriter().flush();
			outgoing.getWriter().close();
		} catch (JSONException e) {
			new WebScriptException("JSON Exception: Failed building FLRImport WebScriptResponse");
		} catch (IOException e) {
			new WebScriptException("IOException: Failed building FLRImport WebScriptResponse");
		}
	}
	
	protected String getTargetURL(WebScriptRequest req) {

		String targetURL = req.getParameter("targetURL");
		if (targetURL == null) {
			throw new WebScriptException("Invalid targetURL.");			
		}
		return targetURL;

	}

	protected JSONArray getDocArray(JSONObject obj) {

		JSONArray docs;
		try {
			docs = obj.getJSONArray("documents");
		} catch (JSONException e) {
			throw new WebScriptException("Error retrieving document array from FLR JSON.");
		}
		if (docs == null) {
			throw new WebScriptException("No FLR documents found.");
		}
		return docs;

	}

	protected String getKeys(JSONObject obj) {
		String keys = "[]";
		try {
			keys = obj.getString("keys");
		} catch (JSONException e) {
			//throw new WebScriptException("Error retrieving document keys from FLR JSON for "+curDocURL);
			partialFiles.put("FLR_MISSING_KEYS-"+curDocURL);
		}
		return keys;
	}
	
	protected String getDocURL(JSONObject obj) {
		String url = "";
		try {
			url = obj.getString("resource_locator");
		} catch (JSONException e1) {
			//throw new WebScriptException("Error retrieving document URL from FLR JSON.");
			try {
				url = obj.getString("doc_ID");
			} catch (JSONException e2) {
				//throw new WebScriptException("Error retrieving document URL from FLR JSON.");
			}
		}

		//validate Id
//		UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);  // Options aren't compiling
		UrlValidator urlValidator = new UrlValidator();
		if (!urlValidator.isValid(url)) {
			badFiles.put("FLR_INVALID_DOC_URL-"+url);
			//throw new WebScriptException(FLR_INVALID_DOC_URL+": "+url);
			url = null;			
		} 
		return url;
	}

	protected String getDocID(JSONObject obj) {
		String Id = "";
		try {
			Id = obj.getString("doc_ID");
		} catch (JSONException e) {
			partialFiles.put("FLR_MISSING_DOC_ID-"+curDocURL);
			//throw new WebScriptException("Error retrieving document ID from FLR JSON.");
		}
		return Id;
	}

	protected String getPayloadSchema(JSONObject obj) {
		JSONArray schemas = null;
		String format = PAYLOAD_SCHEMA_UNKNOWN;
		Boolean supported = false;
		try {
			schemas = obj.getJSONArray("payload_schema");
		} catch (JSONException e) {
			partialFiles.put("PAYLOAD_SCHEMA_NOT_FOUND-"+curDocURL);
//			throw new WebScriptException("Error retrieving payload schema from FLR JSON for "+curDocID);
		}
		if (schemas == null) {
			//throw new WebScriptException("Undefined payload schemas");
		}
		else {
			//validate schema
			for (int i=0; i<schemas.length(); i++)  {
				try {
					format = (String) schemas.get(i);
				} catch (JSONException e) {
//					throw new WebScriptException("Error extracting format from payload_schema-");
				}
				if (format.contains("NSDL DC")) {
					format = PAYLOAD_SCHEMA_NSDL_DC; 
					supported = true;
					i = schemas.length();
				}
			}
			if (!supported) {
				format = PAYLOAD_SCHEMA_UNKNOWN;
				partialFiles.put("PAYLOAD_SCHEMA_UNKNOWN-"+curDocURL);
			}			
		}
		return format;
	}
	
	protected String getPublisher(JSONObject obj) {
		String attribution = null;
		String submitter = null;
		String subattr = null;
		JSONObject temp = null;
		
		try {
			temp = obj.getJSONObject("TOS");
			if (temp != null) {
				subattr = temp.getString("submission_attribution");
			}
			temp = obj.getJSONObject("identity");
			if (temp != null) {
				submitter = temp.getString("submitter");
			}
		} catch (JSONException e) {
			partialFiles.put("PAYLOAD_TOS_NOT_FOUND-"+curDocURL);
			//throw new WebScriptException("Error retrieving document TOS from FLR JSON for "+curDocURL+" obj="+temp);
		}
		if (submitter!=null && subattr!=null) attribution = submitter+", "+ subattr; 
		if (submitter==null && subattr!=null) attribution = subattr; 
		if (submitter!=null && subattr==null) attribution = submitter; 
		return attribution;
	}
	
	protected JSONArray getDocRecordsArray(JSONObject obj) {

		JSONArray recs = null;
		try {
			recs = obj.getJSONArray("document");
		} catch (JSONException e) {
			//buildResponse();
			badFiles.put("PAYLOAD_DOCREC_NOT_FOUND-"+curDocURL);
			//throw new WebScriptException("Error retrieving document records array from FLR JSON for "+curDocURL);
		}
		if (recs == null) {
			badFiles.put("PAYLOAD_DOCREC_NOT_FOUND-"+curDocURL);
			//throw new WebScriptException("No FLR records found for document: "+curDocURL);
		}
		return recs;

	}

	protected Document getXMLRecord(JSONObject obj) {
		DocumentBuilderFactory dbf;
		DocumentBuilder db = null;
		String tempXML = ""; 
		Document XMLdoc = null;
		
		try {
			tempXML = obj.getString("resource_data");
		} catch (JSONException e) {
			XMLdoc = null;
			partialFiles.put("PAYLOAD_METADATA_NOT_FOUND-"+curDocURL);
			return null;
			//throw new WebScriptException("Error retrieving XML string from FLR JSON for "+curDocURL);
		}
		
		dbf = DocumentBuilderFactory.newInstance();
		try {
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			XMLdoc = null;
			partialFiles.put("PAYLOAD_METADATA_NOT_PARSED-"+curDocURL);
			//throw new WebScriptException("Error building XML from FLR JSON for "+curDocURL);
			return null;
			}
	    InputSource is = new InputSource(new StringReader(tempXML));

		try {
			XMLdoc = db.parse(is);
		} catch (IOException e) {
			XMLdoc = null;
			partialFiles.put("PAYLOAD_METADATA_NOT_PARSED-"+curDocURL);
			//throw new WebScriptException("IO Exception: Error parsing XML from FLR JSON for "+curDocURL);
			return null;
			} catch (SAXException e) {
			XMLdoc = null;
			partialFiles.put("PAYLOAD_METADATA_NOT_PARSED-"+curDocURL);
			//throw new WebScriptException("SAX Exception: Error parsing XML from FLR JSON for "+curDocURL);
			return null;
			}
		return XMLdoc;
	}

	private static String getXMLValue(String tag, Document element) {
		StringBuffer buf = new StringBuffer();
		NodeList nodes = element.getElementsByTagName(tag).item(0).getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
	        Node textChild = nodes.item(i);
	        if (textChild.getNodeType() != Node.TEXT_NODE) {
	            //System.err.println("Mixed content! Skipping child element " + textChild.getNodeName());
	            continue;
	        }
	        buf.append(textChild.getNodeValue());
	    }
	    return buf.toString();
	}

	
}