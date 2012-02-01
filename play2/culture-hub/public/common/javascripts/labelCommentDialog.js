$("#addLabel").click(function () {
    if ($("#add-comment.dialog")) {
        $("#addComment").removeClass("on");
        $("#add-comment-dialog").hide("slow");
    }
    $("#add-label-dialog").toggle("slow");
    $(this).toggleClass("on");
});
$("#addComment").click(function () {
    if ($("#add-label.dialog")) {
        $("#addLabel").removeClass("on");
        $("#add-label-dialog").hide("slow");
    }
    $("#add-comment-dialog").toggle("slow");
    $(this).toggleClass("on");
});