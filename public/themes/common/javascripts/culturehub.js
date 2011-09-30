/**
 * Initialize elements based on classes
 */
function initializeElements() {
    $(".saveButton").button();

//    //all hover and click logic for dui-buttons
//    $(".dui-button:not(.ui-state-disabled)")
//            .hover(
//            function() {
//                $(this).addClass("ui-state-hover");
//            },
//            function() {
//                $(this).removeClass("ui-state-hover");
//            }
//    )
//            .mousedown(function() {
//                $(this).parents('.dui-buttonset-single:first').find(".dui-button.ui-state-active").removeClass("ui-state-active");
//                if ($(this).is('.ui-state-active.dui-button-toggleable, .dui-buttonset-multi .ui-state-active')) {
//                    $(this).removeClass("ui-state-active");
//                }
//                else {
//                    $(this).addClass("ui-state-active");
//                }
//            })
//            .mouseup(function() {
//                if (! $(this).is('.dui-button-toggleable, .dui-buttonset-single .dui-button,  .dui-buttonset-multi .dui-button')) {
//                    $(this).removeClass("ui-state-active");
//                }
//            });

//On Hover Over
    function megaHoverOver() {
        $(this).find(".sub").stop().fadeTo('fast', 1).show(); //Find sub and fade it in
        (function($) {
            //Function to calculate total width of all ul's
            jQuery.fn.calcSubWidth = function() {
                rowWidth = 0;
                //Calculate row
                $(this).find("ul").each(function() { //for each ul...
                    rowWidth += $(this).width(); //Add each ul's width together
                });
            };
        })(jQuery);

        if ($(this).find(".row").length > 0) { //If row exists...

            var biggestRow = 0;

            $(this).find(".row").each(function() {    //for each row...
                $(this).calcSubWidth(); //Call function to calculate width of all ul's
                //Find biggest row
                if (rowWidth > biggestRow) {
                    biggestRow = rowWidth;
                }
            });

            $(this).find(".sub").css({'width' :biggestRow}); //Set width
            $(this).find(".row:last").css({'margin':'0'});  //Kill last row's margin

        } else { //If row does not exist...

            $(this).calcSubWidth();  //Call function to calculate width of all ul's
            $(this).find(".sub").css({'width' : rowWidth}); //Set Width

        }
    }

//On Hover Out
    function megaHoverOut() {
        $(this).find(".sub").stop().fadeTo('fast', 0, function() { //Fade to 0 opactiy
            $(this).hide();  //after fading, hide it
        });
    }

//Set custom configurations
    var config = {
        sensitivity: 2, // number = sensitivity threshold (must be 1 or higher)
        interval: 100, // number = milliseconds for onMouseOver polling interval
        over: megaHoverOver, // function = onMouseOver callback (REQUIRED)
        timeout: 500, // number = milliseconds delay before onMouseOut
        out: megaHoverOut // function = onMouseOut callback (REQUIRED)
    };

    $("ul#user-menu li .sub").css({'opacity':'0'}); //Fade sub nav to 0 opacity on default
    $("ul#user-menu li").hoverIntent(config); //Trigger Hover intent with custom configurations

}

/**
 * Helper to fetch a thumbnail URL
 * @param id the ID of the thumbnail
 */
function thumbnailUrl(id) {
    if (typeof id === 'undefined' || id === "") {
        return '/public/images/dummy-object.png';
    } else {
        return '/thumbnail/' + (typeof id === 'function' ? id() : id);
    }
}

/**
 * Helper to add a template to a page as script tag
 * @param templateName the name of the template
 * @param templateMarkup the markup of the template
 */
function addTemplate(templateName, templateMarkup) {
    $("body").append("<script type='text/html' id='" + templateName + "'>" + templateMarkup + "</script>");
}

/**
 * Handles submission of a viewModel via KO
 * @param url the submission URL
 * @param viewModel the viewModel to submit
 * @param formSelector jQuery selector for the form that holds the viewModel
 * @param redirectUrl the URl to redirect to in case of success, can be null
 * @param onSuccess what to do in case of success
 * @param onError what to do in case of failure
 * @param additionalData additional Data to be sent over
 */
function handleSubmit(url, viewModel, formSelector, redirectUrl, onSuccess, onError, additionalData) {
    Spinners.create('.wait').play();
    if ((typeof formSelector !== 'undefined' && formSelector != null && $(formSelector).validate({meta: 'validate'}).form()) || !formSelector) {
        $.postKOJson(url, viewModel, function(data) {
            Spinners.get('.wait').remove();
            updateViewModel(data, viewModel);
            if (typeof onSuccess !== 'undefined') onSuccess.call();
            if (redirectUrl) window.location.href = redirectUrl;
        }, function(jqXHR, textStatus, errorThrown) {
            Spinners.get('.wait').remove();
            updateViewModel($.parseJSON(jqXHR.responseText), viewModel);
            if (typeof onError !== 'undefined') onError.call();
        }, additionalData);
    } else {
        Spinners.get('.wait').remove();
    }
}

/**
 * Post knockoutJS form data as JSON string, into a parameter called "data". Deserializes mapping from the mapping plugin.
 *
 * @param url the URL to submit to
 * @param viewModel the data object
 * @param onSuccess the success callback to run after successful execution
 * @param onFailure the failure callback to run on error
 * @param additionalData an additional object with data, directly extended into what is sent to the server
 */
$.postKOJson = function (url, viewModel, onSuccess, onFailure, additionalData) {
    var data = typeof additionalData === 'undefined' ? { data: ko.mapping.toJSON(viewModel) } : $.extend({ data: ko.mapping.toJSON(viewModel) }, additionalData);
    return jQuery.ajax({
        type: 'POST',
        url: url,
        data: data,
        contentType: 'application/x-www-form-urlencoded; charset=utf-8',
        dataType: 'json'
    }).success(onSuccess).error(onFailure);
};

/**
 * Loads an object via JSON into a view model
 * @param url where to load the object from
 * @param viewModel the view model to update
 * @param scope the scope for the view model binding
 */
function load(url, viewModel, scope, callback) {
    $.getJSON(url, {}, function(data) {
        updateViewModel(data, viewModel, scope);
        if (typeof callback !== 'undefined' && typeof callback === 'function') callback.call();
    });
}

/**
 * Updates a view model given a javascript object
 * @param data the data object
 * @param viewModel the knockoutJS viewModel
 * @param scope the scope of the model binding
 */
function updateViewModel(data, viewModel, scope) {

    var mapping = {
        'errors': {
            'create': function(options) {
                return ko.observable(ko.mapping.fromJS(options.data));
            }
        }
    };

    if (ko.mapping.isMapped(viewModel)) {
        ko.mapping.updateFromJS(viewModel, data)
    } else {
        $.extend(viewModel, ko.mapping.fromJS(data, mapping));
        if (typeof scope !== 'undefined') {
            ko.applyBindings(viewModel, scope);
        } else {
            ko.applyBindings(viewModel);
        }
    }

    if (data.errors) {
        viewModel.errors(ko.mapping.fromJS(data.errors));
    }
}

// ~~~ common dialogs

function confirmDeletion(elementSelector, onDelete) {
    $(elementSelector).dialog({
        resizable: false,
        height:140,
        modal: true,
        buttons: {
            "Delete": function() {
                if (typeof onDelete !== 'undefined' && typeof onDelete === 'function') onDelete.call();
                $(this).dialog("close");
            },
            Cancel: function() {
                $(this).dialog("close");
            }
        }
    });

}


// ~~~ object handling functions

/**
 * Handles object addition from an object selection box
 * @param objects the observable array in which objects are stored
 * @param selectedObjectIds the observable array containing the checked object IDs
 * @param availableObjects the objects the user still has available for selection
 */
function addSelectedObjects(objects, selectedObjectIds, availableObjects) {
    $.each(selectedObjectIds(), function(index, selectedId) {
        var objs = $.grep(availableObjects(), function(searchedElement, searchedIndex) {
            return elementIdMatch(selectedId, searchedElement);
        });
        var obj = ko.toJS(objs[0]);
        objects.push(obj);
        availableObjects.remove(function(item) {
            return elementIdMatch(obj.id, item);
        });
    });
    selectedObjectIds.removeAll();
}

/**
 * Checks if an element has a given ID
 * @param id the ID to match against
 * @param element the element - may or may not be a mapped knockout object
 */
function elementIdMatch(id, element) {
    return id === ko.toJS(element).id;
}


// ~~~ KnockoutJS binders

/**
 * KnockoutJS binder for the jQuery tokenInput plugin
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
ko.bindingHandlers.tokens = {
    init: function(element, valueAccessor, allBindingsAccessor, viewModel) {
        viewModel.lock = false;
        var modelValue = valueAccessor();

        $(element).change(function() {
            if (!viewModel.lock) {
                var tokens = $(element).tokenInput('get');
                if (typeof tokens !== 'undefined') {
                    if (ko.isWriteableObservable(modelValue)) {
                        var existing = ko.utils.unwrapObservable(modelValue);
                        var existingNames = [];
                        $.each(existing, function(index, el) {
                            existingNames.push(typeof el.name == 'function' ? el.name() : el.name);
                        });
                        var updatedNames = [];
                        $.each(tokens, function(index, el) {
                            updatedNames.push(el.name);
                        });

                        // remove elements
                        $.each(existing, function(index, el) {
                            var name = typeof el.name == 'function' ? el.name() : el.name;
                            if ($.inArray(name, updatedNames) < 0) {
                                modelValue.remove(el);
                                tokens.splice(index, 1);
                            }
                        });
                        // add new
                        $.each(tokens, function(index, el) {
                            if ($.inArray(el.name, existingNames) < 0) {
                                modelValue.push({id: el.id, name: el.name});
                            }
                        });
                    }

                }
            }
        });


    },
    update:
            function(element, valueAccessor, allBindingsAccessor, viewModel) {
                viewModel.lock = true;
                $(element).tokenInput('clear');
                var value = valueAccessor();
                var tokens = ko.utils.unwrapObservable(value);
                $(tokens).each(function() {
                    var token = ko.utils.unwrapObservable(this);
                    $(element).tokenInput('add', {id: typeof token.id == 'function' ? token.id() : token.id, name: typeof token.name == 'function' ? token.name() : token.name});
                });
                delete viewModel.lock;
            }
};

if (typeof Delving === 'undefined') {
    Delving = {};
}

/**
 * KnockoutJS binder for TinyMCE
 */
ko.bindingHandlers.tinymce = {
    init: function (element, valueAccessor, allBindingsAccessor, context) {
        var options = allBindingsAccessor().tinymceOptions || {};
        var modelValue = valueAccessor();

        //handle edits made in the editor
        options.setup = function (ed) {
            ed.onChange.add(function (ed, l) {
                if (ko.isWriteableObservable(modelValue)) {
                    modelValue(l.content);
                }
            });
        };

        //handle destroying an editor (based on what jQuery plugin does)
        ko.utils.domNodeDisposal.addDisposeCallback(element, function () {
            $(element).parent().find("span.mceEditor,div.mceEditor").each(function (i, node) {
                var ed = tinyMCE.get(node.id.replace(/_parent$/, ""));
                if (ed) {
                    ed.remove();
                }
            });
        });

        setTimeout(function() {
            //$(element).tinymce(options);
            Delving.wysiwyg(options);
        }, 0);

    },
    update: function (element, valueAccessor, allBindingsAccessor, context) {
        //handle programmatic updates to the observable
        var value = ko.utils.unwrapObservable(valueAccessor());
        var ed = tinyMCE.get(element.id.replace(/_parent$/, ""));
        if (typeof ed !== 'undefined') ed.setContent(value);
    }
};


/** Binding for adding stylized and rich multiselect - jQuery UI MultiSelect Widget
 *  http://www.erichynds.com/jquery/jquery-ui-multiselect-widget/
 *
 *  source: https://github.com/thelinuxlich/knockout_bindings/blob/master/knockout_bindings.js#L156
 */
ko.bindingHandlers.jqMultiSelect = {
    init: function(element, valueAccessor, allBindingsAccessor, viewModel) {
        var defaults = {
            click: function(event, ui) {
                var selected_options = $.map($(element).multiselect("getChecked"), function(a) {
                    return $(a).val()
                });
                allBindingsAccessor()['selectedOptions'](selected_options);
            }
        };
        var options = $.extend(defaults, valueAccessor());

        var selected = allBindingsAccessor()['selectedOptions']();
        var available = allBindingsAccessor()['options']();
        var optionsText = allBindingsAccessor()['optionsText'];
        var optionsValue = allBindingsAccessor()['optionsValue'];

        $.each(selected, function(index, el) {
            var option = $.grep(available, function(e, index) {
                return (typeof e[optionsValue] === 'function' ? e[optionsValue]() : e[optionsValue]) == el
            });
            var optionText = (option[0])[optionsText];
            $(element).append("<option value='" + el + "'>" + optionText + "</option>");
        });

        allBindingsAccessor()['options'].subscribe(function(value) {
            ko.bindingHandlers.jqMultiSelect.regenerateMultiselect(element, options, viewModel);
        });
        allBindingsAccessor()['selectedOptions'].subscribe(function(value) {
            ko.bindingHandlers.jqMultiSelect.regenerateMultiselect(element, options, viewModel);
        });
    },
    update: function (element, valueAccessor, allBindingsAccessor, context) {
        ko.bindingHandlers.jqMultiSelect.regenerateMultiselect(element, null, context);
    },
    regenerateMultiselect: function(element, options, viewModel) {
        if ($(element).next().hasClass("ui-multiselect")) {
            setTimeout(function() {
                if ($(element).multiselect('option', 'filter') === true) {
                    return $(element).multiselect("refresh").multiselectfilter({
                        label: $(element).multiselect('option', 'filterLabel') || "Search: "
                    });
                } else {
                    return $(element).multiselect("refresh");
                }
            }, 0);
        } else {
            setTimeout(function() {
                if ($(element).multiselect('option', 'filter') === true) {
                    $(element).multiselect(options).multiselectfilter({
                        label: $(element).multiselect('option', 'filterLabel') || "Search: "
                    });
                } else {
                    $(element).multiselect(options);
                    $(element).multiselect('close');
                }
                if ($(element).multiselect('option', 'noChecks') === true) {
                    $(element).next().next().find(".ui-helper-reset:first").remove();
                }
            }, 0);
        }
    }
};

/**
 * Add the TinyMCE WYSIWG editor to a page.
 * Default is to add to all textareas.
 *
 * @param {Object} [params] Parameters to pass to TinyMCE, these override the
 * defaults.
 */
Delving.wysiwyg = function (params) {
    // Default parameters
    initParams = {
        convert_urls: false,
        mode: "textareas", // All textareas
        theme: "advanced",
        editor_selector : "mceEditor",
        editor_deselector : "mceNoEditor",
        theme_advanced_toolbar_location: "top",
        force_br_newlines: false,
        forced_root_block: 'p', // Needed for 3.x
        remove_linebreaks: true,
        fix_content_duplication: false,
        fix_list_elements: true,
        valid_child_elements: "ul[li],ol[li]",
        theme_advanced_buttons1: "bold,italic,underline,justifyleft,justifycenter,justifyright,bullist,numlist,link,formatselect,code",
        theme_advanced_buttons2: "",
        theme_advanced_buttons3: "",
        theme_advanced_toolbar_align: "left"
    };

    // Overwrite default params with user-passed ones.
    for (var attribute in params) {
        // Account for annoying scripts that mess with prototypes.
        if (params.hasOwnProperty(attribute)) {
            initParams[attribute] = params[attribute];
        }
    }

    tinyMCE.init(initParams);
};