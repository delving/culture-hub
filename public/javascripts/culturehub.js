/**
 * Initialize elements based on classes
 */
function initializeElements() {
    $(".saveButton").button();

    //all hover and click logic for dui-buttons
    $(".dui-button:not(.ui-state-disabled)")
            .hover(
            function() {
                $(this).addClass("ui-state-hover");
            },
            function() {
                $(this).removeClass("ui-state-hover");
            }
    )
            .mousedown(function() {
                $(this).parents('.dui-buttonset-single:first').find(".dui-button.ui-state-active").removeClass("ui-state-active");
                if ($(this).is('.ui-state-active.dui-button-toggleable, .dui-buttonset-multi .ui-state-active')) {
                    $(this).removeClass("ui-state-active");
                }
                else {
                    $(this).addClass("ui-state-active");
                }
            })
            .mouseup(function() {
                if (! $(this).is('.dui-button-toggleable, .dui-buttonset-single .dui-button,  .dui-buttonset-multi .dui-button')) {
                    $(this).removeClass("ui-state-active");
                }
            });
}

/**
 * Post knockoutJS form data as JSON string, into a parameter called "data". Deserializes mapping from the mapping plugin.
 *
 * @param url the URL to submit to
 * @param viewModel the data object
 * @param onSuccess the success callback to run after successful execution
 * @param onFailure the failure callback to run on error
 */
$.postKOJson = function (url, viewModel, onSuccess, onFailure) {
    return jQuery.ajax({
        type: 'POST',
        url: url,
        data: {data: ko.mapping.toJSON(viewModel) },
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
function load(url, viewModel, scope) {
    $.getJSON(url, {}, function(data) {
        updateViewModel(data, viewModel, scope);
    });
}

/**
 * Updates a view model given a javascript object
 * @param data the data object
 * @param viewModel the knockoutJS viewModel
 * @param scope the scope of the model binding
 */
function updateViewModel(data, viewModel, scope) {
    if (ko.mapping.isMapped(viewModel)) {
        ko.mapping.updateFromJS(viewModel, data)
    } else {
        $.extend(viewModel, ko.mapping.fromJS(data));
        if (typeof scope !== 'undefined') {
            ko.applyBindings(viewModel, scope);
        } else {
            ko.applyBindings(viewModel);
        }
    }
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
        $(element).html(value);
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