console.log("Start loading behaviours pad");
var collaborativeeditorBehaviours = {
    /**
     * Resources set by the user.
     */
    resources : {
        read : {
            right : 'net-atos-entng-collaborativeeditor-controllers-CollaborativeEditorController|retrieve'
        },
        contrib : {
            right : 'net-atos-entng-collaborativeeditor-controllers-CollaborativeEditorController|update'
        },
        manage : {
            right : 'net-atos-entng-collaborativeeditor-controllers-CollaborativeEditorController|delete'
        }
    },

    /**
     * Workflow rights are defined by the administrator. This associates a name
     * with a Java method of the server.
     */
    workflow : {
        view : 'net.atos.entng.collaborativeeditor.controllers.CollaborativeEditorController|view',
        list : 'net.atos.entng.collaborativeeditor.controllers.CollaborativeEditorController|list',
        create : 'net.atos.entng.collaborativeeditor.controllers.CollaborativeEditorController|create'
    }
};

/**
 * Register behaviours.
 */
Behaviours.register('collaborativeeditor', {
    behaviours : collaborativeeditorBehaviours,

    /**
     * Allows to set rights for behaviours.
     */
    resource : function(resource) {
        var rightsContainer = resource;
        if (!resource.myRights) {
            resource.myRights = {};
        }

        for (var behaviour in collaborativeeditorBehaviours.resources) {
            if (model.me.hasRight(rightsContainer, collaborativeeditorBehaviours.resources[behaviour]) || model.me.userId === resource.owner.userId || model.me.userId === rightsContainer.owner.userId) {
                if (resource.myRights[behaviour] !== undefined) {
                    resource.myRights[behaviour] = resource.myRights[behaviour] && collaborativeeditorBehaviours.resources[behaviour];
                } else {
                    resource.myRights[behaviour] = collaborativeeditorBehaviours.resources[behaviour];
                }
            }
        }
        return resource;
    },

    /**
     * Allows to load workflow rights according to rights defined by the
     * administrator for the current user in the console.
     */
    workflow : function() {
        var workflow = {};

        var collaborativeeditorWorkflow = collaborativeeditorBehaviours.workflow;
        for (var prop in collaborativeeditorWorkflow) {
            if (model.me.hasWorkflow(collaborativeeditorWorkflow[prop])) {
                workflow[prop] = true;
            }
        }

        return workflow;
    },

    /**
     * Allows to define all rights to display in the share windows. Names are
     * defined in the server part with
     * <code>@SecuredAction(value = "xxxx.read", type = ActionType.RESOURCE)</code>
     * without the prefix <code>xxx</code>.
     */
    resourceRights : function() {
        return [ 'read', 'contrib', 'manager' ];
    },

    /**
     * Function required by the "linker" component to display the collaborative editor info
     */
    loadResources: function(callback){
        http().get('/collaborativeeditor/list/all').done(function(collaborativeeditors) {          
            this.resources = _.map(collaborativeeditors, function(collaborativeeditor) {
                return {
                    title : collaborativeeditor.name,
                    ownerName : collaborativeeditor.owner.displayName,
                    owner : collaborativeeditor.owner.userId,
                    icon : '/collaborativeeditor/public/img/mindmap.png',
                    path : '/collaborativeeditor#/view/' + mindmap._id,
                    id : collaborativeeditor._id
                };
            })
            if(typeof callback === 'function'){
                callback(this.resources);
            }
        }.bind(this));
    }
});


console.log("End loading behaviours pad");
