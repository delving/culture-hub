# 08.03.2012 - DataSet using userName instead of mongo id

    use culturehub
    var users = db.Datasets.find({}, {"user_id": 1})

    var userNames = {}
    use culturecloud
    users.forEach(function(u) {
        userNames[u.user_id] = db.Users.findOne({_id: u.user_id}).userName
    });

    use culturehub
    db.Datasets.find({}).forEach(function(ds) {
      db.Datasets.update({_id: ds._id}, {$unset: "lockedBy"})
      db.Datasets.update({_id: ds._id}, {$set: {"userName": userNames[ds.user_id]}})
    })

# 08.03.2012 - Organization becoming a remote thing

## Org membership is saved locally in a HubUser, instead of an Organization

    TODO implement migration / do it by hand

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
