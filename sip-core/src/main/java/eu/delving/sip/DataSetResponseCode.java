package eu.delving.sip;

/**
 * An enumeration of the responses that can be returned from the DataSetController
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum DataSetResponseCode {
    THANK_YOU,
    GOT_IT_ALREADY,
    DATA_SET_NOT_FOUND,
    STATE_CHANGE_FAILURE,
    ACCESS_KEY_FAILURE,
    READY_TO_RECEIVE,
    NEWORK_ERROR,
    SYSTEM_ERROR,
    UNKNOWN_RESPONSE
}
