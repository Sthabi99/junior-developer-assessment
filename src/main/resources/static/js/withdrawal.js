// The withdrawal form uses the selected portfolio from index.js.
// The browser catches input mistakes first; the backend still validates every submitted notice.
// The selected product determines the amount limit and the eligibility message.
function renderProductHelp() {
  const product = selectedProduct();
  if (!product) return;
  byId('amount-help').textContent =
    `Maximum for this product: ${money(product.maximumWithdrawal)}`;
  byId('eligibility-help').textContent = product.withdrawalAllowed
    ? product.eligibilityMessage
    : 'Savings withdrawals remain available. Retirement withdrawals are available only to investors older than 65.';
}

// Nothing is saved until the amount is valid and the user confirms it.
// A failed check returns here, leaving the entered amount available to correct.
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
