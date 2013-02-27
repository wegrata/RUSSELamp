package org.alfresco.module.russeltools;

import java.io.IOException;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;


public class SolrSearch extends AbstractWebScript {	
	private ServiceRegistry registry;
	private Repository repository;	
	private static final String NODE = "node";
	private static final String RUSSEL_RATING_SCHEME = "fiveStarRatingScheme";
	
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
		
		SearchParameters querySettings = new SearchParameters();
		String sType = incoming.getParameter("store_type");
		String sId = incoming.getParameter("store_id");
		SearchService searchHandle = registry.getSearchService();
		
		querySettings.addStore(new StoreRef(sType + "://" + sId));
		querySettings.setDefaultOperator(SearchParameters.AND);
		querySettings.setDefaultFTSOperator(SearchParameters.AND);
		querySettings.setDefaultFTSFieldConnective(SearchParameters.AND);
		querySettings.setLanguage(SearchService.LANGUAGE_SOLR_FTS_ALFRESCO);
		querySettings.setMaxItems(Integer.valueOf(incoming.getParameter("rowLimit")));
		querySettings.setQuery(incoming.getParameter("terms").trim());
		querySettings.setSkipCount(Integer.valueOf(incoming.getParameter("rowLimit")) * Integer.valueOf(incoming.getParameter("page")));
		if (incoming.getParameter("sort")!="") {
			String sortData = incoming.getParameter("sort");
			String[] fieldOrder = sortData.split("\\|");
			String[] namespaceField = fieldOrder[0].split(":");
			if (namespaceField[0].equalsIgnoreCase("cm"))
				namespaceField[0] = NamespaceService.CONTENT_MODEL_1_0_URI;
			else if (namespaceField[0].equalsIgnoreCase("russel"))
				namespaceField[0] = "russel.russelMetadata";
			querySettings.addSort(QName.createQName(namespaceField[0], namespaceField[1]).toString(), new Boolean(fieldOrder[1]));
		}
		ResultSet results = null;
		JSONArray jsonResponse = new JSONArray();
		NodeService nodeService = registry.getNodeService();
		try {
			try {
				results = searchHandle.query(querySettings);
			} catch (Exception e) {
				querySettings.setQuery(querySettings.getQuery().replaceAll("[\\\\+\\-\\!\\(\\)\\:\\^\\]\\{\\&\\}\\~\\*\\?\\|\\]\\\"\\.]", "\\\\$0"));
				results = searchHandle.query(querySettings);
			}
			for (int resultIndex=0;resultIndex<results.length();resultIndex++) {
				JSONObject jsonRecord = new JSONObject();
				NodeRef nodeHandle = results.getNodeRef(resultIndex);
				try {
					jsonRecord.put("id", nodeHandle.getId());
					jsonRecord.put("name", nodeService.getProperty(nodeHandle, ContentModel.PROP_NAME).toString());
					jsonRecord.put("description", nodeService.getProperties(nodeHandle).get(QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "description")));
					jsonRecord.put("rating", registry.getRatingService().getAverageRating(nodeHandle, RUSSEL_RATING_SCHEME));
					jsonRecord.put("commentsCount", nodeService.getProperties(nodeHandle).get(QName.createQName(NamespaceService.FORUMS_MODEL_1_0_URI, "commentCount")));
				} catch (JSONException e) {
					//bad record
				}
				jsonResponse.put(jsonRecord);
			}
		} finally {
			if (results!=null)
				results.close();
		}
		
	    outgoing.getWriter().write("{\"items\":" + jsonResponse + "}");
    	outgoing.getWriter().flush();
    	outgoing.getWriter().close();
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