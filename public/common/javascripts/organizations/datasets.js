function stateToClass(state) {
    var stateClass = "";
    switch (state) {
        case "uploaded":
            stateClass = "uploaded";
            break;
        case "incomplete":
            stateClass = "incomplete";
            break;
        case "error":
            stateClass = 'error';
            break;
        case "enabled":
            stateClass = 'enabled';
            break;
        case "disabled":
            stateClass = 'disabled';
            break;
        case "processing":
            stateClass = 'processing';
            break;
        case "queued":
            stateClass = 'queued';
            break;
        case "parsing":
            stateClass = 'parsing';
            break;
        default:
            stateClass = "";
            break
    }
    return "badge-" + stateClass;
}