$(document).ready(function () {

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

    var mltEndpoint = '/organizations/' + Thing.orgId + '/api/search?id=' + Thing.hubId + '&format=json&mlt=true';

    $.get(mltEndpoint, function (data) {
        var rItems = data.result.relatedItems.item, html = '', tmp, org, owner, id, uri;
        if (rItems) {
            html = '<h5>' + jsLabels.relatedItems + '</h5><ul class="thumbnails">';
            $.each(rItems, function (i, item) {
                tmp = item.fields['delving_hubId'].split('_');
                org = tmp[0];
                owner = tmp[1];
                id = tmp[2];
                uri = "/" + org + "/thing/" + owner + "/" + id + "?mlt=true";
                html += '<li class="thumbnail">';
                html += '<a href="' + uri + '" rel="nofollow"><img class="mlt" src="' + item.fields['delving_thumbnail'] + '" alt="' + item.fields['delving_title'] + '" width="108" onerror="showDefaultImg(this)"/></a></li>';
            });
            html += "</ul>";
            $('.related-items').html(html);
        }
    });

});