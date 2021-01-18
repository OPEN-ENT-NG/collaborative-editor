/**
 * Model to create a collaborative editor.
 */
import {http} from "entcore";

declare let model: any;

export let collaborativeEditor: any = {};

collaborativeEditor.CollaborativeEditor = function() {}


/**
 * Allows to save the collaborative editor. If the collaborative editor is new and does not have any id set,
 * this method calls the create method otherwise it calls the update method.
 * @param callback a function to call after saving.
 */
collaborativeEditor.CollaborativeEditor.prototype.save = function(callback) {
	if (this._id) {
		this.update(callback);
	} else {
		this.create(callback);
	}
};


/**
 * Allows to create a new collaborative editor. This method calls the REST web service to
 * persist data.
 * @param callback a function to call after create.
 */
collaborativeEditor.CollaborativeEditor.prototype.create = function(callback) {
    http().postJson('/collaborativeeditor', this).done(function() {
        if(typeof callback === 'function'){
            callback();
        }
    });
};

/**
 * Allows to update the collaborative editor. This method calls the REST web service to persist
 * data.
 * @param callback a function to call after create.
 */
collaborativeEditor.CollaborativeEditor.prototype.update = function(callback) {
    http().putJson('/collaborativeeditor/' + this._id, this).done(function() {
        if(typeof callback === 'function'){
            callback();
        }
    });
};

/**
 * Allows to delete the collaborative editor. This method calls the REST web service to delete
 * data.
 * @param callback a function to call after delete.
 */
collaborativeEditor.CollaborativeEditor.prototype.delete = function(callback) {
    http().delete('/collaborativeeditor/' + this._id).done(function() {
        model.collaborativeEditors.remove(this);
        if(typeof callback === 'function'){
            callback();
        }
    }.bind(this));
};

/**
 * Gets a session on the pad
 * @param callback a function to call after delete.
 */
collaborativeEditor.CollaborativeEditor.prototype.session = function(callback) {
    http().get('/collaborativeeditor/session/' + this._id).done(function() {
        if(typeof callback === 'function'){
            callback();
        }
    }.bind(this));
};


/**
 * Removes a session on the pad
 * @param callback a function to call after delete.
 */
collaborativeEditor.CollaborativeEditor.prototype.deleteSession = function(callback) {
    http().get('/collaborativeeditor/deleteSession/' + this._id).done(function() {
        if(typeof callback === 'function'){
            callback();
        }
    }.bind(this));
};
/**
 * Allows to convert the current collaborative editor into a JSON format.
 * @return the current collaborative editor in JSON format.
 */
collaborativeEditor.CollaborativeEditor.prototype.toJSON = function() {
    return {
        name: this.name,
        description: this.description,
        thumbnail: this.thumbnail,
        url: this.url,
        readOnlyUrl: this.readOnlyUrl
    }
};

/**
 * Allows to create a model and load the list of collaborative editors from the backend.
 */
model.build = function() {
    this.makeModels(collaborativeEditor);

    this.collection(collaborativeEditor.CollaborativeEditor, {

        sync: function(callback){
            http().get('/collaborativeeditor/list/all').done(function(collaborativeeditors){
                this.load(collaborativeeditors);
                if(typeof callback === 'function'){
                    callback();
                }
            }.bind(this));
        },

        behaviours: 'collaborativeeditor'
    });
};