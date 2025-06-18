package net.atos.entng.collaborativeeditor.listeners;

import io.vertx.core.json.JsonObject;
import org.entcore.broker.api.dto.resources.ResourceInfoDTO;
import org.entcore.broker.proxy.ResourceBrokerListener;
import org.entcore.common.resources.MongoResourceBrokerListenerImpl;

import java.util.Date;

/**
 * Implementation of ResourceBrokerListener for the CollaborativeEditor module.
 * Retrieves resource information from the collaborativeeditor collection.
 * Implements ResourceBrokerListener to detect Broker annotations.
 */
public class ResourceBrokerListenerImpl extends MongoResourceBrokerListenerImpl implements ResourceBrokerListener {

    /**
     * Name of the MongoDB collection containing collaborative editors data
     */
    private static final String COLLABORATIVEEDITOR_COLLECTION = "collaborativeeditor";

    /**
     * Create a new MongoDB implementation of ResourceBrokerListener for collaborative editors.
     */
    public ResourceBrokerListenerImpl() {
        super(COLLABORATIVEEDITOR_COLLECTION);
    }
    
    /**
     * Convert MongoDB collaborative editor document to ResourceInfoDTO.
     * Overrides parent method to match the specific document structure in collaborativeeditor.
     *
     * @param resource The MongoDB document from collaborativeeditor collection
     * @return ResourceInfoDTO with extracted information
     */
    @Override
    protected ResourceInfoDTO convertToResourceInfoDTO(JsonObject resource) {
        if (resource == null) {
            return null;
        }
        
        try {
            // Extract basic information
            final String id = resource.getString("_id");
            final String title = resource.getString("name", "");
            final String description = resource.getString("description", "");
            final String thumbnail = resource.getString("thumbnail", "");
            
            // Extract owner information
            final JsonObject owner = resource.getJsonObject("owner", new JsonObject());
            final String authorId = owner.getString("userId", "");
            final String authorName = owner.getString("displayName", "");
            
            // Handle creation and modification dates
            Date creationDate = this.parseDate(resource.getValue("created"));
            Date modificationDate = this.parseDate(resource.getValue("modified"));
            
            return new ResourceInfoDTO(
                id,
                title,
                description,
                thumbnail,
                authorName,
                authorId,
                creationDate,
                modificationDate
            );
        } catch (Exception e) {
            log.error("Error converting CollaborativeEditor document to ResourceInfoDTO", e);
            return null;
        }
    }
}