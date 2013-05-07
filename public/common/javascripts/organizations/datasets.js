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
            break;
    }
    return stateClass;
}

/**
 * Handles WebSocket connection to DataSet feed
 * @returns {number} the clientId of the connection, necessary for communicating with the server
 */
function feed(orgId, spec, onopen, onmessage, connectionRetries) {
    var localRetries = connectionRetries;
    var clientId = Math.floor(1 + 10001 * Math.random());
    var url = "ws://" + location.host + "/organizations/" + orgId + "/dataset/feed?clientId=" + clientId + '&spec=' + spec;
    ws = new WebSocket(url);
    ws.onopen = function() {
        localRetries = 0;
        onopen();
    };
    ws.onmessage = onmessage;
    ws.onclose = function() {
        if (connectionRetries < 3) {
            console.log("WebSocket disconnected, attempting to reconnect, attempt " + connectionRetries);
            setTimeout(function() { feed(orgId, spec, onopen, onmessage, localRetries + 1) }, 5000);
        } else {
            // TODO here we might want to retry, but with a bigger timeout
            // also instead of a bootbox alert, show a banner
            bootbox.alert("Lost connection with the server. Please refresh the page.");

        }
    };
    return clientId;
}