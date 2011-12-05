$(document).ready(function() {
    // Facets stuff
    if ($(".facet-container").length > 0) {
        //Hide (Collapse) the toggle containers on load
        $(".facet-container").hide();

        //Switch the "Open" and "Close" state per click then slide up/down (depending on open/close state)
        $("h4.trigger").click(function() {
            $(this).toggleClass("active").next().slideToggle("slow");
            return false; //Prevent the browser jump to the link anchor
        });

        //Check to see if there are any active facets that need to be toggled to open
        var toggles = $(document).find("h4.trigger");
        $.each(toggles, function() {
            if ($(this).hasClass("active")) {
                $(this).next().css("display", "block");
            }
        })
    }

    if ($(".facet-container input[type=checkbox]").length > 0) {
        $(".facet-container input[type=checkbox]").click(function() {
            window.location.href = $(this).val();
        })
    }

    // Scroll to first checked term
    $(".facet-container").each(function(i) {
        $this = $(this);
        $checked = $this.find("input:checked");
        if ($checked.length) {
            $this.animate({
                scrollTop: $checked.offset().top - $this.offset().top - $this.height() / 2
            }, 1);
        }
    });

    // Drag and Drop stuff

    var actionArea = $("#dropbox-actions");

    $('.draggable').draggable({
        opacity: .5,
        revert: true,
        revertDuration: 500,
        helpers: 'clone',
        containment: 'body',
        cursor: 'move'
    });

    $("#dropbox").droppable({
        accept: '.object, .mdr',
        drop: addToDropbox
    });

    $("#dropbox").droppable({ hoverClass: 'hover' });

    $('#dropbox').bind('removeItem', function(event) {
        $('#dropbox input[value="' + event.itemId + '"]').closest('li').remove();
        if ($('#dropbox ul').find('li').size() == 0) {
            $("#dropbox-info").show();
            actionArea.delay(500).fadeOut(500);
        }
    });

    function addToDropbox(e, ui) {
        var item = ui.draggable;
        var title = $(item).find('input[name="title"]').val();
        var thumb = $(item).find('input[name="thumb"]').val();
        var itemId = $(item).find('input[name="idUri"]').val();

        var list = $(this).find("ul");
        if ($(list).find('li').size() == 0) {
            $("#dropbox-info").hide();
            actionArea.delay(500).fadeIn(500);
        }

        var exists = false;
        $('#dropbox input[name="itemId"]').each(function() {
            if ($(this).val() == itemId) {
                exists = true;
                $(this).closest('.media').effect("bounce", { times:3 }, 300);
            }
        });

        if (!exists) {
            var html = '<li><div class="media"><img class="img" src="' + thumb + '" width="50" /><a class="remove imgExt" href="#">X</a>';
            html += title + '<input type="hidden" name="itemId" value="' + itemId + '"/></div></li>';
            $(html).appendTo(list).hide().delay("250").fadeIn("500");
        }
        $("a.remove").click(function() {
            $('#dropbox').trigger({
                type: 'removeItem',
                itemId: itemId
            });
        });
    }
});