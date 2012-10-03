$(document).ready(function () {

    // If deepzoom URL is present initiate SeaDragon
    if ($('a[data-type=zoom]').size() > 0) {
        // Deepzoom image & viewer
        var zoomImg = $('a[data-type=zoom]').attr('href'), viewer;
        Seadragon.Config.imagePath = "/assets/common/javascripts/seadragon/img/";
        Seadragon.Config.autoHideControls = false;
        viewer = new Seadragon.Viewer("zoom-viewer");
        viewer.openDzi({
            "url":zoomImg,
            "width":1800,
            "height":1400,
            "tileSize":256,
            "tileOverlap":0,
            "tileFormat":"jpg"});
    }

    // Endpoint to retrieve related items for this object
    var mltEndpoint = '/organizations/' + Thing.orgId + '/api/search?id=' + Thing.hubId + '&format=json&mlt=true';

    if(jsLabels.objTitle.length && $('#object-title-big').length) {
        $('#object-title-big').html(jsLabels.objTitle);
    }

    // Make the request for the related objects. If this is a Collection or a Museum, then this request will fail.
    // Use the bad request to change the layout to accomodate a Museum or Collection view definition
    $.ajax({
        type: "GET",
        url: mltEndpoint,
        success: function(data){
            var rItems = data.result.relatedItems.item, html = '', tmp, org, owner, id, uri;
            if (rItems) {
                $('.object-title').toggleClass('hide');
                html = '<h5>' + jsLabels.relatedItems + '</h5>';
                $.each(rItems, function (i, item) {
                    tmp = item.fields['delving_hubId'].split('_');
                    org = tmp[0];
                    owner = tmp[1];
                    id = tmp[2];
                    uri = "/" + org + "/" + owner + "/" + id + "?mlt=true";
                    html += '<div class="media">';
                    html += '<a class="img" href="' + uri + '" rel="nofollow"><img class="mlt" src="' + item.fields['delving_thumbnail'] + '" alt="' + item.fields['delving_title'] + '" width="80" onerror="showDefaultImg(this)"/></a>';
                    html += '<div class="bd"><a href="' + uri + '" rel="nofollow"><div class="title">'+item.fields['delving_title'].trunc(40)+'</a></div>';
                    if (item.fields['dc_creator']) {
                        html += '<div rel="dc:creator"><span>'+jsLabels.creator+':</span> '+item.fields['dc_creator']+'</div>';
                    }
                    if (item.fields['europeana_collectionTitle']) {
                        html += '<div rel="eruopeana:collectionTitle"><span>'+jsLabels.collection+':</span> '+item.fields['europeana_collectionTitle']+'</div>';
                    }
                    html += '</div></div>';
                });
                html += "</ul>";
                $('#related-items').html(html);
            }
        },
        error: function(){
//            $('.object-data').removeClass('span8');
        }
    });
});