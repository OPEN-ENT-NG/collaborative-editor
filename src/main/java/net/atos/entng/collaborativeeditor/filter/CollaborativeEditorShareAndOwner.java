package net.atos.entng.collaborativeeditor.filter;

import com.mongodb.client.model.Filters;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import net.atos.entng.collaborativeeditor.CollaborativeEditor;
import org.bson.conversions.Bson;
import org.entcore.common.http.filter.MongoAppFilter;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.List;

public class CollaborativeEditorShareAndOwner implements ResourcesProvider {
    public void authorize(HttpServerRequest request, Binding binding, UserInfos user, Handler<Boolean> handler) {
        // Get the id of the pad from the request
        String id = request.params().get("id");
        if (id != null && !id.trim().isEmpty()) {
            List<Bson> groups = new ArrayList();
            // Get the shared method name from the request
            String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");
            // Filter by userId having the sharedMethod set to true
            groups.add(Filters.and(new Bson[]{Filters.eq("userId", user.getUserId()), Filters.eq(sharedMethod, true)}));
            // Filter by groupId having the sharedMethod set to true
            for(final String groupdId : user.getGroupsIds()){
                groups.add(Filters.and(new Bson[]{Filters.eq("groupId", groupdId), Filters.and(new Bson[]{Filters.eq(sharedMethod, true)})}));
            }
            // Filter by the pad (id OR epName) AND (the owner OR the sharedMethod set to true by group OR the sharedMethod set to true by user))
            Bson query = Filters.and(new Bson[]{
                    Filters.or(new Bson[]{ Filters.eq("_id", id), Filters.eq("epName", id) }),
                    Filters.or(new Bson[]{
                            Filters.eq("owner.userId", user.getUserId()),
                            Filters.elemMatch("shared", Filters.or(groups))
                    })
            });
            // Execute the query and expect 1 result
            MongoAppFilter.executeCountQuery(request, CollaborativeEditor.COLLABORATIVEEDITOR_COLLECTION, MongoQueryBuilder.build(query), 1, handler);
        } else {
            // No id found in the request => reject
            handler.handle(false);
        }

    }
}
