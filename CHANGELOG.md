# CultureHub changelog

This is the changelog of the CultureHub. It documents changes of the main platform, without modules.

# 13.05

## Hotfixes

- manu: Fixing an issue with submitting new SimpleDocuments

# 13.05

## New features

- manu: CORS support for statistics API
- eric: favicon per CSS theme
- eric, manu: view definition for LIDO, with experimental map rendering

## Fixes and refactoring

- manu: updating to SBT 0.12.3 and Scala 2.10.1
- manu: updating to new version of play extensions library

# 13.04.1

## Hotfixes

- manu: re-enabling the simple-document-upload plugin, that got disabled due to a merge issue

# 13.04

## New features

- manu: First version of the Mediator - replacement for the Delving Object Server. Supports direct FTP connection to a hub and takes care of processing files one-by-one
- manu: "Links" files are also sent back on download. #881
- manu: Caching DataSet source for download, hence speeding up download time considerably for large sets (#837)
- manu: Admin Action to queue all sets for processing at once
- manu: when asked for via the Accept-Encoding directive, resources are now GZIPed. This causes a known issue with lighttpd, which does not seem to handle this encoding properly in a proxy set-up.
- manu: graceful reconnection mechanism for WebSocket failures (DataSet pages)
- bajomi: Swedish internationalization

## Fixes and refactoring

- manu: Simplifying dynamic build
- sjoerd: Created a geosort field from the first coordinate in a the multivalued delving_geoHash field
- manu: Updating facts file. Closes #877
- manu: Fixing bug causing language-keyed pages to disappear, small refactoring
- manu: new themes inclusion mechanism
- manu: First shot at refactoring the rendering mechanism in the search API
- manu: Prepending image cache URL when the image cache is enabled
- sjoerd: Update to the mechanism that enables sorting by distance
- manu: Fixing query building for MLT
- manu: fixes related to CMS links
- eric: common css for rights url and context link
- eric, gerald, manu: i18n refactoring

# 13.03

### New features

manu: the hub can now function in a "read only mode", in case of failover
manu: CSV rendering of statistics

### Fixes and refactoring

manu: initializing all plugin actors using a parent actor (for each plugin)
manu: stability improvement: The SchemaRepository must have been initialized at least once in order to start
manu: modularizing the search and indexing
manu: cleaning up unused UI resources
manu: Removed schemas no longer show in the invalid count list. Fixes #805
manu: DOS: Thumbnails now get the correct orgId in storage
manu: A DataSet name should not contain ampersands. Fixes #853
manu: Renaming a DataSet spec deletes old collection from the index. Fixes #842
manu: Fixing the routing for IDs with slashes in them (again). Fixes #857
manu: Maximum row limit can be configured. Fixes #855
manu: Fixing issue with URL-encoding of facets. Fixes #853
manu: Workaround for an issue with Play 2.1 JNotify mechanism, making development turnaround considerably faster
manu: Fixing statistics API caching
manu: Fixing URL redirection after uploading CMS images
manu: Always using the orgId of the currently active organization, thus making it impossible to have wrong resolutions. Fixes #862
gerald: fixing storage of strange identifiers in BaseX
sjoerd: KML fixes
eric: Adding dc:source to the ICN view definition
manu: DataSet page: Disabling sorting on the record number header for the time being, closes #866
manu: Updating to Sip-Creator 13.03, Sip-Core 13.03, Schema-Repository 13.03

# 13.02

### New features

- manu: Introducing quota management for DataSets
- manu: statistics can now be publicly available
- manu: dynamic configuration
- sjoerd: DeepZoom checking script


### Fixes and refactoring

- manu: Upgrade to Play 2.1
- manu: fixing #667, #698, #728, #790, #791, #787, #788
- sjoerd: Added support for items with multiple coordinates being properly rendered in AB-C type KML
- manu: Added support for MLT filtering based on delving owner
- manu: Fix for localIds containing slashes
- manu: IndexApi is now a plugin
- manu: making it possible to list CMS pages without a menu
- manu: processing now a lot more fault-resilient
- manu: improvements in TIFF Normalizer, new version of IM4Java
- manu: fixing bug in ProcessingQueueWatcher
- manu: Plugins now have their own top-level actor. Fixes #836
- manu: minor fixes in DOS

# 13.01

### New features

- manu: adding phone number to user profile
- manu: parallelized collection processing using Akka
- manu: Configuration-based API key mechanism
- sjoerd: add geo info to statistics view
- manu: reviving multi-instance capability

### Fixes and refactoring

- manu: Fixing detection of related item
- manu: Fixing rendering of namespaces for MLT items
- eric: add dc:description field to icn view def
- eric: hide return-to-results nav tab if view object from direct link
- sjoerd: Hardened the indexing of the delving_geoHash field to ensure less illegal coordinates are passed onto solr for indexing
- sjoerd: fix with custom formating of the description field in kml-a
- sjoerd: important fix to kml rendering and usage of query filters
- sjoerd: small fix for using curly quotes in a-href
- manu: Convenience method for getting boolean values from body
- manu: Checking if a role has a description at startup
- manu: Introducing FileStoreService, and subsequent refactoring
- manu: Using normalization for media files to lower problems with DeepZoom image rendering via IIP
- manu: Renaming DomainConfiguration to OrganizationConfiguration. Fixes #770
- eric: Improved deepZoom fallback: 1. flash > 2. seadragon.js > 3. regular image
- manu: Passing the list of schema versions to the Sip-Creator
- manu: Notifications of new users go to registration
- manu: Allowing to display IndexApi items
- sjoerd: Updated the KML output on the basis of the updated layout documents.

# 12.12

### New features

- manu: adding description field to DataSet creation page
- manu: phone number in user profile


### Fixes and refactoring

- manu: using new Sip-Creator version, JNLP rendering now done via Sip-Core
- manu: full view: fixing rendering of namespaces for MLT items
- eric: adding missing translations for norwegian
- eric: hide return-to-results nav tab if view object from direct link
- eric: updated tib layout template for ipad fix
- sjoerd: Hardened the indexing of the delving_geoHash field to ensure less illegal coordinates are passed onto solr for indexing

# 12.11

### New features

- sjoerd: Search API: support for free-field identifiers
- manu: CRUD functionality for organization section
- sjoerd: Search API: added MLT count

### Fixes and refactoring

# 12.10

### New features

- manu: HubNodes core component
- manu: Adding description field to Harvestable
- sjoerd: experimental KML rendering
- sjoerd: added facet.limit as statistics parameter
- manu: two-level CMS menu generation

### Fixes and refactoring
- manu: fixes in OAI-PMH resumption token computation
- manu: fixing demo DZ browser

# 12.09

### New features
- eric: collapsible facets
- manu: SystemField for collection name
- sjoerd: first version of KML support

### Fixes and refactoring
- manu: fixes for image caching coming from the same domain
- manu: fixing PDF thumbnail creation
- manu: using new Salat version
- manu: modularizing homepage so that plugins can contribute to it
- manu: fixes with Processing, introducing new processing state
- manu: Increasing default page size. Fixes #687
- manu: searchIn service
- manu: unitRoles support for better plugin modularity

# 12.08

### New features

- manu: multi-tenancy support
- manu, eric: advanced search plugin with auto-completion
- manu, sjoerd, eric: reviving related items feature
- manu: making it possible to harvest raw source data
- manu: making it possible to add administrators via the interface
- manu: selective harvesting for OAI-PMH
- manu: using schemas.delving.eu for schemas
- eric: responsive layouts
- manu: generic view resolution mechanism for full view rendering
- gerald: refactoring tests and test data provisioning


### Fixes and refactoring
- manu: refactoring role management to be generic via Roles and ResourceTypes (#630)
- manu: DataSets are now an own module
- manu: better error reporting for SOLR search failures
- manu: constant typification
- manu: starting to use subcut
- manu: adding ScalaTest support
- manu: restructuring project
- manu: refactoring core services and other services, using subcut whenever possible
- gerald: fixing issue with sip-creator, sip-core and schema-repository versions
- manu: #423, #617, #622, #625, #628, #634, #639, #640, #642, #644, #662
- eric: #491, #475, #638

# 12.07

### New features

- manu, eric: complete overhaul of the DataSet page. Now using WebSockets to keep clients in sync

### Fixes and refactoring

- manu: refactoring VirtualCollections to a plugin
- manu: renaming PortalTheme to DomainConfiguration
- manu: using new play2-extensions version, addressing a memory-leak


# 12.06

### New features

- manu: new storage layer:
  - all items saved in mongodb are now in a standardized `MetadataCache`, each item being a `MetadataItem`
  - using BaseX (http://www.basex.org) in order to store source data
- manu: AFF API rendering
- manu: verbatim rendering mechanism for APIs
- manu: DirectoryService lookup to match Provider and DataProvider in the DataSet creation page
- eric: statistics mockup
- manu: multiple domain support for themes (#42)
- manu: buildinfo now prints which version of the hub and the sip-creator are used at startup
- manu: replacing scala pull parser with stax
- manu: redeploy action
- manu: Making it possible to reset hashes on sets stuck in parsing state
- eric: DeepZoom in full view
- eric: full view shows roles separately
- manu: Simplifying deployment process: support for "play dist" deployment
- manu: Checkbox to select whether the query count of a VirtualCollection should be recomputed periodically or not
- manu: Storing statistics generated by the Sip-Creator in MongoDB so that we can quickly compute aggregated statistics.
- sjoerd: misc fixes for the itin endpoint
- manu: Mechanism for giving the ID of an item in the proxy search response
- manu: OaiPmh API for listing only sets of a given format
- eric: flag to control visibility of the login links, per theme

### Fixes and refactoring
- manu: better JSON rendering for ViewRendering APIs
- manu: fix #497, #504, #509, #510, #512
- manu: changing search API layout so that it is valid when rendered as JSON
- manu: correctly rendering the language for ViewRendering API requests
- manu: improving robustness of ImageCache
- manu: Hardening front page against search backend timeout
- manu: limiting search API to MDRs
- manu: JSON rendering overhaul, in order to keep XML and JSON renering in sync
- manu: Groups can be deleted. Fixes #496
- manu: Solid JSON parsing for directory. Fixes #556
- manu: Revining OAuth2 spec, fixing response in case of error. Fixes #543
- manu: Themes are now part of the default application configuration. Closes #525
- manu: Streaming dummy image instead of trying to access via file
- eric: view improvements for several formats (MusIP, ICN)
- manu: Fixing SIP source download to generate identical content as upload

## 12.05.2

### Fixes

- sjoerd: IndexApi accepting default namespaces, aligning system fields
- sjoerd: upgrading to solr-j 3.6.0
- sjoerd: fixing deleteOrphans implementation
- sjoerd: fix in search pager
- sjoerd: fixing search summary difference between xml and json representations

## 12.05.1

### Fixes

- sjoerd: more robust extractor for hubIds, in case the localId contains an underscore
- sjoerd: Fixes to deal with html entities in the ingested xml that are not indexed properly
- manu: URL-encoding the id we get in the SearchService before querying for it, since IDs are URL-encoded in the index

## 12.05.0

### New features

- eric: entirely revisited user interface using Twitter Bootstrap
- sjoerd, manu, gerald: interoperability with the hierarchical Sip-Creator
- manu, sjoerd: revised and much more robust DataSet processing workflow
- manu, eric: XML-based ViewRendering mechanism
- manu, eric: Virtual Collections: create collections of records from various real DataSets, based on search criteria and individual exclusion of records
- manu: refactored core architecture, introducing plugin mechanism extending Play 2.0's native plugin mechanism
- manu, eric: improvements on the DataSet list page (displaying error message details)

### Fixes

- manu, sjoerd, eric: many bug fixes and robustness improvements
