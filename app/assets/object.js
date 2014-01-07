$(document).ready(function () {
    /*
    * DEEPZOOM FUNCTIONALITY
    * First checks if flash is enabled, if so use OpenZoomViewer.swf.
    * If that fails use Seadragon.js
    * And if that fails, view a regular image
    */
    var zoomNav = true,
        zoomUrls = $('#deepZoomUrls a'),
        imageUrls = $('#imageUrls a'),
        imageNavThumbs = $('#thumbnails .img img');
        imageViewContainer = '#image-viewer';
        zoomViewContainer = '#zoom-viewer';
        viewedImageSource = imageViewContainer + ' .img img',
        activateDeepZoom = function(el) {

            var index = imageNavThumbs.index(el),
                deepZoomElement = $(zoomUrls).get(index),
                deepZoomUrl = $(deepZoomElement).attr("href");
            if (hasFlash) {
                var flashEmbed = '<embed class="flash-viewer-window" width="100%" height="100%" flashvars="source=' + deepZoomUrl + '" src="/assets/flash/OpenZoomViewer.swf" menu="false" wmode="opaque" allowFullScreen="true" pluginspage="http://www.adobe.com/go/getflashplayer" type="application/x-shockwave-flash"></embed>';
                $(zoomViewContainer).html(flashEmbed);
            }
            else if (!isIE) {
                // seadragon Zoom
                if (typeof deepZoomElement !== 'undefined') {
                    if(typeof viewer === 'undefined') {
                        $.getScript('/assets/common/javascripts/seadragon/seadragon-min.js', function(){
                            Seadragon.Config.imagePath = "/assets/common/javascripts/seadragon/img/";
                            Seadragon.Config.autoHideControls = false;
                            var viewer = new Seadragon.Viewer(zoomViewContainer.replace('#',''));
                            viewer.addEventListener("error",function(){
                                // if viewer fails, then revert back to a regular image view
                                zoomNav = false;
                                var rImage = $(imageUrls).first().attr('href');
                                // change zoom-viewer id to regular image viewer for navigation and image replacement
                                $(zoomViewContainer).attr('id',imageViewContainer.replace('#',''));
                                // load the first image
                                $(imageViewContainer).html('<div class="img"><img src="'+rImage+'" alt=""/></div>');
                            });
                            viewer.openDzi(deepZoomUrl);
                        });
                    }else {
                        viewer.openDzi(deepZoomUrl);
                    }
                }
            }
        }

    // VIEW DEEP ZOOM IMAGE
    if ($(zoomViewContainer).length) {
        // activate deepzoom functionality for first image
        activateDeepZoom(0)
    }
    // hide thumb nav if there is only one image to view
    if( imageNavThumbs.size() <= 1 ){
        $('#thumbnails').hide();
    }
    // or  make thumbs clickable
    else {      
        imageNavThumbs.each(function(index, el) {
            // switch image src onclick
            $(el).on("click", function() {
                if (zoomNav == false) {
                    var index = $(imageNavThumbs).index(el);
                    var imageSrc = $(imageUrls).get(index);
                    if(typeof imageSrc !== 'undefined') {
                        $(viewedImageSource).attr("src", imageSrc);
                    }
                }
                else {
                    activateDeepZoom(el);
                }
 
            });
        });        
    }

    /*
     * RIGHTS ICONS
     * Append the corresponding icon image to the rights URL
     */
    if ($('#rightsUrl').length) {
        var rights = stripTrailingSlash($('#rightsUrl').find('a').attr('href')),
            icon='',
            img = new Image();

        if (rights != 'undefined') {
            switch (rights) {
                case 'http://creativecommons.org/publicdomain/mark/1.0':
                    icon = 'cc_publicdomain_mark.png';
                    break;
                case 'http://creativecommons.org/publicdomain/zero/1.0':
                    icon = 'cc_publicdomain_zero.png';
                    break;
                case 'http://creativecommons.org/licenses/by/3.0':
                    icon = 'cc_by.png';
                    break;
                case 'http://creativecommons.org/licenses/by-nd/3.0':
                    icon = 'cc_by-nd-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-nc-sa/3.0':
                    icon = 'cc_by-nc-sa-3.0.png';
                    break;
                case 'http://creativecommons.org/licenses/by-sa/3.0/nl':
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
                case 'http://www.europeana.eu/rights/rr-f':
                    icon = 'eu_free_access.jpg';
                    break;
                case 'http://www.europeana.eu/rights/rr-p':
                    icon = 'eu_paid_access.jpg';
                    break;
                case 'http://www.europeana.eu/rights/rr-r':
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

//    if($('.lido-view').length) {
//        var $toggles = $('.lido-view').children('.lido-parent');
//        var $boxes = $toggles.next('div');
//
//    }

});


/*
 * RELATED ITEMS, AKA 'MORE LIKE THIS'
 * Loads related items via ajax call
 */
function renderRelatedItems() {

    // Endpoint to retrieve related items for this object
    var mltEndpoint = '/api/search?id=' + Thing.hubId + '&format=json&mlt=true';

    if(jsLabels.objTitle.length && $('#object-title-big').length) {
        $('#object-title-big').html(jsLabels.objTitle);
    }

    // Make the request for the related objects. If this is a Collection or a Museum, then this request will fail.
    // Use the bad request to change the layout to accomodate a Museum or Collection view definition
    try {
    $.ajax({
        type: "GET",
        url: mltEndpoint,
        success: function(data) {
            if (data.result && data.result.relatedItems) {
                var rItems = data.result.relatedItems.item, html = '', tmp, org, owner, id, uri, title;
                if (rItems) {

                    $('.object-title').toggleClass('hide');
                    html = '<h5>' + jsLabels.relatedItems + '</h5>';
                    $.each(rItems, function (i, item) {

                        tmp = item.fields['delving_hubId'].split('_');
                        org = tmp[0];
                        owner = tmp[1];
                        id = tmp[2];
                        uri = "/" + org + "/" + owner + "/" + id + "?mlt=true";
                        // clean up title since sometimes it contains html
                        title = item.fields['delving_title'].replace(/<\/?[a-z][a-z0-9]*[^<>]*>/ig, "");

                        html += '<div class="media">';
                        html += '<a class="img" href="' + uri + '" rel="nofollow"><img class="mlt" src="' + item.fields['delving_thumbnail'] + '" alt="' + title + '" width="80" onerror="showDefaultImg(this)"/></a>';
                        html += '<div class="bd"><a href="' + uri + '" rel="nofollow"><div class="title">'+ title.trunc(40) +'</a></div>';
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
                else {
                     $('#related-items').hide();
                }
            }
        }
    });
    } catch(e) {
        //..do nothing and fail gracefully
    }
}

/*
 * PAGE UTILITES
 */

function stripTrailingSlash(str) {
    if(str.substr(-1) == '/') {
        return str.substr(0, str.length - 1);
    }
    return str;
}

var isIE = (navigator.appName.indexOf("Microsoft") != -1 && navigator.appVersion.indexOf("Windows") > -1)+1-1;

var hasFlash = function(){
    var nRequiredVersion = 8;
    if(isIE){
        document.write('<script language="VBScript"\> \non error resume next \nhasFlash = (IsObject(CreateObject("ShockwaveFlash.ShockwaveFlash." & ' + nRequiredVersion + '))) \n</script\> \n');
        if(window.hasFlash != null){ return window.hasFlash;};
    };
    if(navigator.mimeTypes && navigator.mimeTypes["application/x-shockwave-flash"] && navigator.mimeTypes["application/x-shockwave-flash"].enabledPlugin){
        var flashDescription = (navigator.plugins["Shockwave Flash 2.0"] || navigator.plugins["Shockwave Flash"]).description;
        var vr = parseInt(flashDescription.charAt(flashDescription.indexOf(".") - 1)); if (vr < 4) vr += 10;
        return vr >= nRequiredVersion;
    };
    return false;
}();
