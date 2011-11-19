$(document).ready(function() {
    var docHeight = $(document).height();
    $(".scrollable, .story, .story-container").height(docHeight);
    var root = $(".scrollable").scrollable().navigator("#pnav").navigator("#inav");
    var api = root.scrollable();
    api.onBeforeSeek(function(event, i) {
        $("#pnav li").removeClass("active").eq(i).addClass("active");
        $("#inav li").removeClass("active").eq(i).addClass("active");
    });
});