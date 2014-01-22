$(document).ready(function() {

    // set for multiple uses further on
    var $facetContainer = $('.facet-container');

    // facets collapse
    if ($facetContainer.hasClass('collapsible')){
        // hide() or show() the toggle containers on load
        $facetContainer.hide();
        //Switch the "Open" and "Close" state per click then slide up/down (depending on open/close state)
        $(".facet-toggle").click(function() {
            $(this).toggleClass("open").next().slideToggle("slow");
            return false; //Prevent the browser jump to the link anchor
        });
        //Check to see if there are any active facets that need to be toggled to open
        var toggles = $(document).find(".facet-toggle");
        $.each(toggles, function() {
            if ($(this).hasClass("open")) {
                $(this).next().css("display", "block");
            }
        });
    }

    // hide the sorting tools for tiny lists
    $facetContainer.find('.list').each(function(i, list){
        if(list.children.length < 5){
           $(list).prevAll("div.facet-tools").hide();
        }
    });

    // Scroll to first checked term for clarity
    $facetContainer.each(
        function(i) {
            $this = $(this);
            $checked = $this.find("input:checked");
            if ($checked.length) {
                $this.animate({
                    scrollTop: $checked.offset().top - $this.offset().top - $this.height() / 2
                }, 1);
            }
        });

    // create click functionality for checkboxes
    function launchCheckQuery(){
        if ($facetContainer.find('input[type=checkbox]').length > 0) {
            $facetContainer.find('input[type=checkbox]').click(function() {
                window.location.href = $(this).val();
            });
        }
    }

    // Let user sort the facet lists alphabetically or by hit count
    function sortFacets(target, type){
        // @target is the id associated with the list
        // @type is either on 'name' or 'count
        var theList = $('#'+target).find('ul');
        var theListItems = $.makeArray(theList.children("li"));

        if(!theList.hasClass('sorted')){
            theListItems.sort(function(a, b) {
                var textA = $(a).data(type);
                var textB = $(b).data(type);
                if (textA < textB) return -1;
               if (textA > textB) return 1;
                return 0;
            });
            theList.empty();
            $.each(theListItems, function(index, item) {
                theList.append(item);
            });
            theList.addClass('sorted');
        }
        else {
            theListItems.reverse();
            theList.empty();
            $.each(theListItems, function(index, item) {
                theList.append(item);
            });
            theList.removeClass('sorted');
        }
        // rebuild the checkbox functionality
        launchCheckQuery();
    }

    $('.sort').on('click', function(e){
        e.preventDefault();
        var target = $(this).data('id');
        var type = $(this).data('sort-type');
        sortFacets(target, type);
    });
    
    // launch query when checkbox is selected/deselected
    $('.include-without-digtalobjects').on('click', function(){
        document.location.href=$(this).val();
    });

    launchCheckQuery();

});

