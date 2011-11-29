$(document).ready(function() {
    var viewAreaHeight = 0;
    var root = $(".scrollable").scrollable().navigator("#pnav").navigator("#inav");
    var api = root.scrollable();
    setTimeout(function(){ setHeight() }, 1000);
    api.onBeforeSeek(function(event, i) {
        $("#pnav li").removeClass("active").eq(i).addClass("active");
        $("#inav li").removeClass("active").eq(i).addClass("active");
    });
    api.onSeek(function(event, i) {
        setHeight();
    });

    function setHeight() {
        currentIndex = api.getIndex();
        $('div#page_' + currentIndex).each(function(i) {
            viewAreaHeight += $(this).outerHeight() + 40;
        });
        $('.scrollable').css("height", viewAreaHeight);
        viewAreaHeight = 0;

    }
});
