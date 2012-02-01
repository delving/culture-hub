/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function($) {
  $.uiTableFilter = function(jq, phrase, column, ifHidden){
    var new_hidden = false;
    if( this.last_phrase === phrase ) return false;

    var phrase_length = phrase.length;
    var words = phrase.toLowerCase().split(" ");

    // these function pointers may change
    var matches = function(elem) { elem.show() }
    var noMatch = function(elem) { elem.hide(); new_hidden = true }
    var getText = function(elem) { return elem.text() }

    if( column ) {
      var index = null;
      jq.find("thead > tr:last > th").each( function(i){
        if( $.trim($(this).text()) == column ){
          index = i; return false;
        }
      });
      if( index == null ) throw("given column: " + column + " not found")

      getText = function(elem){ return $(elem.find(
        ("td:eq(" + index + ")")  )).text()
      }
    }

    // if added one letter to last time,
    // just check newest word and only need to hide
    if( (words.size > 1) && (phrase.substr(0, phrase_length - 1) ===
          this.last_phrase) ) {

      if( phrase[-1] === " " )
      { this.last_phrase = phrase; return false; }

      var words = words[-1]; // just search for the newest word

      // only hide visible rows
      matches = function(elem) {;}
      var elems = jq.find("tbody:first > tr:visible")
    }
    else {
      new_hidden = true;
      var elems = jq.find("tbody:first > tr")
    }

    elems.each(function(){
      var elem = $(this);
      $.uiTableFilter.has_words( getText(elem), words, false ) ?
        matches(elem) : noMatch(elem);
    });

    last_phrase = phrase;
    if( ifHidden && new_hidden ) ifHidden();
    return jq;
  };

  // caching for speedup
  $.uiTableFilter.last_phrase = ""

  // not jQuery dependent
  // "" [""] -> Boolean
  // "" [""] Boolean -> Boolean
  $.uiTableFilter.has_words = function( str, words, caseSensitive )
  {
    var text = caseSensitive ? str : str.toLowerCase();
    for (var i=0; i < words.length; i++) {
      if (text.indexOf(words[i]) === -1) return false;
    }
    return true;
  }
}) (jQuery);
