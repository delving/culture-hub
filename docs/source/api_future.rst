.. contents::
   :depth: 3
..

Planned functionality for the API 
==================================



access statistics API
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Overview envisioned functionality:

-  statistics

   -  index fields and usages

      -  which fields are indexed
      -  which types

         -  these are the dynamic type suffixes

            -  based on the record definition

      -  which can be used as facets
      -  access to individual histograms

         -  gathered in the Sip-Creator

   -  access statistics

      -  origin

         -  unique users

            -  return visitors

         -  unique areas

            -  reverse ip lookups

      -  information accessed

         -  per

            -  municipality
            -  county
            -  country
            -  language
            -  provider
            -  dataprovider
            -  record type

         -  in

            -  search result page view
            -  used as facet
            -  objects viewed
            -  nr of outgoing ` <>`__\ links clicked

      -  From

         -  API consumer

            -  instant website
            -  Drupal module
            -  other
            -  which named-slice or API is used

         -  Hub-Website

   -  quantitative indicators

Grouping / Clustering API
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The grouping API is designed to group together the search results, based
on the value of a field. You could, for example, group the search
results of your query by country or language and then show under each
header the first 5 results. This functionality is nice for home pages
where you want to show the variety of the collection you have gathered
by provider, dataProvider, etc.
