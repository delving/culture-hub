Proxies API
-----------

The proxy API is an XML API that has been implemented as convenience to
group together various remote resources with the same output wrapping as
all the other Culture-Hub search APIs. The configuration of these
proxies is done in the organization ``production.conf``.

The API base URL for all proxy actions is:

::

    `http://{baseUrl}:{portNumber}/api/proxy`

**note**: The **proxy** API is an XML only API.

List all proxies
~~~~~~~~~~~~~~~~

This commands list all the proxies thats have been configured for this
organization.

The **list** command is given via a REST command appended to the proxy
base URL:

::

        `http://{baseUrl}:{portNumber}/api/proxy/list`     

The output of the **list** command is a list of all available proxies
structured as follows:

::

        <explain>
            <item>
                <id>europeana</id>
                <url>http://api.europeana.eu/api/opensearch.rss</url>
            </item>
        </explain>

-  ``<id>`` = is the identifier that can be used for the proxy search
-  ``<url>`` = is the url that is used by the proxy

In the proxy configuration some hidden parameters like api-keys are
already included.

**examples** =
http://kulturnett2.delving.org:80/proxy/list

Search a specific proxy
~~~~~~~~~~~~~~~~~~~~~~~

The **search** command is given via a REST command appended to the proxy
base URL:

::

        `http://{baseUrl}:{portNumber}/api/proxy/{proxyId}/search` 

The **proxyId** is the ``<id>`` from the output of the proxy **list**
command.

The **search** command accepts the following url query parameters:

-  **query** = any search query supported by the service that is
   proxied.
-  **start** = any integer that is less than the total records return
   and starts at 1. Services which are zero based will be remapped to 1
   based paging.

The output of the **search** command is a list of records structured as
follows:

::

    <results xmlns:europeana="http://www.europeana.eu" xmlns:atom="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:enrichment="http://www.europeana.eu/schemas/ese/enrichment/" xmlns:dc="http://purl.org/dc/elements/1.1/">
        <pagination>
            <numFound>1</numFound>
            <start>1</start>
            <rows>1</rows>
        </pagination>
        <items>
            <item>
                <id>{itemId}</id>
                <fields>
                    {all metadata fields as returned by the proxied service}
                </fields>
            </item>
        </items>
    </results>

-  ``<pagination>`` = if the proxied service supports returning paging
   information the pagination block will be returned in the response

   -  ``<numFound>`` = the total numbers of records found (int)
   -  ``<start>`` = the start number of the first record of the returned
      page (int)
   -  ``<rows>`` = to number records - i.e. items - returned on the page
      (int)

-  ``<item>`` = This wraps each record returned by the proxied service

   -  ``<id>`` = is the identifier that can be used to return the
      *full-view* in the **item** service, i.e. *itemId*. The **id**
      field is only shown if the proxied service supports the request of
      a single record with all metadata fields.
   -  ``<fields>`` = has as its children each metadata field returned by
      the proxied service

**examples** =
http://kulturnett2.delving.org:80/proxy/wikipedia.en/search?query=bard

Request full-view item from proxy
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The **item** command is given via a REST command appended to the proxy
base URL:

::

        `http://{baseUrl}:{portNumber}/api/proxy/item/{itemId}`    

The **itemId** can be any of the *ids* specified in the
``/items/item/id/`` path of the **search** response.

The **item** command has no query parameters.

The output of the **item** command is a verbose rendering of the return
of the proxy service.

