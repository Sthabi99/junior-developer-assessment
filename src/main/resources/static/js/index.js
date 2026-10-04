// All portfolio data comes from Spring Boot. No balances are stored in this file.
let portfolio = null;
let notices = [];
let busy = false;
let historyRequest = 0;
const byId = id => document.getElementById(id);
const money = amount => new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(amount));
const selectedProduct = () => portfolio.products.find(product => String(product.id) === byId('product').value);

async function request(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    let details = {};
    try { details = await response.json(); } catch { /* Use a plain message if the server is unavailable. */ }
    const error = new Error(details.message || 'The request failed. Please try again.');
    error.fields = details.fieldErrors || {};
    throw error;
  }
  return response;
}

function setBusy(value) {
  busy = value;
  ['investor-select', 'product', 'amount', 'submit-notice', 'from', 'to', 'history-product', 'clear-filters'].forEach(id => {
    byId(id).disabled = value;
  });
  byId('download').disabled = value || notices.length === 0;
  byId('withdrawal-form').setAttribute('aria-busy', String(value));
}

function message(text, isError = false) {
  byId('feedback').textContent = text;
  byId('feedback').className = isError ? 'error' : 'success';
}

function clearErrors() {
  ['amount', 'product'].forEach(id => byId(id).removeAttribute('aria-invalid'));
  byId('amount-error').textContent = '';
  byId('product-error').textContent = '';
}

function showErrors(error) {
  const fields = error.fields || {};
  message(Object.keys(fields).length ? 'Check the highlighted fields.' : error.message, true);
  if (fields.amount) {
    byId('amount-error').textContent = fields.amount;
    byId('amount').setAttribute('aria-invalid', 'true');
  }
  if (fields.productId) {
    byId('product-error').textContent = fields.productId;
    byId('product').setAttribute('aria-invalid', 'true');
  }
}

function renderProductHelp() {
  const product = selectedProduct();
  if (!product) return;
  byId('amount-help').textContent = `Maximum for this product: ${money(product.maximumWithdrawal)}`;
  byId('eligibility-help').textContent = product.withdrawalAllowed ? product.eligibilityMessage : 'Savings withdrawals remain available. Retirement requires an age above 65.';
}

function renderPie() {
  const total = Number(portfolio.totalBalance);
  const colours = ['#205b95', '#389f8a'];
  let angle = 0;
  const slices = [];
  const descriptions = [];
  byId('balance-chart').replaceChildren();
  portfolio.products.forEach((product, index) => {
    const share = total ? Number(product.balance) / total * 100 : 0;
    const colour = colours[index % colours.length];
    const end = angle + share * 3.6;
    slices.push(`${colour} ${angle}deg ${end}deg`);
    angle = end;
    descriptions.push(`${product.name}: ${share.toFixed(1)}%, ${money(product.balance)}`);
    const item = document.createElement('li');
    item.className = 'pie-legend-item';
    const swatch = document.createElement('span');
    swatch.className = 'legend-swatch';
    swatch.style.backgroundColor = colour;
    swatch.setAttribute('aria-hidden', 'true');
    const details = document.createElement('div');
    [['span', 'legend-name', product.name], ['strong', 'legend-value', money(product.balance)], ['span', 'legend-percentage', `${share.toFixed(1)}% of portfolio`]].forEach(([tag, className, text]) => {
      const element = document.createElement(tag);
      element.className = className;
      element.textContent = text;
      details.append(element);
    });
    item.append(swatch, details);
    byId('balance-chart').append(item);
  });
  byId('balance-pie').style.background = total ? `conic-gradient(${slices.join(', ')})` : '#e3e9f1';
  byId('balance-pie').setAttribute('aria-label', descriptions.join('; ') || 'No investment balance');
}

function renderPortfolio() {
  byId('investor-name').textContent = portfolio.investor.name;
  byId('investor-age').textContent = `${portfolio.investor.age} years old`;
  byId('total-balance').textContent = money(portfolio.totalBalance);
  byId('available-total').textContent = money(portfolio.availableToWithdraw);
  byId('notice-count').textContent = portfolio.noticeCount;
  byId('product-count').textContent = `Across ${portfolio.products.length} investment products`;
  const previous = byId('product').value;
  byId('product').replaceChildren();
  byId('history-product').replaceChildren(new Option('All products', ''));
  byId('products').replaceChildren();
  portfolio.products.forEach(product => {
    byId('product').append(new Option(product.name, product.id));
    byId('history-product').append(new Option(product.name, product.id));
    const row = byId('products').insertRow();
    [product.name, money(product.balance), product.withdrawalAllowed ? money(product.maximumWithdrawal) : 'Not eligible: age must be above 65'].forEach(text => {
      row.insertCell().textContent = text;
    });
  });
  if (portfolio.products.some(product => String(product.id) === previous)) byId('product').value = previous;
  renderProductHelp();
  renderPie();
}

function filters() {
  const from = byId('from').value;
  const to = byId('to').value;
  if (from && to && from > to) throw new Error('From date must be on or before To date.');
  const query = new URLSearchParams();
  if (from) query.set('from', from);
  if (to) query.set('to', to);
  if (byId('history-product').value) query.set('productId', byId('history-product').value);
  return query.toString();
}

function renderHistory() {
  byId('history-rows').replaceChildren();
  byId('download').disabled = busy || notices.length === 0;
  if (!notices.length) {
    const cell = byId('history-rows').insertRow().insertCell();
    cell.colSpan = 4;
    cell.className = 'empty';
    cell.textContent = 'No withdrawal notices match these filters. Create a notice to see it here.';
  }
  notices.forEach(notice => {
    const row = byId('history-rows').insertRow();
    [formatDate(notice.createdAt), notice.productName, money(notice.amount), money(notice.remainingBalance)].forEach(text => {
      row.insertCell().textContent = text;
    });
  });
}

async function loadHistory() {
  // Ignore an older filter response if a newer request has already started.
  const currentRequest = ++historyRequest;
  byId('download').disabled = true;
  try {
    const query = filters();
    const response = await request(`/api/investors/${portfolio.investor.id}/withdrawals?${query}`);
    const result = await response.json();
    if (currentRequest !== historyRequest) return;
    notices = result;
    byId('filter-error').textContent = '';
    renderHistory();
  } catch (error) {
    if (currentRequest !== historyRequest) return;
    notices = [];
    renderHistory();
    byId('filter-error').textContent = error.message;
  }
}

async function loadPortfolio() {
  const response = await request(`/api/investors/${byId('investor-select').value}/portfolio`);
  portfolio = await response.json();
  renderPortfolio();
}

byId('investor-select').addEventListener('change', async () => {
  historyRequest++; // Discard outstanding history requests from the previous investor.
  setBusy(true);
  clearErrors();
  message('');
  byId('amount').value = '';
  byId('from').value = '';
  byId('to').value = '';
  byId('history-product').value = '';
  try {
    await loadPortfolio();
    await loadHistory();
  } catch (error) {
    showErrors(error);
    if (portfolio) byId('investor-select').value = portfolio.investor.id;
  }
  finally { setBusy(false); }
});

byId('withdrawal-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (busy || !portfolio) return;
  clearErrors();
  const product = selectedProduct();
  const raw = byId('amount').value.trim();
  const amount = Number(raw);
  if (!/^\d+(\.\d{1,2})?$/.test(raw) || !Number.isFinite(amount) || amount <= 0) {
    showErrors({ message: 'Check the withdrawal amount.', fields: { amount: 'Enter a positive amount with at most two decimal places.' } });
    return;
  }
  if (!product.withdrawalAllowed) {
    showErrors({ message: product.eligibilityMessage, fields: { productId: product.eligibilityMessage } });
    return;
  }
  if (amount > Number(product.maximumWithdrawal)) {
    const text = `You can withdraw a maximum of ${money(product.maximumWithdrawal)} from this product.`;
    showErrors({ message: text, fields: { amount: text } });
    return;
  }
  setBusy(true);
  byId('submit-notice').textContent = 'Saving...';
  let saved = false;
  try {
    // The server repeats every rule check; browser validation alone is not enough.
    await request(`/api/investors/${portfolio.investor.id}/withdrawals`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId: product.id, amount }),
    });
    saved = true;
    byId('amount').value = '';
    const historyProduct = byId('history-product').value;
    await loadPortfolio();
    byId('history-product').value = historyProduct;
    await loadHistory();
    message('Withdrawal notice saved. Your balance and history have been updated.');
  } catch (error) {
    if (saved) message('The notice was saved, but the page could not refresh. Reload to see it; do not submit it again.', true);
    else showErrors(error);
  }
  finally { setBusy(false); byId('submit-notice').textContent = 'Create withdrawal notice'; }
});

byId('product').addEventListener('change', () => { clearErrors(); message(''); renderProductHelp(); });
['from', 'to', 'history-product'].forEach(id => byId(id).addEventListener('change', loadHistory));
byId('clear-filters').addEventListener('click', () => {
  byId('from').value = ''; byId('to').value = ''; byId('history-product').value = '';
  loadHistory();
});
byId('download').addEventListener('click', async () => {
  byId('download').disabled = true;
  try {
    // Ask Spring Boot for exactly the same filtered records shown in the table.
    const response = await request(`/api/investors/${portfolio.investor.id}/withdrawals/export?${filters()}`);
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url; link.download = `withdrawals-${portfolio.investor.id}.csv`; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) { byId('filter-error').textContent = error.message; }
  finally { byId('download').disabled = notices.length === 0; }
});

function formatDate(value) {
  return new Intl.DateTimeFormat('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

async function start() {
  setBusy(true);
  try {
    const response = await request('/api/investors');
    const investors = await response.json();
    if (!investors.length) throw new Error('No investors were found.');
    byId('investor-select').replaceChildren();
    investors.forEach(investor => byId('investor-select').append(new Option(`${investor.name} (${investor.age})`, investor.id)));
    await loadPortfolio();
    await loadHistory();
    setBusy(false);
  } catch (error) {
    // Keep the form disabled until a real portfolio has loaded.
    byId('investor-name').textContent = 'Portfolio unavailable';
    showErrors(error);
  }
}

start();
