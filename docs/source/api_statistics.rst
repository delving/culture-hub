Statistics API
--------------

The statistics API is a JSON API that provides statistics on any
facetable field. See the **explain** response for a list of facetable
fields.

The API base URL for all statistics actions is:

::

    `http://{baseUrl}:{portNumber}/api/statistics`

The statistics API accepts the following parameters:

-  **facet.field** = a repeatable field with facetable metadata fields
   you want to have returned. Note that you must use the full search
   field as specified in the explain response.
-  **facet.limit** = must contain an integer for the number of
   statistics entries for each field specified in *facet.field*
   parameter. The default value is **100**
-  **filter** = provide any valid query to constrain the set for which
   statistis are being returned. For example, constrain the statistics
   per *region* or *material type*.
-  **lang** = the language in which you want the *i18n* tags to be
   returned. The default value is **en**

The output of the **statistics API** is a list of statistics objects
structured as follows:

::

    {
        statistics: {
            totalRecords: 3304080,
            totalRecordsWithDigitalObjects: 2561466,
            totalRecordsWithLandingPages: 3262741,
            facetCounts: {
                icn_technique_facet: 100,
                icn_material_facet: 100
            },
            facets: [{
                name: "icn_technique_facet",
                i18n: "icn_technique_facet",
                entries: [{
                    name: "zwart-wit foto",
                    total: 18699,
                    digitalObjects: 18258,
                    digitalObjectsPercentage: 1,
                    noDigitalObjects: 3285822,
                    noDigitalObjectsPercentage: 99,
                    landingPages: 18699,
                    landingPagesPercentage: 1,
                    nolandingPages: 3285381,
                    nolandingPagesPercentage: 99
                },
                {… more entries ...}]
            }, {
                name: "icn_material_facet",
                i18n: "icn_material_facet",
                entries: [{
                    name: "aardewerk",
                    total: 27187,
                    digitalObjects: 23093,
                    digitalObjectsPercentage: 1,
                    noDigitalObjects: 3280987,
                    noDigitalObjectsPercentage: 99,
                    landingPages: 27186,
                    landingPagesPercentage: 1,
                    nolandingPages: 3276894,
                    nolandingPagesPercentage: 99
                },
                {… more entries ...}
                }]
            }
        }

-  **statistics**

   -  **totalRecords** = is the total number of records in the index
   -  **totalRecordsWithDigitalObjects** = is the total number of
      records in the index with Digital objects. The definition of
      digital object is that it either has a link to the source object
      or a link to a thumbnail representing the object described in the
      metadata.
   -  **totalRecordsWithLandingPages** = is the total number of records
      in the index with Digital objects. The definition of landingPage
      is the page at the dataProviders website where this object is
      described.
   -  **facetCounts** = returns a map with the names of the statistics
      fields returned and how many entries are returned in the response
   -  **facets**

      -  **name** = is the name of the metadata field whose entries are
         listed. This field is specified in the *facet.field* parameter
      -  **i18n** = if a translation of the metadata field is found this
         is returned based on the value of the *lang* parameter. If no
         translation is found the name of the field is returned
      -  **entries** = is a map of statistics per unique value in the
         facet. This reverse sorted by the frequency in which it occurs
         in the index. The names of the keys should be self-explanatory.

**examples** =
http://www.dimcon.nl:80/api/statistics?facet.field=icn\_technique\_facet&facet.field=icn\_material\_facet&facet.limit=1&lang=nl

