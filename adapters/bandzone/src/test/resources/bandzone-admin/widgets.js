// Bandzone's city search and club autocomplete, as the admin forms behave (seen in the live tests):
// a pick re-renders the edit form (AJAX) about a moment later, so the old elements go stale.
const TOWNS = {
  'hranice': [['Hranice', 'okres Přerov, kraj Olomoucký', 'town-hranice-prerov'],
              ['Hranice', 'okres České Budějovice, kraj Jihočeský', 'town-hranice-cb'],
              ['Hranice', 'okres Cheb, kraj Karlovarský', 'town-hranice-cheb']],
  'košice': [['Košice', 'okres Tábor, kraj Jihočeský', 'town-kosice-tabor'],
             ['Košice', 'okres Košice I, kraj Košický, Slovensko', 'town-kosice-sk']]
};
const CLUBS = [['Zámecký klub', 'club-17'], ['Zámecký klub Hranice - terasa', 'club-18'], ['Klub 007', 'club-7']];

function field(name) { return document.querySelector('[name="' + name + '"]'); }

function reRender() {
  const form = document.getElementById('frmupdateForm-send') && document.querySelector('form');
  if (form) setTimeout(() => form.replaceWith(form.cloneNode(true)), 300);
}

document.addEventListener('click', e => {
  if (e.target.name === 'cityId__container[searchButton]') {
    e.preventDefault();
    const list = document.getElementById('towns');
    list.innerHTML = '';
    for (const [name, description, id] of TOWNS[field('cityId__container[textInput]').value.toLowerCase()] || []) {
      list.insertAdjacentHTML('beforeend', `<li class="town" data-id="${id}"><span class="name">${name}</span>`
          + `<span class="description">${description}</span></li>`);
    }
    list.insertAdjacentHTML('beforeend', '<li>Přidat nové město</li>');
    return;
  }
  const town = e.target.closest && e.target.closest('li.town');
  if (town) {
    field('cityId').value = town.dataset.id;
    field('cityId__container[textInput]').value = town.querySelector('.name').innerText;
    document.getElementById('towns').innerHTML = '';
    reRender();
    return;
  }
  const club = e.target.closest && e.target.closest('ul.ui-autocomplete li');
  if (club) {
    e.preventDefault();
    field('venueId').value = club.dataset.id;
    field('venueId__container[textInput]').value = club.querySelector('h4.title').innerText;
    club.parentElement.style.display = 'none';
    reRender();
  }
});

document.addEventListener('input', e => {
  if (e.target.name !== 'venueId__container[textInput]') return;
  const q = e.target.value.toLowerCase();
  const menu = document.querySelector('ul.ui-autocomplete');
  menu.innerHTML = '';
  for (const [name, id] of CLUBS.filter(([name]) => name.toLowerCase().includes(q))) {
    menu.insertAdjacentHTML('beforeend', `<li data-id="${id}"><a href="#"><h4 class="title">${name}</h4></a></li>`);
  }
  menu.style.display = menu.children.length ? 'block' : 'none';
});
