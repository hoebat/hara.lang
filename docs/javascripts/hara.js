document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('pre code').forEach((block) => {
    block.addEventListener('click', () => block.classList.add('hara-code-touched'), {once: true});
  });
});
