import {_, angular, Controller, moment, ng, template} from "entcore";
import { collaborativeEditor } from "../models/model";

console.log("Start loading controller pad");

declare let window: any;
declare let model: any;

/**
 * Controller for collaborative editors. All methods contained in this controller can be called
 * from the view.
 * @param $scope Angular JS model.
 * @param template all templates.
 * @param model the collaborative editor model.
 */
export const CollaborativeEditorController: Controller = ng.controller('CollaborativeEditorController',
    ['$scope', 'route', '$timeout', '$sce', '$location', function ($scope, route, $timeout, $sce, $location) {
    $scope.template = template;
    $scope.collaborativeeditors = model.collaborativeEditors;
    $scope.me = model.me;
    $scope.display = {};
    $scope.searchbar = {};
    $scope.editorLoaded = false;
    $scope.editorId = 0;
    $scope.exportInProgress = false;
    $scope.action = 'collaborativeeditor-list';
    $scope.notFound = false;
    $scope.creatingPad = false;
    $scope.loading = false;

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

    // By default open the collaborative editor list
    //template.open('collaborativeeditor', 'collaborativeeditor-list');
    template.open('side-panel', 'collaborativeeditor-side-panel');

    $scope.isEmpty = function() {
        return $scope.collaborativeeditors && $scope.collaborativeeditors.all && $scope.collaborativeeditors.all.length < 1;
    }

    /**
     * Allows to create a new collaborative editor and open the "collaborativeeditor-edit.html" template into
     * the "main" div.
     */
    $scope.newCollaborativeeditor = function() {
        $scope.creatingPad = true;
        $scope.collaborativeeditor = new collaborativeEditor.CollaborativeEditor();
        $scope.action = 'collaborativeeditor-create';
        template.open('collaborativeeditor', 'collaborativeeditor-create');
        template.close('etherpad');
    };

	$scope.redirect = function(path){
		$location.path(path);
	};

    /**
     * Allows to save the current edited collaborative editor in the scope. After saving the
     * current collaborative editor this method closes the edit view too.
     */
    $scope.saveCollaborativeeditor = function() {
        $scope.master = angular.copy($scope.collaborativeeditor);

        // Urls should not be updated
        delete $scope.master.url;
        delete $scope.master.readOnlyUrl;

        $scope.master.save(function() {
            $scope.collaborativeeditors.sync(function() {
                 $scope.updateSearchBar();
                 $scope.cancelCollaborativeeditorEdit();
            });
            $scope.creatingPad = false;
        });
    };


    /**
     * Update the search bar according server collaborative editors
     */
    $scope.updateSearchBar = function() {
        model.collaborativeEditors.sync(function() {
            $scope.searchbar.collaborativeeditors = $scope.collaborativeeditors.all.map(function(collaborativeeditor) {
                return {
                    title : collaborativeeditor.name,
                    _id : collaborativeeditor._id,
                    toString : function() {
                                    return this.title;
                               }
                }
            });
        });
    }

    // Update search bar
    $scope.updateSearchBar();


    /**
     * Opens a collaborative editor through the search bar
     */
    $scope.openPageFromSearchbar = function(collaborativeeditorId) {
        window.location.hash = '/view/' + collaborativeeditorId;
    };

    /**
     * Retrieve the collaborative editor thumbnail if there is one
     */
    $scope.getCollaborativeeditorThumbnail = function(collaborativeeditor){
        if(!collaborativeeditor.thumbnail || collaborativeeditor.thumbnail === ''){
            return '/img/illustrations/collaborative-editor.svg';
        }
        return collaborativeeditor.thumbnail + '?thumbnail=120x120';
    };

    /**
     * Open a collaborative editor
     */
    $scope.openCollaborativeeditor = async function(collaborativeeditor) {
        if ($scope.selectedCollaborativeeditor) {
            await $scope.selectedCollaborativeeditor.deleteSession();
        }
        delete $scope.collaborativeeditor;
        delete $scope.selectedCollaborativeeditor;
        delete $scope.padUrl;
        $scope.notFound = false;


        $scope.collaborativeeditors.forEach(function(c) {
            c.showButtons = false;
        });

        $scope.collaborativeeditor = $scope.selectedCollaborativeeditor = collaborativeeditor;

        if ($scope.canContributeCollaborativeeditor(collaborativeeditor)) {
            $scope.padUrl = $sce.trustAsResourceUrl(collaborativeeditor.url);
        } else {
            $scope.padUrl = $sce.trustAsResourceUrl(collaborativeeditor.readOnlyUrl);
        }

        await $scope.collaborativeeditor.session();

        //delay for set cookie
        await sleep(1000);

        template.close('main');
        template.close('collaborativeeditor');
        template.close('etherpad');

        $scope.action = 'collaborativeeditor-open';

        template.open('etherpad', 'collaborativeeditor-edit');

    };


    window.onbeforeunload = function() {
        if ($scope.selectedCollaborativeeditor) {
            $scope.selectedCollaborativeeditor.deleteSession();
            $timeout(function() {
                document.cookie = "sessionID=; expires=Thu, 01 Jan 1970 00:00:00 UTC";
            });
        }
    }

    window.onunload = function () {
        if ($scope.selectedCollaborativeeditor) {
            $scope.selectedCollaborativeeditor.deleteSession();
            $timeout(function() {
                document.cookie = "sessionID=; expires=Thu, 01 Jan 1970 00:00:00 UTC";
            });
        }
    }

    /**
     * Display date in appropriate format
     */
    $scope.formatDate = function(dateObject){
        return moment(dateObject.$date).calendar();
    };


    /**
     * Checks if a user is a manager
     */
    $scope.canManageCollaborativeeditor = function(collaborativeeditor){
        return (collaborativeeditor.myRights.manage !== undefined);
    };

    /**
     * Checks if a user is a contributor
     */
    $scope.canContributeCollaborativeeditor = function(collaborativeeditor){
        return (collaborativeeditor.myRights.contrib !== undefined);
    };


    /**
     * Close the collaborative editor and open the collaborative editors list page
     */
    $scope.cancelCollaborativeeditorEdit = function() {
        if ($scope.selectedCollaborativeeditor) {
            $scope.selectedCollaborativeeditor.deleteSession();
        }
        delete $scope.collaborativeeditor;
        delete $scope.selectedCollaborativeeditor;
        template.close('main');
        template.close('etherpad');
        $scope.action = 'collaborativeeditor-list';
        $scope.creatingPad = false;
        window.open("/collaborativeeditor?view=home", "_self");
    }

    /**
     * List of collaborative editor objects
     */
    $scope.openMainPage = function(){
        if ($scope.selectedCollaborativeeditor) {
            $scope.selectedCollaborativeeditor.deleteSession();
        }
        delete $scope.collaborativeeditor;
        delete $scope.selectedCollaborativeeditor;
        $scope.action = 'collaborativeeditor-list';
        template.close('etherpad');
        window.open("/collaborativeeditor?view=home", "_self");
    }


    /**
     * Allows to set "showButtons" to false for all collaborative editors except the given one.
     * @param collaborativeeditor the current selected collaborative editor.
     * @param event triggered event
     */
    $scope.hideAlmostAllButtons = function(collaborativeeditor, event) {
        event.stopPropagation();

        if (collaborativeeditor.showButtons) {
            $scope.collaborativeeditor = collaborativeeditor;
        } else {
            delete $scope.collaborativeeditor;
        }

        $scope.collaborativeeditors.forEach(function(c) {
            if(!collaborativeeditor || c._id !== collaborativeeditor._id){
                c.showButtons = false;
            }
        });
    };

    /**
     * Allows to set "showButtons" to false for all the collaborative editors except the given one.
     * @param collaborativeeditor the current selected collaborative editor.
     * @param event triggered event
     */
    $scope.hideAllButtons = function(collaborativeeditor, event) {
        event.stopPropagation();

        if (collaborativeeditor.showButtons) {
            $scope.collaborativeeditor = collaborativeeditor;
        } else {
            delete $scope.collaborativeeditor;
        }

        $scope.collaborativeeditors.forEach(function(c) {
            if(!collaborativeeditor || c._id !== collaborativeeditor._id){
                c.showButtons = false;
            }
        });
    };

    /**
     * Edit the properties (name, description) of a collaborative editor
     */
    $scope.editCollaborativeeditor = function(collaborativeeditor, event) {
        event.stopPropagation();
        collaborativeeditor.showButtons = false;
        $scope.master = collaborativeeditor;
        $scope.collaborativeeditor = angular.copy(collaborativeeditor);
        template.close('etherpad');
        template.open('collaborativeeditor', 'collaborativeeditor-create');
    }


    /**
     * Allows to put the current collaborative editor in the scope and set "confirmDeleteCollaborativeeditor"
     * variable to "true".
     * @param collaborativeeditor the collaborative editor to delete.
     * @param event an event.
     */
    $scope.confirmRemoveCollaborativeeditor = function(collaborativeeditor, event) {
        event.stopPropagation();
        $scope.collaborativeeditor = collaborativeeditor;
        $scope.display.confirmDeleteCollaborativeeditor = true;
    };

    /**
     * Allows to cancel the current delete process.
     */
    $scope.cancelRemoveCollaborativeeditor = function() {
        delete $scope.display.confirmDeleteCollaborativeeditor;
    };

    /**
     * Allows to remove the current collaborative editor in the scope.
     */
    $scope.removeCollaborativeeditor = function() {
        _.map($scope.collaborativeeditors.selection(), function(collaborativeeditor){
            collaborativeeditor.delete(function() {
                $scope.updateSearchBar();
                $scope.$apply();
            });
        });

        delete $scope.collaborativeeditor;
        delete $scope.selectedCollaborativeeditor;
        $scope.display.confirmDeleteCollaborativeeditor = false;
    };

    /**
     * Allows to open the "share" panel by setting the
     * "$scope.display.showPanel" variable to "true".
     * @param collaborativeeditor the collaborativeeditor to share.
     * @param event the current event.
     */
    $scope.shareCollaborativeeditor = function(collaborativeeditor, event){
        $scope.collaborativeeditor = collaborativeeditor;
        $scope.display.showPanel = true;
        event.stopPropagation();
    };

    $scope.workflowReadOnly = function() {
        return $scope.me
            && $scope.me.workflow
            && $scope.me.workflow.collaborativeeditor
            && ($scope.me.workflow.collaborativeeditor.view || $scope.me.workflow.collaborativeeditor.list)
            && !$scope.me.workflow.collaborativeeditor.create;
    }

    /**
     * Collaborative editor routes definition
     */
    route({

        /**
         * Retrieve a collaborative editor from its database id and open it
         */
        viewCollaborativeeditor: function(params) {
            $scope.loading = true;
            model.collaborativeEditors.sync(function() {
                $scope.loading = false;
                var c = _.find(model.collaborativeEditors.all, function(collaborativeeditor){
                    return collaborativeeditor._id === params.collaborativeeditorId;
                });
                if (c) {
                    $scope.notFound = "false";
                    $scope.openCollaborativeeditor(c);
                } else {
                    $scope.notFound = "true";
                    $scope.openMainPage();
                }
            });
        },

        /**
         * Display the collaborative editors list
         **/
        listCollaborativeeditor: function(params){
            $scope.loading = true;
            model.collaborativeEditors.sync(function () {
                $scope.loading = false;
                $scope.openMainPage();
            });
        }
    });

}]);

console.log("End loading controller pad");
