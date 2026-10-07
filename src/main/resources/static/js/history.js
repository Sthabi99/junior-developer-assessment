// Date filters, totals and CSV downloads belong together: they all use the same matching records.
let notices = [];
// A newer filter request must not be replaced by a response that arrives late.
let historyRequest = 0;
let historyLoading = false;
let downloading = false;

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

// Download is available once the current history has loaded and the previous export has finished.
function updateDownloadButton() {
  byId('download').disabled =
    busy || historyLoading || downloading || notices.length === 0;
}

// Both history and CSV requests use these product and date values.
function filters() {
  const from = byId('from').value;
  const to = byId('to').value;
  if (from && to && from > to) throw new Error('From date must be on or before To date.');
  const query = new URLSearchParams();
  if (from) query.set('from', from);
  if (to) query.set('to', to);
  if (byId('history-product').value)
    query.set('productId', byId('history-product').value);
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

// The API does the filtering. These results are used for both the table and its total.
// Keeping one result list means the amount shown always matches the visible rows.
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

byId('history-product').addEventListener('change', loadHistory);

// If the user types a date, the preset no longer describes that range.
// The selector changes to Custom dates, and history is loaded for the typed range.
['from', 'to'].forEach((id) =>
  byId(id).addEventListener('change', () => {
    byId('history-period').value =
      byId('from').value || byId('to').value
        ? 'custom'
        : 'all';
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
