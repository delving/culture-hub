# 08.03.2012 - DataSet using userName instead of mongo id

    use culturehub
    var users = db.Datasets.find({}, {"user_id": 1})
    var lockUsers = db.Datasets.find({}, {"lockedBy": 1})
    var userNames = {}
    var userNamesLock = {}

    use culturecloud
    users.forEach(function(u) {
        userNames[u.user_id] = db.Users.findOne({_id: u.user_id}).userName
    });
    lockUsers.forEach(function(u) {
        userNamesLock[u.lockedBy] = db.Users.findOne({_id: u.lockedBy}).userName
    });

    use culturehub
    db.Datasets.find({}).forEach(function(ds) {
      if(ds.lockedBy != null) {
        db.Datasets.update({_id: ds._id}, {$set: { lockedBy: userNamesLock[ds.lockedBy]}})
      }
      db.Datasets.update({_id: ds._id}, {$set: { userName: userNames[ds.user_id]} } )
    })

# 08.03.2012 - Organization becoming a remote thing

## Adding organizations to users from Organization

db.Users.find().forEach(function(u) { u.organizations.clear(); db.Users.save(u); });

db.Organizations.find().forEach(function(org) {
  var users = org.users;
  for(var key in users) {
    var userName = users[key];
    print(userName);
    var user = db.Users.findOne({userName: userName});
    if(user.hasOwnProperty("organizations")) {
      user.organizations.push(org.orgId);
    } else {
      user.organizations = [org.orgId];
    }
    db.Users.save(user);
  }
})

## Org membership is saved locally in a HubUser, instead of an Organization

// configure the organization and the users here, make sure to adjust the database names
var userNames = ["bob", "dan"];
var orgId = "delving";

// fetch users from culturecloud
use culturecloud
var users = db.Users.find({userName: {$in: userNames}});

// create in culturehub
use culturehub
users.forEach(function(u) {
  print(u.userName);
  db.Users.insert({
    "_typeHint" : "models.HubUser",
    userName: u.userName,
    email: u.email,
    firstName: u.firstName,
    lastName: u.lastName,
    organizations: [ orgId ],
    "userProfile" : {
    		"_typeHint" : "models.UserProfile",
    		"isPublic" : u.userProfile.isPublic,
    		"description" : u.userProfile.description,
    		"funFact" : u.userProfile.funFact,
    		"twitter" : u.userProfile.twitter,
    		"linkedIn" : u.userProfile.linkedIn,
    		"websites" : u.userProfile.websites
    	}
  });
  }
)


## CMS files and images aren't linked via the org mongo id anymore but via the orgId (I knew this would get back at me)

May not be a problem but we need to check if there are any items live.



# 09.03.2012 - RecordDefinition keeping namespaces

    db.Datasets.update({}, {$set: {"details.metadataFormat.allNamespaces": []}}, false, true)

    db.Datasets.find().forEach(function(ds) {
      for(var key in ds.mappings) {
        if(ds.mappings.hasOwnProperty(key)) {
          print(key);
          var mapping = ds.mappings[key];
          mapping.format.allNamespaces = [];
          ds.mappings[key] = mapping;
        }
      }
      db.Datasets.save(ds);
    })

14.03.2012 - AccessKey implementation

    db.Datasets.find().forEach(function(ds) {
      ds.formatAccessControl = {};
      db.Datasets.save(ds);
    })

15.03.2012 - RecordDefinition flatness

    db.Datasets.update({}, {$set: {"details.metadataFormat.isFlat": true}}, false, true)

    db.Datasets.find().forEach(function(ds) {
      for(var key in ds.mappings) {
        if(ds.mappings.hasOwnProperty(key)) {
          print(key);
          var mapping = ds.mappings[key];
          mapping.format.isFlat = true;
          ds.mappings[key] = mapping;
        }
      }
      db.Datasets.save(ds);
    })

16.03.2012 - SummaryField storage in MDRs

    db.Datasets.find().forEach(function(ds) {
      var mongoCollectionName = "Records." + ds.orgId + "_" + ds.spec;
      var records = db.getCollection(mongoCollectionName)
      records.update({}, { $set: { summaryFields: {} } }, false, true);
    })

