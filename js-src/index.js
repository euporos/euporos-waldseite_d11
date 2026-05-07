import 'photoswipe/style.css';
import '../styles/main.scss';
import PhotoSwipeLightbox from 'photoswipe/lightbox';

addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-gallery]').forEach((el) => {
    if (!el.id) return;
    new PhotoSwipeLightbox({
      gallery: '#' + el.id,
      children: 'a',
      pswpModule: () => import('photoswipe'),
    }).init();
  });
});
