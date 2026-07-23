const typed = document.querySelector('#typed');
const snippets = ['(map #(+ % 1) [1 2 3])', '(promise/then job render)', '(require [crypto.hash :as hash])'];
let snippetIndex = 0;

setInterval(() => {
  if (!typed) return;
  snippetIndex = (snippetIndex + 1) % snippets.length;
  typed.innerHTML = `${snippets[snippetIndex]}<span class="cursor"></span>`;
}, 4200);

const menu = document.querySelector('.menu-toggle');
const nav = document.querySelector('.nav');
menu?.addEventListener('click', () => {
  nav?.classList.toggle('open');
});
