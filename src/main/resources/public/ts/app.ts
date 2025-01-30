import {ng, routes} from 'entcore';

import {CollaborativeEditorController} from "./controllers/controller";

const URL = new URLSearchParams(location.search);
const HAS_VIEW = URL.has("view");

function redirectToReact() {
    if (!HAS_VIEW) {
        window.location.replace("/collaborativeeditor?view=home");
    }
}

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
            action: HAS_VIEW && 'listCollaborativeeditor',
            redirectTo: redirectToReact,
        });
});