$(document).ready(function () {

    var imageNavThumbs = $('#thumbnails .img img');
    
    // hide thumbnav if there is only one image to view
    if( imageNavThumbs.size() <= 1 ){
        $('#thumbnails').hide();
    }

    // regular images present
    if ( $('#image-viewer').length) {
        imageNavThumbs.each(function(index, el) {
            // switch image src onclick
            $(el).on("click", function() {
                var index = $('#thumbnails .img img').index(el);
                var imageSrc = $('#imageUrls a').get(index);
                if(typeof imageSrc !== 'undefined') {
                    $('#image-viewer .img img').attr("src", imageSrc)
                }
            });
        });

    }

    // deepzoom images present
    if ($('#zoom-viewer').length) {
        var viewer;
        // activate deepzoom functionality for first image
        activateDeepZoom(0)
        // switch zoom image onclick
        imageNavThumbs.each(function(index, el) {
            $(el).on("click", function() { activateDeepZoom(el); });
        });
    }

    function activateDeepZoom(el) {
        var index = imageNavThumbs.index(el);
        var deepZoomElement = $('#deepZoomUrls a').get(index);
        if (typeof deepZoomElement !== 'undefined') {
            var deepZoomUrl = $(deepZoomElement).attr("href");
            if(typeof viewer === 'undefined') {
                Seadragon.Config.imagePath = "/assets/common/javascripts/seadragon/img/";
                Seadragon.Config.autoHideControls = false;
                viewer = new Seadragon.Viewer("zoom-viewer");
            }
            viewer.openDzi({
                "url":deepZoomUrl,
                "width":1800,
                "height":1400,
                "tileSize":256,
                "tileOverlap":0,
                "tileFormat":"jpg"});
        }
    }

    if ($('#rightsUrl').length) {
        var rights = $('#rightsUrl').find('a').attr('href'), icon='', img = new Image();

        if (rights != 'undefined') {
            switch (rights) {
                case 'http://creativecommons.org/publicdomain/mark/1.0/':
                    icon = 'cc_publicdomain_mark.png';
                    break;
                case 'http://creativecommons.org/publicdomain/zero/1.0/':
                    icon = 'cc_publicdomain_zero.png';
                    break;
                case 'http://creativecommons.org/licenses/by/3.0/':
                    icon = 'cc_by.png';
                    break;
                case 'http://creativecommons.org/licenses/by-nd/3.0/':
                    icon = 'cc_by-nd-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-nc-sa/3.0/':
                    icon = 'cc_by-nc-sa-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-sa/3.0/nl/':
                    icon = 'cc_by-sa-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-sa/3.0':
                    icon = 'cc_by-sa-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-nc/3.0':
                    icon = 'cc_by-nc-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-nc-nd/3.0':
                    icon = 'cc_by-nc-nd-3.0.png';
                    break;
                case 'http://www.europeana.eu/rights/rr-f/':
                    icon = 'eu_free_access.jpg';
                    break;
                case 'http://www.europeana.eu/rights/rr-p/':
                    icon = 'eu_paid_access.jpg';
                    break;
                case 'http://www.europeana.eu/rights/rr-r/':
                    icon = 'eu_restricted_access.jpg';
                    break;
                default:
                    icon = '';
            }
            if (icon != '') {
                img.src = '/assets/common/images/rights/'+icon;
                $('#rightsUrl').find('a').append(img);
            }
        }
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
                    html += '<a class="img" href="/' + uri + '" rel="nofollow"><img class="mlt" src="' + item.fields['delving_thumbnail'] + '" alt="' + item.fields['delving_title'] + '" width="80" onerror="showDefaultImg(this)"/></a>';
                    html += '<div class="bd"><a href="/' + uri + '" rel="nofollow"><div class="title">'+item.fields['delving_title'].trunc(40)+'</a></div>';
                    if (item.fields['dc_creator']) {
                        html += '<div rel="dc:creator"><span>'+jsLabels.creator+':</span> '+item.fields['dc_creator']+'</div>';
                    }
                    if (item.fields['europeana_collectionTitle']) {
                        html += '<div rel="europeana:collectionTitle"><span>'+jsLabels.collection+':</span> '+item.fields['europeana_collectionTitle']+'</div>';
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