import {ng, routes} from 'entcore';

import {CollaborativeEditorController} from "./controllers/controller";

ng.controllers.push(CollaborativeEditorController);

/**
 * Collaborative Editor routes declaration
 */
routes.define(function($routeProvider){
    $routeProvider
        .when('/view/:collaborativeeditorId', {
            action: 'viewCollaborativeeditor'
        })
        .otherwise({
            action: 'listCollaborativeeditor'
        });
});