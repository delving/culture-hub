$(document).ready(function() {
    // masonry grid
    var $container = $('.grid-container');
    $container.imagesLoaded( function() {
        $container.masonry({
            itemSelector : '.grid-item',
            gutterWidth : 10
        });
    });
    
// facets
//    if ($(".facet-container").length > 0) {
//        // hide() or show() the toggle containers on load
////        $(".facet-container").hide();
//
//        //Switch the "Open" and "Close" state per click then slide up/down (depending on open/close state)
//        $(".facet-toggle").click(function() {
//            $(this).toggleClass("open").next().slideToggle("slow");
//            return false; //Prevent the browser jump to the link anchor
//        });
//
//        //Check to see if there are any active facets that need to be toggled to open
//        var toggles = $(document).find(".facet-toggle");
//        $.each(toggles, function() {
//            if ($(this).hasClass("open")) {
//                $(this).next().css("display", "block");
//            }
//        });
//    }
    // create click functionality for checkboxes
    if ($(".facet-container input[type=checkbox]").length > 0) {
        $(".facet-container input[type=checkbox]").click(function() {
            window.location.href = $(this).val();
        });
    }

    // Scroll to first checked term for clarity
    $(".facet-container").each(
        function(i) {
            $this = $(this);
            $checked = $this.find("input:checked");
            if ($checked.length) {
                $this.animate({
                    scrollTop: $checked.offset().top - $this.offset().top - $this.height() / 2
                }, 1);
            }
    });
});