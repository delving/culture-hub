/**
 * Initialisation function for pagination
 * @param pageObject.count - int: total nr. of records
 * @param pageObject.page - int: current page nr.
 * @param pageObject.previous - string: "previous" text
 * @param pageObject.next - string: "next" text
 * @param pageObject.link - sting: querystring for url
 * @param pageObject.itemsPerPage - int: nr of records per page
 * @param pageObject.selector - string: class or id of target element to create pager in
 * The pageObject is created in list.html
 */
function initPagination() {
    // Create content inside pagination element
    $(pageObject.selector).pagination(pageObject.count, {
        one_indexed: 1,
        current_page:  parseInt(pageObject.page),
        num_edge_entries:1,
        num_display_entries: 6,
        callback: function() {},
        items_per_page: pageObject.itemsPerPage,
        prev_text: pageObject.previous,
        next_text:pageObject.next,
        link_to: pageObject.link
    });
 }
$(document).ready(function(){
    initPagination();
});