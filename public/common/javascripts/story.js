$(document).ready(function() {
    var viewAreaHeight = 0;
    var root = $("#story").scrollable().navigator("#pnav").navigator("#inav");
    var api = root.scrollable();
    setTimeout(function(){ setHeight() }, 1200);
    api.onBeforeSeek(function(event, i) {
        $("#pnav li a, #inav li a").removeClass("active").eq(i).addClass("active");
    });
    api.onSeek(function(event, i) {
        setHeight();
    });

    function setHeight() {
        currentIndex = api.getIndex();
        $('div#page_' + currentIndex).each(function(i) {
            viewAreaHeight += $(this).outerHeight() + 40;
        });
        $('#story').css("height", viewAreaHeight);
        viewAreaHeight = 0;

    }
});
