// import 'photoswipe/style.css';
import '@splidejs/splide/css';
import '../styles/main.scss';
// import PhotoSwipeLightbox from 'photoswipe/lightbox';
import Splide from '@splidejs/splide';

addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-gallery]').forEach((el) => {
    if (!el.id) return;

    new Splide(el, {
      type: 'loop',
      perPage: 1,
      gap: '1rem',
      pagination: false,
      arrows: true,
      heightRatio: 0.6667,
      cover: true,
      keyboard: 'global',
    }).mount();

    // Click-to-fullscreen lightbox disabled — slide <a> falls back to
    // opening the full-res image in a new tab via target="_blank".
    // new PhotoSwipeLightbox({
    //   gallery: '#' + el.id,
    //   children: '.splide__slide:not(.splide__slide--clone) a',
    //   pswpModule: () => import('photoswipe'),
    // }).init();
  });
});
