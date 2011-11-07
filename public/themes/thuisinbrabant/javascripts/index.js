jQuery(document).ready(function() {
    // random background images for the header
    var imgArr = [
        themePath + 'images/' + 'top_image_bhic1.jpg',
        themePath + 'images/' + 'top_image_bhic2.jpg',
        themePath + 'images/' + 'top_image_brabantcoll1.jpg',
        themePath + 'images/' + 'top_image_brabantcoll2.jpg',
        themePath + 'images/' + 'top_image_nbm1.jpg',
        themePath + 'images/' + 'top_image_nbm2.jpg',
        themePath + 'images/' + 'top_image_textielm1.jpg',
        themePath + 'images/' + 'top_image_textielm2.jpg'
    ];
     var preloadArr = new Array();
     var i;

     /* preload images */
     for(i=0; i < imgArr.length; i++){
     preloadArr[i] = new Image();
     preloadArr[i].src = imgArr[i];
     }

     var currImg = 1;
     var intID = setInterval(changeImg, 6000);
     $('.header').append('<div class="header-bg"></div>');
     /* image rotator */
     function changeImg(){
     $('.header-bg').animate({opacity: 0}, 1000, function(){
     $(this).css('background','url(' + preloadArr[currImg++%preloadArr.length].src +') top center no-repeat');
     }).animate({opacity: 1}, 2000);
     }



    //$('.header').css({'background-image': 'url(' + themePath + 'images/' + images[Math.floor(Math.random() * images.length)] + ')'});
});