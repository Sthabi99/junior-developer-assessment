// This file loads the accounts and draws the portfolio. The form and history have their own files.
// The shared account data and API helpers stay here because all three sections use them.
// All portfolio data comes from Spring Boot. No balances are stored in this file.

let portfolio = null;

let busy = false;

// byId is a shorter name for finding an element by its HTML id.
const byId = (id) => document.getElementById(id);

const money = (amount) =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(amount));

const selectedProduct = () =>
  portfolio.products.find(
    (product) => String(product.id) === byId('product').value,
  );

// API errors are read here so each form can use the same error format.
async function request(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    let details = {};
    try {
      details = await response.json();
    } catch {
      /* Some failed responses have no JSON body, so the fallback message below is used. */
    }
    const error = new Error(details.message || 'The request failed. Please try again.');
    error.fields = details.fieldErrors || {};
    throw error;
  }
  return response;
}

// The form stays disabled while a request is running to avoid duplicate submissions.
function setBusy(value) {
  busy = value;
  [
    'investor-select',
    'product',
    'amount',
    'submit-notice',
    'from',
    'to',
    'history-product',
    'clear-filters',
    'history-period',
  ].forEach((id) => {
    byId(id).disabled = value;
  });
  byId('download').disabled = value || notices.length === 0;
  byId('withdrawal-form').setAttribute('aria-busy', String(value));
}

// Saving, cancelling and request errors use the same message area below the form.
function message(text, isError = false) {
  byId('feedback').textContent = text;
  byId('feedback').className = isError ? 'error' : 'success';
}

// Errors from the previous attempt are cleared before the form is checked again.
function clearErrors() {
  ['amount', 'product'].forEach((id) =>
    byId(id).removeAttribute('aria-invalid'),
  );
  byId('amount-error').textContent = '';
  byId('product-error').textContent = '';
}

// The backend sends field names with validation errors. These match the amount and product fields.
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

// Each pie slice shows that product's share of the total balance.
function renderPie() {
  const total = Number(portfolio.totalBalance);
  const colours = ['#205b95', '#389f8a'];
  let angle = 0;
  const slices = [];
  const descriptions = [];
  byId('balance-chart').replaceChildren();
  portfolio.products.forEach((product, index) => {
    const share = total ? (Number(product.balance) / total) * 100 : 0;
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
    [
      ['span', 'legend-name', product.name],
      ['strong', 'legend-value', money(product.balance)],
      ['span', 'legend-percentage', `${share.toFixed(1)}% of portfolio`],
    ].forEach(([tag, className, text]) => {
      const element = document.createElement(tag);
      element.className = className;
      element.textContent = text;
      details.append(element);
    });
    item.append(swatch, details);
    byId('balance-chart').append(item);
  });
  byId('balance-pie').style.background = total
    ? `conic-gradient(${slices.join(', ')})`
    : '#e3e9f1';
  document
    .getElementById('balance-pie')
    .setAttribute('aria-label', descriptions.join('; ') || 'No investment balance');
}

// Switching accounts rebuilds the product options as well as the balances.
// This prevents the previous investor's product from staying selected.
function renderPortfolio() {
  byId('investor-name').textContent = portfolio.investor.name;
  byId('investor-age').textContent = `${portfolio.investor.age} years old`;
  byId('total-balance').textContent = money(portfolio.totalBalance);
  byId('available-total').textContent = money(portfolio.availableToWithdraw);
  byId('notice-count').textContent = portfolio.noticeCount;
  byId('product-count').textContent =
    `Across ${portfolio.products.length} investment products`;
  const previous = byId('product').value;
  byId('product').replaceChildren();
  byId('history-product').replaceChildren(new Option('All products', ''));
  byId('products').replaceChildren();
  portfolio.products.forEach((product) => {
    byId('product').append(new Option(product.name, product.id));
    byId('history-product').append(new Option(product.name, product.id));
    const row = byId('products').insertRow();
    [
      product.name,
      money(product.balance),
      product.withdrawalAllowed
        ? money(product.maximumWithdrawal)
        : 'Not eligible: age must be above 65',
    ].forEach((text) => {
      row.insertCell().textContent = text;
    });
  });
  if (portfolio.products.some((product) => String(product.id) === previous))
    byId('product').value = previous;
  renderProductHelp();
  renderPie();
}

// The selected investor ID is used to fetch their portfolio.
async function loadPortfolio() {
  const response = await request(
    `/api/investors/${byId('investor-select').value}/portfolio`,
  );
  portfolio = await response.json();
  renderPortfolio();
}

byId('investor-select').addEventListener('change', async () => {
  historyRequest++; // Responses still loading for the previous investor are now outdated.
  setBusy(true);
  clearErrors();
  message('');
  byId('amount').value = '';
  setDefaultHistoryDates();
  byId('history-product').value = '';
  try {
    await loadPortfolio();
    await loadHistory();
  } catch (error) {
    showErrors(error);
    if (portfolio) byId('investor-select').value = portfolio.investor.id;
  } finally {
    setBusy(false);
  }
});

// The account list loads first because the portfolio request needs an investor ID.
async function start() {
  setDefaultHistoryDates();
  setBusy(true);
  try {
    const response = await request('/api/investors');
    const investors = await response.json();
    if (!investors.length) throw new Error('No investors were found.');
    byId('investor-select').replaceChildren();
    investors.forEach((investor) =>
      document
        .getElementById('investor-select')
        .append(new Option(`${investor.name} (${investor.age})`, investor.id)),
    );
    await loadPortfolio();
    await loadHistory();
    setBusy(false);
  } catch (error) {
    // A failed startup leaves the form disabled because there is no portfolio to submit against.
    byId('investor-name').textContent = 'Portfolio unavailable';
    showErrors(error);
  }
}

// Deferred scripts run before this event, so history.js and withdrawal.js are ready first.
document.addEventListener('DOMContentLoaded', start);
