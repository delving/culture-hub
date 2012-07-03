$(document).ready(function () {

    $("#dataset-table").tablesorter();

    var theTable = $('#dataset-table')

    theTable.find("tbody > tr").find("td:eq(1)").mousedown(function () {
        $(this).prev().find(":checkbox").click();
    });

    $("#filter").keyup(function () {
        $.uiTableFilter(theTable, this.value);
    });

    $('#filter-form').submit(
        function () {
            theTable.find("tbody > tr:visible > td:eq(1)").mousedown();
            return false;
        }).focus(); //Give focus to input field

    $('.badge-error').popover({
        placement: 'right'
    });

    $('.rCount').each(function(){
        var $pElem = $(this);
        $pElem.popover({
            content: $(this).next('.schema-count').html()
        });
    });

    // Check to see if there is a filter cookie. If so filter the table
    if ($.cookie("ds-filter")) {
        $.uiTableFilter(theTable, $.cookie("ds-filter"));
        $('input#filter').val($.cookie("ds-filter"));
    }

});

function resetFilter() {
    $.uiTableFilter($('#dataset-table'), '');
    $('input#filter').val('');
    $.cookie("ds-filter", null);
}

function saveFilter() {
    $.cookie("ds-filter",$('input#filter').val(),"/");
}
