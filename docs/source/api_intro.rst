Introduction Delving Culture-Hub API documentation
=====================================

This document describes the delving Culture-Hub APIs that are available
on the default deployments. The APIs are always constrained to the
information of a single organization.

The URL structure that we use is:

::

    `http://{baseUrl}:{portNumber}/api/{apiType}`

-  **baseUrl** = is the basic ip or domain where the hub is hosted.
-  **portNumber** = is the port at which the hub is listening for
   requests. (**default**: 80)
-  **apiType** = is the main type of the API. Currently, there are the
   following main API types:

   -  search
   -  statistics
   -  proxies
   -  OAI-PMH harvesting

An example of a full URL is the Norvegiana Culture-Hub:

::

    `http://kulturnett2.delving.org:80/api/search?query=norge`

The Culture-Hub API has as its core design principle that all the state
the application has must be available to the API consumer. This means
that computations made on the server should not have to re-computed by
the client that is consuming the API. This is also why the response are
so elaborate. To reduce the verboseness it is possible to specify which main elements should be returned via the `verbose` and `strict` parameters. 

This documentation refers to API version **0.16** and above. The version number of the API can be found as an `@version` attribute in the main element of the API response.
