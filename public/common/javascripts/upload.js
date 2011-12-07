/**
 * Initialize the jQuery File Upload widget:
 */
function initUploadWidget() {
    $.widget('blueimpUIX.fileupload', $.blueimpUI.fileupload, {

        options: {
            errorMessages: {
                maxFileSize: 'File is too big',
                minFileSize: 'File is too small',
                acceptFileTypes: 'Filetype not allowed',
                maxNumberOfFiles: 'Max number of files exceeded'
            }
        },

        _renderUploadTemplate: function (files) {
            var that = this,
                    rows = $();
            $.each(files, function (index, file) {
                file = that._uploadTemplateHelper(file);
                var row = $('<tr class="template-upload">' +
                        '<td class="preview"></td>' +
                        '<td class="selected"></td>' +
                        '<td class="name"></td>' +
                        '<td class="size"></td>' +
                        (file.error ?
                                '<td class="error" colspan="2"></td>'
                                :
                                '<td class="progress"><div></div></td>' +
                                        '<td class="start"><button>Start</button></td>'
                                ) +
                        '<td class="cancel"><button>Cancel</button></td>' +
                        '</tr>');
                row.find('.name').text(file.name);
                row.find('selected').append('<input type="radio">');
                row.find('.size').text(file.sizef);
                if (file.error) {
                    row.addClass('ui-state-error');
                    row.find('.error').text(
                            that.options.errorMessages[file.error] || file.error
                    );
                }
                rows = rows.add(row);
            });
            return rows;
        },

        _renderDownloadTemplate: function (files) {
            var that = this,
                    rows = $();
            $.each(files, function (index, file) {
                file = that._downloadTemplateHelper(file);
                var row = $('<tr class="template-download">' +
                        (file.error ?
                                '<td></td>' +
                                        '<td class="name"></td>' +
                                        '<td class="size"></td>' +
                                        '<td class="error" colspan="2"></td>'
                                :
                                '<td class="id" style="display: none;"></td>' +
                                '<td class="selected"></td>' +
                                '<td class="preview"></td>' +
                                '<td class="name"><a></a></td>' +
                                '<td class="size"></td>' +
                                '<td colspan="2"></td>'
                                ) +
                        '<td class="delete"><button>Delete</button></td>' +
                        '</tr>');
                row.find('.size').text(file.sizef);
                if (file.error) {
                    row.find('.name').text(file.name);
                    row.addClass('ui-state-error');
                    row.find('.error').text(
                            that.options.errorMessages[file.error] || file.error
                    );
                } else {
                    row.find('.name a').text(file.name);
                    if (file.thumbnail_url) {
                        row.find('.preview').append('<a><img width="80"></a>')
                                .find('img').prop('src', file.thumbnail_url);
                        row.find('a').prop('target', '_blank');
                    }
                    var selected = file.selected || files.length == 1;
                    row.find('.id').html(file.id);
                    row.find('.selected').append('<input type="radio" />').find('input').prop('checked', selected).attr('name', 'files');
                    row.find('a').prop('href', file.url);
                    row.find('.delete button')
                            .attr('data-type', file.delete_type)
                            .attr('data-url', file.delete_url);
                }
                rows = rows.add(row);
            });
            return rows;
        },

        _startHandler: function (e) {
            e.preventDefault();
            var button = $(this);
            var tmpl = button.closest('.template-upload');
            var data = tmpl.data('data');
            if (data && data.submit && !data.jqXHR) {
                data.jqXHR = data.submit();
                $(this).fadeOut();
            }
        }
    });
}