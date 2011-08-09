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
 * Post knockoutJS form data as JSON string, into a parameter called "data"
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
        data: {data: ko.toJSON(viewModel) },
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
    $.get(url, function(data) {
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
        ko.mapping.updatefromJSON(viewModel, data)
    } else {
        $.extend(viewModel, ko.mapping.fromJS(data));
        if (typeof scope !== 'undefined') {
            ko.applyBindings(viewModel, scope);
        } else {
            ko.applyBindings(viewModel);
        }
    }
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