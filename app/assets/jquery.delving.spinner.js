/**
 * Attach a "spinner" or busy signal to a div to alert the user that something is going on
 * @author: Eric van der Meulen eric@delving.eu
 * @datecreated: 08-10-2011
 */

(function($) {

    var methods = {
        init : function(options) {
            var defaults = {
                imgSrc: "/assets/common/images/spinner.gif",
                title: "Please wait...",
                alt: "Please wait..."
            };

            var options = $.extend(defaults, options);

            var img = new Image();
		    img.src = options.imgSrc;
            img.alt = options.alt;
            img.title = options.title;

            return this.each(function() {
                obj = $(this);
                obj.html(img);
                obj.css("visibility", "");

            });
        },
        show : function() {
            return this.each(function() {
                obj = $(this);
                obj.css("visibility", "visible");

            });
        },
        hide : function() {
             return this.each(function() {
                obj = $(this);
                obj.css("visibility", "hidden");
            });
        }
    };

    $.fn.spinner = function(method) {
        // Method calling logic
        if (methods[method]) {
            return methods[ method ].apply(this, Array.prototype.slice.call(arguments, 1));
        } else if (typeof method === 'object' || ! method) {
            return methods.init.apply(this, arguments);
        }
        else {
            $.error('Method ' + method + ' does not exist on jQuery.busy');
        }
    };

})(jQuery);