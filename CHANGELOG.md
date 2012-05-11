# CultureHub changelog

This is the changelog of the CultureHub. It documents changes of the main platform, without modules.

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

