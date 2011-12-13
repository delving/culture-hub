/**
 * Initialize elements based on classes
 */
function initializeElements() {
//    $("#page").height($(document).height());
    $('.extHelp').tipTip();
    $('.cancelButton').click(function(e) {
      e.preventDefault();
      document.location = document.referrer;
    });
    $.preloadImages (
        "/public/common/images/spinner.gif"
    );
}

/**
 * A bit of security...
 * @param csrfToken token known only by the client and the server
 */
function bindCSRFToken(csrfToken) {
    $("body").bind("ajaxSend", function(elm, xhr, s) {
      if (s.type == "POST" || s.type == "DELETE" || s.type == "PUT") {
        xhr.setRequestHeader('X-CSRF-Token', csrfToken);
      }
    });
}

function tokenInput(id, searchUrl, prePopulate, params, addUrl, addData, deleteUrl, addCallback, deleteCallback) {
    var defaultParams = {
      prePopulate: prePopulate ? prePopulate : [],
      allowCreation: false,
      preventDuplicates: true,
      onAdd: function(item) {
        $.ajax({
          type: 'POST',
          data: typeof addData === 'function' ? addData.call(this, item) : addData,
          url: typeof addUrl === 'function' ? addUrl.call(this, item) : addUrl,
          success: function(data) {
            if(item.wasCreated) {
              item.id = data.id;
            }
            if(typeof addCallback === 'function') addCallback.call(this, item);
          },
          error: function(jqXHR, textStatus, errorThrown) {
            // TODO prompt the user that something went wrong
            $(id).tokenInput('remove', {id: item.id})
          }
        });
      },
      onDelete: function(item) {
        $.ajax({
          type: 'DELETE',
          url: deleteUrl + item.id,
          success: function() {
            if(typeof deleteCallback === 'function') deleteCallback.call(this, item);
          },
          error: function(jqXHR, textStatus, errorThrown) {
            // TODO prompt the user that something went wrong
            $(id).tokenInput('add', {id: item.id})
          }
        });
      }
    };

    $(id).tokenInput(searchUrl, $.extend(defaultParams, params));


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
    $(".wait").spinner();
    if ((typeof formSelector !== 'undefined' && formSelector != null && $(formSelector).validate({meta: 'validate'}).form()) || !formSelector) {
        $.postKOJson(url, viewModel, function(data) {
            $(".wait").spinner("hide");
            updateViewModel(data, viewModel);
            if (onSuccess) onSuccess.call();
            if (redirectUrl) window.location.href = redirectUrl + viewModel.id();
        }, function(jqXHR, textStatus, errorThrown) {
            $(".wait").spinner("hide");
            updateViewModel($.parseJSON(jqXHR.responseText), viewModel);
            if (typeof onError !== 'undefined') onError.call();
        }, additionalData);
    } else {
        $(".wait").spinner("hide");
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
 * @param data the data to load (as JSON object)
 * @param viewModel the view model to update
 * @param scope the scope for the view model binding
 */
function load(data, viewModel, scope, callback) {
    updateViewModel(data, viewModel, scope);
    if (typeof callback !== 'undefined' && typeof callback === 'function') callback.call();
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
        ko.mapping.fromJS(data, viewModel)
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

function remove(buttonId, dialogId, removeUrl, redirectUrl) {
    $(buttonId).click(function(e) {
      e.preventDefault();
      confirmDeletion(dialogId, function() {
          $.ajax({
            url: removeUrl,
            type: 'DELETE',
            complete: function() {
              document.location = redirectUrl;
            }
          });
      });
    });

}

// ~~~ common dialogs

function confirmationDialog(elementId, onConfirm, message, title) {
    var id = '#' + elementId + 'ConfirmationDialog';
    if($(id).length == 0) {
        $('<div id="' + id + '" title="' + title + '"><p><span class="ui-icon ui-icon-alert" style="float:left; margin:0 7px 20px 0;"></span>' + message + '</p></div>').insertAfter(id);
        confirmDeletion(id, onConfirm);
    }
    $(id).dialog('open');
}

/**
 * Confirmation dialog for item deletions
 * i18n version. Labels are set in the commonHeader.html
 * If no labels are found default englsih texts 'Delete' and 'Cancel' are used
 */
function confirmDeletion(elementSelector, onDelete, onCancel) {
    var btnDelete, btnCancel;
    btnDelete = (jsLabels.remove) ? jsLabels.remove : "Delete";
    btnCancel = (jsLabels.cancel) ? jsLabels.cancel : "Cancel";
    var btnOptions = {};
    btnOptions[btnDelete] = function() { if (typeof onDelete === 'function') onDelete.call(); $(this).dialog("close"); }
    btnOptions[btnCancel] = function() { if (typeof onCancel === 'function') onCancel.call(); $(this).dialog("close"); }
    $(elementSelector).dialog({
        autoOpen: true,
        resizable: false,
        minHeight: 150,
        modal: true,
        buttons: btnOptions
    });
}


// ~~~ object handling functions

/**
 * Handles object addition from an object selection box
 * @param objects the observable array in which objects are stored
 * @param selectedObjectIds the observable array containing the checked object IDs
 * @param availableObjects the objects the user still has available for selection
 * @param selectedObject the selected object / thumbnail
 */
function addSelectedObjects(objects, selectedObjectIds, availableObjects, selectedObject) {
    $.each(selectedObjectIds(), function(index, selectedId) {
        var objs = $.grep(availableObjects(), function(searchedElement, searchedIndex) {
            return elementIdMatch(selectedId, searchedElement);
        });
        var obj = ko.toJS(objs[0]);
        if(objects().length == 0) {
            selectedObject(obj.id);
        }
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
                    $(element).data("writeLock")
                    $(element).data("writeLock", true);
                    modelValue(l.content);
                    $(element).data("writeLock", false);
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
        if(!$(element).data("writeLock")) {
            if (typeof ed !== 'undefined') ed.setContent(value);
        }
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
        theme_advanced_toolbar_align: "left",
        height : "320"
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

/**
 * Helper to preload images
 * @param url for image to preload
 */
$.preloadImages = function() {
	for (var i = 0; i<arguments.length; i++) {
		img = new Image();
		img.src = arguments[i];
	}
}

function isEmpty( inputStr ) {
    if ( null === inputStr || "" == inputStr ) {
        return true;
    }
    return false;
}

function checkSimpleSearchSubmit(oId){
    var o = document.getElementById(oId);

    if (isEmpty(o.value)){
        document.getElementById(oId).style.border="2px dotted firebrick";

        return false;
    }

    return true;
}

$(document).ready(function() {

    // Toggle the dropdown menu's
    $(".dropdown .button, .dropdown button").click(function () {
        if (!$(this).find('span.toggle').hasClass('active')) {
            $('.dropdown-slider').slideUp();
            $('span.toggle').removeClass('active');
        }

// open selected dropown
        $(this).parent().find('.dropdown-slider').slideToggle('fast');
        $(this).find('span.toggle').toggleClass('active');

        return false;
    });

    // Launch TipTip tooltip
    $('.tiptip a.button, .tiptip button').tipTip();

});

// Close open dropdown slider by clicking elsewhwere on page
$(document).bind('click', function (e) {
    if (e.target.id != $('.dropdown').attr('class')) {
        $('.dropdown-slider').slideUp();
        $('span.toggle').removeClass('active');
    }
});







