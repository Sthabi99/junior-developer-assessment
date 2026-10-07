// A preset fills both date inputs, so the table, total and CSV all use the same range.
// Current month starts on the first day; 3 and 6 months count back from today.
// Reset passes "all", which removes both date limits instead of restoring the default.
function setHistoryPeriod(period) {
  byId('history-period').value = period;
  if (period === 'all') {
    byId('from').value = '';
    byId('to').value = '';
    return;
  }
  const today = new Date();
  const months = period === 'current' ? 0 : Number(period);
  const from = new Date(today.getFullYear(), today.getMonth() - months, 1);
  if (period !== 'current') {
    // For example, counting back from the 31st may reach a month with only 30 days.
    // Using the last valid day prevents JavaScript from rolling into the next month.
    const lastDay = new Date(from.getFullYear(), from.getMonth() + 1, 0).getDate();
    from.setDate(Math.min(today.getDate(), lastDay));
  }
  const dateValue = (date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  byId('from').value = dateValue(from);
  byId('to').value = dateValue(today);
}

function setDefaultHistoryDates() {
  setHistoryPeriod('3');
}

// All portfolio data comes from Spring Boot. No balances are stored in this file.

let portfolio = null;

let notices = [];

let busy = false;

// Each request gets a number. Only the latest history response may update the table.
let historyRequest = 0;
let historyLoading = false;
let downloading = false;

const byId = (id) => document.getElementById(id);

const money = (amount) =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(amount));

const selectedProduct = () =>
  portfolio.products.find((product) => String(product.id) === byId('product').value);

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

// Download is available once the current history has loaded and the previous export has finished.
function updateDownloadButton() {
  byId('download').disabled = busy || historyLoading || downloading || notices.length === 0;
}

// The same message area shows success messages and request errors.
function message(text, isError = false) {
  byId('feedback').textContent = text;

  byId('feedback').className = isError ? 'error' : 'success';
}

// Errors from the previous attempt are cleared before the form is checked again.
function clearErrors() {
  ['amount', 'product'].forEach((id) => byId(id).removeAttribute('aria-invalid'));

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

// The selected product determines the amount limit and the eligibility message.
function renderProductHelp() {
  const product = selectedProduct();

  if (!product) return;

  byId('amount-help').textContent = `Maximum for this product: ${money(product.maximumWithdrawal)}`;

  byId('eligibility-help').textContent = product.withdrawalAllowed
    ? product.eligibilityMessage
    : 'Savings withdrawals remain available. Retirement withdrawals are available only to investors older than 65.';
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

  byId('balance-pie').style.background = total ? `conic-gradient(${slices.join(', ')})` : '#e3e9f1';

  byId('balance-pie').setAttribute(
    'aria-label',
    descriptions.join('; ') || 'No investment balance',
  );
}

// A portfolio response supplies the account details, product choices and balances shown here.
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

// Both history and CSV requests use these product and date values.
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

// Only withdrawals returned for the current filters are included in the table and total.
function renderHistory() {
  // Amounts are added in cents because JavaScript decimal addition can produce small rounding errors.

  let totalCents = 0;

  notices.forEach((notice) => {
    totalCents += Math.round(Number(notice.amount) * 100);
  });

  byId('history-total-amount').textContent = money(totalCents / 100);

  const productName = byId('history-product').selectedOptions[0].textContent;

  const from = byId('from').value;

  const to = byId('to').value;

  const dates =
    from && to ? `${from} to ${to}` : from ? `From ${from}` : to ? `Up to ${to}` : 'All dates';

  byId('history-total-scope').textContent = `${productName} · ${dates}`;

  byId('history-rows').replaceChildren();

  updateDownloadButton();

  if (!notices.length) {
    const cell = byId('history-rows').insertRow().insertCell();

    cell.colSpan = 4;

    cell.className = 'empty';

    cell.textContent = 'No withdrawal notices match these filters. Create a notice to see it here.';
  }

  notices.forEach((notice) => {
    const row = byId('history-rows').insertRow();

    [
      formatDate(notice.createdAt),
      notice.productName,
      money(notice.amount),
      money(notice.remainingBalance),
    ].forEach((text) => {
      row.insertCell().textContent = text;
    });
  });
}

// The history request includes the selected investor and the current filters.
async function loadHistory() {
  // Changing filters quickly can leave more than one request running.
  // The request number stops an older response from replacing newer results.

  const currentRequest = ++historyRequest;

  historyLoading = true;
  updateDownloadButton();

  byId('history-total-amount').textContent = 'Loading…';

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

    byId('history-total-amount').textContent = 'Unavailable';

    byId('filter-error').textContent = error.message;
  } finally {
    // Only the latest request clears the loading flag; earlier responses leave it alone.
    if (currentRequest === historyRequest) {
      historyLoading = false;
      updateDownloadButton();
    }
  }
}

// The selected investor ID is used to fetch their portfolio.
async function loadPortfolio() {
  const response = await request(`/api/investors/${byId('investor-select').value}/portfolio`);

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

// The form checks the amount and eligibility before showing confirmation or sending a request.
byId('withdrawal-form').addEventListener('submit', async (event) => {
  event.preventDefault();

  if (busy || !portfolio) return;

  clearErrors();

  const product = selectedProduct();

  const raw = byId('amount').value.trim();

  const amount = Number(raw);

  if (!/^\d+(\.\d{1,2})?$/.test(raw) || !Number.isFinite(amount) || amount <= 0) {
    showErrors({
      message: 'Check the withdrawal amount.',
      fields: { amount: 'Enter a positive amount with at most two decimal places.' },
    });

    return;
  }

  if (!product.withdrawalAllowed) {
    showErrors({
      message: product.eligibilityMessage,
      fields: { productId: product.eligibilityMessage },
    });

    return;
  }

  if (amount > Number(product.maximumWithdrawal)) {
    const text = `You can withdraw a maximum of ${money(product.maximumWithdrawal)} from this product.`;

    showErrors({ message: text, fields: { amount: text } });

    return;
  }

  // The amount has passed the browser checks. Confirmation explains the change to both balances.
  // Cancel returns here before the POST request, so no notice or balance change is saved.
  // Spring Boot checks the rules again if the user confirms.
  const confirmed = window.confirm(
    `Confirm a withdrawal of ${money(amount)} from your ${product.name.toLowerCase()}?\n\nYour ${product.type === 'RETIREMENT' ? 'retirement' : 'savings'} balance and total portfolio balance will both decrease by ${money(amount)}.\n\nSelect OK to confirm, or Cancel to go back.`,
  );
  if (!confirmed) {
    message('Withdrawal cancelled. Your balance has not changed.');
    return;
  }

  setBusy(true);

  byId('submit-notice').textContent = 'Saving...';

  let saved = false;

  try {
    // The server repeats every rule check; browser validation alone is not enough.

    await request(`/api/investors/${portfolio.investor.id}/withdrawals`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },

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
    if (saved)
      message(
        'The notice was saved, but the page could not refresh. Reload to see it; do not submit it again.',
        true,
      );
    else showErrors(error);
  } finally {
    setBusy(false);
    byId('submit-notice').textContent = 'Create withdrawal notice';
  }
});

byId('product').addEventListener('change', () => {
  clearErrors();
  message('');
  renderProductHelp();
});

byId('history-product').addEventListener('change', loadHistory);

// If the user types a date, the preset no longer describes that range.
// The selector changes to Custom dates, and history is loaded for the typed range.
['from', 'to'].forEach((id) =>
  byId(id).addEventListener('change', () => {
    byId('history-period').value = byId('from').value || byId('to').value ? 'custom' : 'all';
    loadHistory();
  }),
);

byId('history-period').addEventListener('change', () => {
  setHistoryPeriod(byId('history-period').value);
  loadHistory();
});

byId('clear-filters').addEventListener('click', () => {
  setHistoryPeriod('all');
  byId('history-product').value = '';

  loadHistory();
});

byId('download').addEventListener('click', async () => {
  if (busy || historyLoading || downloading || !notices.length) return;
  downloading = true;
  updateDownloadButton();

  
  // The filename uses the investor selected when Download was clicked.
  // The saved ID keeps the filename linked to the account that requested the export.
  const investorId = portfolio.investor.id;

  try {
    // The export endpoint receives the same filters as the history request.

    const response = await request(`/api/investors/${investorId}/withdrawals/export?${filters()}`);

    const url = URL.createObjectURL(await response.blob());

    const link = document.createElement('a');

    link.href = url;
    link.download = `withdrawals-${investorId}.csv`;
    link.click();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) {
    byId('filter-error').textContent = error.message;
  } finally {
    downloading = false;
    updateDownloadButton();
  }
});

// Saved timestamps are displayed using the South African date format.
function formatDate(value) {
  return new Intl.DateTimeFormat('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}

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
      byId('investor-select').append(new Option(`${investor.name} (${investor.age})`, investor.id)),
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

start();
