import 'photoswipe/style.css';
import '@splidejs/splide/css';
import '../styles/main.scss';
import PhotoSwipeLightbox from 'photoswipe/lightbox';
import Splide from '@splidejs/splide';

addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-gallery]').forEach((el) => {
    if (!el.id) return;

    new Splide(el, {
      type: 'loop',
      perPage: 1,
      gap: '1rem',
      pagination: true,
      arrows: true,
      heightRatio: 0.6667,
      cover: true,
      keyboard: 'global',
      autoplay: true,
      interval: 4000,
      pauseOnHover: true,
      pauseOnFocus: true,
    }).mount();

    // Click-to-fullscreen lightbox stays disabled on Splide carousels —
    // slide <a> falls back to opening the full-res image in a new tab.
  });

  document.querySelectorAll('[data-photoswipe-gallery]').forEach((el, i) => {
    if (!el.id) el.id = `pswp-gallery-${i}`;
    new PhotoSwipeLightbox({
      gallery: '#' + el.id,
      children: 'a',
      pswpModule: () => import('photoswipe'),
    }).init();
  });
});
