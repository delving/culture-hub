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