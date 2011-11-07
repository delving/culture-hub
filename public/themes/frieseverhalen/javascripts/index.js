jQuery(document).ready(function() {
    var imgArr = [
        themePath + 'images/' + 'header-bg-2.jpg',
        themePath + 'images/' + 'header-bg-3.jpg',
        themePath + 'images/' + 'header-bg-4.jpg',
        themePath + 'images/' + 'header-bg-5.jpg',
        themePath + 'images/' + 'header-bg-1.jpg'
    ];
     var preloadArr = new Array();
     var i;

     /* preload images */
     for(i=0; i < imgArr.length; i++){
     preloadArr[i] = new Image();
     preloadArr[i].src = imgArr[i];
     }

     var currImg = 1;
     var intID = setInterval(changeImg, 10000);
     $('.header').append('<div class="header-bg"></div>');
     /* image rotator */
     function changeImg(){
     $('.header-bg').animate({opacity: 0}, 4000, function(){
     $(this).css('background','url(' + preloadArr[currImg++%preloadArr.length].src +') top center no-repeat');
     }).animate({opacity: .7}, 4000);
     }
});