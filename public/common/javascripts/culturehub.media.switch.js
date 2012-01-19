/*
 * Copyright 2012 Delving B.V.
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

//function mediaSwitch(mediaWidth, parent, trigger, activeClass) {
function mediaSwitch(options) {

    var settings = $.extend({
            'targetElement'         : '#msContainer',
            'triggerElement'        : '.msTrigger',
            'triggerActiveClass'    : 'active',
            'mediaWidth'            : '350'
    }, options);

    $(options.triggerElement).each(function(index) {

        var mimeType = $(this).find("input[name='mimeType']").attr("value");
        var targetId = $(this).find("input[name='targetId']").attr("value");
        var fileSrc = $(this).find("input[name='fileSrc']").attr("value");
        var previewSrc = $(this).find("input[name='previewSrc']").attr("value");

        var regexAudio = /^audio/;
        var regexVideo = /^video/;
        var regexImage = /^image/;

        $(this).click(function() {

            $("div#" + targetId).html('').fadeOut('fast');

            $(options.triggerElement).removeClass("active");

            $(this).addClass("active");
            var html;
            if (mimeType.match(regexImage)) {
                html = '<img src="' + previewSrc + '" />';
            }
            else if (mimeType.match(regexAudio)) {
                html = '<audio controls="controls" type="audio/mp3" src="' + fileSrc + '"></audio>';
            }
            else if (mimeType.match(regexVideo)) {
                html = '<video controls="controls" preload="none"><source type="' + mimeType + '" src="' + fileSrc + '"/></video>';
            }

            $("div#" + targetId).html(html).fadeIn('slow');

            $('video, audio').mediaelementplayer({
                enableAutosize: true,
                defaultVideoWidth: 350,
                defaultVideoHeight: 350,
                videoWidth: options.mediaWidth,
                videoHeight: -1,
                audioWidth: options.mediaWidth,
                audioHeight: 30,
                plugins: ['flash','silverlight'],
                features: ['playpause','progress','current','duration','volume','fullscreen']
            });
        });
    })
}


