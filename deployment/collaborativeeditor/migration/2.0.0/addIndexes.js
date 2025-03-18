db.collaborativeeditor.createIndex({epName:1},{background: true,name:"idx_collaborative_editor_epname"});
db.collaborativeeditor.createIndex({"owner.userId":1},{background: true,name:"idx_collaborative_editor_ownerid"});
db.collaborativeeditor.createIndex({"shared.userId":1},{background: true,name:"idx_collaborative_editor_shared_userid"});
db.collaborativeeditor.createIndex({"shared.groupId":1},{background: true,name:"idx_collaborative_editor_shared_groupid"});
